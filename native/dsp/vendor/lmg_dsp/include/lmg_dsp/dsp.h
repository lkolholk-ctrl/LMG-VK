#ifndef LMG_DSP_DSP_H
#define LMG_DSP_DSP_H

#include "lmg_dsp/export.h"
#include <array>
#include <cstdint>
#include <memory>

namespace lmg { namespace dsp {

constexpr std::uint32_t kGraphicBands = 10;
constexpr std::uint32_t kParametricBands = 8;
constexpr std::array<double, kGraphicBands> kGraphicFrequencies = {
    31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0
};

enum class Status : std::uint32_t {
    Ok = 0, InvalidArgument = 1, NotPrepared = 2,
    QueueFull = 3, InvalidState = 4, OutOfMemory = 5
};
enum class EqMode : std::uint32_t { Off = 0, Graphic = 1, Parametric = 2 };
enum class FilterType : std::uint32_t {
    Peaking = 0, LowShelf = 1, HighShelf = 2,
    LowPass = 3, HighPass = 4, Notch = 5
};
enum class Preset : std::uint32_t { Flat = 0, Warm = 1, Voice = 2, Bright = 3 };

struct Band {
    bool enabled = false;
    FilterType type = FilterType::Peaking;
    double frequencyHz = 1000.0; // [10, 0.475 * sampleRate]
    double gainDb = 0.0;        // [-24, +24]; ignored by LP/HP/notch
    double q = 0.7071067811865475244; // [0.1, 20]; ignored by shelves
    double slope = 1.0;         // RBJ S in [0.1, 1]; shelves only, NOT dB/oct
};

struct LimiterParameters {
    bool enabled = false;
    double thresholdDb = -1.0;  // [-36, 0], <= ceilingDb; no makeup gain
    double ceilingDb = -0.3;    // [-12, 0]; sample-peak, not true-peak
    double attackMs = 1.0;     // finite linear-gain slew; see DSP.md
    double releaseMs = 80.0;   // [5, 2000], exponential time constant
};

struct Parameters {
    bool bypass = false;       // entire effect chain, but NOT its fixed delay
    EqMode mode = EqMode::Off;  // exactly one EQ selection
    double preampDb = 0.0;     // [-36, +12]
    double headroomDb = 0.0;   // explicit additional attenuation [0, 24]
    std::array<double, kGraphicBands> graphicGainDb{}; // [-18, +18]
    std::array<Band, kParametricBands> bands{};
    LimiterParameters limiter{};
    double transitionMs = 20.0; // [5, 200], complete-snapshot transition
};

struct PrepareSpec {
    double sampleRate = 48000.0;    // [44100, 192000]; no resampling
    std::uint32_t channels = 2;    // 1 or 2
    std::uint32_t maxBlockFrames = 1024; // [1, 65536]
    double lookaheadMs = 5.0;      // [0, 20], fixed until next prepare
};

struct EndOptions {
    std::uint32_t tailFrames = 0;     // explicit zero-input IIR tail, <= 10 s
    std::uint32_t tailFadeFrames = 0; // <= tailFrames; 0 or >= 2
};

struct ProcessInfo {
    // Number of leading frames in THIS returned buffer attributable to priming.
    std::uint32_t primingFrames = 0;
    std::uint32_t sanitizedInputSamples = 0; // NaN/Inf or float subnormal -> 0
    std::uint32_t saturatedOutputSamples = 0; // double outside float range
    std::uint32_t numericResets = 0; // nonfinite biquad arithmetic recovery
};

// Both helpers are pure: they do not modify a running Processor.
LMG_DSP_API Parameters defaultParameters() noexcept;
LMG_DSP_API bool makePreset(Preset preset, Parameters& destination) noexcept;
LMG_DSP_API const char* statusString(Status status) noexcept;

// Full threading/ownership/error contract: docs/API.md.
// Lifecycle (prepare/destruction) requires BOTH threads to be quiescent.
// submit(): ONE control producer; process/reset/endInput/drain: ONE audio consumer.
class LMG_DSP_API Processor final {
public:
    Processor() noexcept;
    ~Processor();
    Processor(const Processor&) = delete;
    Processor& operator=(const Processor&) = delete;
    Processor(Processor&&) = delete;
    Processor& operator=(Processor&&) = delete;

    // Non-realtime. Strong failure guarantee: previous instance stays usable.
    Status prepare(const PrepareSpec& spec,
                   const Parameters& initial = Parameters{}) noexcept;
    // Non-realtime producer. Validates/designs coefficients before publishing.
    // QueueFull rejects the ENTIRE update; caller retains/retries its latest copy.
    Status submit(const Parameters& parameters) noexcept;

    // Realtime, in-place interleaved float32. Exactly frames output on success.
    // frames may be 0..maxBlockFrames; null pcm is valid only for zero frames.
    Status process(float* pcm, std::uint32_t frames,
                   ProcessInfo* info = nullptr) noexcept;
    // Realtime discontinuity: discards delay/tail and adopts latest queued state.
    Status reset() noexcept;
    // Real end only, NOT an ordinary track boundary. Stop producer before this.
    Status endInput(const EndOptions& options = EndOptions{}) noexcept;
    // Realtime. Writes only [0, writtenFrames * channels). Repeat until finished.
    Status drain(float* output, std::uint32_t capacityFrames,
                 std::uint32_t& writtenFrames,
                 ProcessInfo* info = nullptr) noexcept;

    // Immutable after prepare, readable from either thread absent lifecycle calls.
    std::uint32_t latencyFrames() const noexcept;
    // Audio-thread-only observation; false before endInput or if unprepared.
    bool finished() const noexcept;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}} // namespace lmg::dsp
#endif

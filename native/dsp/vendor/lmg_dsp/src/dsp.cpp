#include "lmg_dsp/dsp.h"
#include "filter.h"
#include "snapshot_queue.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <new>
#include <vector>

#if defined(__FAST_MATH__)
#error "LMG DSP must not be compiled with fast-math"
#endif

namespace lmg { namespace dsp {
static_assert(sizeof(float) == 4 && std::numeric_limits<float>::is_iec559,
              "LMG DSP requires IEEE-754 binary32 float");
static_assert(std::numeric_limits<double>::is_iec559 &&
              std::numeric_limits<double>::digits >= 53,
              "LMG DSP requires at least IEEE-754 binary64 precision");
namespace {
using detail::Coefficients;
using CoefficientSet = std::array<Coefficients, kGraphicBands>;

bool inRange(double x, double lo, double hi) noexcept {
    return std::isfinite(x) && x >= lo && x <= hi;
}
double dbToLinear(double db) noexcept { return std::pow(10.0, db / 20.0); }
std::uint32_t millisecondsToFrames(double ms, double rate) noexcept {
    return static_cast<std::uint32_t>(std::ceil(ms * rate / 1000.0));
}
double interpolate(double a, double b, double weight) noexcept {
    // Exact endpoints matter for bit-exact dry bypass and the ceiling contract.
    if (weight <= 0.0) return a;
    if (weight >= 1.0) return b;
    return (1.0-weight)*a + weight*b;
}
double smoothstep(double t) noexcept { return t*t*(3.0-2.0*t); }

struct Scalars {
    double preamp = 1.0;
    double threshold = 1.0;
    double ceiling = 1.0;
    double attackStep = 1.0;
    double releaseCoefficient = 0.0;
    double wet = 0.0;
};
struct Snapshot {
    CoefficientSet coefficients{};
    Scalars scalars{};
    std::uint32_t transitionFrames = 2;
};

bool sameScalars(const Scalars& a, const Scalars& b) noexcept {
    return a.preamp == b.preamp && a.threshold == b.threshold &&
        a.ceiling == b.ceiling && a.attackStep == b.attackStep &&
        a.releaseCoefficient == b.releaseCoefficient && a.wet == b.wet;
}
Scalars interpolateScalars(const Scalars& a, const Scalars& b, double t) noexcept {
    return {interpolate(a.preamp,b.preamp,t), interpolate(a.threshold,b.threshold,t),
        interpolate(a.ceiling,b.ceiling,t), interpolate(a.attackStep,b.attackStep,t),
        interpolate(a.releaseCoefficient,b.releaseCoefficient,t),
        interpolate(a.wet,b.wet,t)};
}

bool validSpec(const PrepareSpec& s) noexcept {
    return inRange(s.sampleRate,44100.0,192000.0) &&
        (s.channels == 1 || s.channels == 2) &&
        s.maxBlockFrames >= 1 && s.maxBlockFrames <= 65536 &&
        inRange(s.lookaheadMs,0.0,20.0);
}

Status compileSnapshot(const Parameters& p, const PrepareSpec& spec,
                       std::uint32_t latency, Snapshot& result) noexcept {
    if (p.mode != EqMode::Off && p.mode != EqMode::Graphic &&
        p.mode != EqMode::Parametric) return Status::InvalidArgument;
    if (!inRange(p.preampDb,-36.0,12.0) || !inRange(p.headroomDb,0.0,24.0) ||
        !inRange(p.transitionMs,5.0,200.0)) return Status::InvalidArgument;
    for (double g : p.graphicGainDb)
        if (!inRange(g,-18.0,18.0)) return Status::InvalidArgument;
    for (const auto& b : p.bands) {
        if (b.type != FilterType::Peaking && b.type != FilterType::LowShelf &&
            b.type != FilterType::HighShelf && b.type != FilterType::LowPass &&
            b.type != FilterType::HighPass && b.type != FilterType::Notch)
            return Status::InvalidArgument;
        // Validate inactive/unused values too: snapshots remain valid on mode changes.
        if (!inRange(b.frequencyHz,10.0,0.475*spec.sampleRate) ||
            !inRange(b.gainDb,-24.0,24.0) || !inRange(b.q,0.1,20.0) ||
            !inRange(b.slope,0.1,1.0)) return Status::InvalidArgument;
    }
    const auto& l = p.limiter;
    if (!inRange(l.thresholdDb,-36.0,0.0) || !inRange(l.ceilingDb,-12.0,0.0) ||
        l.thresholdDb > l.ceilingDb || !inRange(l.attackMs,0.0,20.0) ||
        !inRange(l.releaseMs,5.0,2000.0)) return Status::InvalidArgument;
    const auto attackFrames = millisecondsToFrames(l.attackMs,spec.sampleRate);
    // Require the entire finite attack to fit inside the available lookahead.
    if (attackFrames > latency || (latency == 0 && l.attackMs != 0.0))
        return Status::InvalidArgument;

    Snapshot s;
    s.transitionFrames = std::max<std::uint32_t>(2,
        millisecondsToFrames(p.transitionMs,spec.sampleRate));
    s.scalars.preamp = p.bypass ? 1.0 : dbToLinear(p.preampDb-p.headroomDb);
    s.scalars.threshold = dbToLinear(l.thresholdDb);
    s.scalars.ceiling = dbToLinear(l.ceilingDb);
    s.scalars.attackStep = attackFrames == 0 ? 1.0 : 1.0/attackFrames;
    s.scalars.releaseCoefficient = std::exp(-1.0/(spec.sampleRate*l.releaseMs/1000.0));
    s.scalars.wet = (l.enabled && !p.bypass) ? 1.0 : 0.0;
    if (!p.bypass && p.mode == EqMode::Graphic) {
        constexpr double graphicQ = 1.4142135623730950488;
        for (std::uint32_t i = 0; i < kGraphicBands; ++i)
            if (!detail::design(FilterType::Peaking,spec.sampleRate,
                    kGraphicFrequencies[i],p.graphicGainDb[i],graphicQ,1.0,
                    s.coefficients[i])) return Status::InvalidArgument;
    } else if (!p.bypass && p.mode == EqMode::Parametric) {
        for (std::uint32_t i = 0; i < kParametricBands; ++i) {
            const auto& b = p.bands[i];
            if (b.enabled && !detail::design(b.type,spec.sampleRate,b.frequencyHz,
                    b.gainDb,b.q,b.slope,s.coefficients[i])) return Status::InvalidArgument;
        }
    }
    result = s;
    return Status::Ok;
}

// Delay window + fixed-size segment tree. Updating a peak costs exactly
// ceil(log2(latency+1)) parent updates (zero for latency == 0), never O(latency).
class LookaheadLimiter {
public:
    LookaheadLimiter(std::uint32_t latency, std::uint32_t channels)
        : channels_(channels), window_(latency+1), samples_(window_) {
        while (leaves_ < window_) leaves_ *= 2;
        peaks_.assign(static_cast<std::size_t>(leaves_)*2,0.0);
    }
    void reset() noexcept {
        std::fill(samples_.begin(),samples_.end(),std::array<double,2>{});
        std::fill(peaks_.begin(),peaks_.end(),0.0);
        cursor_ = 0;
        gain_ = 1.0;
    }
    std::array<double,2> tick(const std::array<double,2>& input,
                             const Scalars& s) noexcept {
        samples_[cursor_] = input;
        double peak = 0.0;
        for (std::uint32_t ch = 0; ch < channels_; ++ch)
            peak = std::max(peak,std::abs(input[ch]));
        std::uint32_t node = leaves_+cursor_;
        peaks_[node] = peak;
        while (node > 1) {
            node /= 2;
            peaks_[node] = std::max(peaks_[node*2],peaks_[node*2+1]);
        }
        const auto oldest = (cursor_+1) % window_;
        auto output = samples_[oldest];
        cursor_ = oldest;

        const double maximum = peaks_[1];
        const double requested = maximum > s.threshold ? s.threshold/maximum : 1.0;
        if (requested < gain_) gain_ = std::max(requested,gain_-s.attackStep);
        else gain_ = requested + (gain_-requested)*s.releaseCoefficient;
        gain_ = std::max(0.0,std::min(1.0,gain_));

        double delayedPeak = 0.0;
        for (std::uint32_t ch = 0; ch < channels_; ++ch)
            delayedPeak = std::max(delayedPeak,std::abs(output[ch]));
        // Shared-channel safety gain, not per-channel waveform clipping.
        // This also covers parameter changes that invalidate the steady-state proof.
        const double safeCeiling = s.ceiling *
            (1.0-2.0*static_cast<double>(std::numeric_limits<float>::epsilon()));
        double limitedGain = gain_;
        if (delayedPeak > safeCeiling)
            limitedGain = std::min(limitedGain,safeCeiling/delayedPeak);
        const double applied = interpolate(1.0,limitedGain,s.wet);
        for (std::uint32_t ch = 0; ch < channels_; ++ch) output[ch] *= applied;
        return output;
    }
private:
    std::uint32_t channels_, window_, leaves_ = 1, cursor_ = 0;
    std::vector<std::array<double,2>> samples_;
    std::vector<double> peaks_;
    double gain_ = 1.0;
};

double sanitizeInput(float sample, ProcessInfo& info) noexcept {
    if (!std::isfinite(sample) ||
        (sample != 0.0f && std::abs(sample) < std::numeric_limits<float>::min())) {
        ++info.sanitizedInputSamples;
        return 0.0;
    }
    return static_cast<double>(sample);
}
float convertOutput(double sample, ProcessInfo& info) noexcept {
    constexpr double maximum = static_cast<double>(std::numeric_limits<float>::max());
    if (!std::isfinite(sample)) {
        ++info.saturatedOutputSamples;
        return 0.0f;
    }
    if (sample > maximum || sample < -maximum) {
        ++info.saturatedOutputSamples;
        return sample < 0.0 ? -std::numeric_limits<float>::max()
                            : std::numeric_limits<float>::max();
    }
    // Only subnormal float outputs are flushed; finite NORMAL dry PCM is preserved.
    if (sample != 0.0 && std::abs(sample) < std::numeric_limits<float>::min())
        return 0.0f;
    return static_cast<float>(sample);
}
} // namespace

struct Processor::Impl {
    const PrepareSpec spec;
    const std::uint32_t latency;
    detail::SnapshotQueue<Snapshot> queue;
    Snapshot settled, target, pending;
    detail::Bank banks[2];
    LookaheadLimiter limiter;
    std::uint32_t activeBank = 0, transitionPosition = 0;
    std::uint32_t primingRemaining, tailRemaining = 0, delayRemaining = 0;
    EndOptions endOptions{};
    bool hasPending = false, transitioning = false, eqDifferent = false;
    bool inputSeen = false, ending = false;

    Impl(const PrepareSpec& s, std::uint32_t l, const Snapshot& initial)
        : spec(s), latency(l), settled(initial), target(initial), pending(initial),
          limiter(l,s.channels), primingRemaining(l) {
        banks[0].configure(initial.coefficients);
        banks[1].configure(initial.coefficients);
    }
    void receive() noexcept {
        Snapshot latest;
        if (queue.popLatest(latest)) { pending = latest; hasPending = true; }
    }
    void beginPending() noexcept {
        if (transitioning || !hasPending) return;
        target = pending;
        hasPending = false;
        eqDifferent = !(settled.coefficients == target.coefficients);
        if (!eqDifferent && sameScalars(settled.scalars,target.scalars)) {
            settled = target;
            return;
        }
        if (eqDifferent)
            banks[1-activeBank].configure(target.coefficients,&banks[activeBank]);
        transitioning = true;
        transitionPosition = 0;
    }
    double weight() const noexcept {
        if (!transitioning) return 0.0;
        return smoothstep(static_cast<double>(transitionPosition)/
                          static_cast<double>(target.transitionFrames-1));
    }
    void advanceTransition() noexcept {
        if (transitioning && ++transitionPosition == target.transitionFrames) {
            if (eqDifferent) activeBank = 1-activeBank;
            settled = target;
            transitioning = false;
        }
    }
    std::array<double,2> tick(const std::array<double,2>& input, bool feedEq,
                             double tailGain, ProcessInfo& info) noexcept {
        beginPending();
        const double w = weight();
        const Scalars s = transitioning
            ? interpolateScalars(settled.scalars,target.scalars,w) : settled.scalars;
        std::array<double,2> processed{};
        if (feedEq) {
            for (std::uint32_t ch = 0; ch < spec.channels; ++ch) {
                const double x = input[ch]*s.preamp;
                const double oldY = banks[activeBank].tick(x,ch,info.numericResets);
                const double newY = transitioning && eqDifferent
                    ? banks[1-activeBank].tick(x,ch,info.numericResets) : oldY;
                processed[ch] = interpolate(oldY,newY,w)*tailGain;
            }
        }
        const auto output = limiter.tick(processed,s);
        advanceTransition();
        return output;
    }
    void accountPriming(std::uint32_t frames, ProcessInfo& info) noexcept {
        info.primingFrames = std::min(frames,primingRemaining);
        primingRemaining -= info.primingFrames;
    }
    void resetState() noexcept {
        receive();
        const Snapshot desired = hasPending ? pending : (transitioning ? target : settled);
        settled = target = desired;
        hasPending = transitioning = eqDifferent = false;
        activeBank = transitionPosition = 0;
        banks[0].configure(desired.coefficients);
        banks[1].configure(desired.coefficients);
        limiter.reset();
        primingRemaining = latency;
        tailRemaining = delayRemaining = 0;
        inputSeen = ending = false;
        endOptions = EndOptions{};
    }
};

Processor::Processor() noexcept = default;
Processor::~Processor() = default;
Status Processor::prepare(const PrepareSpec& spec, const Parameters& initial) noexcept {
    if (!validSpec(spec)) return Status::InvalidArgument;
    const auto latency = millisecondsToFrames(spec.lookaheadMs,spec.sampleRate);
    Snapshot snapshot;
    const Status status = compileSnapshot(initial,spec,latency,snapshot);
    if (status != Status::Ok) return status;
    try {
        auto replacement = std::make_unique<Impl>(spec,latency,snapshot);
        impl_.swap(replacement);
        return Status::Ok;
    } catch (const std::bad_alloc&) {
        return Status::OutOfMemory;
    } catch (...) {
        // No exception crosses the noexcept or C boundary. With validated vector
        // sizes, allocation is the only expected failure in construction.
        return Status::OutOfMemory;
    }
}
Status Processor::submit(const Parameters& parameters) noexcept {
    if (!impl_) return Status::NotPrepared;
    Snapshot snapshot;
    const Status status = compileSnapshot(parameters,impl_->spec,impl_->latency,snapshot);
    if (status != Status::Ok) return status;
    return impl_->queue.push(snapshot) ? Status::Ok : Status::QueueFull;
}
Status Processor::process(float* pcm, std::uint32_t frames, ProcessInfo* information) noexcept {
    if (information) *information = ProcessInfo{};
    if (!impl_) return Status::NotPrepared;
    auto& p = *impl_;
    if (frames > p.spec.maxBlockFrames || (!pcm && frames != 0))
        return Status::InvalidArgument;
    if (p.ending) return Status::InvalidState;
    if (frames == 0) return Status::Ok;
    ProcessInfo info;
    p.receive();
    p.inputSeen = true;
    for (std::uint32_t frame = 0; frame < frames; ++frame) {
        std::array<double,2> input{};
        const auto base = static_cast<std::size_t>(frame)*p.spec.channels;
        for (std::uint32_t ch = 0; ch < p.spec.channels; ++ch)
            input[ch] = sanitizeInput(pcm[base+ch],info);
        const auto output = p.tick(input,true,1.0,info);
        for (std::uint32_t ch = 0; ch < p.spec.channels; ++ch)
            pcm[base+ch] = convertOutput(output[ch],info);
    }
    p.accountPriming(frames,info);
    if (information) *information = info;
    return Status::Ok;
}
Status Processor::reset() noexcept {
    if (!impl_) return Status::NotPrepared;
    impl_->resetState();
    return Status::Ok;
}
Status Processor::endInput(const EndOptions& options) noexcept {
    if (!impl_) return Status::NotPrepared;
    auto& p = *impl_;
    if (p.ending) return Status::InvalidState;
    if (options.tailFrames > static_cast<std::uint32_t>(p.spec.sampleRate*10.0) ||
        options.tailFadeFrames > options.tailFrames || options.tailFadeFrames == 1)
        return Status::InvalidArgument;
    p.receive(); // capture all snapshots published before endInput
    p.endOptions = options;
    p.tailRemaining = p.inputSeen ? options.tailFrames : 0;
    p.delayRemaining = p.inputSeen ? p.latency : 0;
    p.ending = true;
    return Status::Ok;
}
Status Processor::drain(float* output, std::uint32_t capacityFrames,
                        std::uint32_t& writtenFrames, ProcessInfo* information) noexcept {
    writtenFrames = 0;
    if (information) *information = ProcessInfo{};
    if (!impl_) return Status::NotPrepared;
    auto& p = *impl_;
    if (capacityFrames > p.spec.maxBlockFrames || (!output && capacityFrames != 0))
        return Status::InvalidArgument;
    if (!p.ending) return Status::InvalidState;
    ProcessInfo info;
    const std::uint32_t count = std::min(capacityFrames,p.tailRemaining+p.delayRemaining);
    for (std::uint32_t frame = 0; frame < count; ++frame) {
        const bool feedEq = p.tailRemaining != 0;
        double tailGain = 1.0;
        if (feedEq) {
            if (p.endOptions.tailFadeFrames > 0 && p.tailRemaining <= p.endOptions.tailFadeFrames) {
                const double t = static_cast<double>(p.endOptions.tailFadeFrames-p.tailRemaining)/
                                 static_cast<double>(p.endOptions.tailFadeFrames-1);
                tailGain = 1.0-smoothstep(t);
            }
            --p.tailRemaining;
        } else {
            --p.delayRemaining;
        }
        const auto result = p.tick({},feedEq,tailGain,info);
        const auto base = static_cast<std::size_t>(frame)*p.spec.channels;
        for (std::uint32_t ch = 0; ch < p.spec.channels; ++ch)
            output[base+ch] = convertOutput(result[ch],info);
    }
    p.accountPriming(count,info);
    writtenFrames = count;
    if (information) *information = info;
    return Status::Ok;
}
std::uint32_t Processor::latencyFrames() const noexcept { return impl_ ? impl_->latency : 0; }
bool Processor::finished() const noexcept {
    return impl_ && impl_->ending && impl_->tailRemaining == 0 && impl_->delayRemaining == 0;
}

Parameters defaultParameters() noexcept { return Parameters{}; }
bool makePreset(Preset preset, Parameters& destination) noexcept {
    Parameters p;
    p.mode = EqMode::Graphic;
    switch (preset) {
    case Preset::Flat: break;
    case Preset::Warm:
        p.graphicGainDb = {3.0,2.5,1.5,0.0,-0.5,0.0,0.0,-0.5,-1.0,-1.5};
        p.headroomDb = 6.0;
        break;
    case Preset::Voice:
        p.graphicGainDb = {-6.0,-4.0,-2.0,0.0,1.0,2.0,2.0,1.0,-1.0,-2.0};
        p.headroomDb = 6.0;
        break;
    case Preset::Bright:
        p.graphicGainDb = {-1.0,-1.0,-0.5,0.0,0.0,0.5,1.0,2.0,2.5,2.0};
        p.headroomDb = 6.0;
        break;
    default: return false;
    }
    // Limiter stays explicitly disabled; presets are visible parameter values.
    destination = p;
    return true;
}
const char* statusString(Status status) noexcept {
    switch (status) {
    case Status::Ok: return "ok";
    case Status::InvalidArgument: return "invalid argument";
    case Status::NotPrepared: return "not prepared";
    case Status::QueueFull: return "parameter queue full";
    case Status::InvalidState: return "invalid stream state";
    case Status::OutOfMemory: return "out of memory";
    default: return "unknown status";
    }
}
}} // namespace lmg::dsp

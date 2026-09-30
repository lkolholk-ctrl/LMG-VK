#pragma once
#include "lmg_dsp/dsp.h"
#include <array>
#include <cstddef>
#include <cstdint>

namespace lmg::player_dsp {
constexpr std::size_t kParameterCount = 67;
constexpr std::uint32_t kMaxFrames = 4096;

// Explicit wire layout shared with DspSettings.toNativeParameters().
bool decodeParameters(const float* values, std::size_t count, dsp::Parameters& out) noexcept;

class PcmProcessor {
public:
    dsp::Status prepare(int sampleRate, int channels, const dsp::Parameters& parameters) noexcept;
    dsp::Status submit(const dsp::Parameters& parameters) noexcept { return dsp_.submit(parameters); }
    dsp::Status reset() noexcept { return dsp_.reset(); }
    // Sequential audio-thread calls only. No allocation, locks or retained caller buffers.
    bool process(const void* input, void* output, std::uint32_t frames, int bytesPerSample) noexcept;
    int channels() const noexcept { return channels_; }
private:
    dsp::Processor dsp_;
    int channels_ = 0;
    std::array<float, kMaxFrames * 2> scratch_{};
};
}

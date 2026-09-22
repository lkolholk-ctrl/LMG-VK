#pragma once
#include <array>
#include <cstdint>
namespace lmg::automix {
// MEMixerChannel volume slots, not yet the AVAudioMix ramp-to-slot binding.
struct ChannelVolumeSlot {
  bool active, started, capturePending;
  float current;
  std::int64_t startFrame;
  std::uint32_t duration;
  float startVolume, endVolume;
  std::uint32_t curve;
};
struct ChannelVolumes { float all, selected; bool rampActive; };
// Source 1b8f6188c: preserves slot state and float/FMA arithmetic. When
// selectedEnabled is true, `selected` includes slots 0..9; otherwise it is base.
// `all` always includes all 11 slots. No event creation, smoothing or PCM mixing.
ChannelVolumes combineChannelVolumes(float base, std::array<ChannelVolumeSlot,11>& slots,
    std::int64_t frame, bool selectedEnabled) noexcept;
// UpdateParameters target-event branch. Duration seconds are the slot's stored
// AU ramp-time parameter, narrowed to float before this call. A zero duration
// writes immediately; an interrupted ramp starts from its previous endpoint.
void scheduleChannelVolumeTarget(ChannelVolumeSlot& slot, double sampleRate,
    float durationSeconds, float target, std::int64_t blockFrame,
    std::int32_t eventOffset) noexcept;
}

#pragma once

#include <array>
#include <cstdint>

namespace lmg::automix {

// Control-thread ABI v1, shared with ObservationMetadataProbe.kt.
// Five raw scalars per side: cloud duration (ms), timeline duration (ms),
// bpm.main, bpm.beginning, bpm.ending. Outgoing occupies 0..4, incoming 5..9.
// presentMask, not a zero/NaN sentinel, distinguishes missing from explicit 0.
// traits: 2-bit fields [out audio, in audio, out support, in support].
// audio: 0 absent, 1 linkage-only, 2 resolved. support: 0 unknown, 1 false, 2 true.
// Result: [version, issues, out duration confidence, in duration confidence,
//          main normal tag, main expanded tag, edge normal tag, edge expanded tag].
// Confidence/tag -1 means uncomputed. Tempo tags are the recovered source tags.
// These are labelled numerical probes, NOT planner region extraction or an
// eligibility decision. In particular the edge probe never falls back to main.
inline constexpr std::size_t kMetadataProbeInputSize = 10;
inline constexpr std::size_t kMetadataProbeOutputSize = 8;
using MetadataProbeInput = std::array<double, kMetadataProbeInputSize>;
using MetadataProbeOutput = std::array<std::int64_t, kMetadataProbeOutputSize>;

enum MetadataProbeIssue : std::uint32_t {
  outAudioAbsent = 1u << 0, inAudioAbsent = 1u << 1,
  outAudioUnresolved = 1u << 2, inAudioUnresolved = 1u << 3,
  outSupportFalse = 1u << 4, inSupportFalse = 1u << 5,
  outSupportUnknown = 1u << 6, inSupportUnknown = 1u << 7,
  outDurationUnknown = 1u << 8, inDurationUnknown = 1u << 9,
  outDurationMismatch = 1u << 10, inDurationMismatch = 1u << 11,
  mainTempoMissing = 1u << 12, edgeTempoMissing = 1u << 13,
  invalidScalar = 1u << 14,
  // Always set in Stage 2. No selected style, cue, speed or PCM schedule exists.
  regionsRequired = 1u << 15,
};

MetadataProbeOutput probeMetadataPair(const MetadataProbeInput& input,
                                     std::uint32_t presentMask, std::uint32_t traits);
} // namespace lmg::automix

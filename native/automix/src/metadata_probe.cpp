#include "lmg/automix/metadata_probe.h"
#include "lmg/automix/planner_scoring.h"
#include <cmath>
#include <stdexcept>

namespace lmg::automix {
MetadataProbeOutput probeMetadataPair(const MetadataProbeInput& in,
                                     std::uint32_t mask, std::uint32_t traits) {
  if ((mask & ~0x3ffu) != 0 || (traits & ~0xffu) != 0)
    throw std::invalid_argument("Invalid metadata probe bitmask");
  for (unsigned shift : {0u, 2u, 4u, 6u}) {
    if (((traits >> shift) & 3u) == 3u)
      throw std::invalid_argument("Invalid metadata probe trait");
  }
  // Unset slots are canonical zeros. Never accept a hidden NaN/nonfinite value.
  for (std::size_t i = 0; i < in.size(); ++i) {
    if (!std::isfinite(in[i]) || ((mask & (1u << i)) == 0 && in[i] != 0.0))
      throw std::invalid_argument("Invalid metadata probe scalar encoding");
  }
  MetadataProbeOutput out{1, regionsRequired, -1, -1, -1, -1, -1, -1};
  auto issue = [&](std::uint32_t value) { out[1] |= value; };
  auto present = [&](std::size_t index) { return (mask & (1u << index)) != 0; };
  for (std::size_t side = 0; side < 2; ++side) {
    const auto audio = (traits >> (side * 2)) & 3u;
    const auto support = (traits >> (4 + side * 2)) & 3u;
    if (audio == 0) issue(side == 0 ? outAudioAbsent : inAudioAbsent);
    if (audio == 1) issue(side == 0 ? outAudioUnresolved : inAudioUnresolved);
    if (support == 0) issue(side == 0 ? outSupportUnknown : inSupportUnknown);
    if (support == 1) issue(side == 0 ? outSupportFalse : inSupportFalse);
    const auto start = side * 5;
    if (!present(start) || !present(start + 1)) {
      issue(side == 0 ? outDurationUnknown : inDurationUnknown);
    } else if (!(in[start] > 0) || !(in[start + 1] > 0)) {
      issue(invalidScalar);
    } else {
      // Numerical comparison only. Do NOT infer Apple's download-state flag
      // from VK streaming or grant high confidence for an unknown duration.
      const auto confidence = plannerDurationConfidence(in[start] / 1000.0,
                                                        in[start + 1] / 1000.0, false);
      out[2 + side] = static_cast<std::int64_t>(confidence);
      if (confidence == PlannerConfidence::none)
        issue(side == 0 ? outDurationMismatch : inDurationMismatch);
    }
    // Reject invalid *present* BPMs even when a given slot is not probed below.
    for (std::size_t i = start + 2; i < start + 5; ++i)
      if (present(i) && !(in[i] > 0)) issue(invalidScalar);
  }
  const bool resolved = (traits & 3u) == 2 && ((traits >> 2) & 3u) == 2;
  auto tempos = [&](std::size_t a, std::size_t b, std::size_t dest, std::uint32_t missing) {
    if (!resolved || !present(a) || !present(b)) { issue(missing); return; }
    if (!(in[a] > 0) || !(in[b] > 0)) { issue(invalidScalar); return; }
    try {
      const PlannerTempo reference{in[a], false}, candidate{in[b], false};
      out[dest] = matchPlannerTempos(reference, candidate, kPlannerTempoTolerance).sourceTag;
      out[dest + 1] = matchPlannerTempos(reference, candidate, kPlannerExpandedTempoTolerance).sourceTag;
    } catch (const std::invalid_argument&) {
      // Overflow/underflow in an otherwise finite positive BPM is bad metadata,
      // not a reason to fail or alter playback. Do not expose partial probes.
      out[dest] = out[dest + 1] = -1;
      issue(invalidScalar);
    }
  };
  tempos(2, 7, 4, mainTempoMissing);       // Explicit main -> main diagnostic.
  tempos(4, 8, 6, edgeTempoMissing);       // Explicit ending -> beginning diagnostic.
  return out;
}
} // namespace lmg::automix

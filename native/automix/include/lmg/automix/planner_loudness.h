#pragma once
#include <optional>
#include <vector>

namespace lmg::automix {
struct PlannerLoudnessPoint { double value; double songTime; };
using PlannerLoudnessMap = std::vector<PlannerLoudnessPoint>;
struct PlannerLoudnessWindow { double start; double end; };

// Missing frequency is inferred from count/duration; the original special
// case snaps ratios whose round-to-nearest-away result is 2 to exactly 2 Hz.
// Empty values, invalid source frequency, or unavailable fallback yield no map.
std::optional<PlannerLoudnessMap> makePlannerLoudnessMap(
    const std::vector<double>& values, std::optional<double> samplingFrequency,
    std::optional<double> songDuration);
// Inclusive sample times at BOTH boundaries, equal weighting in source order.
// No interpolation, integration, resampling, or inferred neighboring samples.
std::optional<double> meanPlannerLoudness(const PlannerLoudnessMap& map,
                                        PlannerLoudnessWindow window);
// Source requires a present vocal map but does not inspect it in this function.
// Missing loudness map, empty windows, or either nonnegative mean yield no ratio.
std::optional<double> trailingIncomingLoudnessRatio(
    const PlannerLoudnessMap* map, bool vocalMapAvailable,
    PlannerLoudnessWindow incomingRegion);
} // namespace lmg::automix

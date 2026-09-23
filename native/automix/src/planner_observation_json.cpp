#include "lmg/automix/planner_observation.h"

namespace lmg::automix {
std::vector<std::int64_t> preparePlannerPairObservationJson(
    std::string_view outgoing, std::string_view outgoingSongId,
    std::string_view incoming, std::string_view incomingSongId,
    std::optional<std::int64_t> outgoingDurationMs,
    std::optional<std::int64_t> incomingDurationMs) {
  const auto out = preparePlannerSongObservation(
      decodeMediaApiSongAnalysis(outgoing, outgoingSongId), outgoingDurationMs);
  const auto in = preparePlannerSongObservation(
      decodeMediaApiSongAnalysis(incoming, incomingSongId), incomingDurationMs);
  return encodePlannerPreparationPair(out, in);
}
} // namespace lmg::automix

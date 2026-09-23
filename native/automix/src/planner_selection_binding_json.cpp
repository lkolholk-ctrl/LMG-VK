#include "lmg/automix/planner_selection_binding.h"
namespace lmg::automix {
std::vector<std::int64_t> observePlannerSelectionBindingJson(
    const std::vector<std::int64_t>& request,
    std::string_view outgoing, std::string_view outgoingId,
    std::string_view incoming, std::string_view incomingId) {
  const auto q=decodePlannerSelectionBinding(request);
  // Default mode is explicitly blocked, not a claim that these bytes were
  // decoded here. Production has already run the Stage 1 parser on both songs.
  if (!q.explicitResolvedScope)
    return observePlannerSelectionBinding(request,CloudSongAnalysis{},CloudSongAnalysis{});
  const auto a=decodeMediaApiSongAnalysis(outgoing,outgoingId);
  const auto b=decodeMediaApiSongAnalysis(incoming,incomingId);
  return observePlannerSelectionBinding(request,a,b);
}
} // namespace lmg::automix

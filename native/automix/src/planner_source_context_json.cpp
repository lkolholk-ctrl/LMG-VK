#include "lmg/automix/planner_source_context.h"

namespace lmg::automix {
std::vector<std::int64_t> observePlannerSourceContextJson(
    const std::vector<std::int64_t>& request, std::string_view outgoing,std::string_view outgoingId,
    std::string_view incoming,std::string_view incomingId,std::string_view catalogJson) {
  // Contract validation precedes parsing. Size/depth/type/identity validation is
  // performed by the real existing decoders, not a new Kotlin/cloud interpreter.
  const auto q=decodePlannerSourceContext(request);
  if(plannerSourceContextPreflight(q)!=PlannerSourceContextStatus::resolved)
    return observePlannerSourceContext(request,{},{},{});
  const auto out=decodeMediaApiSongAnalysis(outgoing,outgoingId);
  const auto in=decodeMediaApiSongAnalysis(incoming,incomingId);
  const auto catalog=loadTransitionStyles(catalogJson);
  return observePlannerSourceContext(request,out,in,catalog);
}
} // namespace lmg::automix

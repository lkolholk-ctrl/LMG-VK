#include "lmg/automix/planner_schedule_observation.h"
namespace lmg::automix {
std::vector<std::int64_t> observePlannerScheduledSourceJson(const std::vector<std::int64_t>& request,
    std::string_view out,std::string_view outId,std::string_view in,std::string_view inId,
    std::string_view catalog) {
  const auto q=decodePlannerSourceContext(request);
  if(plannerSourceContextPreflight(q)!=PlannerSourceContextStatus::resolved)
    return observePlannerScheduledSource(request,{},{},{});
  const auto a=decodeMediaApiSongAnalysis(out,outId),b=decodeMediaApiSongAnalysis(in,inId);
  return observePlannerScheduledSource(request,a,b,loadTransitionStyles(catalog));
}
} // namespace lmg::automix

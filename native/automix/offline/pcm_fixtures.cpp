#include "pcm_fixtures.h"
#include "lmg/automix/planner_schedule_observation.h"
#include <algorithm>
#include <fstream>
#include <iterator>
#include <locale>
#include <sstream>
#include <stdexcept>
namespace lmg::automix::offline {
namespace {
void check(bool v){if(!v)throw std::runtime_error("Stage4a fixture composition mismatch");}
std::string analysis(const char* id,int duration,int bars,double bpm){
  std::ostringstream s,times,scores;s.imbue(std::locale::classic());times.imbue(std::locale::classic());s.precision(17);times.precision(17);
  for(int i=0;i<=bars*4;++i){if(i){times<<',';scores<<',';}times<<i*(60./bpm);scores<<(i%16==0?800:i%8==0?600:i%4==0?400:200);}
  s<<"{\"data\":[{\"type\":\"songs\",\"id\":\""<<id<<"\",\"attributes\":{\"durationInMillis\":"<<duration<<",\"supportsSmartTransitions\":true},\"relationships\":{";
  s<<"\"audio-analysis\":{\"data\":[{\"type\":\"audio-analysis\",\"id\":\"aa\",\"attributes\":{\"key\":{\"main\":{\"tonic\":\"C\",\"mode\":\"minor\"}},\"melodicness\":{\"main\":0.8},\"vocalActivity\":[]}}]},";
  s<<"\"flexml-analysis\":{\"data\":[{\"type\":\"flexml-analysis\",\"id\":\"ff\",\"attributes\":{\"videoEvents\":{\"timeInSeconds\":["<<times.str()<<"],\"score\":["<<scores.str()<<"]}}}]}}}]}";return s.str();
}
}
std::string readCatalog(const std::string& path){
  std::ifstream f(path,std::ios::binary|std::ios::ate);if(!f)throw std::runtime_error("Cannot open canonical catalog");
  auto n=f.tellg();if(n<=0||n>1024*1024)throw std::runtime_error("Invalid catalog size");
  std::string s(static_cast<std::size_t>(n),'\0');f.seekg(0);if(!f.read(s.data(),n))throw std::runtime_error("Truncated catalog");return s;
}
PlannerTransitionSchedule fixtureSchedule(const std::string& text,bool stretch){
  const int duration=stretch?96000:120000;std::vector<std::int64_t> q(24);
  q[0]=kPlannerSourceRequestMagic;q[1]=1;q[2]=24;q[3]=404;q[4]=1;q[5]=3;q[6]=q[7]=duration;
  q[9]=16000000;q[10]=1;q[11]=q[12]=q[13]=q[14]=1;q[15]=q[17]=2;q[20]=3;
  const auto aj=analysis("pcm-a",duration,stretch?48:60,120);
  const auto bj=analysis("pcm-b",duration,60,stretch?150:120);
  const auto catalog=loadTransitionStyles(text);
  const auto a=decodeMediaApiSongAnalysis(aj,"pcm-a"),b=decodeMediaApiSongAnalysis(bj,"pcm-b");
  const auto request=decodePlannerSourceContext(q);const auto scope=resolvePlannerSourceScope(request,a,b,catalog);
  check(scope.status==PlannerSourceContextStatus::resolved);const auto resolved=decodePlannerSelectionBinding(scope.resolvedRequest);
  const auto produced=observeProducedMusicKitCandidates(a,b,resolved.outgoingDurationMs,resolved.incomingDurationMs,
    resolved.requestedIds,resolved.catalog,resolved.context,resolved.eligibility,resolved.workBudget);
  check(produced.completeForResolvedScope&&produced.selection&&produced.selection->winner);
  const auto& winner=*produced.selection->winner;check(winner.styleId==(stretch?12:9));
  const auto style=std::find_if(catalog.begin(),catalog.end(),[&](const auto& x){return x.id==winner.styleId;});check(style!=catalog.end());
  const auto ap=preparePlannerSongObservation(a,request.outgoingDurationMs),bp=preparePlannerSongObservation(b,request.incomingDurationMs);
  check(ap.maps.structure&&bp.maps.structure);
  auto plan=compilePlannerTransitionSchedule(winner,*ap.maps.structure,*bp.maps.structure,*style);
  const auto wire=observePlannerScheduledSourceJson(q,aj,"pcm-a",bj,"pcm-b",text);
  check(wire.size()>=10&&wire[5]==0&&wire[8]==0&&wire[9]==0&&wire[6]>=0&&wire[7]>0);
  const auto at=10+static_cast<std::size_t>(wire[6]);check(at<=wire.size()&&wire.size()-at==static_cast<std::size_t>(wire[7]));
  check(std::vector<std::int64_t>(wire.begin()+at,wire.end())==encodePlannerTransitionSchedule(plan));
  check(!plan.canExecute);return plan;
}
}

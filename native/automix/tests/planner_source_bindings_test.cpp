#include "lmg/automix/planner_source_bindings.h"
#include <cmath>
#include <cstring>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
#include <type_traits>
using namespace lmg::automix;
namespace {
std::size_t groups=0, assertions=0;
void check(bool b){++assertions;if(!b)throw std::runtime_error("source binding assertion");}
std::uint64_t bits(double v){std::uint64_t n;std::memcpy(&n,&v,8);return n;}
void good(PlannerCriteriaRangeResult r,double a,double b){check(r.status==PlannerCriteriaResolution::resolved&&r.range.has_value());check(bits(r.range->lower)==bits(a)&&bits(r.range->upper)==bits(b));}
void bad(PlannerCriteriaRangeResult r,PlannerCriteriaResolution s){check(r.status==s&&!r.range);}
template<class F>void test(const char*n,F f){try{f();++groups;}catch(...){std::cerr<<"FAILED "<<n<<'\n';throw;}}
void profile(){
 test("source array count and order",[]{check(plannerDefaultBeatMatchedStyleIds()==std::array<std::int64_t,3>{8,9,12});});
 test("constant lifetime",[]{check(&plannerDefaultBeatMatchedStyleIds()==&plannerDefaultBeatMatchedStyleIds());});
 test("copy cannot mutate profile",[]{auto ids=plannerDefaultBeatMatchedStyleIds();ids[0]=12;check(plannerDefaultBeatMatchedStyleIds()[0]==8&&ids[0]==12);});
 test("profile and catalog closed, full caller binding remains",[]{check(kPlannerRemainingDefaultBindings==4);});
 test("immutable source",[]{static_assert(std::is_const_v<std::remove_reference_t<decltype(plannerDefaultBeatMatchedStyleIds())>>);check(true);});
}
void criteria(){
 const auto invalid=PlannerCriteriaResolution::invalidInput,missing=PlannerCriteriaResolution::durationUnavailable;
 test("incoming after",[]{good(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{3.25}),3.25,120.);});
 test("incoming within",[]{good(resolvePlannerIncomingCriteria(120.,PlannerCriteriaWithin{3.25,7.5}),3.25,7.5);});
 test("incoming inSong",[]{good(resolvePlannerIncomingCriteria(120.,PlannerCriteriaInSong{}),0.,120.);});
 test("outgoing early after",[]{good(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingEarlyAfter{89.25}),89.25,120.);});
 test("inclusive final endpoint",[]{good(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{120.}),120.,120.);
  good(resolvePlannerIncomingCriteria(120.,PlannerCriteriaWithin{120.,120.}),120.,120.);good(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingEarlyAfter{120.}),120.,120.);});
 test("zero duration is a range not execution",[]{good(resolvePlannerIncomingCriteria(0.,PlannerCriteriaInSong{}),0.,0.);
  good(resolvePlannerIncomingCriteria(0.,PlannerCriteriaAfter{0.}),0.,0.);good(resolvePlannerOutgoingCriteria(0.,PlannerOutgoingEarlyAfter{0.}),0.,0.);});
 test("signed zero",[]{good(resolvePlannerIncomingCriteria(-0.,PlannerCriteriaAfter{-0.}),-0.,-0.);
  good(resolvePlannerIncomingCriteria(-0.,PlannerCriteriaInSong{}),0.,-0.);good(resolvePlannerIncomingCriteria(1.,PlannerCriteriaWithin{-0.,0.}),-0.,0.);
  good(resolvePlannerOutgoingCriteria(-0.,PlannerOutgoingEarlyAfter{-0.}),-0.,-0.);});
 test("duration missing",[&]{bad(resolvePlannerIncomingCriteria(std::nullopt,PlannerCriteriaInSong{}),missing);bad(resolvePlannerOutgoingCriteria(std::nullopt,PlannerOutgoingEarlyAfter{2.}),missing);});
 test("invalid payload before missing duration",[&]{bad(resolvePlannerIncomingCriteria(std::nullopt,PlannerCriteriaWithin{2.,1.}),invalid);bad(resolvePlannerOutgoingCriteria(std::nullopt,PlannerOutgoingEarlyAfter{-1.}),invalid);});
 test("late inSong exact source range",[]{good(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingLateInSong{}),0.,120.);});
 test("late after inclusive original payload",[&]{for(double t:{-0.,0.,100.25,120.})good(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingLateAfter{t}),t,120.);
  bad(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingLateAfter{121.}),invalid);});
 test("late missing duration",[&]{bad(resolvePlannerOutgoingCriteria(std::nullopt,PlannerOutgoingLateInSong{}),missing);});
 test("negative times",[&]{bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{-1.}),invalid);bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaWithin{-1.,2.}),invalid);bad(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingEarlyAfter{-1.}),invalid);});
 test("above duration",[&]{bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{121.}),invalid);bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaWithin{100.,121.}),invalid);bad(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingEarlyAfter{121.}),invalid);});
 test("host Range invariant",[&]{bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaWithin{4.,3.}),invalid);});
 test("nonfinite",[&]{for(double v:{std::numeric_limits<double>::infinity(),-std::numeric_limits<double>::infinity(),std::numeric_limits<double>::quiet_NaN()}){
  bad(resolvePlannerIncomingCriteria(v,PlannerCriteriaInSong{}),invalid);bad(resolvePlannerOutgoingCriteria(v,PlannerOutgoingLateInSong{}),invalid);
  bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{v}),invalid);bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaWithin{0.,v}),invalid);
  bad(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingEarlyAfter{v}),invalid);bad(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingLateAfter{v}),invalid);}});
 test("negative duration",[&]{bad(resolvePlannerIncomingCriteria(-1.,PlannerCriteriaInSong{}),invalid);bad(resolvePlannerOutgoingCriteria(-1.,PlannerOutgoingEarlyAfter{0.}),invalid);});
 test("no epsilon near endpoints",[&]{double before=std::nextafter(120.,0.),after=std::nextafter(120.,121.);good(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{before}),before,120.);
  bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{after}),invalid);bad(resolvePlannerOutgoingCriteria(120.,PlannerOutgoingEarlyAfter{after}),invalid);bad(resolvePlannerIncomingCriteria(120.,PlannerCriteriaAfter{std::nextafter(0.,-1.)}),invalid);});
 test("subnormal and huge",[]{double tiny=std::numeric_limits<double>::denorm_min(),huge=std::numeric_limits<double>::max();good(resolvePlannerIncomingCriteria(huge,PlannerCriteriaWithin{tiny,huge}),tiny,huge);good(resolvePlannerOutgoingCriteria(huge,PlannerOutgoingEarlyAfter{tiny}),tiny,huge);});
 test("finite matrix",[&]{for(double d:{0.,0.5,1.,2.,59.999,60.,120.,1e9})for(double a:{-1.,-0.,0.,0.25,1.,2.,60.,120.,1e9}){
  auto i=resolvePlannerIncomingCriteria(d,PlannerCriteriaAfter{a});auto o=resolvePlannerOutgoingCriteria(d,PlannerOutgoingEarlyAfter{a});auto late=resolvePlannerOutgoingCriteria(d,PlannerOutgoingLateAfter{a});
  if(a>=0&&a<=d){good(i,a,d);good(o,a,d);good(late,a,d);}else{bad(i,invalid);bad(o,invalid);bad(late,invalid);}
  for(double b:{-1.,-0.,0.,0.5,1.,2.,60.,120.,1e9}){auto w=resolvePlannerIncomingCriteria(d,PlannerCriteriaWithin{a,b});if(a>=0&&b>=a&&b<=d)good(w,a,b);else bad(w,invalid);}
 }});
}
}
int main(int argc,char**argv){try{
 const std::string mode=argc==2?argv[1]:"all";
 if(mode!="all"&&mode!="profile"&&mode!="criteria")throw std::runtime_error("Unknown mode");
 if(mode!="criteria")profile();
 if(mode!="profile")criteria();
 check(groups==(mode=="all"?25U:mode=="profile"?5U:20U));
 std::cout<<"Source bindings "<<mode<<": "<<groups<<" groups, "<<assertions<<" assertions PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}

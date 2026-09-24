#include "lmg/automix/planner_source_context.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>

using namespace lmg::automix;
namespace {
std::size_t groups=0,assertions=0;
void check(bool ok){++assertions;if(!ok)throw std::runtime_error("source context assertion");}
template<class F>void test(const char* name,F f){try{f();++groups;}catch(...){std::cerr<<"FAILED "<<name<<'\n';throw;}}
template<class F>void rejects(F f){bool caught=false;try{f();}catch(const std::invalid_argument&){caught=true;}check(caught);}
std::int64_t bits(double v){std::int64_t w;std::memcpy(&w,&v,8);return w;}
double value(std::int64_t w){double v;std::memcpy(&v,&w,8);return v;}
TransitionStyle style(int id,std::optional<int> count){TransitionStyle s{};s.id=id;s.name="fixture";s.duration=count;return s;}
std::vector<TransitionStyle> catalog(){return {style(8,8),style(9,8),style(12,16)};}
CloudSongAnalysis song(const char* id,double tempo=120,int bars=60){
 CloudSongAnalysis s;s.id=id;s.durationInMillis=120000;s.supportsSmartTransitions=true;
 CloudAudioAnalysis a;a.key=CloudComposite<CloudKey>{CloudKey{"C","minor"},CloudKey{"F#","major"},CloudKey{"B","major"}};
 a.vocalActivity=std::vector<CloudVocalActivity>{};a.melodicness=CloudComposite<double>{.8,.1,.2};
 s.audio=CloudAnalysisResource<CloudAudioAnalysis>{"audio",a};
 CloudVideoEvents v;v.timeInSeconds.emplace();v.score.emplace();
 for(int i=0;i<=bars*4;++i){v.timeInSeconds->push_back(i*(60.0/tempo));v.score->push_back(i%16==0?800:i%8==0?600:i%4==0?400:200);}
 CloudFlexAnalysis f;f.videoEvents=v;s.flex=CloudAnalysisResource<CloudFlexAnalysis>{"flex",f};return s;
}
std::vector<std::int64_t> request(){
 std::vector<std::int64_t> q(24);q[0]=kPlannerSourceRequestMagic;q[1]=1;q[2]=24;q[3]=71;q[4]=1;
 q[5]=3;q[6]=120000;q[7]=120000;q[9]=16000000;q[10]=1;
 q[11]=q[12]=q[13]=q[14]=1;q[15]=q[17]=2;q[20]=3;return q;
}
struct Snapshot {std::vector<std::int64_t> q,r;};std::vector<Snapshot> snapshots;
std::vector<std::int64_t> run(const std::vector<std::int64_t>& q,
 const CloudSongAnalysis& a=song("a"),const CloudSongAnalysis& b=song("b"),
 const std::vector<TransitionStyle>& c=catalog()){
 auto result=observePlannerSourceContext(q,a,b,c);
 check(result.size()>=12&&result.size()<=140&&result[0]==kPlannerSourceResponseMagic&&result[1]==1&&
   result[2]==static_cast<std::int64_t>(result.size())&&result[3]==q[3]&&result[4]==q[4]&&
   result[8]==3&&result[9]==8&&result[10]==9&&result[11]==12);
 return result;
}
void failed(const std::vector<std::int64_t>& r,PlannerSourceContextStatus status){
 check(r.size()==12&&r[5]==static_cast<std::int64_t>(status)&&r[6]==0&&r[7]==0);
}
std::size_t report(const std::vector<std::int64_t>& r){check(r[5]==0&&r[6]>=24&&r[6]<=80&&r[7]==48);return 12+static_cast<std::size_t>(r[6]);}
void chosen(const std::vector<std::int64_t>& r,int expected){auto p=report(r);
 check(r[p+6]==1&&r[p+8]==1&&r[p+9]==0&&r[p+10]==1&&r[p+28]==expected&&r[p+7]==0);
}
void save(const std::vector<std::int64_t>& q,const std::vector<std::int64_t>& r){snapshots.push_back({q,r});}
void catalogTests(){
 test("source duration is bar count not seconds/beats",[]{auto c=catalog();auto bound=bindPlannerCatalogFields(c);check(bound.size()==3&&bound[0].suffixBars==8&&bound[1].suffixBars==8&&bound[2].suffixBars==16);});
 test("missing option stays absent",[]{auto c=catalog();c[0].duration.reset();auto b=bindPlannerCatalogFields(c);check(!b[0].suffixBars);auto q=resolvePlannerStyleRequests({8},b);check(q.maximumBars==4);});
 test("explicit zero stays present and differs from four",[]{auto c=catalog();c[0].duration=0;auto b=bindPlannerCatalogFields(c);check(b[0].suffixBars&&*b[0].suffixBars==0);check(resolvePlannerStyleRequests({8},b).maximumBars==0);});
 test("storage order retained but requests use source profile",[]{auto c=catalog();std::reverse(c.begin(),c.end());auto b=bindPlannerCatalogFields(c);check(b[0].id==12&&b[2].id==8);const auto&p=plannerDefaultBeatMatchedStyleIds();auto q=resolvePlannerStyleRequests({p.begin(),p.end()},b);check(q.styles[0].id==8&&q.styles[1].id==9&&q.styles[2].id==12);});
 test("mapping does not mutate decoded catalog",[]{auto c=catalog();c[0].offset=StyleTime{.5,-0.0};auto b=bindPlannerCatalogFields(c);b[0].suffixBars=99;check(c[0].duration==8&&c[0].offset->relative==.5&&std::signbit(*c[0].offset->offsetInSeconds));});
 test("duplicate identity rejected",[]{auto c=catalog();c.push_back(c[0]);rejects([&]{bindPlannerCatalogFields(c);});});
 test("negative fields rejected as host domain",[]{auto c=catalog();c[0].duration=-1;rejects([&]{bindPlannerCatalogFields(c);});c=catalog();c[0].id=-1;rejects([&]{bindPlannerCatalogFields(c);});});
 test("bounded catalog and empty decoding",[]{check(bindPlannerCatalogFields({}).empty());std::vector<TransitionStyle> c;for(int i=0;i<15;++i)c.push_back(style(i,1));rejects([&]{bindPlannerCatalogFields(c);});});
 test("maximum raw host Int is widened without overflow",[]{auto b=bindPlannerCatalogFields({style(8,std::numeric_limits<int>::max())});check(b[0].suffixBars==std::numeric_limits<int>::max());});
 test("sparse full catalog isn't a 0..13 table",[]{std::vector<TransitionStyle> c;for(int id:{0,1,2,3,4,6,7,8,9,10,11,12,33,44})c.push_back(style(id,id==8||id==9?std::optional<int>(8):id==12?std::optional<int>(16):std::nullopt));auto b=bindPlannerCatalogFields(c);check(b.size()==14&&b[13].id==44&&!b[13].suffixBars);});
}
void providerTests(){
 test("constructor fields for120",[]{const auto b=plannerMusicKitProviderBounds(120);check(b.minimumOutgoingStart==75&&b.minimumOutgoingEnd==60&&b.maximumIncomingEnd==45);});
 test("duration helper discontinuity at60",[]{auto a=plannerMusicKitProviderBounds(std::nextafter(60.,0.)),b=plannerMusicKitProviderBounds(60);check(a.minimumOutgoingStart==std::nextafter(60.,0.)-2&&a.maximumIncomingEnd==2&&b.minimumOutgoingStart==45&&b.maximumIncomingEnd==15);});
 test("small positive durations",[]{for(double d:{.001,.5,1.,2.}){auto b=plannerMusicKitProviderBounds(d);check(b.minimumOutgoingStart==0&&b.minimumOutgoingEnd==0&&b.maximumIncomingEnd==d);}});
 test("minStart is not minEnd",[]{auto b=plannerMusicKitProviderBounds(200);check(b.minimumOutgoingStart==115&&b.minimumOutgoingEnd==140&&b.maximumIncomingEnd==60);});
 test("maximum incoming cap",[]{for(double d:{150.,151.,600.,3600.})check(plannerMusicKitProviderBounds(d).maximumIncomingEnd==60);});
 test("invalid helper input",[]{for(double d:{0.,-1.,std::numeric_limits<double>::infinity(),std::numeric_limits<double>::quiet_NaN()})rejects([&]{plannerMusicKitProviderBounds(d);});});
 test("source arithmetic finite oracle",[]{for(int milliseconds=1;milliseconds<=200000;milliseconds+=137){double d=milliseconds/1000.0;const auto b=plannerMusicKitProviderBounds(d);double preferred=d<60?std::min(d,2.):(d-30)*.5;check(bits(b.minimumOutgoingStart)==bits(d-preferred)&&bits(b.minimumOutgoingEnd)==bits(d-std::min(d,60.))&&bits(b.maximumIncomingEnd)==bits(std::min(preferred,60.)));}});
 test("late tag remains distinct but finite ranges coincide",[]{for(double d:{0.,1.,60.,120.})for(double a:{-0.,0.,.5,1.,60.,120.}){auto early=resolvePlannerOutgoingCriteria(d,PlannerOutgoingEarlyAfter{a}),late=resolvePlannerOutgoingCriteria(d,PlannerOutgoingLateAfter{a});check(early.status==late.status);if(late.range)check(bits(late.range->lower)==bits(a)&&bits(late.range->upper)==bits(d));}});
 test("late inSong copies duration including negative zero",[]{auto a=resolvePlannerOutgoingCriteria(-0.,PlannerOutgoingLateInSong{});check(a.range&&bits(a.range->lower)==bits(0.)&&bits(a.range->upper)==bits(-0.));});
 test("late no epsilon",[]{auto a=resolvePlannerOutgoingCriteria(120.,PlannerOutgoingLateAfter{std::nextafter(120.,121.)});check(a.status==PlannerCriteriaResolution::invalidInput&&!a.range);});
}
void scopeTests(){
 test("source-only inputs compose to style9",[]{auto q=request(),r=run(q);chosen(r,9);const auto p=report(r);check(value(r[p+40])==15.104);save(q,r);});
 test("source-only inputs expanded tempo style12",[]{auto q=request();q[6]=q[7]=96000;auto a=song("a",120,48),b=song("b",150,60);a.durationInMillis=b.durationInMillis=96000;auto r=run(q,a,b);chosen(r,12);const auto p=report(r);check(r[p+41]==252&&value(r[p+40])==10.064);save(q,r);});
 test("larger generated pair scope fails without provisional winner",[]{auto q=request(),r=run(q,song("a"),song("b",150,75));const auto p=report(r);check(r[p+6]==8&&r[p+8]==0&&r[p+10]==0&&r[p+43]==0);save(q,r);});
 test("constructor-derived geometry encoded in actual existing request",[]{auto q=request(),r=run(q);const double expected[]={75,45,75,60,45,0,120,0,120};for(std::size_t i=0;i<9;++i)check(bits(expected[i])==r[12+13+i]);check(r[12+11]==3&&r[12+24]==8&&r[12+25]==9&&r[12+26]==12);});
 test("unresolved gate never becomes supports true",[]{auto q=request();q[10]=0;auto r=run(q);failed(r,PlannerSourceContextStatus::eligibilityUnresolved);save(q,r);});
 test("denied upper gate",[]{auto q=request();q[10]=2;auto r=run(q);failed(r,PlannerSourceContextStatus::ineligible);save(q,r);});
 test("maximum complexity cannot be overridden by allowed",[]{auto q=request();q[20]=2;auto r=run(q);failed(r,PlannerSourceContextStatus::ineligible);save(q,r);});
 test("source facts must be explicit",[]{auto q=request();q[11]=0;auto r=run(q);failed(r,PlannerSourceContextStatus::sourceFactsUnknown);save(q,r);});
 test("spatial mapping unsupported is not stereo",[]{auto q=request();q[12]=2;auto r=run(q);failed(r,PlannerSourceContextStatus::spatialUnsupported);save(q,r);});
 test("previous state isn't silently dropped",[]{auto q=request();q[13]=2;auto r=run(q);failed(r,PlannerSourceContextStatus::previousStateUnsupported);save(q,r);});
 test("missing reference doesn't use actual",[]{auto q=request();auto a=song("a");a.durationInMillis.reset();auto r=run(q,a);failed(r,PlannerSourceContextStatus::referenceDurationMissing);save(q,r);});
 test("missing playback doesn't use reference",[]{auto q=request();q[5]=2;q[6]=0;auto r=run(q);failed(r,PlannerSourceContextStatus::playbackDurationMissing);save(q,r);});
 test("unequal durations fail closed without normalization",[]{auto q=request();auto a=song("a");a.durationInMillis=119999;auto r=run(q,a);failed(r,PlannerSourceContextStatus::durationMappingRequired);save(q,r);});
 test("one millisecond discrepancy differs from Apple confidence gate",[]{auto q=request();q[6]=120001;failed(run(q),PlannerSourceContextStatus::durationMappingRequired);});
 test("invalid reference duration not reinterpreted",[]{auto q=request();for(double d:{-1.,0.,120000.5,std::numeric_limits<double>::infinity()}){auto a=song("a");a.durationInMillis=d;failed(run(q,a),PlannerSourceContextStatus::invalidInput);}});
 test("invalid Criteria resolution no candidate",[]{auto q=request();q[15]=1;q[16]=bits(121.);auto r=run(q);failed(r,PlannerSourceContextStatus::criteriaRejected);save(q,r);});
 test("missing default record never uses remaining subset",[]{auto q=request();auto c=catalog();c.pop_back();auto r=run(q,song("a"),song("b"),c);failed(r,PlannerSourceContextStatus::catalogRejected);save(q,r);});
 test("catalog changes cannot change source request order",[]{auto q=request();auto c=catalog();std::reverse(c.begin(),c.end());auto r=run(q,song("a"),song("b"),c);chosen(r,9);check(r[12+24]==8&&r[12+25]==9&&r[12+26]==12&&r[12+27]==12);});
 test("zero budget no geometry or provisional winner",[]{auto q=request();q[9]=0;auto r=run(q);failed(r,PlannerSourceContextStatus::resourceLimit);save(q,r);});
 test("exhaustion after resolution delegates to existing shared budget",[]{auto q=request();q[9]=1;auto r=run(q);auto p=report(r);check(r[p+6]==8&&r[p+8]==0&&r[p+10]==0&&r[p+11]==0&&r[p+43]==0);save(q,r);});
 test("missing structure doesn't mean no-positive success",[]{auto q=request();auto b=song("b");b.flex.reset();auto r=run(q,song("a"),b);auto p=report(r);check(r[p+6]==5&&r[p+8]==0&&r[p+10]==0);save(q,r);});
 test("caller range can reject all candidates without fallback",[]{auto q=request();q[15]=1;q[16]=bits(119.);auto r=run(q);auto p=report(r);check(r[p+6]==1&&r[p+8]==1&&r[p+10]==0);save(q,r);});
 test("signed zero survives Criteria wire and native resolution",[]{auto q=request();q[15]=1;q[16]=bits(-0.);q[17]=1;q[18]=bits(-0.);q[19]=bits(120.);auto r=run(q);chosen(r,9);check(r[12+18]==bits(-0.)&&r[12+20]==bits(-0.));save(q,r);});
 test("within upper is not replaced by provider duration",[]{auto q=request();q[17]=1;q[18]=bits(1.);q[19]=bits(7.);auto r=run(q);check(r[12+20]==bits(1.)&&r[12+21]==bits(7.));});
 test("request and inputs not mutated",[]{auto q=request();const auto saved=q;auto a=song("a"),b=song("b");const auto times=*a.flex->attributes->videoEvents->timeInSeconds;run(q,a,b);check(q==saved&&times==*a.flex->attributes->videoEvents->timeInSeconds&&a.durationInMillis==120000);});
 test("generation and revision remain signed64 exact",[]{auto q=request();q[3]=std::numeric_limits<std::int64_t>::max();q[4]=99;auto r=run(q);chosen(r,9);auto p=report(r);check(r[3]==q[3]&&r[p+3]==q[3]&&r[p+4]==99);});
 test("every truncated request rejected",[]{auto q=request();for(std::size_t n=0;n<24;++n){auto x=q;x.resize(n);rejects([&]{decodePlannerSourceContext(x);});}});
 test("oversized trailing and unknown contract fields rejected",[]{auto q=request();q.push_back(0);rejects([&]{decodePlannerSourceContext(q);});for(int i:{0,1,2,3,4,5,9,10,11,12,13,14,15,17,20}){auto x=request();x[i]=-1;rejects([&]{decodePlannerSourceContext(x);});}for(int i:{8,21,22,23}){auto x=request();x[i]=1;rejects([&]{decodePlannerSourceContext(x);});}});
 test("absent and invalid duration slots rejected",[]{auto q=request();q[5]=0;rejects([&]{decodePlannerSourceContext(q);});q=request();q[6]=0;rejects([&]{decodePlannerSourceContext(q);});q=request();q[7]=9007199254740992LL;rejects([&]{decodePlannerSourceContext(q);});});
 test("inactive Criteria payload cannot carry values",[]{auto q=request();q[16]=bits(1.);rejects([&]{decodePlannerSourceContext(q);});q=request();q[18]=bits(1.);rejects([&]{decodePlannerSourceContext(q);});q=request();q[17]=0;q[19]=bits(2.);rejects([&]{decodePlannerSourceContext(q);});});
 test("nonfinite and reversed Criteria rejected before gate",[]{for(double d:{-1.,std::numeric_limits<double>::infinity(),std::numeric_limits<double>::quiet_NaN()}){auto q=request();q[15]=0;q[16]=bits(d);q[10]=0;rejects([&]{decodePlannerSourceContext(q);});}auto q=request();q[17]=1;q[18]=bits(2.);q[19]=bits(1.);rejects([&]{decodePlannerSourceContext(q);});});
 test("typed unknown enum cannot assert eligibility",[]{auto q=decodePlannerSourceContext(request());q.context.outgoingSpatial=static_cast<PlannerSourceKnowledge>(99);check(resolvePlannerSourceScope(q,song("a"),song("b"),catalog()).status==PlannerSourceContextStatus::invalidInput);});
 test("catalog duplicate causes bounded resolution rejection",[]{auto q=request();auto c=catalog();c.push_back(c[0]);failed(run(q,song("a"),song("b"),c),PlannerSourceContextStatus::catalogRejected);});
 test("all future execution flags remain false",[]{static_assert(!PlannerProducedObservation::canExecute);static_assert(!PlannerCandidateSelection::canExecute);auto r=run(request());check(r[report(r)+9]==0);});
}
void number(std::ostream& o,std::uint64_t v,int n){for(int i=0;i<n;++i)o.put(static_cast<char>((v>>(i*8))&255));}
}
int main(int argc,char**argv){try{
 const std::string mode=argc>1?argv[1]:"all";
 check(mode=="all"||mode=="catalog"||mode=="provider"||mode=="scope");
 if(mode=="all"||mode=="catalog")catalogTests();
 if(mode=="all"||mode=="provider")providerTests();
 if(mode=="all"||mode=="scope")scopeTests();
 if(argc>2){std::ofstream o(argv[2],std::ios::binary);check(static_cast<bool>(o));number(o,snapshots.size(),4);
  for(const auto&s:snapshots){number(o,s.q.size(),4);number(o,s.r.size(),4);for(auto v:s.q)number(o,static_cast<std::uint64_t>(v),8);for(auto v:s.r)number(o,static_cast<std::uint64_t>(v),8);}check(static_cast<bool>(o));}
 std::cout<<"Source catalog/provider/context "<<mode<<": "<<groups<<" groups, "<<assertions<<" assertions PASSED; "<<snapshots.size()<<" native snapshots\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}

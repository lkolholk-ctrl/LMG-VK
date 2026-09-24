#include "lmg/automix/planner_selection_binding.h"
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace lmg::automix;
namespace {
int groups=0;
void check(bool v){if(!v)throw std::runtime_error("selection binding assertion");}
template<class F>void test(const char* n,F f){try{f();++groups;}catch(...){std::cerr<<"FAILED "<<n<<'\n';throw;}}
template<class F>void rejects(F f){bool yes=false;try{f();}catch(const std::invalid_argument&){yes=true;}check(yes);}
std::int64_t bits(double v){std::int64_t w;std::memcpy(&w,&v,8);return w;}
double value(std::int64_t w){double v;std::memcpy(&v,&w,8);return v;}
CloudSongAnalysis cloud(const char* id,double bpm=120,int bars=60){
  CloudSongAnalysis s;s.id=id;s.durationInMillis=120000;s.supportsSmartTransitions=true;
  CloudAudioAnalysis a;a.key=CloudComposite<CloudKey>{CloudKey{"C","minor"},CloudKey{"A","major"},CloudKey{"B","major"}};
  a.vocalActivity=std::vector<CloudVocalActivity>{};a.melodicness=CloudComposite<double>{.8,.1,.2};
  s.audio=CloudAnalysisResource<CloudAudioAnalysis>{"audio",a};
  CloudVideoEvents v;v.timeInSeconds.emplace();v.score.emplace();
  for(int i=0;i<=bars*4;++i){v.timeInSeconds->push_back(i*(60.0/bpm));
    v.score->push_back(i%16==0?800:i%8==0?600:i%4==0?400:200);}
  CloudFlexAnalysis f;f.videoEvents=v;s.flex=CloudAnalysisResource<CloudFlexAnalysis>{"flex",f};return s;
}
std::vector<std::int64_t> request(bool explicitScope=true){
  std::vector<std::int64_t> w(24);
  w[0]=kPlannerBindingRequestMagic;w[1]=1;w[3]=71;w[4]=explicitScope?1:0;w[5]=explicitScope?1:0;
  w[6]=3;w[7]=120000;w[8]=120000;w[9]=16000000;
  if(explicitScope){
    w[10]=1;w[11]=3;w[12]=3;
    const double bounds[]={104,32,60,104,32,60,120,0,32};
    for(std::size_t i=0;i<9;++i)w[13+i]=bits(bounds[i]);
    w.insert(w.end(),{8,9,12,8,1,8,9,1,8,12,1,16});
  }
  w[2]=static_cast<std::int64_t>(w.size());return w;
}
struct Snapshot{std::vector<std::int64_t> q,r;};
std::vector<Snapshot> samples;
std::vector<std::int64_t> run(std::vector<std::int64_t> q,const CloudSongAnalysis& a=cloud("a"),const CloudSongAnalysis& b=cloud("b")){
  auto r=observePlannerSelectionBinding(q,a,b);check(r.size()==48&&r[1]==2&&r[9]==0&&r[3]==q[3]&&r[4]==q[4]&&r[44]==3&&r[45]==8&&r[46]==9&&r[47]==12);return r;
}
void writeNumber(std::ostream& f,std::uint64_t n,int bytes){for(int i=0;i<bytes;++i)f.put(static_cast<char>((n>>(i*8))&255));}
}
int main(int argc,char**argv){try{
  test("default knows profile but preserves unresolved full provider policy",[]{auto q=request(false),r=run(q);
    check(r[6]==0&&r[7]==4&&r[8]==0&&r[28]==-1);samples.push_back({q,r});});
  test("resolved scope automatic seeds -> style9",[]{auto q=request(),r=run(q);
    check(r[6]==1&&r[7]==0&&r[8]==1&&r[10]==1&&r[11]==12&&r[28]==9&&r[29]==9&&r[30]==1);
    check(std::abs(value(r[40])-15.104)<1e-12&&value(r[36])==104&&value(r[39])==16);samples.push_back({q,r});});
  test("expanded tempo -> style12",[]{auto q=request(),r=run(q,cloud("a"),cloud("b",150,75));
    check(r[28]==12&&r[41]==252&&std::abs(value(r[40])-10.088)<1e-12);samples.push_back({q,r});});
  test("denied does not escape winner",[]{auto q=request();q[10]=2;auto r=run(q);check(r[6]==3&&r[10]==0&&r[8]==0);samples.push_back({q,r});});
  test("unresolved eligibility is not supports=true",[]{auto q=request();q[10]=0;auto r=run(q);check(r[6]==2&&r[8]==0);samples.push_back({q,r});});
  test("missing timeline duration not replaced",[]{auto q=request();q[6]=1;q[8]=0;auto r=run(q);check(r[6]==7&&r[10]==0);samples.push_back({q,r});});
  test("missing structure not successful empty",[]{auto q=request();auto b=cloud("b");b.flex.reset();auto r=run(q,cloud("a"),b);check(r[6]==5);samples.push_back({q,r});});
  test("absent requested descriptor denies partial selection",[]{auto q=request();q[24]=999;auto r=run(q);check(r[6]==4&&r[8]==0);samples.push_back({q,r});});
  test("zero work budget has no provisional counters",[]{auto q=request();q[9]=0;auto r=run(q);check(r[6]==8&&r[11]==0&&r[43]==0);samples.push_back({q,r});});
  test("empty explicit profile no inferred default",[]{auto q=request();q[11]=0;q.erase(q.begin()+24,q.begin()+27);q[2]=static_cast<std::int64_t>(q.size());
    auto r=run(q);check(r[6]==1&&r[8]==1&&r[10]==0&&r[20]==0);samples.push_back({q,r});});
  test("duplicate requested order survives transport",[]{auto q=request();q[24]=9;q[25]=9;q[26]=8;auto r=run(q);check(r[28]==9&&r[30]==0);samples.push_back({q,r});});
  test("native failure keeps no partial structure",[]{auto q=request();auto b=cloud("b");b.flex->attributes->videoEvents->score->clear();auto r=run(q,cloud("a"),b);check(r[6]==6&&r[8]==0);samples.push_back({q,r});});
  test("correlation values retained without floating conversions",[]{auto q=request();q[3]=std::numeric_limits<std::int64_t>::max();q[4]=99;auto r=run(q);check(r[3]==q[3]&&r[4]==99);});
  test("scope fields unmodified by native work",[]{const auto q=request();const auto copy=q;run(q);check(q==copy);});
  test("invalid envelope and reserved words",[]{for(const auto field:{0,1,2,3,4,5,6,9,10,11,12,22,23}){
    auto q=request();q[static_cast<std::size_t>(field)]=-1;rejects([&]{decodePlannerSelectionBinding(q);});}});
  test("all request truncations rejected",[]{auto q=request();for(std::size_t n=0;n<q.size();++n){auto t=q;t.resize(n);rejects([&]{decodePlannerSelectionBinding(t);});}});
  test("oversized and trailing words rejected",[]{auto q=request();q.push_back(0);rejects([&]{decodePlannerSelectionBinding(q);});q.resize(81);q[2]=81;rejects([&]{decodePlannerSelectionBinding(q);});});
  test("duration presence strict",[]{auto q=request();q[6]=0;rejects([&]{decodePlannerSelectionBinding(q);});q=request();q[7]=0;rejects([&]{decodePlannerSelectionBinding(q);});q[7]=9007199254740992LL;rejects([&]{decodePlannerSelectionBinding(q);});});
  test("nonfinite and negative bounds rejected",[]{for(int i=13;i<22;++i)for(double x:{-1.,std::numeric_limits<double>::infinity(),std::numeric_limits<double>::quiet_NaN()}){
    auto q=request();q[static_cast<std::size_t>(i)]=bits(x);rejects([&]{decodePlannerSelectionBinding(q);});}});
  test("reversed bounds rejected",[]{auto q=request();q[18]=bits(121);rejects([&]{decodePlannerSelectionBinding(q);});q=request();q[20]=bits(40);rejects([&]{decodePlannerSelectionBinding(q);});});
  test("internal descriptor presence and uniqueness",[]{auto q=request();q[28]=2;rejects([&]{decodePlannerSelectionBinding(q);});q=request();q[28]=0;rejects([&]{decodePlannerSelectionBinding(q);});q=request();q[30]=8;rejects([&]{decodePlannerSelectionBinding(q);});});
  test("default cannot smuggle resolved scope",[]{auto q=request(false);q[10]=1;rejects([&]{decodePlannerSelectionBinding(q);});q=request(false);q[13]=bits(1);rejects([&]{decodePlannerSelectionBinding(q);});q=request(false);q[4]=1;rejects([&]{decodePlannerSelectionBinding(q);});});
  test("explicit binding requires revision",[]{auto q=request();q[4]=0;rejects([&]{decodePlannerSelectionBinding(q);});});
  test("catalog order does not reorder requested traversal",[]{auto q=request();for(int j=0;j<3;++j)std::swap(q[27+j],q[33+j]);auto r=run(q);check(r[28]==9&&r[30]==1);});
  if(argc==2){std::ofstream f(argv[1],std::ios::binary);if(!f)throw std::runtime_error("snapshot output");
    writeNumber(f,samples.size(),4);for(const auto&s:samples){writeNumber(f,s.q.size(),4);for(auto w:s.q)writeNumber(f,static_cast<std::uint64_t>(w),8);for(auto w:s.r)writeNumber(f,static_cast<std::uint64_t>(w),8);}check(static_cast<bool>(f));}
  std::cout<<"Selection binding: "<<groups<<"/24 groups PASSED; "<<samples.size()<<" native wire snapshots\n";
  return groups==24?0:1;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}

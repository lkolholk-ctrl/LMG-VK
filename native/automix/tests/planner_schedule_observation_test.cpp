#include "support/schedule_fixtures.h"
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <vector>
using namespace schedule_test;
namespace {
std::size_t groups=0,assertions=0;
void check(bool v){++assertions;if(!v)throw std::runtime_error("schedule composition assertion "+std::to_string(assertions));}
struct Snapshot{std::vector<std::int64_t> request,response;};
std::vector<Snapshot> snapshots;
std::vector<std::int64_t> run(const std::vector<std::int64_t>&q,const CloudSongAnalysis&a,
 const CloudSongAnalysis&b,const std::vector<TransitionStyle>&styles,bool capture=false){
 const auto r=observePlannerScheduledSource(q,a,b,styles);
 check(r.size()>=22&&r.size()<=2048&&r[0]==kPlannerScheduleEnvelopeMagic&&r[1]==1&&r[2]==std::int64_t(r.size()));
 check(r[3]==q[3]&&r[4]==q[4]&&r[8]==0&&r[9]==0&&10+r[6]+r[7]==r[2]);
 const auto previous=observePlannerSourceContext(q,a,b,styles);
 check(std::vector<std::int64_t>(r.begin()+10,r.begin()+10+r[6])==previous);
 check((r[5]==0)==(r[7]>0));
 if(capture) { snapshots.push_back({q,r}); }
 return r;
}
void save(const char* path){
 std::ofstream f(path,std::ios::binary);check(bool(f));
 auto put=[&](std::int64_t w){for(unsigned i=0;i<8;++i)f.put(char(std::uint64_t(w)>>(8*i)));};
 put(std::int64_t(snapshots.size()));for(const auto&s:snapshots){put(s.request.size());for(auto w:s.request)put(w);put(s.response.size());for(auto w:s.response)put(w);}check(bool(f));
}
}
int main(int argc,char**argv){try{
 auto a=cloud("a"),b=cloud("b");auto c=catalog();auto q=request();
 auto r=run(q,a,b,c,true);check(r[5]==0);auto p=10+std::size_t(r[6]);
 check(r[p+3]==9&&real(r[p+11])==15.104&&real(r[p+12])==104&&real(r[p+13])==120);
 check(real(r[p+14])==0&&real(r[p+15])==16&&real(r[p+23])==16&&real(r[p+24])==8);
 check(r[p+33]==4&&r[p+34]==3&&r[p+35]==18&&r[p+36]==0&&r[p+37]==1);++groups;
 q=request(96000);r=run(q,cloud("a",96000,48,120),cloud("b",96000,60,150),c,true);p=10+r[6];
 check(r[5]==0&&r[p+3]==12&&real(r[p+11])==10.064);check(real(r[p+19])>1&&real(r[p+20])<1);++groups;
 q=request();q[9]=1;r=run(q,a,b,c,true);check(r[5]==2&&r[7]==0);++groups;
 q=request();q[10]=0;r=run(q,{}, {},{},true);check(r[5]==1&&r.size()==22);++groups;
 q=request();q[10]=2;r=run(q,a,b,c,true);check(r[5]==1);++groups;
 q=request();q[11]=0;r=run(q,a,b,c,true);check(r[5]==1);++groups;
 q=request();q[12]=2;r=run(q,a,b,c,true);check(r[5]==1);++groups;
 q=request();q[14]=2;r=run(q,a,b,c,true);check(r[5]==1);++groups;
 q=request();auto altered=a;altered.durationInMillis=119999;r=run(q,altered,b,c,true);check(r[5]==1);++groups;
 q=request();r=run(q,a,b,{},true);check(r[5]==1);++groups;
 auto noFlex=a;noFlex.flex.reset();r=run(q,noFlex,b,c,true);check(r[5]==2);++groups;
 auto broken=c;broken[1].offset->relative=.5;r=run(q,a,b,broken,true);check(r[5]==4&&r[7]==0);++groups;
 broken=c;broken[1].incoming[1].ramps[0].from.parameterName="unresolved";r=run(q,a,b,broken,true);check(r[5]==4);++groups;
 broken=c;broken[1].outgoing[2].ramps[0].from.fallback=0;r=run(q,a,b,broken,true);check(r[5]==4);++groups;
 broken=c;broken[1].outgoing.clear();broken[1].incoming.clear();r=run(q,a,b,broken,true);p=10+r[6];check(r[5]==0&&r[p+33]==1&&r[p+34]==1);++groups;
 q=request();q[3]=987654;q[4]=19;r=run(q,a,b,c,true);check(r[3]==987654&&r[4]==19);++groups;
 const auto before=a.flex->attributes->videoEvents->timeInSeconds;
 r=run(q,a,b,c);check(a.flex->attributes->videoEvents->timeInSeconds==before);++groups;
 broken=c;broken[1].outgoing.push_back(broken[1].outgoing[1]);r=run(q,a,b,broken);p=10+r[6];check(r[5]==0&&r[p+33]==5);++groups;
 q=request();q[20]=2;r=run(q,a,b,c);check(r[5]==1);++groups;
 q=request();q[8]=1;bool rejected=false;try{observePlannerScheduledSource(q,a,b,c);}catch(const std::invalid_argument&){rejected=true;}check(rejected);++groups;
 if(argc==2)save(argv[1]);else check(argc==1);
 std::cout<<"Source selection -> schedule: "<<groups<<" groups, "<<assertions<<" assertions PASSED; "<<snapshots.size()<<" native snapshots\n";
 return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}

#include "lmg/automix/planner_source_context.h"
#include <cstring>
#include <fstream>
#include <iostream>
#include <iterator>
#include <sstream>
#include <stdexcept>
using namespace lmg::automix;
namespace {
std::size_t groups=0;
void check(bool v){if(!v)throw std::runtime_error("source JSON assertion");}
template<class F>void rejects(F f){bool yes=false;try{f();}catch(const std::invalid_argument&){yes=true;}check(yes);}
std::vector<std::int64_t> request(std::int64_t duration=120000){
 std::vector<std::int64_t> q(24);q[0]=kPlannerSourceRequestMagic;q[1]=1;q[2]=24;q[3]=71;q[4]=1;
 q[5]=3;q[6]=q[7]=duration;q[9]=16000000;q[10]=1;q[11]=q[12]=q[13]=q[14]=1;q[15]=q[17]=2;q[20]=3;return q;
}
std::string json(const char* id,int duration=120000,int bars=60,double bpm=120){
 std::ostringstream s,times,scores;s.precision(17);times.precision(17);
 for(int i=0;i<=bars*4;++i){if(i){times<<',';scores<<',';}times<<i*(60./bpm);scores<<(i%16==0?800:i%8==0?600:i%4==0?400:200);}
 s<<"{\"data\":[{\"type\":\"songs\",\"id\":\""<<id<<"\",\"attributes\":{\"durationInMillis\":"<<duration<<",\"supportsSmartTransitions\":true},\"relationships\":{";
 s<<"\"audio-analysis\":{\"data\":[{\"type\":\"audio-analysis\",\"id\":\"aa\",\"attributes\":{\"key\":{\"main\":{\"tonic\":\"C\",\"mode\":\"minor\"}},\"melodicness\":{\"main\":0.8},\"vocalActivity\":[]}}]},";
 s<<"\"flexml-analysis\":{\"data\":[{\"type\":\"flexml-analysis\",\"id\":\"ff\",\"attributes\":{\"videoEvents\":{\"timeInSeconds\":["<<times.str()<<"],\"score\":["<<scores.str()<<"]}}}]}}}]}";return s.str();
}
std::size_t report(const std::vector<std::int64_t>&r){check(r[5]==0&&r[7]==48);return 12+static_cast<std::size_t>(r[6]);}
}
int main(int argc,char**argv){try{
 check(argc==2);std::ifstream input(argv[1],std::ios::binary);check(bool(input));
 const std::string catalog((std::istreambuf_iterator<char>(input)),{});check(!catalog.empty());
 const auto raw=loadTransitionStyles(catalog);auto records=bindPlannerCatalogFields(raw);
 check(records.size()==14);auto profile=resolvePlannerStyleRequests({8,9,12},records);
 check(profile.status==PlannerStyleResolutionStatus::resolved&&profile.styles[0].suffixBars==8&&profile.styles[1].suffixBars==8&&profile.styles[2].suffixBars==16);++groups;
 const auto a=json("a"),b=json("b");auto q=request();
 auto r=observePlannerSourceContextJson(q,a,"a",b,"b",catalog);auto p=report(r);
 check(r[p+28]==9&&r[p+9]==0&&r[p+8]==1&&r[12+12]==14);++groups;
 q=request(96000);r=observePlannerSourceContextJson(q,json("a",96000,48,120),"a",json("b",96000,60,150),"b",catalog);p=report(r);check(r[p+28]==12&&r[p+9]==0);++groups;
 q=request();rejects([&]{observePlannerSourceContextJson(q,a,"wrong",b,"b",catalog);});++groups;
 rejects([&]{observePlannerSourceContextJson(q,"broken","a",b,"b",catalog);});++groups;
 rejects([&]{observePlannerSourceContextJson(q,a,"a",b,"b","broken");});++groups;
 r=observePlannerSourceContextJson(q,a,"a",b,"b","[]");check(r.size()==12&&r[5]==9);++groups;
 q[10]=0;r=observePlannerSourceContextJson(q,"unused","a","unused","b","unused");check(r.size()==12&&r[5]==1);++groups;
 q=request();q[11]=2;r=observePlannerSourceContextJson(q,a,"a",b,"b",catalog);check(r.size()==12&&r[5]==3);++groups;
 q=request();r=observePlannerSourceContextJson(q,json("a",119999),"a",b,"b",catalog);check(r.size()==12&&r[5]==7);++groups;
 std::cout<<"Canonical catalog/raw JSON -> source context: "<<groups<<" groups PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}

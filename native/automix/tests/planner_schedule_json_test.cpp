#include "lmg/automix/planner_schedule_observation.h"
#include <cstring>
#include <fstream>
#include <iostream>
#include <iterator>
#include <sstream>
#include <stdexcept>
using namespace lmg::automix;
namespace {
std::size_t groups=0;
void check(bool v){if(!v)throw std::runtime_error("schedule JSON assertion");}
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
}
int main(int argc,char**argv){try {
 check(argc==2);std::ifstream f(argv[1],std::ios::binary);check(bool(f));
 const std::string catalog((std::istreambuf_iterator<char>(f)),{});check(!catalog.empty());
 auto q=request();const auto a=json("a"),b=json("b");
 auto r=observePlannerScheduledSourceJson(q,a,"a",b,"b",catalog);check(r[5]==0);auto p=10+r[6];
 check(r[p+3]==9&&r[p+33]==5&&r[p+34]==5&&r[p+35]==24&&r[p+36]==0);++groups;
 double value;std::memcpy(&value,&r[p+23],8);check(value==16);std::memcpy(&value,&r[p+24],8);check(value==8);++groups;
 const auto previous=observePlannerSourceContextJson(q,a,"a",b,"b",catalog);
 check(std::vector<std::int64_t>(r.begin()+10,r.begin()+10+r[6])==previous);++groups;
 q=request(96000);r=observePlannerScheduledSourceJson(q,json("a",96000,48,120),"a",json("b",96000,60,150),"b",catalog);p=10+r[6];
 check(r[5]==0&&r[p+3]==12&&r[p+33]==4&&r[p+34]==5);++groups;
 q=request();rejects([&]{observePlannerScheduledSourceJson(q,a,"wrong",b,"b",catalog);});++groups;
 rejects([&]{observePlannerScheduledSourceJson(q,"broken","a",b,"b",catalog);});++groups;
 rejects([&]{observePlannerScheduledSourceJson(q,a,"a",b,"b","broken");});++groups;
 r=observePlannerScheduledSourceJson(q,a,"a",b,"b","[]");check(r.size()==22&&r[5]==1&&r[7]==0);++groups;
 q[10]=0;r=observePlannerScheduledSourceJson(q,"unused","a","unused","b","unused");check(r.size()==22&&r[5]==1);++groups;
 q=request();q[9]=1;r=observePlannerScheduledSourceJson(q,a,"a",b,"b",catalog);check(r[5]==2&&r[7]==0);++groups;
 q=request();q[11]=2;r=observePlannerScheduledSourceJson(q,a,"a",b,"b",catalog);check(r[5]==1&&r[7]==0);++groups;
 q=request();r=observePlannerScheduledSourceJson(q,json("a",119999),"a",b,"b",catalog);check(r[5]==1&&r[7]==0);++groups;
 std::cout<<"Canonical raw JSON -> compiled schedule: "<<groups<<" groups PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}

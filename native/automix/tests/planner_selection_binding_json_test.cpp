#include "lmg/automix/planner_selection_binding.h"
#include <cmath>
#include <cstring>
#include <iostream>
#include <sstream>
#include <stdexcept>
using namespace lmg::automix;
namespace {
void check(bool v){if(!v)throw std::runtime_error("raw binding assertion");}
std::int64_t bits(double v){std::int64_t w;std::memcpy(&w,&v,8);return w;}
std::vector<std::int64_t> req(){
 std::vector<std::int64_t>w{ kPlannerBindingRequestMagic,1,36,71,1,1,3,120000,120000,16000000,1,3,3 };
 for(double d:{104.,32.,60.,104.,32.,60.,120.,0.,32.})w.push_back(bits(d));
 w.insert(w.end(),{0,0,8,9,12,8,1,8,9,1,8,12,1,16});return w;
}
std::string json(const char* id,int bars=60,double bpm=120){
 std::ostringstream s,times,scores;s.precision(17);times.precision(17);
 for(int i=0;i<=bars*4;++i){if(i){times<<',';scores<<',';}times<<i*(60./bpm);scores<<(i%16==0?800:i%8==0?600:i%4==0?400:200);}
 s<<"{\"data\":[{\"type\":\"songs\",\"id\":\""<<id<<"\",\"attributes\":{\"durationInMillis\":120000,\"supportsSmartTransitions\":true},\"relationships\":{";
 s<<"\"audio-analysis\":{\"data\":[{\"type\":\"audio-analysis\",\"id\":\"aa\",\"attributes\":{\"key\":{\"main\":{\"tonic\":\"C\",\"mode\":\"minor\"}},\"melodicness\":{\"main\":0.8},\"vocalActivity\":[]}}]},";
 s<<"\"flexml-analysis\":{\"data\":[{\"type\":\"flexml-analysis\",\"id\":\"ff\",\"attributes\":{\"videoEvents\":{\"timeInSeconds\":["<<times.str()<<"],\"score\":["<<scores.str()<<"]}}}]}}}]}";
 return s.str();
}
}
int main(){try{
 const auto a=json("a"),b=json("b");auto q=req();
 auto r=observePlannerSelectionBindingJson(q,a,"a",b,"b");check(r[28]==9&&r[9]==0&&r[8]==1);
 r=observePlannerSelectionBindingJson(q,a,"a",json("b",75,150),"b");check(r[28]==12);
 bool rejected=false;try{observePlannerSelectionBindingJson(q,a,"wrong",b,"b");}catch(const std::invalid_argument&){rejected=true;}check(rejected);
 rejected=false;try{observePlannerSelectionBindingJson(q,"broken","a",b,"b");}catch(const std::invalid_argument&){rejected=true;}check(rejected);
 q[6]=1;q[8]=0;r=observePlannerSelectionBindingJson(q,a,"a",b,"b");check(r[6]==7&&r[28]==-1);
 std::cout<<"Raw JSON -> scoped selection: 5/5 groups PASSED\n";return 0;
}catch(const std::exception&e){std::cerr<<e.what()<<'\n';return 1;}}

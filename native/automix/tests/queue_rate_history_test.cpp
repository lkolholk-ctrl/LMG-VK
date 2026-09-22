#include "lmg/automix/queue_rate_history.h"
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <limits>
using namespace lmg::automix;
#define CHECK(x) do{if(!(x))throw std::runtime_error(#x);}while(false)
template<class T>T read(std::istream& in){T v;in.read(reinterpret_cast<char*>(&v),sizeof(v));CHECK(in.good());return v;}
bool bits(double a,double b){return std::memcmp(&a,&b,sizeof(a))==0;}
int main(){try{
  std::ifstream in(LMG_AUTOMIX_FIXTURES "/queue_rate_history.bin",std::ios::binary);
  CHECK(read<unsigned>(in)==1024);
  for(unsigned i=0;i<1024;++i){
    const auto count=read<unsigned>(in);const auto scaled=read<double>(in);
    const auto unscaled=read<std::int64_t>(in),high=read<std::int64_t>(in);
    std::vector<QueueRateAnchor> nodes;
    for(unsigned j=0;j<count;++j){
      const auto x=read<std::int64_t>(in),y=read<std::int64_t>(in);
      const auto origin=read<double>(in),rate=read<double>(in);
      nodes.push_back({x,y,origin,rate});
    }
    const auto expectedUnscaled=read<std::int64_t>(in),highUnscaled=read<std::int64_t>(in);
    const auto expectedScaled=read<double>(in),expectedRate=read<double>(in);
    const auto highScaled=read<std::int64_t>(in);
    QueueRateHistory forward(nodes,high),reverse(nodes,high);
    CHECK(forward.toUnscaled(scaled)==expectedUnscaled);CHECK(forward.highWater()==highUnscaled);
    double rate;const auto actual=reverse.toScaled(unscaled,&rate);
    if(!bits(actual,expectedScaled)){std::cerr<<"case "<<i<<'\n';throw std::runtime_error("scaled bits mismatch");}
    CHECK(bits(rate,expectedRate));CHECK(reverse.highWater()==highScaled);
    CHECK(bits(reverse.toScaled(unscaled),actual));
  }
  CHECK(in.peek()==std::char_traits<char>::eof());
  bool rejected=false;
  try{QueueRateHistory invalid({{0,0,0,0}},0);}catch(const std::invalid_argument&){rejected=true;}
  CHECK(rejected);rejected=false;
  try{QueueRateHistory empty({},0);empty.toUnscaled(std::numeric_limits<double>::quiet_NaN());}
  catch(const std::invalid_argument&){rejected=true;}
  CHECK(rejected);
  std::cout<<"2048 original queue conversions matched, including rate and high-water state\n";
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}

#include "lmg/automix/planner_vocals.h"
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& f) {
  T v{};f.read(reinterpret_cast<char*>(&v),sizeof v);CHECK(f.gcount()==sizeof v);return v;
}
template<class F> void rejects(F fn) {
  bool caught=false;try { fn(); }catch(const std::invalid_argument&) {caught=true;}
  CHECK(caught);
}
int main() {
  try {
    std::ifstream f(std::string(LMG_AUTOMIX_FIXTURES)+"/planner_vocals.bin",std::ios::binary);
    CHECK(f.good());const auto count=read<std::uint32_t>(f);CHECK(count==256);
    for(unsigned i=0;i<count;++i) {
      const auto n=read<std::uint32_t>(f);
      const PlannerVocalWindow window{read<double>(f),read<double>(f)};
      const auto strength=read<std::uint32_t>(f),significant=read<std::uint32_t>(f),
                 missingWindow=read<std::uint32_t>(f),missingMap=read<std::uint32_t>(f);
      PlannerVocalMap map;
      for(unsigned j=0;j<n;++j) {
        PlannerVocalActivity a{read<double>(f),read<double>(f),read<std::uint8_t>(f),read<std::uint8_t>(f)};
        for(unsigned k=0;k<6;++k)read<std::uint8_t>(f);
        map.push_back(a);
      }
      const auto prepared=makePlannerVocalMap(map);CHECK(prepared.size()==map.size());
      CHECK(plannerVocalStrength(prepared,window).value_or(5)==strength);
      CHECK(leadingIncomingVocalSignificant(&prepared,window)==bool(significant));
      CHECK(leadingIncomingVocalSignificant(&prepared,std::nullopt)==bool(missingWindow));
      CHECK(leadingIncomingVocalSignificant(nullptr,window)==bool(missingMap));
    }
    CHECK(f.peek()==std::char_traits<char>::eof());
    const auto map=makePlannerVocalMap({{1,0,255,255},{0,0,2,4},{2,3,0,0}});
    CHECK(map.size()==2 && map[0].strengthTag==4 && map[1].start==2);
    CHECK(plannerVocalStrength(map,{0,0})==4);
    CHECK(plannerVocalStrength(map,{1,2})==0); // endpoint contact includes zero strength
    CHECK(!plannerVocalStrength(map,{.1,.9}));
    rejects([]{makePlannerVocalMap({{0,1,3,4}});});
    rejects([]{makePlannerVocalMap({{0,1,1,5}});});
    rejects([&]{plannerVocalStrength(map,{1,0});});
    CHECK(!normalizeCloudVocalActivities(std::nullopt));
    CHECK(normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{})->empty());
    const std::vector<std::string> strengths{"very-low","low","medium","high","very-high"};
    const std::vector<std::string> kinds{"singing","speech","rapping"};
    for(unsigned strength=0;strength<5;++strength) for(unsigned kind=0;kind<3;++kind) {
      CloudVocalActivity cloud{{-1500,2500},strengths[strength],kinds[kind]};
      const auto converted=normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{cloud});
      CHECK(converted && converted->size()==1);
      CHECK(converted->front().start==-1.5 && converted->front().end==2.5);
      CHECK(converted->front().kindTag==kind && converted->front().strengthTag==strength);
    }
    CloudVocalActivity unknown{{0,1000},"veryHigh","UNKNOWN"};
    const auto fallback=normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{unknown});
    CHECK(fallback->front().kindTag==0 && fallback->front().strengthTag==2);
    unknown.strength.reset(); unknown.kind.reset();
    const auto missing=normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{unknown});
    CHECK(missing->front().kindTag==0 && missing->front().strengthTag==2);
    unknown.time.endInMilliseconds.reset();
    CHECK(normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{unknown})->empty());
    unknown.time={1000,0};
    CHECK(normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{unknown})->empty());
    unknown.time={0.5,1000};
    rejects([&]{normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{unknown});});
    unknown.time={0,0x1p63};
    rejects([&]{normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{unknown});});
    unknown.time={-0x1p63,0};
    CHECK(normalizeCloudVocalActivities(std::vector<CloudVocalActivity>{unknown})->size()==1);
    std::cout<<count<<" original vocal overlap cases and "<<3*count<<" leading predicates match\n";
  } catch(const std::exception& e) {std::cerr<<e.what()<<'\n';return 1;}
}

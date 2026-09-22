#include "lmg/automix/planner_loudness.h"
#include <cstdint>
#include <cmath>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> T read(std::ifstream& file) {
  T value{}; file.read(reinterpret_cast<char*>(&value),sizeof value);
  CHECK(file.gcount()==sizeof value); return value;
}
std::uint64_t bits(double v) { std::uint64_t n; std::memcpy(&n,&v,sizeof n); return n; }
PlannerLoudnessMap readMap(std::ifstream& f,unsigned n) {
  PlannerLoudnessMap points(n);
  for(auto& p:points) { p.value=read<double>(f);p.songTime=read<double>(f); }
  return points;
}
template<class F> void rejects(F fn) {
  bool caught=false;try { fn(); } catch(const std::invalid_argument&) { caught=true; }
  CHECK(caught);
}
void references() {
  std::ifstream maps(std::string(LMG_AUTOMIX_FIXTURES)+"/planner_loudness_maps.bin",std::ios::binary);
  CHECK(maps.good());const auto n=read<unsigned>(maps);CHECK(n==50);
  for(unsigned i=0;i<n;++i) {
    const auto count=read<unsigned>(maps),present=read<unsigned>(maps);
    const double frequency=read<double>(maps),duration=read<double>(maps);
    std::vector<double> values(count);for(auto& v:values)v=read<double>(maps);
    const auto expected=readMap(maps,count);
    const auto actual=makePlannerLoudnessMap(values,present?std::optional<double>(frequency):std::nullopt,duration);
    CHECK(actual && actual->size()==count);
    for(unsigned j=0;j<count;++j) {
      CHECK(bits((*actual)[j].value)==bits(expected[j].value));
      CHECK(bits((*actual)[j].songTime)==bits(expected[j].songTime));
    }
  }
  CHECK(maps.peek()==std::char_traits<char>::eof());
  std::ifstream means(std::string(LMG_AUTOMIX_FIXTURES)+"/planner_loudness_means.bin",std::ios::binary);
  CHECK(means.good());const auto m=read<unsigned>(means);CHECK(m==510);
  for(unsigned i=0;i<m;++i) {
    const auto count=read<unsigned>(means);
    const PlannerLoudnessWindow window{read<double>(means),read<double>(means)};
    const auto selected=read<unsigned>(means);
    const auto input=readMap(means,count);readMap(means,selected);
    const auto expected=read<std::uint64_t>(means);const bool missing=read<unsigned>(means)!=0;
    const auto actual=meanPlannerLoudness(input,window);
    CHECK(actual.has_value()!=missing);if(actual)CHECK(bits(*actual)==expected);
  }
  CHECK(means.peek()==std::char_traits<char>::eof());
  std::cout<<n<<" source loudness maps and "<<m<<" source window means match\n";
}
void boundariesAndRatio() {
  const PlannerLoudnessMap map{{-40,0},{-20,1},{-10,2}};
  CHECK(meanPlannerLoudness(map,{1,1})==-20);
  CHECK(meanPlannerLoudness(map,{0,1})==-30);
  CHECK(meanPlannerLoudness(map,{1,2})==-15);
  CHECK(trailingIncomingLoudnessRatio(&map,true,{0,1})==2);
  CHECK(trailingIncomingLoudnessRatio(&map,true,{1,1})==1);
  CHECK(!trailingIncomingLoudnessRatio(&map,false,{0,1}));
  CHECK(!trailingIncomingLoudnessRatio(nullptr,true,{0,1}));
  CHECK(!meanPlannerLoudness(map,{3,4}));
  CHECK(!meanPlannerLoudness({}, {0,1}));
  CHECK(!trailingIncomingLoudnessRatio(&map,true,{0,3}));
  const PlannerLoudnessMap nonnegative{{0,0},{0,1},{-1,2}};
  CHECK(!trailingIncomingLoudnessRatio(&nonnegative,true,{0,1}));
  const PlannerLoudnessMap nonnegativeTail{{-2,0},{-2,1},{2,2}};
  CHECK(!trailingIncomingLoudnessRatio(&nonnegativeTail,true,{0,1}));
  CHECK(!makePlannerLoudnessMap({},2,1));
  CHECK(!makePlannerLoudnessMap({-1},std::nullopt,std::nullopt));
  CHECK(!makePlannerLoudnessMap({-1},std::nullopt,0));
  CHECK(!makePlannerLoudnessMap({-1},0,1)); // present invalid frequency does not use duration
  CHECK(makePlannerLoudnessMap({-1,-2},2,std::nullopt)->at(1).songTime==.5);
  CHECK(makePlannerLoudnessMap({-1,-2},std::nullopt,1.2)->at(1).songTime==.5);
  rejects([&]{meanPlannerLoudness(map,{1,0});});
  rejects([]{makePlannerLoudnessMap({-1},std::numeric_limits<double>::infinity(),1);});
}
int main() {
  try { references();boundariesAndRatio(); }
  catch(const std::exception& e) { std::cerr<<e.what()<<'\n';return 1; }
}

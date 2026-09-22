#include "lmg/automix/planner_scoring.h"
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
  T result{}; file.read(reinterpret_cast<char*>(&result), sizeof result);
  CHECK(file.gcount() == sizeof result); return result;
}
std::uint64_t bits(double v) { std::uint64_t n; std::memcpy(&n,&v,sizeof n); return n; }
template<class F> void rejects(F fn) {
  bool caught=false;
  try { fn(); } catch(const std::invalid_argument&) { caught=true; }
  CHECK(caught);
}
void references() {
  std::ifstream tempo(std::string(LMG_AUTOMIX_FIXTURES)+"/planner_tempos.bin",std::ios::binary);
  CHECK(tempo.good()); const auto n=read<std::uint32_t>(tempo); CHECK(n>=3000);
  for (unsigned i=0;i<n;++i) {
    PlannerTempo a{read<double>(tempo),read<std::uint32_t>(tempo)!=0};
    PlannerTempo b{read<double>(tempo),read<std::uint32_t>(tempo)!=0};
    const auto tolerance=read<double>(tempo); const auto expected=read<std::uint32_t>(tempo);
    const auto actual=matchPlannerTempos(a,b,tolerance);
    if(actual.sourceTag!=expected) throw std::runtime_error("Tempo fixture "+std::to_string(i));
    CHECK(actual.compatible==(expected!=0xfc)); CHECK(actual.exact==(expected<3));
    if(actual.compatible) CHECK(static_cast<unsigned>(actual.scale)==(expected&3));
  }
  CHECK(tempo.peek()==std::char_traits<char>::eof());
  std::ifstream scores(std::string(LMG_AUTOMIX_FIXTURES)+"/planner_scores.bin",std::ios::binary);
  CHECK(scores.good()); const auto m=read<std::uint32_t>(scores); CHECK(m==306);
  for(unsigned i=0;i<m;++i) {
    const double base=read<double>(scores),delta=read<double>(scores);
    std::vector<double> weights(read<std::uint32_t>(scores));
    for(auto& w:weights) w=read<double>(scores);
    CHECK(bits(plannerCandidateScore(base,weights,delta))==read<std::uint64_t>(scores));
  }
  CHECK(scores.peek()==std::char_traits<char>::eof());
  std::cout<<n<<" original tempo cases and "<<m<<" original scores match\n";
}
void styles() {
  BeatMatchedScoreInputs x;
  x.normalTempoCompatible=true; x.expandedTempoCompatible=true;
  x.tonalityCompatible=true; x.matchingBarCount=8; x.barCountRatio=1;
  CHECK(beatMatchedStyleScore(8,x)==10); CHECK(beatMatchedStyleScore(9,x)==15);
  CHECK(beatMatchedStyleScore(12,x)==0); // expanded path excluded when normal passes
  x.normalTempoCompatible=false;
  CHECK(beatMatchedStyleScore(8,x)==0); CHECK(beatMatchedStyleScore(9,x)==0);
  CHECK(beatMatchedStyleScore(12,x)==10);
  x.expandedTempoCompatible=false; CHECK(beatMatchedStyleScore(12,x)==0);
  x.normalTempoCompatible=true; x.leadingIncomingVocalSignificant=true;
  x.trailingIncomingLoudnessRatio=2;
  CHECK(beatMatchedStyleScore(8,x)==15); CHECK(beatMatchedStyleScore(9,x)==22.5);
  x.barCountRatio.reset(); CHECK(beatMatchedStyleScore(8,x)==0);
  x.matchingBarCount=7; CHECK(beatMatchedStyleScore(9,x)==0);
  x.matchingBarCount.reset(); CHECK(beatMatchedStyleScore(9,x)==0);
  x.matchingBarCount=8; x.vocalRelationshipIncompatible=true;
  CHECK(beatMatchedStyleScore(9,x)==0);
  x.vocalRelationshipIncompatible=false; x.tonalityCompatible=false;
  CHECK(beatMatchedStyleScore(9,x)==0);
  for(auto id:{0,1,2,3,4,6,7,10,11,33,44}) CHECK(!beatMatchedStyleScore(id,x));
}
void selectionAndValidation() {
  CHECK(!bestPlannerCandidate({})); CHECK(!bestPlannerCandidate({-1,0,-0.}));
  CHECK(bestPlannerCandidate({-3,0,15,10,15})==2);
  CHECK(bestPlannerCandidate({10,10.001,10.001})==1);
  CHECK(plannerCandidateScore(0,{1},10000)==0);
  CHECK(plannerCandidateScore(1,{},-2000)==-1);
  CHECK(!bestPlannerCandidate({plannerCandidateScore(1,{},-2000)}));
  CHECK(matchPlannerTempos({120,false},{.5,true},0).exact);
  CHECK(matchPlannerTempos({120,false},{240,false},0).scale==TempoBinaryScale::half);
  CHECK(matchPlannerTempos({120,false},{60,false},0).scale==TempoBinaryScale::two);
  const auto scaled=applyTempoBinaryScale({.5,true},TempoBinaryScale::two);
  CHECK(scaled.secondsPerBeat && scaled.value==.25);
  rejects([] { matchPlannerTempos({0,false},{120,false},.16); });
  rejects([] { matchPlannerTempos({120,false},{120,false},-1); });
  rejects([] { bestPlannerCandidate({std::numeric_limits<double>::quiet_NaN()}); });
  rejects([] { plannerCandidateScore(1,{std::numeric_limits<double>::infinity()},0); });
  rejects([] { applyTempoBinaryScale({120,false},static_cast<TempoBinaryScale>(3)); });
}
void tonalityReferences() {
 std::ifstream input(std::string(LMG_AUTOMIX_FIXTURES)+"/planner_tonality.bin",std::ios::binary);
 CHECK(read<std::uint32_t>(input)==1764);
 for(unsigned i=0;i<1764;++i){
  PlannerTonality a{read<std::uint8_t>(input),read<std::uint8_t>(input)};
  PlannerTonality b{read<std::uint8_t>(input),read<std::uint8_t>(input)};
  CHECK(plannerTonalityRelationship(a,b)==read<std::uint8_t>(input));
 }
 CHECK(input.peek()==std::char_traits<char>::eof());
 CHECK(plannerTonalityRelationship(std::nullopt,PlannerTonality{0,0})==3);
 CHECK(!plannerMelodicnessInsignificant(std::nullopt));
 CHECK(plannerMelodicnessInsignificant(std::nextafter(.25,0.0)));
 CHECK(!plannerMelodicnessInsignificant(.25));
 CHECK(!plannerMelodicnessInsignificant(1));
 CHECK(plannerMelodicnessInsignificant(std::nextafter(1.,2.)));
 CHECK(plannerTonalitiesCompatible(std::nullopt,std::nullopt,.1,.9));
 CHECK(plannerTonalitiesCompatible(std::nullopt,std::nullopt,.9,.1));
 CHECK(!plannerTonalitiesCompatible(std::nullopt,std::nullopt,.9,.9));
 CHECK(!plannerTonalitiesCompatible(std::nullopt,std::nullopt,std::nullopt,std::nullopt));
}
void catalogSelection() {
 using A=PlannerAlgorithm;
 std::array<bool,4> allowed{true,true,true,true};
 std::array<std::vector<double>,4> scores{{{1000},{2000},{3000},{.1,.2,.2}}};
 auto result=selectPlannerCatalogCandidate(allowed,scores);
 CHECK(result&&result->algorithm==A::beatMatched&&result->candidateIndex==1);
 allowed[3]=false;result=selectPlannerCatalogCandidate(allowed,scores);
 CHECK(result&&result->algorithm==A::smart);
 scores[2]={0,-1};result=selectPlannerCatalogCandidate(allowed,scores);
 CHECK(result&&result->algorithm==A::deadAir);
 scores[1].clear();result=selectPlannerCatalogCandidate(allowed,scores);
 CHECK(result&&result->algorithm==A::fallback);
 scores[0]={0};CHECK(!selectPlannerCatalogCandidate(allowed,scores));
 allowed.fill(false);CHECK(!selectPlannerCatalogCandidate(allowed,scores));
 // Ineligible strategies are not evaluated, including their unbuilt scores.
 scores[3]={std::numeric_limits<double>::quiet_NaN()};
 CHECK(!selectPlannerCatalogCandidate(allowed,scores));
 allowed[3]=true;rejects([&]{selectPlannerCatalogCandidate(allowed,scores);});
}
void complexityGates() {
 using A=PlannerAlgorithm;using C=PlannerConfidence;
 CHECK(plannerDurationConfidence(100,101.999,false)==C::high);
 CHECK(plannerDurationConfidence(100,102,false)==C::none);
 CHECK(plannerDurationConfidence(100,98,false)==C::none);
 CHECK(plannerDurationConfidence(0,1000,true)==C::high);
 CHECK(plannerSpatialDriftConfidence(std::nullopt)==C::none);
 CHECK(plannerSpatialDriftConfidence(.04)==C::high);
 CHECK(plannerSpatialDriftConfidence(std::nextafter(.04,1.0))==C::low);
 const auto all=plannerDurationComplexities(C::high);
 const auto low=plannerDurationComplexities(C::low);
 CHECK(all.size()==4&&low.size()==3);
 CHECK(plannerEligibleAlgorithms(A::beatMatched,all,all)==PlannerComplexities({A::beatMatched,A::smart,A::deadAir,A::fallback}));
 CHECK(plannerEligibleAlgorithms(A::smart,all,low)==PlannerComplexities({A::smart,A::deadAir,A::fallback}));
 CHECK(plannerEligibleAlgorithms(A::beatMatched,{A::fallback,A::beatMatched},all)==PlannerComplexities({A::beatMatched,A::fallback}));
 CHECK(plannerEligibleAlgorithms(A::beatMatched,all,{}).empty());
 CHECK(plannerSpatialComplexities(C::low,C::high)==PlannerComplexities({A::fallback,A::beatMatched}));
 CHECK(plannerSpatialComplexities(C::high,C::high)==all);
 for(auto close:{C::none,C::low,C::high})for(auto drift:{C::none,C::low})
  CHECK(plannerSpatialComplexities(close,drift)==PlannerComplexities({A::fallback}));
 CHECK(!plannerComplexityAllowed(A::beatMatched,A::beatMatched,all,low));
 CHECK(!plannerComplexityAllowed(A::beatMatched,A::smart,all,all));
 CHECK(plannerComplexityAllowed(A::smart,A::smart,all,low));
 CHECK(!plannerComplexityAllowed(A::fallback,A::beatMatched,{},all));
 CHECK(!plannerComplexityAllowed(A::fallback,A::beatMatched,all,{}));
 rejects([]{plannerDurationComplexities(static_cast<C>(3));});
}
int main() {
  try { references(); styles(); selectionAndValidation(); complexityGates(); catalogSelection(); tonalityReferences(); }
  catch(const std::exception& e) { std::cerr<<e.what()<<'\n'; return 1; }
}

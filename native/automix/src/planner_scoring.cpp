#include "lmg/automix/planner_scoring.h"
#include <cmath>
#include <algorithm>
#include <stdexcept>

namespace lmg::automix {
namespace {
void finite(double value) {
  if (!std::isfinite(value)) throw std::invalid_argument("Planner value must be finite");
}
void validTempo(PlannerTempo tempo) {
  finite(tempo.value);
  if (!(tempo.value > 0)) throw std::invalid_argument("Planner tempo must be positive");
}
double bpm(PlannerTempo tempo) {
  const double value = tempo.secondsPerBeat ? 60.0 / tempo.value : tempo.value;
  finite(value);
  if (!(value > 0)) throw std::invalid_argument("Planner tempo conversion underflow");
  return value;
}
bool sameTempo(PlannerTempo a, PlannerTempo b) {
  // Source compares in the original representation if tags are equal. Converting
  // both to BPM first changes exact/approximate classification after rounding.
  if (a.secondsPerBeat == b.secondsPerBeat) return a.value == b.value;
  return bpm(a) == bpm(b);
}
} // namespace

namespace {
void validConfidence(PlannerConfidence c) {
 if(static_cast<unsigned>(c)>2)throw std::invalid_argument("Invalid planner confidence");
}
void validAlgorithm(PlannerAlgorithm a) {
 if(static_cast<unsigned>(a)>3)throw std::invalid_argument("Invalid algorithm complexity");
}
}
PlannerConfidence plannerDurationConfidence(double expected,double actual,bool notDownloaded) {
 // 272224eac: the not-downloaded branch does not inspect either duration.
 if(notDownloaded)return PlannerConfidence::high;
 finite(expected);finite(actual);
 return std::abs(expected-actual)<2.0?PlannerConfidence::high:PlannerConfidence::none;
}
PlannerConfidence plannerSpatialDriftConfidence(std::optional<double> drift) {
 if(!drift)return PlannerConfidence::none;
 finite(*drift);
 // 272225810 compares the supplied value, not abs(value).
 return *drift<=0.04?PlannerConfidence::high:PlannerConfidence::low;
}
PlannerComplexities plannerDurationComplexities(PlannerConfidence c) {
 validConfidence(c);
 if(c==PlannerConfidence::none)return {PlannerAlgorithm::fallback};
 if(c==PlannerConfidence::low)return {PlannerAlgorithm::fallback,PlannerAlgorithm::deadAir,PlannerAlgorithm::smart};
 return {PlannerAlgorithm::fallback,PlannerAlgorithm::deadAir,PlannerAlgorithm::smart,PlannerAlgorithm::beatMatched};
}
PlannerComplexities plannerSpatialComplexities(PlannerConfidence close,PlannerConfidence drift) {
 validConfidence(close);validConfidence(drift);
 if(close==PlannerConfidence::none||drift!=PlannerConfidence::high)return {PlannerAlgorithm::fallback};
 if(close==PlannerConfidence::low)return {PlannerAlgorithm::fallback,PlannerAlgorithm::beatMatched};
 return plannerDurationComplexities(PlannerConfidence::high);
}
bool plannerComplexityAllowed(PlannerAlgorithm algorithm,PlannerAlgorithm maximum,
 const PlannerComplexities& outgoing,const PlannerComplexities& incoming) {
 validAlgorithm(algorithm);validAlgorithm(maximum);
 for(auto a:outgoing)validAlgorithm(a);
 for(auto a:incoming)validAlgorithm(a);
 return maximum>=algorithm&&std::find(outgoing.begin(),outgoing.end(),algorithm)!=outgoing.end()&&
   std::find(incoming.begin(),incoming.end(),algorithm)!=incoming.end();
}

PlannerComplexities plannerEligibleAlgorithms(PlannerAlgorithm maximum,
 const PlannerComplexities& outgoing,const PlannerComplexities& incoming) {
 PlannerComplexities result;
 for(auto algorithm:kPlannerAlgorithmOrder)
  if(plannerComplexityAllowed(algorithm,maximum,outgoing,incoming))result.push_back(algorithm);
 return result;
}

std::uint8_t plannerTonalityRelationship(std::optional<PlannerTonality> a,
 std::optional<PlannerTonality> b) noexcept {
 if(!a||!b)return 3;
 if(a->tonic==b->tonic&&a->mode==b->mode)return 0;
 if(a->mode>1||b->mode>1)return 3;
 constexpr std::array<std::uint8_t,12> mode0{4,11,6,1,8,3,10,5,0,7,2,9};
 constexpr std::array<std::uint8_t,12> mode1{1,8,3,10,5,0,7,2,9,4,11,6};
 const auto& at=a->mode==0?mode0:mode1;
 const auto& bt=b->mode==0?mode0:mode1;
 const auto ai=std::find(at.begin(),at.end(),a->tonic)-at.begin();
 const auto bi=std::find(bt.begin(),bt.end(),b->tonic)-bt.begin();
 if(ai==12||bi==12)return 3;
 if(a->mode!=b->mode)return ai==bi?1:3;
 const auto delta=ai>bi?ai-bi:bi-ai;
 return delta==1||delta==11?2:3;
}
bool plannerMelodicnessInsignificant(std::optional<double> value) {
 if(!value)return false;
 finite(*value);return *value<.25||*value>1;
}
bool plannerTonalitiesCompatible(std::optional<PlannerTonality> a,
 std::optional<PlannerTonality> b,std::optional<double> am,std::optional<double> bm) {
 return plannerTonalityRelationship(a,b)!=3||
   plannerMelodicnessInsignificant(am)||plannerMelodicnessInsignificant(bm);
}

PlannerTempo applyTempoBinaryScale(PlannerTempo tempo, TempoBinaryScale scale) {
  validTempo(tempo);
  if (scale == TempoBinaryScale::one) return tempo;
  if (scale != TempoBinaryScale::half && scale != TempoBinaryScale::two)
    throw std::invalid_argument("Invalid tempo binary scale");
  const double factor = scale == TempoBinaryScale::half ? 0.5 : 2.0;
  tempo.value = tempo.secondsPerBeat ? tempo.value / factor : factor * tempo.value;
  validTempo(tempo);
  return tempo;
}

PlannerTempoMatch matchPlannerTempos(PlannerTempo reference, PlannerTempo candidate,
                                    double tolerance) {
  validTempo(reference); validTempo(candidate); finite(tolerance);
  if (tolerance < 0) throw std::invalid_argument("Negative tempo tolerance");
  const double referenceLog = std::log(bpm(reference));
  auto selected = TempoBinaryScale::half;
  double minimum = std::abs(std::log(bpm(applyTempoBinaryScale(candidate, selected))) - referenceLog);
  for (const auto scale : {TempoBinaryScale::one, TempoBinaryScale::two}) {
    const double distance = std::abs(std::log(bpm(applyTempoBinaryScale(candidate, scale))) - referenceLog);
    if (distance < minimum) { minimum = distance; selected = scale; }
  }
  const auto scaled = applyTempoBinaryScale(candidate, selected);
  const auto tag = static_cast<std::uint8_t>(selected);
  if (sameTempo(scaled, reference)) return {selected, true, true, tag};
  const bool compatible = minimum <= tolerance;
  return {selected, compatible, false,
          static_cast<std::uint8_t>(compatible ? (tag | 0x80) : 0xfc)};
}

double plannerCandidateScore(double base, const std::vector<double>& weights,
                             double tieBreakDelta) {
  finite(base); finite(tieBreakDelta);
  double product = base;
  for (const double weight : weights) { finite(weight); product = product * weight; }
  finite(product);
  // Keep multiply and add separate, including on ARM (compile -ffp-contract=off).
  if (!(product > 0)) return product;
  const double adjustment = tieBreakDelta * 0.001;
  const double result = adjustment + product;
  finite(result);
  return result;
}

std::optional<std::size_t> bestPlannerCandidate(const std::vector<double>& scores) {
  std::optional<std::size_t> winner;
  for (std::size_t i = 0; i < scores.size(); ++i) {
    finite(scores[i]);
    if (scores[i] > 0 && (!winner || scores[*winner] < scores[i])) winner = i;
  }
  return winner;
}

std::optional<PlannerCatalogSelection> selectPlannerCatalogCandidate(
 const std::array<bool,4>& eligible,const std::array<std::vector<double>,4>& scores) {
 for(auto algorithm:kPlannerAlgorithmOrder){
  const auto index=static_cast<std::size_t>(algorithm);
  if(!eligible[index])continue;
  if(auto candidate=bestPlannerCandidate(scores[index]))
   return PlannerCatalogSelection{algorithm,*candidate};
 }
 return std::nullopt;
}

std::optional<double> beatMatchedStyleScore(std::int64_t styleId,
                                          const BeatMatchedScoreInputs& in) {
  if (styleId != 8 && styleId != 9 && styleId != 12) return std::nullopt;
  const double leading = in.leadingIncomingVocalSignificant ? 0.75 : 1.0;
  const double trailing = in.trailingIncomingLoudnessRatio.value_or(1.0);
  const double bars = in.matchingBarCount && *in.matchingBarCount > 7 ? 1.0 : 0.0;
  if (styleId == 8)
    return plannerCandidateScore(10.0, {in.normalTempoCompatible ? 1.0 : 0.0,
        leading, in.barCountRatio.value_or(0.0), trailing}, in.tieBreakDelta);
  if (styleId == 9)
    return plannerCandidateScore(15.0, {in.normalTempoCompatible ? 1.0 : 0.0,
        in.tonalityCompatible ? 1.0 : 0.0, bars,
        in.vocalRelationshipIncompatible ? 0.0 : 1.0, leading, trailing}, in.tieBreakDelta);
  // 272234944..948: expanded style requires NORMAL INCOMPATIBILITY.
  return plannerCandidateScore(10.0,
      {!in.normalTempoCompatible && in.expandedTempoCompatible ? 1.0 : 0.0,
       bars, leading, trailing}, in.tieBreakDelta);
}
} // namespace lmg::automix

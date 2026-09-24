#include "lmg/automix/planner_transition_schedule.h"
#include <algorithm>
#include <cmath>
#include <limits>
#include <stdexcept>
#include <utility>

namespace lmg::automix {
namespace {
void need(bool ok) { if(!ok) throw std::invalid_argument("Unsupported structured schedule input"); }
void window(SecondsWindow w) {
  need(std::isfinite(w.begin) && std::isfinite(w.end) && w.begin>=0 && w.end>w.begin &&
       std::isfinite(w.end-w.begin));
}
void positive(double v) { need(std::isfinite(v) && v>0); }
std::int64_t beats(const SongStructure& structure, StructureRegion region, SecondsWindow w) {
  need(structure.events.size()<=4096 && region.startEvent<region.endEvent &&
       region.endEvent<structure.events.size());
  const auto& a=structure.events[region.startEvent];
  const auto& b=structure.events[region.endEvent];
  need(a.songTime==w.begin && b.songTime==w.end && a.beatIndex>=0 && b.beatIndex>a.beatIndex);
  // Subtraction cannot overflow once both nonnegative ordered indices are checked.
  const auto n=b.beatIndex-a.beatIndex;
  need(n<=4096);
  // This preserves references and source order rather than snapping/deduplicating.
  for(auto i=region.startEvent;i<region.endEvent;++i)
    need(std::isfinite(structure.events[i].songTime) &&
         structure.events[i].songTime<structure.events[i+1].songTime &&
         structure.events[i].beatIndex<structure.events[i+1].beatIndex);
  return n;
}
void instructions(const std::vector<StyleInstruction>& list) {
  need(list.size()<=kPlannerScheduleMaxAutomationsPerSide);
  std::size_t count=0;
  for(const auto& instruction:list) {
    need(instruction.ramps.size()<=kPlannerScheduleMaxAutomationsPerSide);
    count+=instruction.ramps.size();
    // Reserve one possible bypa record; do not compile an arbitrary prefix.
    need(count<kPlannerScheduleMaxAutomationsPerSide);
    for(const auto& ramp:instruction.ramps)
      need(!ramp.from.parameterName && !ramp.to.parameterName);
  }
}
void validateAutomations(const std::vector<ContinuousAutomation>& list, SecondsWindow parent) {
  need(list.size()<=kPlannerScheduleMaxAutomationsPerSide);
  for(const auto& a:list) {
    need(a.points.size()>=2 && a.points.size()<=4);
    (void)ContinuousAutomationValues(a); // Existing value/curve validation, no new DSP formula.
    for(const auto& p:a.points) {
      need(std::isfinite(p.value) && std::isfinite(p.songTime) && p.songTime>=parent.begin &&
           p.songTime<=parent.end && p.curve && *p.curve<=0xfb);
      if(a.parameter.id=="ts_rate") positive(p.value);
    }
  }
}
}

PlannerStructuredRates plannerStructuredPlaybackRates(SecondsWindow out,SecondsWindow in,
    std::int64_t outBeats,std::int64_t inBeats,TempoBinaryScale scale) {
  window(out);window(in);
  need(outBeats>0 && outBeats<=4096 && inBeats>0 && inBeats<=4096 && static_cast<unsigned>(scale)<=2);
  constexpr double factors[]{0.5,1.0,2.0}; // Exact table at 272296638.
  // 27220fda4: multiply FIRST, divide second. These are beat indices, not bars/BPM.
  const double ratio=(factors[static_cast<unsigned>(scale)]*static_cast<double>(inBeats))/
                     static_cast<double>(outBeats);
  positive(ratio);
  const double outDuration=out.end-out.begin;
  const double effective=(in.end-in.begin)/ratio; // 27220fba0.
  positive(effective);
  const double outEnd=outDuration/effective; // 27220fb44; nondegenerate supported domain.
  const double virtualStart=in.end-effective;
  const double virtualStartRate=effective/outDuration;
  positive(outEnd);positive(virtualStartRate);need(std::isfinite(virtualStart));
  // 27220fc00 evaluates a virtual ramp at the ACTUAL incoming start. It is not
  // always effective/outDuration. Existing evaluator preserves end-before-start.
  const ContinuousAutomation virtualRamp{playbackRateParameter(),
      {{virtualStartRate,virtualStart,0x80},{1.0,in.end,0x80}}};
  const double inStart=ContinuousAutomationValues(virtualRamp).valueAt(in.begin);
  positive(inStart);
  return {ratio,effective,{1.0,outEnd},{inStart,1.0}};
}
PlaybackTimeMap PlannerScheduledSide::timeMap() const {
  return schedulePlaybackTimeMap(playbackTransitionRange.begin,startPlaybackSongTime,
                                 std::nullopt,automations);
}
PlannerTransitionSchedule::PlannerTransitionSchedule(std::int64_t id,TempoBinaryScale scale,double score,
    PlannerStructuredRates rates,SecondsWindow transition,double handoff,
    PlannerScheduledSide out,PlannerScheduledSide in)
    :styleId_(id),scale_(scale),score_(score),ratio_(rates.scaledBeatRatio),
     effective_(rates.effectiveIncomingDuration),transition_(transition),referenceTime_(handoff),
     out_(std::move(out)),in_(std::move(in)) {}

PlannerTransitionSchedule compilePlannerTransitionSchedule(const PlannerScoredCandidate& candidate,
    const SongStructure& outgoing,const SongStructure& incoming,const TransitionStyle& style) {
  need((candidate.styleId==8 || candidate.styleId==9 || candidate.styleId==12) && style.id==candidate.styleId);
  need(std::isfinite(candidate.score) && candidate.score>0 &&
       style.offset && style.offset->relative==0 && style.offset->offsetInSeconds.value_or(0)==0);
  SecondsWindow a{candidate.outgoingStart,candidate.outgoingEnd}, b{candidate.incomingStart,candidate.incomingEnd};
  window(a);window(b);
  const auto na=beats(outgoing,candidate.outgoing,a), nb=beats(incoming,candidate.incoming,b);
  const auto rates=plannerStructuredPlaybackRates(a,b,na,nb,candidate.incomingScale);
  instructions(style.outgoing);instructions(style.incoming);
  auto aa=compileContinuousStyle(style.outgoing,a,rates.outgoing);
  auto ba=compileContinuousStyle(style.incoming,b,rates.incoming);
  validateAutomations(aa,a);validateAutomations(ba,b);
  // Same first-descriptor/first-ramp semantics as the source constructor. These
  // temporary maps measure durations at transition anchor0 before final placement.
  const double ta=schedulePlaybackTimeMap(0,a.begin,std::nullopt,aa).stretchedDuration(a.begin,a.end);
  const double tb=schedulePlaybackTimeMap(0,b.begin,std::nullopt,ba).stretchedDuration(b.begin,b.end);
  positive(ta);positive(tb);
  // 27220fd28/568: OUTGOING anchored, not max(ta,tb), no forced duration equality.
  const double incomingStart=std::max(0.0,ta-tb);
  need(std::isfinite(incomingStart) && incomingStart<ta);
  const double handoff=incomingStart+(ta-incomingStart)*0.5;
  need(std::isfinite(handoff) && incomingStart<=handoff && handoff<=ta);
  PlannerScheduledSide out{candidate.outgoing,na,a,{0,ta},a.begin,ta,rates.outgoing,std::move(aa)};
  PlannerScheduledSide in{candidate.incoming,nb,b,{incomingStart,ta},b.begin,tb,rates.incoming,std::move(ba)};
  // Force final map construction here. No schedule escapes if a compiler/rate
  // combination is outside the existing PlaybackTimeMap's supported domain.
  for(const auto* side:{&out,&in}) {
    const auto map=side->timeMap();
    for(double t:{side->sourceRegion.begin,side->sourceRegion.end}) {
      const auto p=map.fromSong(t);
      need(std::isfinite(p.song)&&std::isfinite(p.stretchedSong)&&std::isfinite(p.transition));
    }
  }
  return PlannerTransitionSchedule(style.id,candidate.incomingScale,candidate.score,rates,
                                   {0,ta},handoff,std::move(out),std::move(in));
}
PlannerScheduleFreshness checkPlannerScheduleFreshness(std::int64_t generation,std::int64_t revision,
    std::int64_t currentGeneration,std::int64_t currentRevision,double cue,
    std::optional<double> position) noexcept {
  if(generation<0 || currentGeneration!=generation) return PlannerScheduleFreshness::staleGeneration;
  if(revision<=0 || currentRevision!=revision) return PlannerScheduleFreshness::staleRevision;
  if(!position) return PlannerScheduleFreshness::positionUnavailable;
  if(!std::isfinite(cue)||cue<0||!std::isfinite(*position)||*position<0)
    return PlannerScheduleFreshness::invalidPosition;
  return *position>cue?PlannerScheduleFreshness::startAlreadyPassed:PlannerScheduleFreshness::positionNotPastStart;
}
} // namespace lmg::automix

#pragma once
#include <cstdint>
namespace lmg::automix {
// MEMixerChannel::EEndOfStreamState values. Names describe their positions
// relative to TimePitch; graph-tail completion is an explicit external event.
enum class ChannelEndStage : std::int32_t {
  Running=0, Upstream=1, TimePitch=2, Downstream=3, Complete=4
};
class ChannelEndState {
 public:
  ChannelEndState(bool hasGraph,bool graphUpstream,bool hasTimePitch) noexcept
    : hasGraph_(hasGraph),graphUpstream_(graphUpstream),hasTimePitch_(hasTimePitch) {}
  void propagate(ChannelEndStage requested) noexcept;
  // Called AFTER the upstream pull has propagated any EOS event.
  void upstreamPulled(double sourceStart,std::uint32_t frames) noexcept;
  // Receives the source start reported by TimePitch's sample-time callback,
  // not an output frame or a rate-scaled guess.
  void timePitchReported(double mappedSourceStart) noexcept;
  ChannelEndStage stage() const noexcept {return stage_;}
  double timePitchBoundary() const noexcept {return boundary_;}
 private:
  bool hasGraph_,graphUpstream_,hasTimePitch_;
  ChannelEndStage stage_=ChannelEndStage::Running;
  double boundary_=1e63;
};
}

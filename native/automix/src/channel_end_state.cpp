#include "lmg/automix/channel_end_state.h"
namespace lmg::automix {
void ChannelEndState::propagate(ChannelEndStage requested) noexcept {
  // 1b8f635ec..63660: skip absent stages, advance monotonically.
  auto next=requested;
  if(next==ChannelEndStage::Upstream && !(hasGraph_&&graphUpstream_))
    next=ChannelEndStage::TimePitch;
  if(next==ChannelEndStage::TimePitch && !hasTimePitch_)
    next=ChannelEndStage::Downstream;
  if(next==ChannelEndStage::Downstream && (!hasGraph_||graphUpstream_))
    next=ChannelEndStage::Complete;
  if(next>stage_)stage_=next;
}
void ChannelEndState::upstreamPulled(double start,std::uint32_t frames) noexcept {
  // 1b8f5f2fc..5f324, sentinel from 1b916bed0.
  if(stage_==ChannelEndStage::TimePitch&&boundary_==1e63)
    boundary_=start+static_cast<double>(frames);
}
void ChannelEndState::timePitchReported(double source) noexcept {
  // 1b8f689d4..68a04. Compare in this direction to preserve unordered behavior.
  if(stage_==ChannelEndStage::TimePitch&&boundary_<=source)
    propagate(ChannelEndStage::Downstream);
}
}

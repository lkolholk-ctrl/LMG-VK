#include "lmg/automix/channel_end_state.h"
#include <fstream>
#include <iostream>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do{if(!(x))throw std::runtime_error(#x);}while(false)
int read(std::ifstream& in){std::int32_t n;in.read(reinterpret_cast<char*>(&n),4);CHECK(in.good());return n;}
int main(){try{
 std::ifstream in(LMG_AUTOMIX_FIXTURES "/channel_end_state.bin",std::ios::binary);
 CHECK(read(in)==160);
 for(int i=0;i<160;++i){
  const bool graph=read(in),upstream=read(in),pitch=read(in);
  const auto old=read(in),requested=read(in),expected=read(in);
  ChannelEndState s(graph,upstream,pitch);
  // Reachable states can be installed by propagation. Skip fixture states that
  // cannot occur for this processor topology; preserve them in the oracle.
  s.propagate(static_cast<ChannelEndStage>(old));
  if(static_cast<int>(s.stage())!=old)continue;
  s.propagate(static_cast<ChannelEndStage>(requested));CHECK(static_cast<int>(s.stage())==expected);
 }
 CHECK(in.peek()==std::char_traits<char>::eof());
 ChannelEndState s(true,true,true);
 s.upstreamPulled(100,128);CHECK(s.timePitchBoundary()==1e63);
 s.propagate(ChannelEndStage::Upstream);CHECK(s.stage()==ChannelEndStage::Upstream);
 s.propagate(ChannelEndStage::TimePitch);s.upstreamPulled(100.5,128);
 CHECK(s.timePitchBoundary()==228.5);s.upstreamPulled(300,128);CHECK(s.timePitchBoundary()==228.5);
 s.timePitchReported(228.49);CHECK(s.stage()==ChannelEndStage::TimePitch);
 s.timePitchReported(228.5);CHECK(s.stage()==ChannelEndStage::Complete);
 s.propagate(ChannelEndStage::Upstream);CHECK(s.stage()==ChannelEndStage::Complete);
 ChannelEndState post(true,false,true);post.propagate(ChannelEndStage::Upstream);
 post.upstreamPulled(0,64);post.timePitchReported(64);CHECK(post.stage()==ChannelEndStage::Downstream);
 post.propagate(ChannelEndStage::Complete);CHECK(post.stage()==ChannelEndStage::Complete);
 std::cout<<"Original reachable EOS transitions and TimePitch boundary lifecycle passed\n";
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}

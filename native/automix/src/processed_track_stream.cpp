#include "lmg/automix/processed_track_stream.h"
#include <cmath>
#include <stdexcept>
#include <utility>
namespace lmg::automix {
ProcessedTrackStream::ProcessedTrackStream(double fs, unsigned channels, unsigned maximumFrames,
    Frame firstFrame, double firstOutputFrame, EffectSettings initial,
    std::vector<GraphEvent> events, bool scheduled, const TimePitchControls& controls,
    TimePitchTimeMapView map)
 : channels_(channels), maximumFrames_(maximumFrames),
   graph_(PreparedGraph(fs,initial),channels,maximumFrames,firstFrame,std::move(events)),
   pitch_(fs,channels,maximumFrames,scheduled,controls),
   interleaved_(static_cast<std::size_t>(maximumFrames)*channels) {
  if(firstFrame<0 || firstFrame>9007199254740991LL || !std::isfinite(firstOutputFrame))
    throw std::invalid_argument("invalid processed-track origin");
  for(unsigned c=0;c<channels_;++c) {
    input_[c].resize(maximumFrames_); output_[c].resize(maximumFrames_);
  }
  pitch_.reset(static_cast<double>(firstFrame),firstOutputFrame);
  if(map.sourceFrame) pitch_.setTimeMap(map);
}
void ProcessedTrackStream::flushPending() {
  if(offset_==pending_) return;
  const float* planes[]{input_[0].data()+offset_,nullptr};
  if(channels_==2) planes[1]=input_[1].data()+offset_;
  offset_+=pitch_.enqueue(planes,pending_-offset_,static_cast<double>(pendingStart_+offset_));
}
unsigned ProcessedTrackStream::enqueue(const float* input,unsigned frames,GraphSilence silence) {
  if(frames>maximumFrames_ || frames>9007199254740991LL-graph_.position())
    throw std::invalid_argument("invalid processed-track input length");
  if(!frames) return 0;
  if(input) for(std::size_t i=0;i<static_cast<std::size_t>(frames)*channels_;++i)
    if(!std::isfinite(input[i])) throw std::invalid_argument("nonfinite processed-track PCM");
  flushPending();
  if(offset_!=pending_) return 0;
  pendingStart_=graph_.position();
  if(!graph_.process(input,interleaved_.data(),frames,silence))
    throw std::invalid_argument("invalid processed-track graph block");
  for(unsigned i=0;i<frames;++i) for(unsigned c=0;c<channels_;++c)
    input_[c][i]=interleaved_[static_cast<std::size_t>(i)*channels_+c];
  pending_=frames; offset_=0;
  flushPending();
  return frames;
}
unsigned ProcessedTrackStream::dequeue(float* output,unsigned frames,double outputFrame) {
  if(frames>maximumFrames_ || (frames&&!output) || !std::isfinite(outputFrame))
    throw std::invalid_argument("invalid processed-track output block");
  if(!frames) return 0;
  flushPending();
  float* planes[]{output_[0].data(),output_[1].data()};
  const unsigned delivered=pitch_.dequeue(planes,frames,outputFrame);
  for(unsigned i=0;i<delivered;++i) for(unsigned c=0;c<channels_;++c)
    output[static_cast<std::size_t>(i)*channels_+c]=output_[c][i];
  flushPending();
  return delivered;
}
}

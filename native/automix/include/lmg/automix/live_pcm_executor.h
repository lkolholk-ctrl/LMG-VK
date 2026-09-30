#pragma once
#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace lmg::automix {
// Two-source bounded renderer. The host supplies only a Stage4a compiled plan,
// accepted source PCM and real EOS, never musical predicates or invented windows.
// No device, Player, thread, network, hidden limiter or wall clock is created.
struct LivePcmOptions {
  std::uint32_t sampleRate=48000, channels=2, maximumFrames=1024, effectStepFrames=256;
  std::int64_t generation=0, revision=0;
  // Live handoff only: prime the scheduled STFT with REAL outgoing pre-cue PCM.
  // Offline/legacy callers retain their cold-start contract unless opted in.
  bool primeOutgoing=false;
};
struct LivePcmStats {
  std::int64_t accepted[2]{}, zeroPadding[2]{}, dequeued[2]{}, consumed[2]{};
  std::int64_t produced=0, saturatedSamples=0, underrunPolls=0;
  bool eos[2]{}, finished=false, failed=false;
};
class LivePcmExecutor {
 public:
  LivePcmExecutor(const std::vector<std::int64_t>& compiledPlan, LivePcmOptions);
  ~LivePcmExecutor();
  LivePcmExecutor(const LivePcmExecutor&)=delete;
  LivePcmExecutor& operator=(const LivePcmExecutor&)=delete;
  // Source frames begin at the rounded cue minus outgoingPrerollFrames() on side 0.
  // Priming output is discarded internally; prefixes are handled by the output
  // owner. Return ONLY accepted frames; zero means backpressure, not EOF.
  unsigned push(unsigned side,const float* pcm,unsigned frames,std::int64_t firstSourceFrame);
  // Real decoded EOF must equal the accepted cursor. A short source cannot be
  // silently padded across its planned musical interval.
  void endInput(unsigned side,std::int64_t sourceEndFrame);
  // Output is mixed interleaved float32. Preserves per-side cached data when its
  // partner starves. No zero insertion except the scheduled pre-incoming gap and
  // bounded DSP lookahead after explicit EOS. Returned frames alone advance time.
  unsigned pull(float* output,unsigned frames);
  // Explicit output-format conversion: PCM16 saturates and counts; FLOAT does
  // not clamp. Neither conversion applies gain/master again. Little-endian bytes.
  void encode(const float* pcm,unsigned frames,unsigned bytesPerSample,void* output,std::size_t bytes);
  double sourceSecondsForOutput(unsigned side,double outputFrame) const;
  std::int64_t cueFrame(unsigned side) const;
  std::int64_t outgoingPrerollFrames() const;
  std::int64_t transitionFrames() const;
  LivePcmStats stats() const;
 private:
  struct Impl;std::unique_ptr<Impl> impl_;
};
} // namespace lmg::automix

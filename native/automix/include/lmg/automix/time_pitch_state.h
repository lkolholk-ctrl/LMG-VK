#pragma once
#include <array>
#include <cstdint>
namespace lmg::automix {
struct TimePitchHistoryEntry {
  bool active = false;
  double sourceTime = -999999;
  std::int64_t inputFrame = -999999, outputFrame = -999999;
};
// Source reset/advance fields only. Rate, transient state and phase storage are
// deliberately separate: original reset does not reset every constructor field.
struct TimePitchStreamState {
  std::int64_t totalInput = 0, totalOutput = 0;
  bool inputAnchorValid = false;
  double inputAnchorTime = -999999;
  std::int64_t inputAnchorFrame = -999999, inputAnchorOther = -999999;
  bool outputAnchorValid = true;
  double outputAnchorTime = 0;
  std::int64_t outputAnchorFrame = 0, outputAnchorOther = 0;
  std::array<TimePitchHistoryEntry,64> history{};
  std::uint32_t historyIndex = 0;
  double nextInputTime = 0, nextOutputTime = 0;
  double inputTime = 0, outputTime = 0;
  bool inputTimePending = true;
  double sourcePullTime = 0, processingInputTime = 0, outputPullTime = 0;
  std::int64_t inputWrite = 0, inputRead = 0, inputPadding = 0;
  std::int64_t outputWrite = 0, outputRead = 0, outputClear = 0, outputPadding = 0;
  std::uint32_t deliveredFrames = 0;
  bool spectralActive = false, phaseInitialized = true, mappingReset = true;
};
// Scalar part of 234f43a68. Caller separately zeros all input/output rings and,
// when spectral processing is enabled, N floats of each channel's phase arrays.
// Previous PCM and processingInputTime are retained, as in the source reset.
void timePitchResetStream(TimePitchStreamState&,std::uint32_t fftSize,
                         bool scheduledOrOffline,double inputTime,double outputTime) noexcept;
// 234f46208, after one committed spectral/identity hop. next*Time already set.
void timePitchCommitHop(TimePitchStreamState&,double effectiveInputHop,double outputHop) noexcept;
// 234f467f0 unmapped branch: window-weighted source position from hop history.
// fftSize/window and outputHop must match the active synthesis configuration.
double timePitchHistorySourceTime(const TimePitchStreamState&,std::uint32_t fftSize,
    double outputHop,const float* window,double outputTime);
// 234f44794. fftSize <= ringSize; ringSize is a validated power of two.
void timePitchCopyInputFrame(const float* ring,std::uint32_t ringSize,
                            std::int64_t cursor,float* frame,std::uint32_t fftSize) noexcept;
}

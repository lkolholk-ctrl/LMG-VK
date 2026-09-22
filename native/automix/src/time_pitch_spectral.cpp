#include "lmg/automix/time_pitch_spectral.h"
#include "lmg/automix/time_pitch_setup.h"
#include "lmg/automix/time_pitch_phase.h"
#include "lmg/automix/time_pitch_magnitude.h"
#include "lmg/automix/time_pitch_synthesis.h"
#include "lmg/automix/time_pitch_seed_phase.h"
#include <algorithm>
#include <cmath>
#include <stdexcept>
namespace lmg::automix {
namespace {
std::uint32_t validateChannels(std::uint32_t channels) {
  if(channels<1 || channels>2) throw std::invalid_argument("TimePitch channels must be one or two");
  return channels;
}
}
TimePitchSpectral::TimePitchSpectral(std::uint32_t n,std::uint32_t channels)
 : n_(n),bins_(n/2),channels_(validateChannels(channels)),fft_(n),
 window_(timePitchAnalysisWindow(n)),frame_(n),real_(bins_),imag_(bins_),
 remapReal_(bins_),remapImag_(bins_),magnitude_(bins_),correction_(bins_),scratch_(bins_),
 rotationReal_(bins_),rotationImag_(bins_),mapping_(bins_),peaks_(bins_),starts_(bins_),ends_(bins_) {
  for(std::uint32_t c=0;c<channels_;++c) {
    analysis_[c].resize(bins_);synthesis_[c].resize(bins_);previousIdentity_[c].resize(n_);
  }
  setPitch(1);
}
void TimePitchSpectral::reset() noexcept {
  for(std::uint32_t c=0;c<channels_;++c) {
    std::fill(analysis_[c].begin(),analysis_[c].end(),0);
    std::fill(synthesis_[c].begin(),synthesis_[c].end(),0);
  }
  timePitchPrepareBinMapping(mapping_.data(),bins_,pitch_);
}
void TimePitchSpectral::setPitch(float pitch) {
  if(!std::isfinite(pitch)||pitch<.03125f||pitch>32) throw std::invalid_argument("invalid TimePitch pitch ratio");
  pitch_=pitch;timePitchPrepareBinMapping(mapping_.data(),bins_,pitch_);
}
void TimePitchSpectral::processIdentity(const float* const* frames,float* const* rings,
    std::uint32_t ringSize,std::uint64_t cursor,float smoothness) noexcept {
  for(std::uint32_t c=0;c<channels_;++c)
    timePitchIdentityWindow(frames[c],previousIdentity_[c].data(),window_.data(),n_,
      smoothness,rings[c],ringSize,cursor);
}
void TimePitchSpectral::seedFromPreviousIdentity() noexcept {
  // 234f465fc..66a0: only the leading channel of a coherent pair is reseeded.
  // Previous identity PCM already carries one window and its gain; the source
  // applies the window again here, then converts radians to cycles.
  for(std::uint32_t i=0;i<n_;++i) frame_[i]=previousIdentity_[0][i]*window_[i];
  fft_.forward(frame_.data(),real_.data(),imag_.data());
  timePitchSeedPhaseRadians(real_.data(),imag_.data(),analysis_[0].data(),bins_);
  constexpr float radiansToCycles=0x1.45f306p-3f; // 0x3e22f983, source 234ffe0ac
  for(std::uint32_t i=0;i<bins_;++i) {
    analysis_[0][i]=analysis_[0][i]*radiansToCycles;
    synthesis_[0][i]=analysis_[0][i];
  }
}
void TimePitchSpectral::process(const float* const* frames,float* const* rings,
    std::uint32_t ringSize,std::uint64_t cursor,double rate,double outputHop,
    bool coherence,bool preserveTransients,TimePitchTransientState& transient) noexcept {
  const float inverse=1.0f/static_cast<float>(n_);
  for(std::uint32_t c=0;c<channels_;++c) {
    for(std::uint32_t i=0;i<n_;++i) frame_[i]=frames[c][i]*window_[i];
    fft_.forward(frame_.data(),real_.data(),imag_.data());
    if(c==0) {
      timePitchMagnitudes(real_.data(),imag_.data(),magnitude_.data(),bins_);
      timePitchAnalyzePhase(real_.data(),imag_.data(),analysis_[c].data(),synthesis_[c].data(),
        correction_.data(),bins_,inverse,transient.effectiveInputHop,outputHop,pitch_);
      if(coherence) {
        TimePitchRegions regions{peaks_.data(),starts_.data(),ends_.data()};
        timePitchApplyCoherence(magnitude_.data(),correction_.data(),mapping_.data(),bins_,pitch_,regions);
        if(preserveTransients) timePitchPreserveTransients(magnitude_.data(),analysis_[c].data(),
          correction_.data(),scratch_.data(),bins_,regions,rate,outputHop,transient);
      }
    }
    const auto edges=timePitchSynthesize(real_.data(),imag_.data(),analysis_[c].data(),correction_.data(),
      synthesis_[c].data(),rotationReal_.data(),rotationImag_.data(),bins_,outputHop,inverse,c==0);
    timePitchRemapSpectrum(real_.data(),imag_.data(),remapReal_.data(),remapImag_.data(),mapping_.data(),bins_,edges);
    fft_.inverse(remapReal_.data(),remapImag_.data(),frame_.data());
    timePitchOverlapAdd(rings[c],ringSize,cursor,frame_.data(),window_.data(),n_);
  }
}
} // namespace lmg::automix

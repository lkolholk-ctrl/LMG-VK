#include "pcm_processor.h"
#include <algorithm>
#include <cmath>
#include <cstring>

namespace lmg::player_dsp {
bool decodeParameters(const float* v, std::size_t count, dsp::Parameters& out) noexcept {
    if (!v || count != kParameterCount) return false;
    for (std::size_t i=0; i<count; ++i) if (!std::isfinite(v[i])) return false;
    auto flag=[](float x) { return x==0.f || x==1.f; };
    if (!flag(v[0]) || !flag(v[4]) || v[1]<0 || v[1]>2 || std::floor(v[1])!=v[1]) return false;
    dsp::Parameters p;
    p.bypass=v[0]==0; p.mode=static_cast<dsp::EqMode>(static_cast<int>(v[1]));
    p.preampDb=v[2]; p.headroomDb=v[3]; p.limiter.enabled=v[4]!=0;
    p.limiter.thresholdDb=v[5]; p.limiter.ceilingDb=v[6]; p.limiter.releaseMs=v[7];
    // Zero lookahead keeps Media3's PCM duration unchanged, including EOS/bypass.
    p.limiter.attackMs=0; p.transitionMs=v[8];
    for (std::size_t i=0; i<dsp::kGraphicBands; ++i) p.graphicGainDb[i]=v[9+i];
    for (std::size_t i=0; i<dsp::kParametricBands; ++i) {
        const auto j=19+i*6;
        if (!flag(v[j]) || v[j+1]<0 || v[j+1]>5 || std::floor(v[j+1])!=v[j+1]) return false;
        p.bands[i]={v[j]!=0, static_cast<dsp::FilterType>(static_cast<int>(v[j+1])),
            v[j+2], v[j+3], v[j+4], v[j+5]};
    }
    // Control-thread validation also covers the case with no configured audio sinks.
    dsp::Processor validator;
    if (validator.prepare({44100,2,1,0},p)!=dsp::Status::Ok) return false;
    out=p; return true;
}

dsp::Status PcmProcessor::prepare(int rate, int channels, const dsp::Parameters& p) noexcept {
    auto status=dsp_.prepare({static_cast<double>(rate),static_cast<std::uint32_t>(channels),kMaxFrames,0},p);
    if (status==dsp::Status::Ok) channels_=channels;
    return status;
}

bool PcmProcessor::process(const void* input, void* output, std::uint32_t frames, int width) noexcept {
    if (!channels_ || frames>kMaxFrames || (width!=2 && width!=4) || (!input && frames) || (!output && frames)) return false;
    const auto n=static_cast<std::size_t>(frames)*channels_;
    const auto* src=static_cast<const unsigned char*>(input);
    auto* dst=static_cast<unsigned char*>(output);
    for (std::size_t i=0;i<n;++i) {
        if (width==2) { std::int16_t s; std::memcpy(&s,src+i*2,2); scratch_[i]=s/32768.f; }
        else std::memcpy(&scratch_[i],src+i*4,4);
    }
    if (dsp_.process(scratch_.data(),frames)!=dsp::Status::Ok) return false;
    for (std::size_t i=0;i<n;++i) {
        if (width==2) {
            const auto s=static_cast<std::int16_t>(std::lround(std::clamp(scratch_[i],-1.f,32767.f/32768.f)*32768.f));
            std::memcpy(dst+i*2,&s,2);
        } else std::memcpy(dst+i*4,&scratch_[i],4);
    }
    return true;
}
}

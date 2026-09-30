#include "pcm_processor.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <vector>

using namespace lmg;
using namespace lmg::player_dsp;
#define CHECK(condition) do { if (!(condition)) { std::cerr << "Failed line " << __LINE__ << ": " #condition << '\n'; std::exit(1); } } while(false)

static dsp::Parameters parameters() {
    auto p=dsp::defaultParameters(); p.limiter.attackMs=0; p.bypass=false; return p;
}
static void exactDryPcm16() {
    auto p=parameters(); p.bypass=true;
    PcmProcessor dsp; CHECK(dsp.prepare(48000,2,p)==dsp::Status::Ok);
    std::vector<std::int16_t> input(65536), output(65536);
    for(int i=0;i<65536;++i) input[i]=static_cast<std::int16_t>(i-32768);
    for(int start=0;start<65536;start+=8192) CHECK(dsp.process(input.data()+start,output.data()+start,4096,2));
    CHECK(input==output); // All int16 values, including negative full scale.
    CHECK(!dsp.process(input.data(),output.data(),4097,2));
    CHECK(!dsp.process(input.data(),output.data(),1,3));
}
static void gainAndLimiter() {
    auto p=parameters(); p.preampDb=-6;
    PcmProcessor dsp; CHECK(dsp.prepare(44100,1,p)==dsp::Status::Ok);
    float in[4]={.25f,-.5f,1.f,-1.f},out[4]{};
    CHECK(dsp.process(in,out,4,4));
    for(int i=0;i<4;++i) CHECK(std::abs(out[i]-in[i]*std::pow(10.f,-6.f/20))<1e-6f);
    p.preampDb=12; p.limiter.enabled=true; p.limiter.thresholdDb=-1; p.limiter.ceilingDb=-.3;
    CHECK(dsp.prepare(192000,2,p)==dsp::Status::Ok);
    std::vector<float> loud(8192,1.f), limited(8192);
    CHECK(dsp.process(loud.data(),limited.data(),4096,4));
    for(auto sample:limited) CHECK(std::isfinite(sample) && std::abs(sample)<=std::pow(10.f,-.3f/20));
}
static void eqAndIndependentSinks() {
    auto p=parameters(); p.mode=dsp::EqMode::Graphic; p.graphicGainDb[5]=12;
    PcmProcessor a,b; CHECK(a.prepare(48000,2,p)==dsp::Status::Ok);CHECK(b.prepare(48000,2,p)==dsp::Status::Ok);
    std::vector<float> input(8192), whole(8192), split(8192);
    for(int i=0;i<4096;++i) input[2*i]=input[2*i+1]=.05f*std::sin(2*3.141592653589793*1000*i/48000);
    CHECK(a.process(input.data(),whole.data(),4096,4));
    for(int start=0;start<4096;start+=128) CHECK(b.process(input.data()+start*2,split.data()+start*2,128,4));
    CHECK(whole==split); // No block-size dependence or dropped frames at boundaries.
    double dry=0,wet=0;
    for(int i=1024;i<8192;++i) { dry+=input[i]*input[i];wet+=whole[i]*whole[i]; }
    CHECK(wet>dry*10);
    CHECK(a.reset()==dsp::Status::Ok);
    std::vector<float> silence(32),silentOut(32);
    CHECK(a.process(silence.data(),silentOut.data(),16,4));
    for(auto v:silentOut)CHECK(v==0); // Seek reset removes prior IIR state.
    p.mode=dsp::EqMode::Parametric; p.bands[0]={true,dsp::FilterType::Notch,1000,0,1,1};
    CHECK(a.prepare(48000,2,p)==dsp::Status::Ok);
    CHECK(a.process(input.data(),whole.data(),4096,4));
    wet=0;for(int i=1024;i<8192;++i)wet+=whole[i]*whole[i];CHECK(wet<dry*.01);
}
static void validationAndQueue() {
    std::array<float,kParameterCount> v{};
    v[0]=1;v[1]=1;v[5]=-1;v[6]=-.3;v[7]=80;v[8]=20;
    for(int i=0;i<8;++i) {v[19+i*6+2]=1000;v[19+i*6+4]=1;v[19+i*6+5]=1;}
    dsp::Parameters p;CHECK(decodeParameters(v.data(),v.size(),p));
    v[19+2]=24000;CHECK(!decodeParameters(v.data(),v.size(),p));v[19+2]=1000;
    v[2]=std::numeric_limits<float>::quiet_NaN();CHECK(!decodeParameters(v.data(),v.size(),p));
    CHECK(!decodeParameters(v.data(),v.size()-1,p));
    PcmProcessor a;CHECK(a.prepare(22050,2,parameters())==dsp::Status::InvalidArgument);
    CHECK(a.prepare(48000,6,parameters())==dsp::Status::InvalidArgument);
    p=parameters();CHECK(a.prepare(48000,1,p)==dsp::Status::Ok);
    bool full=false;for(int i=0;i<32;++i){p.preampDb=-i; if(a.submit(p)==dsp::Status::QueueFull){full=true;break;}}
    CHECK(full);
    float sample=.1f,out=0;CHECK(a.process(&sample,&out,1,4));
    p.preampDb=-18;CHECK(a.submit(p)==dsp::Status::Ok); // Final update can be retried after paused queue drains.
    std::vector<float> in(4096,.1f),result(4096);
    CHECK(a.process(in.data(),result.data(),4096,4));
    CHECK(std::abs(result.back()-.1f*std::pow(10.f,-18.f/20))<1e-6f);
}
int main(){exactDryPcm16();gainAndLimiter();eqAndIndependentSinks();validationAndQueue();std::cout<<"PCM adapter checks passed\n";}

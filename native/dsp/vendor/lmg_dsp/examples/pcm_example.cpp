// Generates PCM in memory, processes it, and optionally writes raw native-endian
// float32 interleaved PCM. No decoder, audio device, player, or JNI is involved.
#include "lmg_dsp/dsp.h"
#include <algorithm>
#include <cmath>
#include <fstream>
#include <iostream>
#include <vector>

namespace d = lmg::dsp;
int main(int argc, char** argv) {
    if (argc > 2) {
        std::cerr << "Usage: lmg_dsp_pcm_example [output.f32]\n";
        return 1;
    }
    constexpr double pi = 3.14159265358979323846;
    d::PrepareSpec spec;
    d::Parameters parameters;
    parameters.mode = d::EqMode::Graphic;
    parameters.graphicGainDb[5] = 3.0;
    parameters.headroomDb = 6.0;
    parameters.limiter.enabled = true;
    d::Processor processor;
    auto check = [](d::Status s) {
        if (s == d::Status::Ok) return true;
        std::cerr << d::statusString(s) << '\n';
        return false;
    };
    if (!check(processor.prepare(spec,parameters))) return 1;
    const auto frames = static_cast<std::uint32_t>(spec.sampleRate);
    std::vector<float> pcm(static_cast<std::size_t>(frames)*spec.channels);
    for (std::uint32_t n = 0; n < frames; ++n) {
        pcm[n*2] = static_cast<float>(0.25*std::sin(2.0*pi*1000.0*n/spec.sampleRate));
        pcm[n*2+1] = static_cast<float>(0.25*std::sin(2.0*pi*1500.0*n/spec.sampleRate));
    }
    std::vector<float> scratch(spec.maxBlockFrames*spec.channels);
    std::vector<float> rendered;
    rendered.reserve(pcm.size()); // host-side sink only, not inside process()
    const auto append = [&](const float* data, std::uint32_t count, const d::ProcessInfo& info) {
        // Remove KNOWN startup latency, never inspect amplitude for trimming.
        rendered.insert(rendered.end(),data+info.primingFrames*spec.channels,
                        data+count*spec.channels);
    };
    for (std::uint32_t offset = 0; offset < frames;) {
        const auto count = std::min(spec.maxBlockFrames,frames-offset);
        d::ProcessInfo info;
        float* data = pcm.data()+offset*spec.channels;
        if (!check(processor.process(data,count,&info))) return 1;
        append(data,count,info);
        offset += count;
    }
    // Final end, not a track boundary. tailFrames=0 emits only buffered PCM.
    if (!check(processor.endInput())) return 1;
    while (!processor.finished()) {
        std::uint32_t written = 0;
        d::ProcessInfo info;
        if (!check(processor.drain(scratch.data(),spec.maxBlockFrames,written,&info))) return 1;
        append(scratch.data(),written,info);
    }
    if (rendered.size() != static_cast<std::size_t>(frames)*spec.channels) return 2;
    if (argc == 2) {
        std::ofstream file(argv[1],std::ios::binary);
        file.write(reinterpret_cast<const char*>(rendered.data()),
                   static_cast<std::streamsize>(rendered.size()*sizeof(float)));
        if (!file) { std::cerr << "Unable to write output\n"; return 1; }
    }
    double peak = 0.0;
    for (float x : rendered) peak = std::max(peak,std::abs(static_cast<double>(x)));
    std::cout << "Rendered " << rendered.size()/spec.channels << " frames; fixed latency "
              << processor.latencyFrames() << " frames; peak " << peak << '\n';
}

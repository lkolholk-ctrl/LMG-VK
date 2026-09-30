#include "lmg_dsp/dsp.h"
#include "lmg_dsp/c_api.h"
#include "filter.h"
#include "snapshot_queue.h"
#include <algorithm>
#include <array>
#include <atomic>
#include <cmath>
#include <complex>
#include <cstdlib>
#include <cstring>
#include <functional>
#include <iostream>
#include <limits>
#include <new>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>
#ifdef _WIN32
#include <malloc.h>
#endif

// Test instrumentation, not part of the library. Covers C++17 normal/aligned,
// array, sized, and nothrow allocation. It does not interpose arbitrary C malloc.
namespace allocations {
thread_local bool realtime = false;
thread_local std::uint64_t allocated = 0, freed = 0;
void* allocate(std::size_t n) {
    if (realtime) ++allocated;
    if (void* p = std::malloc(n ? n : 1)) return p;
    throw std::bad_alloc();
}
void deallocate(void* p) noexcept {
    if (p && realtime) ++freed;
    std::free(p);
}
void* aligned(std::size_t n, std::size_t a) {
    if (realtime) ++allocated;
    void* p = nullptr;
#ifdef _WIN32
    p = _aligned_malloc(n ? n : 1,a);
#else
    if (posix_memalign(&p,a,n ? n : 1) != 0) p = nullptr;
#endif
    if (!p) throw std::bad_alloc();
    return p;
}
void unaligned(void* p) noexcept {
    if (p && realtime) ++freed;
#ifdef _WIN32
    _aligned_free(p);
#else
    std::free(p);
#endif
}
struct Scope {
    bool previous = realtime;
    Scope() { realtime = true; }
    ~Scope() { realtime = previous; }
};
}
void* operator new(std::size_t n) { return allocations::allocate(n); }
void* operator new[](std::size_t n) { return ::operator new(n); }
void operator delete(void* p) noexcept { allocations::deallocate(p); }
void operator delete[](void* p) noexcept { ::operator delete(p); }
void operator delete(void* p, std::size_t) noexcept { ::operator delete(p); }
void operator delete[](void* p, std::size_t) noexcept { ::operator delete(p); }
void* operator new(std::size_t n, std::align_val_t a) { return allocations::aligned(n,static_cast<std::size_t>(a)); }
void* operator new[](std::size_t n, std::align_val_t a) { return ::operator new(n,a); }
void operator delete(void* p, std::align_val_t) noexcept { allocations::unaligned(p); }
void operator delete[](void* p, std::align_val_t a) noexcept { ::operator delete(p,a); }
void operator delete(void* p, std::size_t, std::align_val_t a) noexcept { ::operator delete(p,a); }
void operator delete[](void* p, std::size_t, std::align_val_t a) noexcept { ::operator delete(p,a); }
void* operator new(std::size_t n, const std::nothrow_t&) noexcept { try { return ::operator new(n); } catch (...) { return nullptr; } }
void* operator new[](std::size_t n, const std::nothrow_t& t) noexcept { return ::operator new(n,t); }
void operator delete(void* p, const std::nothrow_t&) noexcept { ::operator delete(p); }
void operator delete[](void* p, const std::nothrow_t&) noexcept { ::operator delete(p); }
void* operator new(std::size_t n, std::align_val_t a, const std::nothrow_t&) noexcept { try { return ::operator new(n,a); } catch (...) { return nullptr; } }
void* operator new[](std::size_t n, std::align_val_t a, const std::nothrow_t& t) noexcept { return ::operator new(n,a,t); }
void operator delete(void* p, std::align_val_t a, const std::nothrow_t&) noexcept { ::operator delete(p,a); }
void operator delete[](void* p, std::align_val_t a, const std::nothrow_t&) noexcept { ::operator delete(p,a); }

namespace {
namespace d = lmg::dsp;
constexpr double pi = 3.141592653589793238462643383279502884;
void require(bool condition, const char* message) {
    if (!condition) throw std::runtime_error(message);
}
void ok(d::Status s) { require(s == d::Status::Ok,d::statusString(s)); }
void near(double actual, double expected, double tolerance, const char* what) {
    if (!std::isfinite(actual) || std::abs(actual-expected) > tolerance)
        throw std::runtime_error(std::string(what)+": actual="+std::to_string(actual)+
                                 ", expected="+std::to_string(expected));
}
void identical(const std::vector<float>& a, const std::vector<float>& b, const char* what) {
    require(a.size() == b.size(),what);
    require(a.empty() || std::memcmp(a.data(),b.data(),a.size()*sizeof(float)) == 0,what);
}
std::vector<float> noise(std::uint32_t frames, std::uint32_t channels, double scale = 0.25) {
    std::uint64_t state = 0x547893214abcd123ULL;
    std::vector<float> result(static_cast<std::size_t>(frames)*channels);
    for (auto& x : result) {
        state ^= state << 13; state ^= state >> 7; state ^= state << 17;
        x = static_cast<float>((static_cast<double>(state >> 11)/9007199254740992.0*2.0-1.0)*scale);
    }
    return result;
}
d::PrepareSpec spec(double rate = 48000.0, std::uint32_t channels = 1, double lookahead = 0.0) {
    return {rate,channels,1024,lookahead};
}
d::Parameters dry() { d::Parameters p; p.limiter.attackMs = 0.0; return p; }
void processChunks(d::Processor& processor, float* pcm, std::uint32_t frames,
                   std::uint32_t channels, std::uint32_t block = 1024) {
    for (std::uint32_t offset = 0; offset < frames;) {
        const auto count = std::min(block,frames-offset);
        d::Status status;
        { allocations::Scope guard; status = processor.process(pcm+offset*channels,count); }
        ok(status);
        offset += count;
    }
}
struct Rendered {
    std::vector<float> raw, aligned;
    std::uint64_t priming = 0, sanitized = 0, saturated = 0, resets = 0;
};
Rendered render(const d::PrepareSpec& s, const d::Parameters& p,
                const std::vector<float>& input, std::uint32_t block = 127,
                d::EndOptions end = {}) {
    d::Processor processor;
    ok(processor.prepare(s,p));
    Rendered result;
    std::vector<float> buffer(s.maxBlockFrames*s.channels);
    const auto append = [&](std::uint32_t count, const d::ProcessInfo& info) {
        require(info.primingFrames <= count,"invalid priming count");
        result.raw.insert(result.raw.end(),buffer.begin(),buffer.begin()+count*s.channels);
        result.aligned.insert(result.aligned.end(),buffer.begin()+info.primingFrames*s.channels,
                              buffer.begin()+count*s.channels);
        result.priming += info.primingFrames;
        result.sanitized += info.sanitizedInputSamples;
        result.saturated += info.saturatedOutputSamples;
        result.resets += info.numericResets;
    };
    const auto frames = static_cast<std::uint32_t>(input.size()/s.channels);
    for (std::uint32_t offset = 0; offset < frames;) {
        const auto count = std::min(block,frames-offset);
        std::copy_n(input.data()+offset*s.channels,count*s.channels,buffer.data());
        d::ProcessInfo info;
        d::Status status;
        { allocations::Scope guard; status = processor.process(buffer.data(),count,&info); }
        ok(status); append(count,info); offset += count;
    }
    d::Status status;
    { allocations::Scope guard; status = processor.endInput(end); }
    ok(status);
    while (!processor.finished()) {
        std::uint32_t written = 0;
        d::ProcessInfo info;
        { allocations::Scope guard; status = processor.drain(buffer.data(),block,written,&info); }
        ok(status); require(written > 0,"drain did not make progress"); append(written,info);
    }
    return result;
}

double measureDb(const d::Parameters& p, double rate, double frequency) {
    auto s = spec(rate);
    const auto count = static_cast<std::uint32_t>(rate);
    std::vector<float> samples(count*2);
    for (std::uint32_t i = 0; i < count*2; ++i)
        samples[i] = static_cast<float>(0.001*std::sin(2*pi*frequency*i/rate));
    d::Processor processor;
    ok(processor.prepare(s,p));
    processChunks(processor,samples.data(),count*2,1);
    double real = 0.0, imaginary = 0.0;
    for (std::uint32_t i = count; i < count*2; ++i) {
        const double phase = 2*pi*frequency*i/rate;
        real += samples[i]*std::cos(phase);
        imaginary -= samples[i]*std::sin(phase);
    }
    return 20.0*std::log10(std::max(1.0e-30,2.0*std::hypot(real,imaginary)/count/0.001));
}

// Independent reference: evaluate analog prototypes at the prewarped bilinear
// variable, NOT the production biquad coefficients or recurrence.
std::complex<double> reference(d::FilterType type, double rate, double frequency,
                               double center, double gain, double q, double slope) {
    const double r = std::tan(pi*frequency/rate)/std::tan(pi*center/rate);
    const std::complex<double> z(0.0,r);
    const double A = std::pow(10.0,gain/40.0);
    if (type == d::FilterType::LowShelf || type == d::FilterType::HighShelf)
        q = 1.0/std::sqrt((A+1.0/A)*(1.0/slope-1.0)+2.0);
    switch (type) {
    case d::FilterType::Peaking: return (z*z+z*(A/q)+1.0)/(z*z+z/(A*q)+1.0);
    case d::FilterType::LowPass: return 1.0/(z*z+z/q+1.0);
    case d::FilterType::HighPass: return z*z/(z*z+z/q+1.0);
    case d::FilterType::Notch: return (z*z+1.0)/(z*z+z/q+1.0);
    case d::FilterType::LowShelf:
        return A*(z*z+z*(std::sqrt(A)/q)+A)/(A*z*z+z*(std::sqrt(A)/q)+1.0);
    case d::FilterType::HighShelf:
        return A*(A*z*z+z*(std::sqrt(A)/q)+1.0)/(z*z+z*(std::sqrt(A)/q)+A);
    }
    throw std::runtime_error("unknown reference filter");
}

void testResponses() {
    for (double rate : {44100.0,48000.0,96000.0}) {
        for (std::uint32_t type = 0; type < 6; ++type) {
            auto p = dry(); p.mode = d::EqMode::Parametric;
            auto& b = p.bands[0];
            b.enabled = true; b.type = static_cast<d::FilterType>(type);
            b.frequencyHz = 1000.0; b.gainDb = 12.0; b.q = 0.7071067811865476; b.slope = 0.65;
            for (double f : {250.0,1000.0,4000.0}) {
                const double measured = measureDb(p,rate,f);
                if (b.type == d::FilterType::Notch && f == b.frequencyHz) {
                    require(measured < -100.0,"notch does not reject its center");
                } else {
                    const double expected = 20*std::log10(std::abs(reference(b.type,rate,f,
                        b.frequencyHz,b.gainDb,b.q,b.slope)));
                    near(measured,expected,0.01,"measured frequency response");
                }
            }
        }
        for (std::uint32_t band = 0; band < d::kGraphicBands; ++band) {
            auto p = dry(); p.mode = d::EqMode::Graphic; p.graphicGainDb[band] = 6.0;
            near(measureDb(p,rate,d::kGraphicFrequencies[band]),6.0,0.01,"graphic band center");
        }
    }
}
void testStabilityGrid() {
    for (double rate : {44100.0,48000.0,96000.0,192000.0})
    for (double frequency : {10.0,31.0,1000.0,16000.0,0.475*rate})
    for (double q : {0.1,0.7071067811865476,20.0})
    for (double gain : {-24.0,0.0,24.0})
    for (double slope : {0.1,1.0})
    for (std::uint32_t type = 0; type < 6; ++type) {
        d::detail::Coefficients c;
        require(d::detail::design(static_cast<d::FilterType>(type),rate,frequency,gain,q,slope,c),
                "coefficient design failed at range boundary");
        require(c.stable(),"Jury stability failure");
        const auto root = std::sqrt(std::complex<double>(c.a1*c.a1-4*c.a2,0.0));
        require(std::abs((-c.a1+root)*0.5) < 1.0 &&
                std::abs((-c.a1-root)*0.5) < 1.0,"pole outside unit circle");
        for (double probe : {100.0,2000.0,0.45*rate}) {
            const auto z = std::exp(std::complex<double>(0.0,-2*pi*probe/rate));
            const auto measured = (c.b0+c.b1*z+c.b2*z*z)/(1.0+c.a1*z+c.a2*z*z);
            const auto expected = reference(static_cast<d::FilterType>(type),rate,probe,
                                             frequency,gain,q,slope);
            require(std::abs(measured-expected) < 1.0e-5*(1.0+std::abs(expected)),
                    "coefficient/analog reference mismatch");
        }
    }
}
void testBoostCutCancellation() {
    for (double rate : {44100.0,48000.0,96000.0}) {
        auto p = dry(); p.mode = d::EqMode::Parametric;
        p.bands[0] = {true,d::FilterType::Peaking,700.0,12.0,3.0,1.0};
        p.bands[1] = p.bands[0]; p.bands[1].gainDb = -12.0;
        const auto input = noise(10000,1);
        const auto output = render(spec(rate),p,input).aligned;
        for (std::size_t i = 0; i < input.size(); ++i)
            near(output[i],input[i],3.0e-7,"boost/cut cancellation");
    }
}
void testBypassAndModes() {
    auto input = noise(3000,2,2.0);
    input[19] = -0.0f; input[34] = 1.0e-35f;
    auto p = dry(); p.bypass = true; p.mode = d::EqMode::Parametric;
    p.preampDb = 12.0; p.headroomDb = 3.0; p.limiter.enabled = true;
    p.bands[0] = {true,d::FilterType::LowPass,100.0,12.0,20.0,1.0};
    identical(render(spec(48000,2,5),p,input).aligned,input,"global bypass is not exact");
    for (auto mode : {d::EqMode::Off,d::EqMode::Graphic,d::EqMode::Parametric}) {
        p = dry(); p.mode = mode;
        identical(render(spec(48000,2,5),p,input).aligned,input,"neutral EQ is not exact");
    }
    p = dry(); p.mode = d::EqMode::Parametric;
    p.bands[0] = {true,d::FilterType::Peaking,1000.0,6.0,1.0,1.0};
    const auto parametric = render(spec(48000,2),p,input).aligned;
    p.graphicGainDb.fill(18.0);
    identical(render(spec(48000,2),p,input).aligned,parametric,"graphic EQ leaked into PEQ");
    p.mode = d::EqMode::Graphic;
    const auto graphic = render(spec(48000,2),p,input).aligned;
    p.bands[0].enabled = false;
    identical(render(spec(48000,2),p,input).aligned,graphic,"PEQ leaked into graphic EQ");
    p.mode = d::EqMode::Off;
    identical(render(spec(48000,2),p,input).aligned,input,"off mode is not dry");
}
void testChannelIndependence() {
    const auto mono = noise(5000,1);
    std::vector<float> stereo(mono.size()*2,0.0f);
    for (std::size_t i = 0; i < mono.size(); ++i) stereo[i*2] = mono[i];
    auto p = dry(); p.mode = d::EqMode::Graphic; p.graphicGainDb[5] = 12.0;
    const auto one = render(spec(),p,mono).aligned;
    const auto two = render(spec(48000,2),p,stereo).aligned;
    for (std::size_t i = 0; i < one.size(); ++i) {
        require(two[i*2] == one[i],"mono/stereo left mismatch");
        require(two[i*2+1] == 0.0f,"EQ crosstalk into right channel");
    }
}
void testLatencyAndEnd() {
    for (double rate : {44100.0,48000.0,96000.0})
    for (std::uint32_t channels : {1u,2u})
    for (double lookahead : {0.0,0.01,5.0,20.0}) {
        const auto delay = static_cast<std::uint32_t>(std::ceil(rate*lookahead/1000));
        const auto s = spec(rate,channels,lookahead);
        d::Processor processor; ok(processor.prepare(s,dry()));
        require(processor.latencyFrames() == delay,"latency report mismatch");
        for (auto n : {0u,1u,delay > 0 ? delay-1 : 0u,delay,delay+1,2013u}) {
            const auto input = noise(n,channels);
            const auto out = render(s,dry(),input,63);
            identical(out.aligned,input,"EOS lost, duplicated, or changed samples");
            require(out.raw.size() == (n == 0 ? 0 : n+delay)*channels,"raw EOS length mismatch");
            require(out.priming == (n == 0 ? 0 : delay),"priming count mismatch");
        }
    }
}
void testErrorsAndQueue() {
    d::Processor processor; float x = 0.25f;
    require(processor.process(&x,1) == d::Status::NotPrepared,"unprepared processing accepted");
    require(x == 0.25f,"error modified PCM");
    ok(processor.prepare(spec(),dry()));
    auto s = spec(); s.sampleRate = 22050;
    require(processor.prepare(s,dry()) == d::Status::InvalidArgument,"unsupported rate accepted");
    ok(processor.process(&x,1)); require(x == 0.25f,"failed prepare destroyed old engine");
    auto p = dry(); p.bands[0].frequencyHz = 0.475*48000+1;
    require(processor.submit(p) == d::Status::InvalidArgument,"Nyquist margin not enforced");
    p = dry(); p.bands[7].q = std::numeric_limits<double>::quiet_NaN();
    require(processor.submit(p) == d::Status::InvalidArgument,"inactive NaN accepted");
    p = dry(); p.mode = static_cast<d::EqMode>(99);
    require(processor.submit(p) == d::Status::InvalidArgument,"invalid EQ enum accepted");
    p = dry(); p.bands[0].slope = 1.01;
    require(processor.submit(p) == d::Status::InvalidArgument,"invalid shelf slope accepted");
    p = dry(); p.limiter.attackMs = 1;
    require(processor.submit(p) == d::Status::InvalidArgument,"attack beyond lookahead accepted");
    p = dry(); p.limiter.thresholdDb = 0; p.limiter.ceilingDb = -1;
    require(processor.submit(p) == d::Status::InvalidArgument,"threshold above ceiling accepted");
    for (int i = 0; i < 7; ++i) ok(processor.submit(dry()));
    require(processor.submit(dry()) == d::Status::QueueFull,"full queue not rejected");
    ok(processor.process(nullptr,0));
    require(processor.submit(dry()) == d::Status::QueueFull,"zero frames advanced control time");
    ok(processor.process(&x,1)); ok(processor.submit(dry()));
    require(processor.process(&x,1025) == d::Status::InvalidArgument,"oversized block accepted");
    require(processor.process(nullptr,1) == d::Status::InvalidArgument,"null PCM accepted");
    std::uint32_t written = 99;
    require(processor.drain(&x,1,written) == d::Status::InvalidState && written == 0,"early drain accepted");
    require(processor.endInput({10,1}) == d::Status::InvalidArgument,"one-frame fade accepted");
    ok(processor.endInput());
    require(processor.endInput() == d::Status::InvalidState,"duplicate EOS accepted");
    require(processor.process(&x,1) == d::Status::InvalidState,"processing after EOS accepted");
    ok(processor.reset()); ok(processor.process(&x,1));
}
void testSeekReset() {
    auto p = dry(); p.mode = d::EqMode::Parametric;
    p.bands[0] = {true,d::FilterType::LowPass,100.0,0.0,10.0,1.0};
    auto s = spec(48000,2,5);
    d::Processor a,b; ok(a.prepare(s,p));
    auto old = noise(1000,2); processChunks(a,old.data(),1000,2);
    p.bands[0].frequencyHz = 200.0;
    ok(a.submit(p)); ok(a.reset()); ok(b.prepare(s,p));
    auto x = noise(2000,2), y = x;
    processChunks(a,x.data(),2000,2,127); processChunks(b,y.data(),2000,2,511);
    identical(x,y,"seek reset did not clear history/adopt latest parameters");
}
void testFixedBlockIndependence() {
    auto p = dry(); p.mode = d::EqMode::Parametric; p.limiter.enabled = true;
    p.limiter.attackMs = 3; p.preampDb = 3;
    for (std::uint32_t i = 0; i < d::kParametricBands; ++i)
        p.bands[i] = {true,static_cast<d::FilterType>(i%3),50.0*std::pow(2.0,i),
                     (i%2 ? -3.0 : 3.0),1.5,0.7};
    const auto input = noise(8000,2,2.0);
    auto s = spec(48000,2,5);
    const auto a = render(s,p,input,1,{1000,200});
    const auto b = render(s,p,input,37,{1000,200});
    const auto c = render(s,p,input,1024,{1000,200});
    identical(a.raw,b.raw,"37-frame partition changed output");
    identical(a.raw,c.raw,"1024-frame partition changed output");
}
std::vector<float> automation(std::uint32_t block) {
    auto p = dry();
    const auto s = spec(48000,2,5);
    d::Processor processor; ok(processor.prepare(s,p));
    auto audio = noise(12000,2,0.75);
    const std::array<std::uint32_t,9> times{0,173,2200,2301,2500,5500,7000,7100,11000};
    std::size_t event = 0;
    for (std::uint32_t pos = 0; pos < 12000;) {
        if (event < times.size() && pos == times[event]) {
            p.mode = event%2 ? d::EqMode::Parametric : d::EqMode::Graphic;
            p.graphicGainDb[event%10] = 6.0;
            p.bands[0] = {true,d::FilterType::LowShelf,250.0,6.0,0.7,0.8};
            p.bypass = event == 5;
            p.preampDb = event%2 ? -6.0 : 0.0;
            p.limiter.enabled = event%3 != 0;
            p.limiter.attackMs = 2.0;
            p.transitionMs = event%2 ? 10.0 : 20.0;
            ok(processor.submit(p)); ++event;
        }
        const auto next = event < times.size() ? times[event] : 12000u;
        const auto count = std::min(block,next-pos);
        ok(processor.process(audio.data()+pos*2,count)); pos += count;
    }
    ok(processor.endInput({200,50}));
    std::vector<float> tail(block*2);
    while (!processor.finished()) {
        std::uint32_t count = 0; ok(processor.drain(tail.data(),block,count));
        audio.insert(audio.end(),tail.begin(),tail.begin()+count*2);
    }
    return audio;
}
void testAutomationBlockIndependence() {
    const auto a = automation(1);
    identical(a,automation(53),"automation depends on 53-frame partition");
    identical(a,automation(1024),"automation depends on 1024-frame partition");
}
void testSmoothChanges() {
    auto p = dry(); d::Processor processor;
    ok(processor.prepare(spec(),p));
    float first = 0.125f; ok(processor.process(&first,1));
    p.preampDb = -12.0; p.transitionMs = 20;
    ok(processor.submit(p));
    std::vector<float> audio(2000,0.125f);
    processChunks(processor,audio.data(),2000,1,127);
    require(audio[0] == first,"parameter update introduced a hard boundary step");
    for (std::size_t i = 1; i < audio.size(); ++i)
        require(std::abs(audio[i]-audio[i-1]) < 0.00016f,"preamp ramp has excessive step");
    near(audio.back(),0.125*std::pow(10.0,-12.0/20),1.0e-8,"preamp ramp endpoint");
    p = dry(); p.mode = d::EqMode::Parametric;
    p.bands[0] = {true,d::FilterType::LowShelf,500.0,12.0,0.7,1.0};
    ok(processor.prepare(spec(),p));
    audio.assign(48000,0.125f); processChunks(processor,audio.data(),48000,1);
    const float previous = audio.back();
    p.bypass = true; ok(processor.submit(p));
    audio.assign(2000,0.125f); processChunks(processor,audio.data(),2000,1);
    near(audio[0],previous,2.0e-7,"bypass boundary jump");
    for (std::size_t i = 1; i < audio.size(); ++i)
        require(std::abs(audio[i]-audio[i-1]) < 0.001f,"bypass ramp has excessive step");
    require(audio.back() == 0.125f,"bypass did not become exact dry");
}
void testLimiterPeaksAndLinking() {
    auto p = dry(); p.limiter.enabled = true;
    p.limiter.thresholdDb = -3; p.limiter.ceilingDb = -0.5; p.limiter.attackMs = 5;
    auto input = noise(20000,2,20.0);
    const double threshold = std::pow(10.0,-3.0/20), ceiling = std::pow(10.0,-0.5/20);
    auto output = render(spec(48000,2,5),p,input,97).aligned;
    for (float x : output) {
        require(std::isfinite(x) && std::abs(x) <= ceiling,"sample ceiling violated");
        require(std::abs(x) <= threshold+1.0e-6,"lookahead threshold proof violated");
    }
    input.assign(5000*2,0.0f);
    for (std::size_t i = 0; i < 5000; ++i) {
        input[i*2] = i%997 == 0 ? 20.0f : 2.0f;
        input[i*2+1] = input[i*2]*0.25f;
    }
    output = render(spec(48000,2,5),p,input).aligned;
    for (std::size_t i = 0; i < 5000; ++i)
        near(output[i*2+1],output[i*2]*0.25,1.0e-7,"limiter is not stereo-linked");
    input = noise(5000,2,0.1);
    identical(render(spec(48000,2,5),p,input).aligned,input,"hidden AGC below threshold");
}
void testLimiterAttackRelease() {
    auto p = dry(); p.limiter.enabled = true; p.limiter.thresholdDb = -6;
    p.limiter.ceilingDb = -0.3; p.limiter.releaseMs = 100;
    std::vector<float> input(10000,0.1f); input[0] = 2.0f;
    const auto output = render(spec(),p,input).aligned;
    const double g0 = std::pow(10.0,-6.0/20)/2.0;
    for (auto i : {1u,10u,100u,4800u,9600u}) {
        const double expectedGain = 1-(1-g0)*std::exp(-static_cast<double>(i)/4800.0);
        near(output[i],static_cast<double>(input[i])*expectedGain,1.0e-7,"release time constant");
    }
    p.limiter.attackMs = 5;
    input.assign(2000,0.1f); input[480] = 2.0f;
    const auto attacked = render(spec(48000,1,5),p,input).aligned;
    for (std::uint32_t k = 0; k < 150; ++k) {
        const double g = std::max(g0,1.0-(k+1.0)/240.0);
        near(attacked[240+k],static_cast<double>(input[240+k])*g,2.0e-7,"finite attack slew");
    }
    near(attacked[480],std::pow(10.0,-6.0/20),1.0e-6,"peak after lookahead");
}
void testLimiterEnableAndCeilingChange() {
    auto p = dry(); auto s = spec(48000,1,5);
    p.limiter.thresholdDb = -3; p.limiter.ceilingDb = -1;
    d::Processor processor; ok(processor.prepare(s,p));
    std::vector<float> warm(1000,3.0f); processChunks(processor,warm.data(),1000,1);
    p.limiter.enabled = true; ok(processor.submit(p));
    std::vector<float> result(2000,3.0f); processChunks(processor,result.data(),2000,1,37);
    require(result.front() == 3.0f,"enabling limiter introduced an immediate hard clamp");
    const double ceiling = std::pow(10.0,-1.0/20);
    for (std::size_t i = 960; i < result.size(); ++i)
        require(std::abs(result[i]) <= ceiling,"enabled limiter failed ceiling");
    p.limiter.thresholdDb = -12; p.limiter.ceilingDb = -6; ok(processor.submit(p));
    result.assign(2000,3.0f); processChunks(processor,result.data(),2000,1,53);
    for (std::size_t i = 0; i < result.size(); ++i) {
        const double t = std::min(1.0,static_cast<double>(i)/959.0);
        const double w = t*t*(3-2*t);
        const double effectiveCeiling = (1-w)*ceiling+w*std::pow(10.0,-6.0/20);
        require(std::abs(result[i]) <= effectiveCeiling+1.0e-7,"ramped ceiling violated");
    }
}
void testTailAndSilence() {
    auto p = dry(); p.mode = d::EqMode::Parametric;
    p.bands[0] = {true,d::FilterType::LowPass,50.0,0.0,10.0,1.0};
    std::vector<float> input(100,0); input[0] = 0.5f;
    const auto s = spec(48000,1,5);
    const auto withTail = render(s,p,input,19,{1200,0});
    auto extended = input; extended.resize(input.size()+1200,0.0f);
    const auto explicitZeros = render(s,p,extended,233);
    identical(withTail.raw,explicitZeros.raw,"IIR tail differs from explicit zero input");
    const auto faded = render(s,p,input,97,{1200,400}).aligned;
    require(faded.size() == 1300,"tail length changed");
    for (std::size_t i = 900; i < 1300; ++i) {
        const double t = static_cast<double>(i-900)/399;
        near(faded[i],withTail.aligned[i]*(1-t*t*(3-2*t)),1.0e-8,"tail fade envelope");
    }
    require(faded.back() == 0.0f,"tail fade did not reach zero");
    const auto silence = render(s,p,std::vector<float>(2,0.0f),1,{100,10});
    require(silence.aligned.size() == 102,"amplitude-based silence trimming occurred");
    require(render(s,p,{},1,{100,10}).raw.empty(),"empty stream emitted fake tail");
}
void testNumericDefence() {
    auto p = dry();
    std::vector<float> input = {std::numeric_limits<float>::quiet_NaN(),
        std::numeric_limits<float>::infinity(),-std::numeric_limits<float>::infinity(),
        std::numeric_limits<float>::denorm_min(),std::numeric_limits<float>::max(),
        -std::numeric_limits<float>::max(),1.0f,-0.0f,std::numeric_limits<float>::min()};
    const auto a = render(spec(),p,input);
    require(a.sanitized == 4 && a.saturated == 0 && a.resets == 0,"numeric report mismatch");
    for (float x : a.aligned) require(std::isfinite(x),"nonfinite output escaped");
    for (std::size_t i = 4; i < input.size(); ++i)
        require(std::memcmp(&a.aligned[i],&input[i],sizeof(float)) == 0,"finite dry sample changed");
    p.preampDb = 12;
    const auto b = render(spec(),p,input);
    require(b.saturated == 2,"finite float overflow not reported");
    p.limiter.enabled = true;
    const auto c = render(spec(),p,input);
    require(c.saturated == 0,"limiter did not contain huge finite samples");
    for (float x : c.aligned) require(std::isfinite(x) && std::abs(x) <= 1.0f,"numeric limiter failure");
}
void testExtremeCascades() {
    for (double rate : {44100.0,48000.0,96000.0}) {
        for (std::uint32_t trial = 0; trial < 24; ++trial) {
            auto p = dry(); p.mode = d::EqMode::Parametric;
            p.preampDb = 12; p.limiter.enabled = true;
            for (std::uint32_t i = 0; i < d::kParametricBands; ++i) {
                auto& b = p.bands[i]; b.enabled = true;
                b.type = static_cast<d::FilterType>((trial+i)%6);
                b.frequencyHz = trial%3 == 0 ? 10.0 : (trial%3 == 1 ? 0.475*rate : 1000.0);
                b.gainDb = trial%2 ? -24.0 : 24.0; b.q = trial%2 ? 0.1 : 20.0;
                b.slope = trial%2 ? 0.1 : 1.0;
            }
            auto input = noise(8000,2,1.0); input.resize(24000,0.0f);
            const auto out = render(spec(rate,2,5),p,input,251);
            require(out.resets == 0 && out.saturated == 0,"extreme valid cascade needed recovery");
            for (float x : out.aligned) require(std::isfinite(x) && std::abs(x) <= 1,"unstable cascade");
        }
    }
}
void testQInterpretationAndPreamp() {
    auto p = dry(); p.mode = d::EqMode::Parametric;
    p.bands[0] = {true,d::FilterType::LowShelf,1000.0,12.0,0.1,0.5};
    const auto input = noise(3000,1);
    const auto a = render(spec(),p,input).aligned;
    p.bands[0].q = 20;
    identical(render(spec(),p,input).aligned,a,"shelf incorrectly uses both Q and slope");
    p = dry(); p.preampDb = 6; p.headroomDb = 3;
    const auto out = render(spec(),p,input).aligned;
    for (std::size_t i = 0; i < input.size(); ++i)
        near(out[i],input[i]*std::pow(10.0,3.0/20),3.0e-8,"preamp/headroom applied incorrectly");
    d::Parameters preset;
    require(d::makePreset(d::Preset::Warm,preset),"preset unavailable");
    require(!preset.limiter.enabled && preset.headroomDb == 6,"preset has hidden dynamics");
    require(!d::makePreset(static_cast<d::Preset>(99),preset),"unknown preset accepted");
}
void testDrainBounds() {
    d::Processor processor; ok(processor.prepare(spec(48000,2,5),dry()));
    float input[2] = {0.2f,-0.3f}; ok(processor.process(input,1)); ok(processor.endInput());
    std::vector<float> buffer(2048,123.0f);
    std::uint32_t total = 0;
    while (!processor.finished()) {
        std::fill(buffer.begin(),buffer.end(),123.0f);
        std::uint32_t written = 0;
        ok(processor.drain(buffer.data(),127,written)); total += written;
        for (std::size_t i = written*2; i < buffer.size(); ++i)
            require(buffer[i] == 123.0f,"drain wrote beyond produced prefix");
    }
    require(total == 240,"drain count wrong");
    std::uint32_t written = 99; ok(processor.drain(nullptr,0,written));
    require(written == 0,"empty drain produced data");
}
void testRealtimeAllocations() {
    auto p = dry(); p.mode = d::EqMode::Graphic; p.graphicGainDb[0] = 12;
    d::Processor processor; ok(processor.prepare(spec(48000,2,5),p));
    p.mode = d::EqMode::Parametric;
    p.bands[0] = {true,d::FilterType::HighShelf,1000,6,0.7,1};
    ok(processor.submit(p));
    std::array<float,2048> pcm{};
    const auto allocationsBefore = allocations::allocated, freesBefore = allocations::freed;
    d::Status a,b,c,e,f;
    std::uint32_t written = 0;
    {
        allocations::Scope guard;
        a = processor.process(pcm.data(),1024);
        b = processor.reset();
        c = processor.process(pcm.data(),1024);
        e = processor.endInput({1500,100});
        f = d::Status::Ok;
        while (!processor.finished()) {
            auto status = processor.drain(pcm.data(),1024,written);
            if (status != d::Status::Ok) { f = status; break; }
        }
    }
    ok(a); ok(b); ok(c); ok(e); ok(f);
    require(allocations::allocated == allocationsBefore && allocations::freed == freesBefore,
            "allocation/deallocation in realtime path");
}
void testSnapshotConcurrency() {
    struct Snapshot { std::uint64_t sequence = 0; std::array<std::uint64_t,64> values{}; };
    d::detail::SnapshotQueue<Snapshot> queue;
    constexpr std::uint64_t last = 100000;
    std::thread producer([&] {
        for (std::uint64_t i = 1; i <= last; ++i) {
            Snapshot p; p.sequence = i; p.values.fill(i^0xabcdef1234567890ULL);
            while (!queue.push(p)) std::this_thread::yield();
        }
    });
    bool coherent = true;
    std::uint64_t received = 0;
    while (received < last) {
        Snapshot p;
        if (!queue.popLatest(p)) { std::this_thread::yield(); continue; }
        coherent = coherent && p.sequence > received;
        for (auto v : p.values) coherent = coherent && v == (p.sequence^0xabcdef1234567890ULL);
        received = p.sequence;
    }
    producer.join();
    require(coherent,"torn or unordered parameter snapshot");
}
void testProcessorConcurrency() {
    auto p = dry(); p.limiter.enabled = true;
    d::Processor processor; ok(processor.prepare(spec(48000,2,5),p));
    std::atomic<bool> done{false}, error{false};
    std::thread producer([&] {
        auto next = dry(); next.limiter.enabled = true; next.mode = d::EqMode::Graphic;
        for (std::uint32_t i = 0; i < 4000; ++i) {
            next.graphicGainDb[i%10] = static_cast<double>(i%25)-12;
            d::Status s;
            do { s = processor.submit(next); if (s == d::Status::QueueFull) std::this_thread::yield(); }
            while (s == d::Status::QueueFull);
            if (s != d::Status::Ok) error.store(true,std::memory_order_relaxed);
        }
        done.store(true,std::memory_order_release);
    });
    std::array<float,256> buffer{};
    bool valid = true;
    std::uint32_t count = 0;
    while (!done.load(std::memory_order_acquire) || count < 100) {
        buffer.fill(0.5f);
        d::Status status;
        { allocations::Scope guard; status = processor.process(buffer.data(),128); }
        valid = valid && status == d::Status::Ok;
        for (float x : buffer) valid = valid && std::isfinite(x) && std::abs(x) <= 1.0f;
        if (++count%57 == 0) {
            allocations::Scope guard;
            if (processor.reset() != d::Status::Ok) valid = false;
        }
    }
    producer.join();
    require(valid && !error.load(),"concurrent processing/submission failed");
}

struct Test { const char* name; void (*run)(); };
}
int main() {
    const Test tests[] = {
        {"measured_frequency_responses_44k_48k_96k",testResponses},
        {"boundary_grid_poles_and_independent_reference",testStabilityGrid},
        {"boost_cut_cancellation",testBoostCutCancellation},
        {"bit_exact_bypass_and_exclusive_eq_modes",testBypassAndModes},
        {"channel_independence",testChannelIndependence},
        {"latency_eos_short_empty_streams",testLatencyAndEnd},
        {"errors_validation_queue_backpressure",testErrorsAndQueue},
        {"seek_reset_adopts_latest_and_clears_state",testSeekReset},
        {"fixed_parameter_block_independence",testFixedBlockIndependence},
        {"scheduled_automation_block_independence",testAutomationBlockIndependence},
        {"smooth_preamp_and_bypass",testSmoothChanges},
        {"limiter_threshold_ceiling_stereo_link",testLimiterPeaksAndLinking},
        {"limiter_attack_release_timing",testLimiterAttackRelease},
        {"limiter_enable_and_dynamic_ceiling",testLimiterEnableAndCeilingChange},
        {"tail_fade_and_no_silence_trimming",testTailAndSilence},
        {"nan_inf_denormal_float_overflow",testNumericDefence},
        {"extreme_valid_cascades",testExtremeCascades},
        {"q_slope_preamp_headroom_presets",testQInterpretationAndPreamp},
        {"drain_memory_bounds",testDrainBounds},
        {"no_cpp_allocations_in_realtime",testRealtimeAllocations},
        {"spsc_snapshot_concurrency",testSnapshotConcurrency},
        {"processor_control_audio_concurrency",testProcessorConcurrency}
    };
    std::size_t passed = 0;
    for (const auto& test : tests) {
        try {
            test.run(); ++passed;
            std::cout << "PASS " << test.name << '\n';
        } catch (const std::exception& error) {
            std::cerr << "FAIL " << test.name << ": " << error.what() << '\n';
        }
    }
    std::cout << passed << '/' << sizeof(tests)/sizeof(tests[0]) << " groups passed\n";
    return passed == sizeof(tests)/sizeof(tests[0]) ? 0 : 1;
}

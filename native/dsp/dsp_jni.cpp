#include "pcm_processor.h"
#include <jni.h>
#include <memory>
#include <mutex>
#include <new>
#include <unordered_map>

namespace {
using namespace lmg;
struct Instance { player_dsp::PcmProcessor pcm; std::uint64_t revision=0; };
// Registry/lifecycle/producer only. Audio process/reset NEVER take this mutex.
std::mutex controlMutex;
std::unordered_map<Instance*,std::unique_ptr<Instance>> instances;
dsp::Parameters latest=[] { dsp::Parameters p; p.bypass=true; p.limiter.attackMs=0; return p; }();
std::uint64_t revision=1;

bool publishPending() {
    bool done=true;
    for (auto& entry:instances) {
        auto& i=*entry.second;
        if (i.revision==revision) continue;
        if (i.pcm.submit(latest)==dsp::Status::Ok) i.revision=revision;
        else done=false;
    }
    return done;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_lmg_vk_engine_dsp_NativeDsp_nativeCreate(JNIEnv*,jobject,jint rate,jint channels) {
    try {
        std::lock_guard<std::mutex> lock(controlMutex);
        auto p=std::make_unique<Instance>();
        if (p->pcm.prepare(rate,channels,latest)!=dsp::Status::Ok) return 0;
        p->revision=revision;
        auto* raw=p.get(); instances.emplace(raw,std::move(p));
        return reinterpret_cast<jlong>(raw);
    } catch (...) { return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_com_lmg_vk_engine_dsp_NativeDsp_nativeDestroy(JNIEnv*,jobject,jlong handle) {
    std::lock_guard<std::mutex> lock(controlMutex);
    instances.erase(reinterpret_cast<Instance*>(handle));
}
extern "C" JNIEXPORT void JNICALL
Java_com_lmg_vk_engine_dsp_NativeDsp_nativeReset(JNIEnv*,jobject,jlong handle) {
    if (handle) reinterpret_cast<Instance*>(handle)->pcm.reset();
}
extern "C" JNIEXPORT jint JNICALL
Java_com_lmg_vk_engine_dsp_NativeDsp_nativePublish(JNIEnv* env,jobject,jfloatArray values) {
    if (!values || env->GetArrayLength(values)!=player_dsp::kParameterCount) return -1;
    std::array<float,player_dsp::kParameterCount> wire{};
    env->GetFloatArrayRegion(values,0,wire.size(),wire.data());
    if (env->ExceptionCheck()) return -1;
    dsp::Parameters p;
    if (!player_dsp::decodeParameters(wire.data(),wire.size(),p)) return -1;
    std::lock_guard<std::mutex> lock(controlMutex);
    latest=p; ++revision;
    return publishPending()?1:0;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_lmg_vk_engine_dsp_NativeDsp_nativeRetry(JNIEnv*,jobject) {
    std::lock_guard<std::mutex> lock(controlMutex);
    return publishPending();
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_lmg_vk_engine_dsp_NativeDsp_nativeProcess(JNIEnv* env,jobject,jlong handle,
        jobject input,jint offset,jobject output,jint frames,jint width) {
    if (!handle || !input || !output || frames<0 || frames>static_cast<jint>(player_dsp::kMaxFrames) || offset<0 || (width!=2 && width!=4)) return false;
    auto* p=reinterpret_cast<Instance*>(handle);
    const jlong bytes=static_cast<jlong>(frames)*p->pcm.channels()*width;
    if (env->GetDirectBufferCapacity(input)<offset+bytes || env->GetDirectBufferCapacity(output)<bytes) return false;
    auto* src=static_cast<unsigned char*>(env->GetDirectBufferAddress(input));
    auto* dst=env->GetDirectBufferAddress(output);
    return src && dst && p->pcm.process(src+offset,dst,frames,width);
}

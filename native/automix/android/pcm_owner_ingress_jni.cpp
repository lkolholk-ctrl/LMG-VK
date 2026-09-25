#include "lmg/automix/pcm_owner_ingress.h"
#include <jni.h>
#include <atomic>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <unordered_map>
using lmg::automix::PcmOwnerIngress;
namespace {
struct Entry {
  Entry(unsigned c, unsigned e, unsigned n) : ingress(c, static_cast<lmg::automix::OwnerEncoding>(e), n) {}
  std::mutex operation;
  PcmOwnerIngress ingress;
};
std::mutex registryMutex;
std::unordered_map<jlong, std::shared_ptr<Entry>> registry;
jlong nextId = 1;
void failure(JNIEnv* env, const char* name, const char* text) noexcept {
  if (env->ExceptionCheck()) return;
  jclass type = env->FindClass(name);
  if (type) { env->ThrowNew(type, text); env->DeleteLocalRef(type); }
}
template<class T, class Run> T guarded(JNIEnv* env, T fallback, Run run) noexcept {
  try { return run(); }
  catch (const std::bad_alloc&) { failure(env,"java/lang/OutOfMemoryError","PCM owner allocation failed"); }
  catch (const std::invalid_argument&) { failure(env,"java/lang/IllegalArgumentException","Invalid PCM owner input"); }
  catch (...) { failure(env,"java/lang/IllegalStateException","PCM owner state or thread mismatch"); }
  return fallback;
}
std::shared_ptr<Entry> entry(jlong id) {
  std::lock_guard<std::mutex> lock(registryMutex);
  auto it = registry.find(id);
  if (it == registry.end()) throw std::logic_error("Unknown owner handle");
  return it->second;
}
std::uint8_t* buffer(JNIEnv* env, jobject value, jint offset, jint bytes) {
  if (!value || offset < 0 || bytes < 0) throw std::invalid_argument("Missing direct buffer");
  const auto capacity = env->GetDirectBufferCapacity(value);
  auto* data = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(value));
  if (!data || capacity < 0 || offset > capacity || bytes > capacity - offset)
    throw std::invalid_argument("Invalid direct range");
  return data + offset;
}
}
extern "C" JNIEXPORT jint JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeProtocol(JNIEnv*, jobject) { return 1; }
extern "C" JNIEXPORT jlong JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeCreate(
    JNIEnv* env, jobject, jint channels, jint encoding, jint capacity) {
  return guarded<jlong>(env,0,[&] {
    if (channels < 1 || channels > 2 || (encoding != 2 && encoding != 4) ||
        capacity <= 0 || capacity > jint(PcmOwnerIngress::maximumCapacity))
      throw std::invalid_argument("Invalid capacity");
    auto value = std::make_shared<Entry>(channels,encoding,capacity);
    std::lock_guard<std::mutex> lock(registryMutex);
    if (nextId == std::numeric_limits<jlong>::max()) throw std::logic_error("Handle space exhausted");
    const auto id = nextId++; registry.emplace(id,std::move(value)); return id;
  });
}
extern "C" JNIEXPORT void JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeStage(
    JNIEnv* env, jobject, jlong id, jlong ticket, jlong generation, jlong revision,
    jobject a, jint ao, jint an, jlong af, jlong ac,
    jobject b, jint bo, jint bn, jlong bf, jlong bc) {
  guarded<int>(env,0,[&] {
    if (ticket <= 0) throw std::invalid_argument("Invalid ticket");
    const auto* ap = buffer(env,a,ao,an); const auto* bp = buffer(env,b,bo,bn);
    auto e = entry(id); std::lock_guard<std::mutex> lock(e->operation);
    e->ingress.stage(static_cast<std::uint64_t>(ticket), generation, revision,
                     {ap,static_cast<std::size_t>(an),af,ac},{bp,static_cast<std::size_t>(bn),bf,bc});
    return 1;
  });
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeCommit(JNIEnv* env,jobject,jlong id,jlong ticket) {
  return guarded<jboolean>(env,JNI_FALSE,[&] {
    auto e=entry(id);std::lock_guard<std::mutex> lock(e->operation);
    return jboolean(ticket>0 && e->ingress.commit(static_cast<std::uint64_t>(ticket)));
  });
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeAbort(JNIEnv* env,jobject,jlong id,jlong ticket) {
  return guarded<jboolean>(env,JNI_FALSE,[&] {
    auto e=entry(id);std::lock_guard<std::mutex> lock(e->operation);
    return jboolean(ticket>0 && e->ingress.abort(static_cast<std::uint64_t>(ticket)));
  });
}
extern "C" JNIEXPORT jint JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativePush(
    JNIEnv* env,jobject,jlong id,jint side,jobject pcm,jint offset,jint bytes,jlong first) {
  return guarded<jint>(env,0,[&] {
    const auto* p=buffer(env,pcm,offset,bytes);auto e=entry(id);std::lock_guard<std::mutex> lock(e->operation);
    return jint(e->ingress.push(static_cast<unsigned>(side),p,static_cast<std::size_t>(bytes),first));
  });
}
extern "C" JNIEXPORT jint JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeRead(
    JNIEnv* env,jobject,jlong id,jint side,jobject dst,jint offset,jint frames,jlongArray metadata) {
  return guarded<jint>(env,0,[&] {
    auto e=entry(id);std::lock_guard<std::mutex> lock(e->operation);
    if (frames < 0 || frames > jint(e->ingress.capacity()) || !metadata || env->GetArrayLength(metadata)!=3)
      throw std::invalid_argument("Invalid read size/metadata");
    const auto bytes=frames*static_cast<jint>(e->ingress.channels())*4;
    auto* p=buffer(env,dst,offset,bytes);
    if (reinterpret_cast<std::uintptr_t>(p)%alignof(float))throw std::invalid_argument("Unaligned read");
    // Kotlin checks read-only state; JNI additionally enforces it for direct callers.
    jclass type=env->GetObjectClass(dst);if(!type)throw std::logic_error("Class lookup");
    const auto method=env->GetMethodID(type,"isReadOnly","()Z");
    if(!method){env->DeleteLocalRef(type);throw std::logic_error("Method lookup");}
    const auto readOnly=env->CallBooleanMethod(dst,method);env->DeleteLocalRef(type);
    if(env->ExceptionCheck())throw std::logic_error("Read-only query");
    if(readOnly)throw std::invalid_argument("Read-only output");
    const auto r=e->ingress.read(static_cast<unsigned>(side),reinterpret_cast<float*>(p),frames);
    const jlong info[]{r.firstFrame,static_cast<jlong>(r.frames),r.prefix?1:0};
    env->SetLongArrayRegion(metadata,0,3,info);
    return jint(r.frames);
  });
}
extern "C" JNIEXPORT void JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeStats(
    JNIEnv* env,jobject,jlong id,jint side,jlongArray metadata) {
  guarded<int>(env,0,[&] {
    if(!metadata||env->GetArrayLength(metadata)!=8)throw std::invalid_argument("Invalid stats size");
    auto e=entry(id);std::lock_guard<std::mutex> lock(e->operation);
    const auto s=e->ingress.stats(static_cast<unsigned>(side));
    const jlong info[]{static_cast<jlong>(e->ingress.phase()),s.nextReadFrame,s.nextWriteFrame,s.cueFrame,
      static_cast<jlong>(s.accepted),static_cast<jlong>(s.read),static_cast<jlong>(s.discarded),s.queued};
    env->SetLongArrayRegion(metadata,0,8,info);return 1;
  });
}
extern "C" JNIEXPORT void JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativePcmOwnerIngress_nativeDestroy(JNIEnv* env,jobject,jlong id) {
  guarded<int>(env,0,[&] {
    std::shared_ptr<Entry> removed;
    {std::lock_guard<std::mutex> lock(registryMutex);
     const auto it=registry.find(id);if(it==registry.end())throw std::logic_error("Closed owner");
     removed=std::move(it->second);registry.erase(it);}
    return 1;
  });
}

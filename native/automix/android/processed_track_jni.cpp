#include "lmg/automix/processed_track_stream.h"
#include "lmg/automix/time_pitch_time_map.h"
#include <jni.h>
#include <cmath>
#include <cstdint>
#include <memory>
#include <limits>
#include <new>
#include <stdexcept>
#include <vector>
namespace {
using namespace lmg::automix;
struct Context {
  unsigned channels, maxFrames;
  std::unique_ptr<TimePitchTimeMap> map;
  ProcessedTrackStream stream;
  Context(double fs, unsigned channels, unsigned capacity, Frame start, double output,
      EffectSettings initial, std::vector<GraphEvent> events, bool scheduled,
      TimePitchControls controls, std::unique_ptr<TimePitchTimeMap> mapping)
    : channels(channels), maxFrames(capacity), map(std::move(mapping)),
      stream(fs,channels,capacity,start,output,initial,std::move(events),scheduled,controls,
             map ? map->view() : TimePitchTimeMapView{}) {}
};
void fail(JNIEnv* env, const char* type, const char* message) {
  if (env->ExceptionCheck()) return;
  const auto klass = env->FindClass(type);
  if (klass) env->ThrowNew(klass, message);
}
Context* context(jlong handle) {
  if (!handle) throw std::logic_error("Processed track is closed");
  return reinterpret_cast<Context*>(static_cast<std::uintptr_t>(handle));
}
float* buffer(JNIEnv* env, jobject object, jint offset, std::size_t bytes) {
  if (!object || offset < 0) throw std::invalid_argument("Missing PCM buffer or negative offset");
  const auto capacity = env->GetDirectBufferCapacity(object);
  auto* address = static_cast<unsigned char*>(env->GetDirectBufferAddress(object));
  if (!address || capacity < offset || bytes > static_cast<std::uint64_t>(capacity - offset))
    throw std::invalid_argument("Direct PCM buffer is too small");
  address += offset;
  if (reinterpret_cast<std::uintptr_t>(address) % alignof(float))
    throw std::invalid_argument("PCM buffer is not float aligned");
  return reinterpret_cast<float*>(address);
}
void translate(JNIEnv* env) noexcept {
  try { throw; }
  catch (const std::bad_alloc&) { fail(env, "java/lang/OutOfMemoryError", "Native graph allocation failed"); }
  catch (const std::invalid_argument& e) { fail(env, "java/lang/IllegalArgumentException", e.what()); }
  catch (const std::exception& e) { fail(env, "java/lang/IllegalStateException", e.what()); }
  catch (...) { fail(env, "java/lang/IllegalStateException", "Unknown native graph error"); }
}
}
#define JNI_METHOD(name) Java_com_lmg_vk_engine_automix_nativecore_NativeProcessedTrack_##name
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(nativeCreate)(JNIEnv* env,jobject,
    jdouble fs,jint channels,jint capacity,jlong start,jdouble output,jboolean scheduled,
    jdouble rate,jfloat pitch,jfloat smoothness,jboolean coherence,jboolean transients,
    jintArray initialIds,jdoubleArray initialValues,jlongArray frames,jintArray ids,
    jdoubleArray values,jdoubleArray segments) {
  try {
    if(channels<1||channels>2||capacity<1||capacity>16384||!initialIds||!initialValues||!frames||!ids||!values)
      throw std::invalid_argument("Invalid processed-track configuration");
    const auto initialCount=env->GetArrayLength(initialIds),count=env->GetArrayLength(ids);
    if(initialCount>27||env->GetArrayLength(initialValues)!=initialCount||count>16384||
       env->GetArrayLength(frames)!=count||env->GetArrayLength(values)!=count)
      throw std::invalid_argument("Invalid processed-track parameter arrays");
    std::vector<jint> addresses(initialCount);std::vector<jdouble> numbers(initialCount);
    if(initialCount){
      env->GetIntArrayRegion(initialIds,0,initialCount,addresses.data());if(env->ExceptionCheck())return 0;
      env->GetDoubleArrayRegion(initialValues,0,initialCount,numbers.data());if(env->ExceptionCheck())return 0;
    }
    // Use recovered time-zero defaults, then explicit initial overrides. Geometry
    // is prepared here, never mutated inside a rendering callback.
    auto initial=compileTimedEffectEvents({}).front();
    for(jsize i=0;i<initialCount;++i) {
      if(!std::isfinite(numbers[i])||std::abs(numbers[i])>std::numeric_limits<float>::max())
        throw std::invalid_argument("Unrepresentable initial parameter");
      for(jsize j=0;j<i;++j)if(addresses[i]==addresses[j])throw std::invalid_argument("Duplicate initial parameter");
      initial.parameters[static_cast<std::uint32_t>(addresses[i])]=static_cast<float>(numbers[i]);
    }
    auto settings=initializeGraphSettings(fs,initial);
    addresses.resize(count);numbers.resize(count);std::vector<jlong> times(count);
    if(count){
      env->GetIntArrayRegion(ids,0,count,addresses.data());if(env->ExceptionCheck())return 0;
      env->GetDoubleArrayRegion(values,0,count,numbers.data());if(env->ExceptionCheck())return 0;
      env->GetLongArrayRegion(frames,0,count,times.data());if(env->ExceptionCheck())return 0;
    }
    std::vector<GraphParameterWrite> writes;writes.reserve(count);
    for(jsize i=0;i<count;++i){
      std::string name(4,'\0');const auto id=static_cast<std::uint32_t>(addresses[i]);
      for(unsigned b=0;b<4;++b)name[b]=static_cast<char>((id>>(24-b*8))&255);
      writes.push_back({times[i],std::move(name),numbers[i]});
    }
    std::unique_ptr<TimePitchTimeMap> map;
    if(segments){
      const auto n=env->GetArrayLength(segments);
      if(n%5||n>16384*5)throw std::invalid_argument("Invalid time-map array");
      std::vector<jdouble> data(n);if(n){env->GetDoubleArrayRegion(segments,0,n,data.data());if(env->ExceptionCheck())return 0;}
      std::vector<TimePitchMapSegment> prepared;prepared.reserve(n/5);
      for(jsize i=0;i<n;i+=5)prepared.push_back(timePitchMapSegment(data[i],data[i+1],data[i+2],data[i+3],data[i+4]));
      map=std::make_unique<TimePitchTimeMap>(std::move(prepared));
    }
    auto state=std::make_unique<Context>(fs,channels,capacity,start,output,settings,
        prepareGraphEvents(fs,settings,writes),scheduled,
        TimePitchControls{rate,pitch,smoothness,bool(coherence),bool(transients)},std::move(map));
    return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(state.release()));
  }catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(nativeEnqueue)(JNIEnv* env,jobject,
    jlong handle,jobject input,jint offset,jint frames,jint silence){
  try{
    auto* s=context(handle);
    if(frames<0||static_cast<unsigned>(frames)>s->maxFrames||(silence&~15))
      throw std::invalid_argument("Invalid processed-track input count or flags");
    const float* in=input?buffer(env,input,offset,static_cast<std::size_t>(frames)*s->channels*sizeof(float)):nullptr;
    return s->stream.enqueue(in,frames,{bool(silence&1),bool(silence&2),bool(silence&4),bool(silence&8)});
  }catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(nativeDequeue)(JNIEnv* env,jobject,
    jlong handle,jobject output,jint offset,jint frames,jdouble time){
  try{
    auto* s=context(handle);
    if(frames<0||static_cast<unsigned>(frames)>s->maxFrames)
      throw std::invalid_argument("Invalid processed-track output count");
    auto* out=buffer(env,output,offset,static_cast<std::size_t>(frames)*s->channels*sizeof(float));
    return s->stream.dequeue(out,frames,time);
  }catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(nativePosition)(JNIEnv* env,jobject,jlong handle){
  try{return context(handle)->stream.inputPosition();}catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(nativePending)(JNIEnv* env,jobject,jlong handle){
  try{return context(handle)->stream.pendingFrames();}catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeDestroy)(JNIEnv*,jobject,jlong handle){
  delete reinterpret_cast<Context*>(static_cast<std::uintptr_t>(handle));
}

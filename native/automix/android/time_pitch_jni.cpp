#include "lmg/automix/time_pitch_stream.h"
#include "lmg/automix/time_pitch_time_map.h"
#include <jni.h>
#include <cmath>
#include <memory>
#include <stdexcept>
namespace {
using namespace lmg::automix;
struct Context {
 unsigned channels,capacity;
 TimePitchStream stream;
 std::unique_ptr<TimePitchTimeMap> map;
 std::array<std::vector<float>,2> scratch;
 Context(double fs,unsigned ch,unsigned n,bool scheduled,TimePitchControls controls)
  :channels(ch),capacity(n),stream(fs,ch,n,scheduled,controls) {
  for(unsigned c=0;c<ch;++c)scratch[c].resize(n);
 }
};
Context& context(jlong h) {
 if(!h)throw std::logic_error("TimePitch is closed");
 return *reinterpret_cast<Context*>(static_cast<std::uintptr_t>(h));
}
void translate(JNIEnv* env) noexcept {
 if(env->ExceptionCheck())return;
 const char* type="java/lang/IllegalStateException";
 try {throw;}catch(const std::bad_alloc&){type="java/lang/OutOfMemoryError";}
 catch(const std::invalid_argument&){type="java/lang/IllegalArgumentException";}catch(...){}
 try {throw;}catch(const std::exception& e){auto c=env->FindClass(type);if(c)env->ThrowNew(c,e.what());}
 catch(...){auto c=env->FindClass(type);if(c)env->ThrowNew(c,"Native TimePitch error");}
}
float* buffer(JNIEnv* env,jobject b,jint offset,unsigned samples) {
 if(!b||offset<0)throw std::invalid_argument("missing TimePitch PCM buffer");
 const auto capacity=env->GetDirectBufferCapacity(b);
 auto* address=static_cast<unsigned char*>(env->GetDirectBufferAddress(b));
 if(!address||capacity<offset||static_cast<std::uint64_t>(samples)*sizeof(float)>static_cast<std::uint64_t>(capacity-offset))
  throw std::invalid_argument("TimePitch direct buffer too small");
 address+=offset;
 if(reinterpret_cast<std::uintptr_t>(address)%alignof(float))throw std::invalid_argument("unaligned PCM buffer");
 return reinterpret_cast<float*>(address);
}
void validateFrames(const Context& c,jint n) {
 if(n<0||static_cast<unsigned>(n)>c.capacity)throw std::invalid_argument("invalid TimePitch frame count");
}
}
#define JNI_METHOD(name) Java_com_lmg_vk_engine_automix_nativecore_NativeTimePitch_##name
extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(nativeCreate)(JNIEnv* env,jobject,jdouble fs,jint ch,jint n,jboolean scheduled,
 jdouble rate,jfloat pitch,jfloat smooth,jboolean coherence,jboolean transients) {
 try {
  if(ch<1||ch>2||n<1||n>16384)throw std::invalid_argument("invalid TimePitch geometry");
  auto p=std::make_unique<Context>(fs,ch,n,scheduled,TimePitchControls{rate,pitch,smooth,coherence!=0,transients!=0});
  return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(p.release()));
 }catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(nativeEnqueue)(JNIEnv* env,jobject,jlong h,jobject input,jint offset,jint n,jdouble time) {
 try {
  auto& c=context(h);validateFrames(c,n);
  const float* planes[]{c.scratch[0].data(),c.scratch[1].data()};
  if(n){const auto* pcm=buffer(env,input,offset,n*c.channels);
   for(int i=0;i<n;++i)for(unsigned ch=0;ch<c.channels;++ch)c.scratch[ch][i]=pcm[i*c.channels+ch];}
  return c.stream.enqueue(planes,n,time);
 }catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT jint JNICALL JNI_METHOD(nativeDequeue)(JNIEnv* env,jobject,jlong h,jobject output,jint offset,jint n,jdouble time) {
 try {
  auto& c=context(h);validateFrames(c,n);
  auto* pcm=n?buffer(env,output,offset,n*c.channels):nullptr;
  float* planes[]{c.scratch[0].data(),c.scratch[1].data()};
  const auto count=c.stream.dequeue(planes,n,time);
  for(unsigned i=0;i<count;++i)for(unsigned ch=0;ch<c.channels;++ch)pcm[i*c.channels+ch]=c.scratch[ch][i];
  return count;
 }catch(...){translate(env);return 0;}
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeConfigure)(JNIEnv* env,jobject,jlong h,jdouble rate,jfloat pitch,jfloat smooth,jboolean coherence,jboolean transients) {
 try{context(h).stream.configure({rate,pitch,smooth,coherence!=0,transients!=0});}catch(...){translate(env);}
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeSetTimeMap)(JNIEnv* env,jobject,jlong h,jdoubleArray values) {
 try{
  auto& c=context(h);
  if(!values){c.stream.clearTimeMap();c.map.reset();return;}
  const auto n=env->GetArrayLength(values);
  if(n%5||n>16384*5)throw std::invalid_argument("invalid TimePitch segment array");
  std::vector<double> raw(n);
  if(n)env->GetDoubleArrayRegion(values,0,n,raw.data());
  if(env->ExceptionCheck())return;
  std::vector<TimePitchMapSegment> segments;segments.reserve(n/5);
  for(int i=0;i<n;i+=5)segments.push_back(timePitchMapSegment(raw[i],raw[i+1],raw[i+2],raw[i+3],raw[i+4]));
  auto prepared=std::make_unique<TimePitchTimeMap>(std::move(segments));
  c.stream.setTimeMap(prepared->view());c.map=std::move(prepared);
 }catch(...){translate(env);}
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeReset)(JNIEnv* env,jobject,jlong h,jdouble input,jdouble output) {
 try{if(!std::isfinite(input)||!std::isfinite(output))throw std::invalid_argument("invalid TimePitch reset time");context(h).stream.reset(input,output);}catch(...){translate(env);}
}
extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeDestroy)(JNIEnv*,jobject,jlong h) {
 delete reinterpret_cast<Context*>(static_cast<std::uintptr_t>(h));
}

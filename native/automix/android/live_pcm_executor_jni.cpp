#include "lmg/automix/live_pcm_executor.h"
#include <jni.h>
#include <limits>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <thread>
#include <unordered_map>
#include <vector>
namespace {
using lmg::automix::LivePcmExecutor;using lmg::automix::LivePcmOptions;
struct Entry{
 std::mutex mutex;std::thread::id owner;LivePcmOptions options;LivePcmExecutor engine;std::vector<float>scratch;
 Entry(const std::vector<std::int64_t>&w,LivePcmOptions o):options(o),engine(w,o),scratch(std::size_t(o.maximumFrames)*o.channels){}
 void bind(){auto t=std::this_thread::get_id();if(owner==std::thread::id{})owner=t;else if(owner!=t)throw std::logic_error("Playback owner mismatch");}
};
std::mutex registryMutex;std::unordered_map<jlong,std::shared_ptr<Entry>>entries;jlong next=1;
void error(JNIEnv*env,const char*cls,const char*msg){if(env->ExceptionCheck())return;auto c=env->FindClass(cls);if(c){env->ThrowNew(c,msg);env->DeleteLocalRef(c);}}
template<class T,class F>T guard(JNIEnv*env,T fallback,F f)noexcept{try{return f();}catch(const std::bad_alloc&){error(env,"java/lang/OutOfMemoryError","Live PCM allocation failed");}catch(const std::invalid_argument&){error(env,"java/lang/IllegalArgumentException","Invalid live PCM input");}catch(...){error(env,"java/lang/IllegalStateException","Live PCM state failed; reset required");}return fallback;}
std::shared_ptr<Entry>get(jlong id){std::lock_guard<std::mutex>lock(registryMutex);auto it=entries.find(id);if(it==entries.end())throw std::logic_error("Closed live PCM");return it->second;}
std::uint8_t*buffer(JNIEnv*env,jobject b,jint offset,jlong size,bool writable){
 if(!b||offset<0||size<0)throw std::invalid_argument("Missing direct PCM");
 auto cap=env->GetDirectBufferCapacity(b);auto*p=static_cast<std::uint8_t*>(env->GetDirectBufferAddress(b));
 if(!p||cap<0||offset>cap||size>cap-offset)throw std::invalid_argument("PCM range");
 if(writable){auto c=env->GetObjectClass(b);if(!c)throw std::logic_error("Buffer class");auto m=env->GetMethodID(c,"isReadOnly","()Z");if(!m){env->DeleteLocalRef(c);throw std::logic_error("Buffer method");}bool ro=env->CallBooleanMethod(b,m);env->DeleteLocalRef(c);if(env->ExceptionCheck())throw std::logic_error("Buffer query");if(ro)throw std::invalid_argument("Readonly output");}
 return p+offset;
}
void frames(const Entry&e,jint n){if(n<0||n>jint(e.options.maximumFrames))throw std::invalid_argument("Block bound");}
}
extern "C" JNIEXPORT jint JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeProtocol(JNIEnv*,jobject){return 1;}
extern "C" JNIEXPORT jlong JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeCreate(JNIEnv*env,jobject,jlongArray plan,jint fs,jint channels,jint maximum,jint cadence,jlong generation,jlong revision){
 return guard<jlong>(env,0,[&]{if(!plan)throw std::invalid_argument("Missing plan");auto n=env->GetArrayLength(plan);if(n<40||n>1898)throw std::invalid_argument("Plan bound");std::vector<jlong>raw(n);env->GetLongArrayRegion(plan,0,n,raw.data());if(env->ExceptionCheck())throw std::logic_error("Plan copy");std::vector<std::int64_t>w(raw.begin(),raw.end());
  LivePcmOptions o{static_cast<unsigned>(fs),static_cast<unsigned>(channels),static_cast<unsigned>(maximum),static_cast<unsigned>(cadence),generation,revision};auto e=std::make_shared<Entry>(w,o);std::lock_guard<std::mutex>lock(registryMutex);if(next==std::numeric_limits<jlong>::max())throw std::logic_error("Registry exhausted");auto id=next++;entries.emplace(id,std::move(e));return id;});
}
extern "C" JNIEXPORT jint JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativePush(JNIEnv*env,jobject,jlong id,jint side,jobject b,jint offset,jint n,jlong first){
 return guard<jint>(env,0,[&]{auto e=get(id);std::lock_guard<std::mutex>lock(e->mutex);e->bind();frames(*e,n);auto*p=buffer(env,b,offset,jlong(n)*e->options.channels*4,false);if(reinterpret_cast<std::uintptr_t>(p)%alignof(float))throw std::invalid_argument("Unaligned float");return jint(e->engine.push(side,reinterpret_cast<const float*>(p),n,first));});
}
extern "C" JNIEXPORT void JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeEof(JNIEnv*env,jobject,jlong id,jint side,jlong end){guard<int>(env,0,[&]{auto e=get(id);std::lock_guard<std::mutex>lock(e->mutex);e->bind();e->engine.endInput(side,end);return 1;});}
extern "C" JNIEXPORT jint JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeRender(JNIEnv*env,jobject,jlong id,jobject out,jint offset,jint n,jint width){
 return guard<jint>(env,0,[&]{auto e=get(id);std::lock_guard<std::mutex>lock(e->mutex);e->bind();frames(*e,n);if(width!=2&&width!=4)throw std::invalid_argument("Encoding");auto*p=buffer(env,out,offset,jlong(n)*e->options.channels*width,true);const auto got=e->engine.pull(e->scratch.data(),n);e->engine.encode(e->scratch.data(),got,width,p,std::size_t(n)*e->options.channels*width);return jint(got);});
}
extern "C" JNIEXPORT void JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeEncodePrefix(JNIEnv*env,jobject,jlong id,jobject in,jint offset,jobject out,jint oo,jint n,jint width){
 guard<int>(env,0,[&]{auto e=get(id);std::lock_guard<std::mutex>lock(e->mutex);e->bind();frames(*e,n);if(width!=2&&width!=4)throw std::invalid_argument("Encoding");auto*a=buffer(env,in,offset,jlong(n)*e->options.channels*4,false);auto*b=buffer(env,out,oo,jlong(n)*e->options.channels*width,true);if(reinterpret_cast<std::uintptr_t>(a)%alignof(float))throw std::invalid_argument("Float alignment");const auto size=std::size_t(n)*e->options.channels*width;auto ai=reinterpret_cast<std::uintptr_t>(a),bi=reinterpret_cast<std::uintptr_t>(b);if(n&&ai<bi+size&&bi<ai+std::size_t(n)*e->options.channels*4)throw std::invalid_argument("Aliasing PCM");e->engine.encode(reinterpret_cast<const float*>(a),n,width,b,size);return 1;});
}
extern "C" JNIEXPORT jdouble JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeSourceTime(JNIEnv*env,jobject,jlong id,jint side,jdouble frame){return guard<jdouble>(env,0,[&]{auto e=get(id);std::lock_guard<std::mutex>lock(e->mutex);e->bind();return e->engine.sourceSecondsForOutput(side,frame);});}
extern "C" JNIEXPORT void JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeStats(JNIEnv*env,jobject,jlong id,jlongArray out){guard<int>(env,0,[&]{if(!out||env->GetArrayLength(out)!=19)throw std::invalid_argument("Stats shape");auto e=get(id);std::lock_guard<std::mutex>lock(e->mutex);e->bind();auto s=e->engine.stats();const jlong w[]{1,e->engine.cueFrame(0),e->engine.cueFrame(1),e->engine.transitionFrames(),s.accepted[0],s.accepted[1],s.zeroPadding[0],s.zeroPadding[1],s.dequeued[0],s.dequeued[1],s.consumed[0],s.consumed[1],s.produced,s.saturatedSamples,s.underrunPolls,s.eos[0]?1:0,s.eos[1]?1:0,s.finished?1:0,s.failed?1:0};env->SetLongArrayRegion(out,0,19,w);return 1;});}
extern "C" JNIEXPORT void JNICALL Java_com_lmg_vk_engine_automix_nativecore_NativeLivePcmExecutor_nativeDestroy(JNIEnv*env,jobject,jlong id){guard<int>(env,0,[&]{auto e=get(id);{std::lock_guard<std::mutex>lock(e->mutex);e->bind();}{std::lock_guard<std::mutex>lock(registryMutex);if(entries.erase(id)!=1)throw std::logic_error("Closed handle");}return 1;});}

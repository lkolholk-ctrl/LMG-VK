#include "lmg/automix/planner_schedule_observation.h"
#include <jni.h>
#include <new>
#include <stdexcept>
#include <string>

namespace {
struct PendingException {};
void raise(JNIEnv* e,const char* kind,const char* message) noexcept {
  if(e->ExceptionCheck()) return;
  jclass type=e->FindClass(kind);
  if(type){e->ThrowNew(type,message);e->DeleteLocalRef(type);}
}
std::string bytes(JNIEnv* e,jbyteArray a,jsize limit) {
  if(!a) throw std::invalid_argument("Missing bytes");
  const auto n=e->GetArrayLength(a);
  if(n>limit) throw std::invalid_argument("Input limit");
  std::string s(static_cast<std::size_t>(n),'\0');
  if(n) e->GetByteArrayRegion(a,0,n,reinterpret_cast<jbyte*>(s.data()));
  if(e->ExceptionCheck()) throw PendingException{};
  return s;
}
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_compileMusicKitScheduleV1(
    JNIEnv* e,jobject,jbyteArray a,jbyteArray aId,jbyteArray b,jbyteArray bId,
    jbyteArray catalog,jlongArray input) {
  try {
    if(!input || e->GetArrayLength(input)!=24) throw std::invalid_argument("Invalid contract length");
    jlong temporary[24]{};
    e->GetLongArrayRegion(input,0,24,temporary);
    if(e->ExceptionCheck()) throw PendingException{};
    std::vector<std::int64_t> request;request.reserve(24);
    for(auto value:temporary)request.push_back(static_cast<std::int64_t>(value));
    const auto q=lmg::automix::decodePlannerSourceContext(request);
    std::vector<std::int64_t> result;
    if(lmg::automix::plannerSourceContextPreflight(q)!=lmg::automix::PlannerSourceContextStatus::resolved) {
      // A denied/unknown context neither copies nor parses private song payloads.
      result=lmg::automix::observePlannerScheduledSource(request,{},{},{});
    } else {
      const auto aa=bytes(e,a,4*1024*1024), ai=bytes(e,aId,4096);
      const auto bb=bytes(e,b,4*1024*1024), bi=bytes(e,bId,4096);
      const auto cc=bytes(e,catalog,1024*1024);
      result=lmg::automix::observePlannerScheduledSourceJson(request,aa,ai,bb,bi,cc);
    }
    if(result.size()<22 || result.size()>lmg::automix::kPlannerScheduleMaxWords) throw std::logic_error("Invalid source result length");
    jlong buffer[lmg::automix::kPlannerScheduleMaxWords]{};
    for(std::size_t i=0;i<result.size();++i)buffer[i]=static_cast<jlong>(result[i]);
    const auto n=static_cast<jsize>(result.size());
    auto out=e->NewLongArray(n);
    if(!out)throw PendingException{};
    e->SetLongArrayRegion(out,0,n,buffer);
    if(e->ExceptionCheck()){e->DeleteLocalRef(out);throw PendingException{};}
    return out;
  }catch(const PendingException&){}
   catch(const std::bad_alloc&){raise(e,"java/lang/OutOfMemoryError","Schedule observation allocation failed");}
   catch(const std::invalid_argument&){raise(e,"java/lang/IllegalArgumentException","Invalid schedule observation input");}
   catch(...){raise(e,"java/lang/IllegalStateException","Schedule observation failed");}
  return nullptr;
}

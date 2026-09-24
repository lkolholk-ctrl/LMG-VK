#include "lmg/automix/planner_selection_binding.h"
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
Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_selectResolvedPairV2(
    JNIEnv* e,jobject,jbyteArray a,jbyteArray aId,jbyteArray b,jbyteArray bId,jlongArray input) {
  try {
    if(!input) throw std::invalid_argument("Missing contract");
    const auto n=e->GetArrayLength(input);
    if(n<24 || n>80) throw std::invalid_argument("Invalid contract length");
    jlong temporary[80]{};
    e->GetLongArrayRegion(input,0,n,temporary);
    if(e->ExceptionCheck()) throw PendingException{};
    std::vector<std::int64_t> request;request.reserve(static_cast<std::size_t>(n));
    for(jsize i=0;i<n;++i) request.push_back(static_cast<std::int64_t>(temporary[i]));
    // Decode before copying payloads; no critical arrays or Java references are retained.
    const auto q=lmg::automix::decodePlannerSelectionBinding(request);
    std::vector<std::int64_t> r;
    if(!q.explicitResolvedScope) {
      r=lmg::automix::observePlannerSelectionBinding(request,{},{});
    } else {
      const auto aa=bytes(e,a,4*1024*1024), ai=bytes(e,aId,4096);
      const auto bb=bytes(e,b,4*1024*1024), bi=bytes(e,bId,4096);
      r=lmg::automix::observePlannerSelectionBindingJson(request,aa,ai,bb,bi);
    }
    if(r.size()!=48) throw std::logic_error("Invalid result");
    jlong result[48];for(std::size_t i=0;i<48;++i) result[i]=static_cast<jlong>(r[i]);
    auto out=e->NewLongArray(48);
    if(!out) throw PendingException{};
    e->SetLongArrayRegion(out,0,48,result);
    if(e->ExceptionCheck()){e->DeleteLocalRef(out);throw PendingException{};}
    return out;
  } catch(const PendingException&) { }
    catch(const std::bad_alloc&) { raise(e,"java/lang/OutOfMemoryError","Observation allocation failed"); }
    catch(const std::invalid_argument&) { raise(e,"java/lang/IllegalArgumentException","Invalid observation selection input"); }
    catch(...) { raise(e,"java/lang/IllegalStateException","Observation selection failed"); }
  return nullptr;
}

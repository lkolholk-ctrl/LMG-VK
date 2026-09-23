#include "lmg/automix/observation.h"
#include <jni.h>
#include <limits>
#include <new>
#include <stdexcept>
#include <string>

namespace {
struct PendingJavaException {};
void throwJava(JNIEnv* env, const char* type, const char* message) noexcept {
  if (env->ExceptionCheck()) return;
  jclass cls = env->FindClass(type);
  if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
std::string bytes(JNIEnv* env, jbyteArray input, jsize maximum) {
  if (!input) throw std::invalid_argument("Missing bytes");
  const auto size = env->GetArrayLength(input);
  if (size > maximum) throw std::invalid_argument("Input exceeds limit");
  std::string out(static_cast<std::size_t>(size), '\0');
  if (size) env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(out.data()));
  if (env->ExceptionCheck()) throw PendingJavaException{};
  return out;
}
jbyteArray result(JNIEnv* env, const std::string& value) {
  if (value.size() > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) throw std::bad_alloc{};
  auto out = env->NewByteArray(static_cast<jsize>(value.size()));
  if (!out) throw PendingJavaException{};
  if (!value.empty()) env->SetByteArrayRegion(out, 0, static_cast<jsize>(value.size()),
                                             reinterpret_cast<const jbyte*>(value.data()));
  if (env->ExceptionCheck()) { env->DeleteLocalRef(out); throw PendingJavaException{}; }
  return out;
}
template<class Run> jbyteArray guarded(JNIEnv* env, Run run) noexcept {
  try { return result(env, run()); }
  catch (const PendingJavaException&) { /* Preserve JVM allocation/copy failure. */ }
  catch (const std::bad_alloc&) { throwJava(env, "java/lang/OutOfMemoryError", "AutoMix observation allocation failed"); }
  catch (const std::invalid_argument&) {
    // Do not put server payload fragments, resource IDs, or URLs into exceptions/logs.
    throwJava(env, "java/lang/IllegalArgumentException", "Invalid AutoMix observation input");
  }
  catch (...) { throwJava(env, "java/lang/IllegalStateException", "AutoMix observation bridge failed"); }
  return nullptr;
}
} // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_describeSong(
    JNIEnv* env, jobject, jbyteArray input, jbyteArray songId) {
  return guarded(env, [&] {
    const auto source = bytes(env, input, 4 * 1024 * 1024);
    const auto id = bytes(env, songId, 4096);
    return lmg::automix::describeSongAnalysis(source, id);
  });
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_describeStyles(
    JNIEnv* env, jobject, jbyteArray input) {
  return guarded(env, [&] {
    return lmg::automix::describeTransitionStyles(bytes(env, input, 1024 * 1024));
  });
}

#include "lmg/automix/planner_observation.h"
#include <jni.h>
#include <array>
#include <new>
#include <stdexcept>
#include <string>

namespace {
struct PendingJavaException {};
void error(JNIEnv* env, const char* name, const char* text) noexcept {
  if (env->ExceptionCheck()) return;
  jclass cls = env->FindClass(name);
  if (cls) { env->ThrowNew(cls, text); env->DeleteLocalRef(cls); }
}
std::string bytes(JNIEnv* env, jbyteArray input, jsize maximum) {
  if (!input) throw std::invalid_argument("Null planner bytes");
  const auto size = env->GetArrayLength(input);
  if (size <= 0 || size > maximum) throw std::invalid_argument("Planner bytes size");
  std::string out(static_cast<std::size_t>(size), '\0');
  env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte*>(out.data()));
  if (env->ExceptionCheck()) throw PendingJavaException{};
  return out;
}
}
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_preparePair(
    JNIEnv* env, jobject, jbyteArray outgoing, jbyteArray outgoingId,
    jbyteArray incoming, jbyteArray incomingId, jlongArray durations, jint presence) {
  try {
    if (!durations || env->GetArrayLength(durations) != 2 || presence < 0 || presence > 3)
      throw std::invalid_argument("Planner duration ABI");
    std::array<jlong, 2> ms{};
    env->GetLongArrayRegion(durations, 0, 2, ms.data());
    if (env->ExceptionCheck()) throw PendingJavaException{};
    std::array<std::optional<std::int64_t>, 2> values;
    for (std::size_t i = 0; i < 2; ++i) {
      if (presence & (1 << i)) values[i] = static_cast<std::int64_t>(ms[i]);
      else if (ms[i] != 0) throw std::invalid_argument("Noncanonical absent duration");
    }
    // Standard UTF-8, not Modified UTF-8. Never include payload/ID in errors.
    const auto out = bytes(env, outgoing, 4 * 1024 * 1024);
    const auto outId = bytes(env, outgoingId, 4096);
    const auto in = bytes(env, incoming, 4 * 1024 * 1024);
    const auto inId = bytes(env, incomingId, 4096);
    const auto report = lmg::automix::preparePlannerPairObservationJson(
        out, outId, in, inId, values[0], values[1]);
    if (report.size() > lmg::automix::kPlannerPreparationMaxWords)
      throw std::logic_error("Planner snapshot overflow");
    // jlong and int64_t need not be the same C++ type on every NDK ABI.
    std::array<jlong, lmg::automix::kPlannerPreparationMaxWords> wire{};
    for (std::size_t i = 0; i < report.size(); ++i) wire[i] = static_cast<jlong>(report[i]);
    auto result = env->NewLongArray(static_cast<jsize>(report.size()));
    if (!result) throw PendingJavaException{};
    env->SetLongArrayRegion(result, 0, static_cast<jsize>(report.size()), wire.data());
    if (env->ExceptionCheck()) { env->DeleteLocalRef(result); throw PendingJavaException{}; }
    return result;
  } catch (const PendingJavaException&) {
    // Preserve an existing VM allocation/copy exception.
  } catch (const std::bad_alloc&) {
    error(env, "java/lang/OutOfMemoryError", "AutoMix preparation allocation failed");
  } catch (const std::invalid_argument&) {
    error(env, "java/lang/IllegalArgumentException", "Invalid AutoMix preparation input");
  } catch (...) {
    error(env, "java/lang/IllegalStateException", "AutoMix preparation failed");
  }
  return nullptr;
}

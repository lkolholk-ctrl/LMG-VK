#include "lmg/automix/metadata_probe.h"
#include <jni.h>
#include <array>
#include <new>
#include <stdexcept>

namespace {
void fail(JNIEnv* env, const char* type, const char* message) noexcept {
  if (env->ExceptionCheck()) return;
  const auto cls = env->FindClass(type);
  if (cls) { env->ThrowNew(cls, message); env->DeleteLocalRef(cls); }
}
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_lmg_vk_engine_automix_nativecore_NativeObservationBridge_probePair(
    JNIEnv* env, jobject, jdoubleArray values, jint presentMask, jint traits) noexcept {
  try {
    using namespace lmg::automix;
    if (!values || env->GetArrayLength(values) != static_cast<jsize>(kMetadataProbeInputSize))
      throw std::invalid_argument("Invalid metadata probe length");
    MetadataProbeInput input{};
    env->GetDoubleArrayRegion(values, 0, static_cast<jsize>(input.size()), input.data());
    if (env->ExceptionCheck()) return nullptr;
    const auto result = probeMetadataPair(input, static_cast<std::uint32_t>(presentMask),
                                          static_cast<std::uint32_t>(traits));
    // int64_t and jlong need not be the same C++ type on every supported ABI.
    std::array<jlong, kMetadataProbeOutputSize> transport{};
    for (std::size_t i = 0; i < result.size(); ++i) transport[i] = static_cast<jlong>(result[i]);
    auto output = env->NewLongArray(static_cast<jsize>(transport.size()));
    if (!output) return nullptr;  // Preserve the pending JVM allocation failure.
    env->SetLongArrayRegion(output, 0, static_cast<jsize>(transport.size()), transport.data());
    if (env->ExceptionCheck()) { env->DeleteLocalRef(output); return nullptr; }
    return output;
  } catch (const std::bad_alloc&) {
    fail(env, "java/lang/OutOfMemoryError", "AutoMix metadata probe allocation failed");
  } catch (const std::invalid_argument&) {
    fail(env, "java/lang/IllegalArgumentException", "Invalid AutoMix metadata probe input");
  } catch (...) {
    fail(env, "java/lang/IllegalStateException", "AutoMix metadata probe failed");
  }
  return nullptr;
}

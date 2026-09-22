#include "lmg/automix/scheduled_graph.h"
#include <jni.h>
#include <cmath>
#include <cstdint>
#include <memory>
#include <new>
#include <stdexcept>
#include <vector>

namespace {
using namespace lmg::automix;
struct Context {
  unsigned channels;
  std::size_t maxFrames;
  ScheduledTrackGraph graph;
  Context(double rate, unsigned count, std::size_t capacity, Frame start,
          const std::vector<GraphParameterWrite>& writes)
      : channels(count), maxFrames(capacity),
        graph(PreparedGraph(rate), count, capacity, start, prepareGraphEvents(rate, {}, writes)) {}
};
void fail(JNIEnv* env, const char* type, const char* message) {
  if (env->ExceptionCheck()) return;
  const auto klass = env->FindClass(type);
  if (klass) env->ThrowNew(klass, message);
}
Context* context(jlong handle) {
  if (!handle) throw std::logic_error("Track effects are closed");
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
} // namespace

#define JNI_METHOD(name) Java_com_lmg_vk_engine_automix_nativecore_NativeTrackEffects_##name

extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(nativeCreate)(JNIEnv* env, jobject,
    jdouble rate, jint channels, jint capacity, jlong start,
    jlongArray frames, jintArray ids, jdoubleArray values) {
  try {
    if (!frames || !ids || !values || channels < 1 || channels > 2 || capacity < 1 || capacity > 16384)
      throw std::invalid_argument("Invalid track graph configuration");
    const auto count = env->GetArrayLength(frames);
    if (count > 16384 || env->GetArrayLength(ids) != count || env->GetArrayLength(values) != count)
      throw std::invalid_argument("Invalid graph write arrays");
    std::vector<jlong> times(count);
    std::vector<jint> addresses(count);
    std::vector<jdouble> numbers(count);
    if (count) {
      env->GetLongArrayRegion(frames, 0, count, times.data());
      if (env->ExceptionCheck()) return 0;
      env->GetIntArrayRegion(ids, 0, count, addresses.data());
      if (env->ExceptionCheck()) return 0;
      env->GetDoubleArrayRegion(values, 0, count, numbers.data());
      if (env->ExceptionCheck()) return 0;
    }
    std::vector<GraphParameterWrite> writes;
    writes.reserve(count);
    for (jsize i = 0; i < count; ++i) {
      const auto id = static_cast<std::uint32_t>(addresses[i]);
      std::string name(4, '\0');
      for (unsigned byte = 0; byte < 4; ++byte) name[byte] = static_cast<char>((id >> (24 - byte * 8)) & 255);
      writes.push_back({times[i], std::move(name), numbers[i]});
    }
    auto result = std::make_unique<Context>(rate, channels, capacity, start, writes);
    return static_cast<jlong>(reinterpret_cast<std::uintptr_t>(result.release()));
  } catch (...) { translate(env); return 0; }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeProcess)(JNIEnv* env, jobject,
    jlong handle, jobject input, jint inputOffset, jobject output, jint outputOffset,
    jint frames, jint silence) {
  try {
    auto* state = context(handle);
    if (frames < 0 || static_cast<std::size_t>(frames) > state->maxFrames || (silence & ~15))
      throw std::invalid_argument("Invalid PCM frame count or silence flags");
    const auto samples = static_cast<std::size_t>(frames) * state->channels;
    const auto bytes = samples * sizeof(float);
    auto* out = buffer(env, output, outputOffset, bytes);
    const auto* in = input ? buffer(env, input, inputOffset, bytes) : nullptr;
    if (in && in != out && bytes) {
      const auto a = reinterpret_cast<std::uintptr_t>(in);
      const auto b = reinterpret_cast<std::uintptr_t>(out);
      if ((a < b ? b - a : a - b) < bytes)
        throw std::invalid_argument("Partially overlapping PCM buffers");
    }
    // Validate before consuming any scheduled writes or changing DSP history.
    if (in) for (std::size_t i = 0; i < samples; ++i)
      if (!std::isfinite(in[i])) throw std::invalid_argument("PCM contains nonfinite samples");
    if (!state->graph.process(in, out, frames,
          {bool(silence & 1), bool(silence & 2), bool(silence & 4), bool(silence & 8)}))
      throw std::invalid_argument("PCM timeline overflow");
  } catch (...) { translate(env); }
}

extern "C" JNIEXPORT jlong JNICALL JNI_METHOD(nativePosition)(JNIEnv* env, jobject, jlong handle) {
  try { return context(handle)->graph.position(); }
  catch (...) { translate(env); return 0; }
}

extern "C" JNIEXPORT void JNICALL JNI_METHOD(nativeDestroy)(JNIEnv*, jobject, jlong handle) {
  delete reinterpret_cast<Context*>(static_cast<std::uintptr_t>(handle));
}

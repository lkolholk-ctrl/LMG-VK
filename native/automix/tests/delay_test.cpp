#include "lmg/automix/delay.h"
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <iostream>
#include <limits>
#include <new>
#include <stdexcept>
#include <vector>
namespace { bool watching = false; std::size_t allocations = 0; }
void* operator new(std::size_t n) {
  if (watching) ++allocations;
  if (void* p = std::malloc(n ? n : 1)) return p;
  throw std::bad_alloc();
}
void* operator new[](std::size_t n) { return ::operator new(n); }
void operator delete(void* p) noexcept { std::free(p); }
void operator delete[](void* p) noexcept { std::free(p); }
void operator delete(void* p, std::size_t) noexcept { std::free(p); }
void operator delete[](void* p, std::size_t) noexcept { std::free(p); }
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
void impulse(unsigned rate) {
  DelayParameters p{100, static_cast<float>(5.9 / rate), 0, 24000};
  DelayKernel delay(rate);
  std::vector<float> x(64), y(64); x[0] = 1;
  bool silent = true;
  CHECK(delay.process(x.data(), y.data(), x.size(), p, &silent));
  CHECK(!silent);
  for (unsigned i = 0; i <= 5; ++i) CHECK(y[i] == 0);
  const double pole = std::exp(-3.14159265358979323846);
  for (unsigned i = 6; i < 20; ++i)
    CHECK(std::abs(y[i] - (1 - pole) * std::pow(pole, i - 6)) < 1e-7);
  // Truncation, not rounding or interpolation: 5.9 samples has the same output
  // as another delay strictly between 5 and 6 samples.
  DelayKernel same(rate); p.seconds = static_cast<float>(5.1 / rate);
  CHECK(same.process(x.data(), x.data(), x.size(), p)); CHECK(x == y);
}
void dryWetAndSilence() {
  DelayKernel delay(48000); DelayParameters p{50, 1, 0, 2500};
  float x = 1, y = 0; bool silent = true;
  CHECK(delay.process(&x, &y, 1, p, &silent));
  CHECK(y == std::sqrt(.5f) && !silent);
  DelayKernel tiny(48000); p.wetPercent = 0; x = 1e-8f; silent = true;
  CHECK(tiny.process(&x, &y, 1, p, &silent)); CHECK(y == x && silent);
  silent = false; x = 0;
  CHECK(tiny.process(&x, &y, 1, p, &silent)); CHECK(!silent);
  CHECK(!tiny.process(nullptr, &y, 1, p));
  CHECK(tiny.process(nullptr, nullptr, 0, p));
  p.wetPercent = 101; y = .5;
  CHECK(!tiny.process(&x, &y, 1, p)); CHECK(y == .5f);
  p.wetPercent = std::numeric_limits<float>::quiet_NaN();
  CHECK(!tiny.process(&x, &y, 1, p)); CHECK(y == .5f);
}
void feedbackAndPartition() {
  constexpr unsigned rate = 8000, count = 70000;
  DelayParameters p{75, .007f, -40, 1900};
  DelayKernel whole(rate), split(rate), other(rate);
  std::vector<float> x(count), a(count), b(count);
  x[0] = 1; x[32767] = -.25; x[32768] = .5; x[65536] = .125;
  watching = true;
  const bool ok = whole.process(x.data(), a.data(), count, p);
  watching = false;
  CHECK(ok && allocations == 0);
  for (std::size_t i = 0; i < count;) {
    const auto n = std::min<std::size_t>(1 + i % 157, count - i);
    CHECK(split.process(x.data() + i, b.data() + i, n, p)); i += n;
  }
  CHECK(a == b); // Fixed parameters: delay has no end-of-block state cleanup.
  CHECK(other.process(x.data(), x.data(), count, p)); CHECK(x == a);
  for (float v : a) CHECK(std::isfinite(v));
  // Signed feedback first appears after two integer delays plus filter latency.
  DelayKernel positive(48000), negative(48000), zero(48000);
  float impulse[40] = {1}, pos[40], neg[40], none[40];
  p = {100, 5.9f / 48000, 50, 24000}; CHECK(positive.process(impulse, pos, 40, p));
  p.feedbackPercent = -50; CHECK(negative.process(impulse, neg, 40, p));
  p.feedbackPercent = 0; CHECK(zero.process(impulse, none, 40, p));
  for (int i = 0; i < 12; ++i) CHECK(pos[i] == none[i] && neg[i] == none[i]);
  CHECK(pos[12] > none[12] && neg[12] < none[12]);
}
int main() {
  try {
    impulse(44100); impulse(48000); dryWetAndSilence(); feedbackAndPartition();
    std::cout << "Recovered delay tests passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}

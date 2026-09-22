#include "lmg/automix/lowpass.h"
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
void near(const LowpassCoefficients& actual, const LowpassCoefficients& expected) {
  for (unsigned i = 0; i < actual.size(); ++i) {
    CHECK(std::isfinite(actual[i])); CHECK(std::abs(actual[i] - expected[i]) < 2e-14);
  }
}
template<class F> void rejects(F f) {
  bool rejected = false;
  try { f(); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}
void processing() {
  LowpassSection impulse({0, 1./3, 1./3, 2./3, 1./3});
  float signal[] = {1, 0, 0, 0, 0, 0, 0, 0, 0};
  CHECK(impulse.process(signal, signal, 9));
  const double expected[] = {1./3, 2./3, 2./9, -2./9, -2./27, 2./27, 2./81, -2./81, -2./243};
  for (unsigned i = 0; i < 9; ++i) CHECK(std::abs(signal[i] - expected[i]) < 3e-8);
  LowpassSection fresh({0, 1./3, 1./3, 2./3, 1./3});
  float zeros[9] = {}; CHECK(fresh.process(zeros, zeros, 9));
  for (float x : zeros) CHECK(x == 0);

  // Binary-derived block cleanup is observable and must not be "fixed" into
  // unconditional block partition equality. Small accumulator history is erased.
  LowpassSection whole({-1, 0, 1, 0, 0}), split({-1, 0, 1, 0, 0});
  float input[] = {1e-16f, 0}, a[2], b[2];
  CHECK(whole.process(input, a, 2));
  CHECK(split.process(input, b, 1)); CHECK(split.process(input + 1, b + 1, 1));
  CHECK(a[1] == input[0] && b[1] == 0);

  // Main loop rounds b0*x before adding b1*x1; scalar tail fuses b0*x+b1*x1.
  const LowpassCoefficients c{0, 0, 1 + 0x1p-40, -1, 0};
  LowpassSection mainLoop(c), scalar(c);
  float x[] = {1 + 0x1p-23f, 1 + 0x1p-23f, 1 + 0x1p-23f, 1 + 0x1p-23f};
  float mainOut[4], tailOut[4];
  CHECK(mainLoop.process(x, mainOut, 4)); CHECK(scalar.process(x, tailOut, 2));
  CHECK(mainOut[1] == 0x1p-40f);
  CHECK(tailOut[1] == std::nextafter(0x1p-40f, 1.f));

  LowpassSection untouched({0, 0, 1, 0, 0});
  float value = .5;
  CHECK(!untouched.process(nullptr, &value, 1)); CHECK(value == .5f);
  CHECK(!untouched.process(&value, &value, std::numeric_limits<std::size_t>::max()));
  CHECK(untouched.process(nullptr, nullptr, 0));
  CHECK(untouched.process(&value, &value, 1)); CHECK(value == .5f);
}
int main() {
  try {
    processing();
    // Analytic pi/2 cases discriminate Q=10^(dB/20) from the rejected Q/sqrt(2).
    near(lowpassCoefficients(12000, 0, 48000), {0, 1./3, 1./3, 2./3, 1./3});
    near(lowpassCoefficients(12000, 20, 48000), {0, 19./21, 10./21, 20./21, 10./21});
    // Independent 70-decimal-digit evaluation of the recovered formulas.
    near(lowpassCoefficients(1000, -6, 44100), {-1.7340876359126389, .75183818399712754,
      .0044376370211221513, .0088752740422443025, .0044376370211221513});
    near(lowpassCoefficients(22000, 0, 48000), {1.7104970464692144, .77083684887137671,
      .87033347383514781, 1.7406669476702956, .87033347383514781});
    CHECK(lowpassCoefficients(-100, 0, 48000) == lowpassCoefficients(10, 0, 48000));
    CHECK(lowpassCoefficients(48000, 0, 48000) == lowpassCoefficients(23760, 0, 48000));
    CHECK(lowpassCoefficients(1000, 0, 48000.0001) == lowpassCoefficients(1000, 0, 48000));
    rejects([] { lowpassCoefficients(1000, 0, 0); });
    rejects([] { lowpassCoefficients(1000, 0, std::numeric_limits<double>::infinity()); });
    rejects([] { lowpassCoefficients(std::numeric_limits<float>::quiet_NaN(), 0, 48000); });
    rejects([] { lowpassCoefficients(1000, std::numeric_limits<float>::infinity(), 48000); });
    std::cout << "Recovered lowpass coefficient tests passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}

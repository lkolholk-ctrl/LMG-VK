#include "lmg/automix/highpass.h"
#include <cmath>
#include <iostream>
#include <stdexcept>
#include <vector>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
void near(const BiquadCoefficients& a, const BiquadCoefficients& b) {
  for (unsigned i = 0; i < a.size(); ++i) CHECK(std::abs(a[i] - b[i]) < 2e-14);
}
int main() {
  try {
    near(highpassCoefficients(12000, 0, 48000), {0, 1./3, 1./3, -2./3, 1./3});
    near(highpassCoefficients(12000, 20, 48000), {0, 19./21, 10./21, -20./21, 10./21});
    for (auto rate : {44100, 48000}) {
      const auto c = highpassCoefficients(1000, 0, rate);
      const auto lp = lowpassCoefficients(1000, 0, rate);
      CHECK(c[0] == lp[0] && c[1] == lp[1]);
      CHECK(c[3] == -2 * c[2] && c[4] == c[2]);
      CHECK(highpassCoefficients(0, 0, rate) == highpassCoefficients(10, 0, rate));
      CHECK(highpassCoefficients(rate, 0, rate) == highpassCoefficients(rate * .495f, 0, rate));
      HighpassSection dc(c), nyquist(c), untouched(c);
      std::vector<float> constant(4096, .5f), alternating(4096), silence(64);
      for (std::size_t i = 0; i < alternating.size(); ++i) alternating[i] = i % 2 ? -.5f : .5f;
      CHECK(dc.process(constant.data(), constant.data(), constant.size()));
      CHECK(nyquist.process(alternating.data(), alternating.data(), alternating.size()));
      for (std::size_t i = 2048; i < constant.size(); ++i) {
        CHECK(std::abs(constant[i]) < 1e-12);
        CHECK(std::abs(alternating[i] - (i % 2 ? -.5 : .5)) < 1e-7);
      }
      CHECK(untouched.process(silence.data(), silence.data(), silence.size()));
      for (float x : silence) CHECK(x == 0);
    }
    HighpassSection impulse({0, 1./3, 1./3, -2./3, 1./3});
    float input[] = {1, 0, 0, 0, 0, 0, 0, 0, 0};
    CHECK(impulse.process(input, input, 9));
    const double expected[] = {1./3, -2./3, 2./9, 2./9, -2./27, -2./27, 2./81, 2./81, -2./243};
    for (unsigned i = 0; i < 9; ++i) CHECK(std::abs(input[i] - expected[i]) < 3e-8);
    // Exercise every main-loop/scalar-tail split, including repeated groups of 5.
    for (std::size_t n = 1; n <= 24; ++n) {
      BiquadSection section({0, 0, 1 + 0x1p-40, -1, 0});
      std::vector<float> signal(n, 1 + 0x1p-23f);
      CHECK(section.process(signal.data(), signal.data(), n));
      const auto mainCount = n < 4 ? 0 : n - (n - 4) % 5;
      for (std::size_t i = 1; i < n; ++i)
        CHECK(signal[i] == (i < mainCount ? 0x1p-40f : std::nextafter(0x1p-40f, 1.f)));
    }
    std::cout << "Recovered highpass and shared section tests passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}

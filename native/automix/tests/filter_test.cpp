#include "lmg/automix/filter.h"
#include <cmath>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>
#include <vector>

using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while (false)

template<class T> void read(std::ifstream& file, T& value) {
  file.read(reinterpret_cast<char*>(&value), sizeof(value)); CHECK(file.good());
}

void reference(unsigned index) {
  std::ifstream file(std::string(LMG_AUTOMIX_FIXTURES) + "/filter_" + std::to_string(index) + ".bin",
                     std::ios::binary);
  CHECK(file.good());
  double rate; std::array<float, 15> raw; std::uint32_t count;
  FilterCoefficients expected;
  read(file, rate); read(file, raw); read(file, count); read(file, expected);
  FilterParameters p;
  p.highpass = raw[0] == 1; p.lowFrequencyHz = raw[1]; p.lowGainDb = raw[2];
  for (unsigned i = 0; i < 3; ++i) p.bands[i] = {raw[3 + 3*i], raw[4 + 3*i], raw[5 + 3*i]};
  p.lowpass = raw[12] == 1; p.highFrequencyHz = raw[13]; p.highGainDb = raw[14];
  const auto actual = filterCoefficients(p, rate);
  for (unsigned section = 0; section < 5; ++section) {
    for (unsigned coefficient = 0; coefficient < 5; ++coefficient) {
      const double a = actual[section][coefficient], e = expected[section][coefficient];
      if (std::memcmp(&a, &e, sizeof(double))) {
        std::cerr << "case " << index << " section " << section << " coefficient " << coefficient
                  << ": " << std::hexfloat << a << " != " << e << '\n';
        CHECK(false);
      }
    }
  }
  std::vector<float> x(count), y(count), expectedOutput(count);
  file.read(reinterpret_cast<char*>(expectedOutput.data()), count * sizeof(float));
  CHECK(file.good());
  for (unsigned i = 0; i < count; ++i) x[i] = i < 600 ? (int((i * 37) % 251) - 125) / 256.0f : 0;
  FilterKernel kernel(actual), inPlace(actual);
  CHECK(kernel.process(x.data(), y.data(), count));
  for (unsigned i = 0; i < count; ++i) {
    if (std::memcmp(&y[i], &expectedOutput[i], sizeof(float))) {
      std::cerr << "case " << index << " output " << i << ": " << std::hexfloat
                << y[i] << " != " << expectedOutput[i] << '\n';
      CHECK(false);
    }
  }
  CHECK(inPlace.process(x.data(), x.data(), count)); CHECK(x == y);
}

void validityAndHistory() {
  auto p = FilterParameters{};
  p.bands[0].gainDb = 12;
  const auto initial = filterCoefficients(p, 48000);
  FilterKernel updated(initial), unchanged(initial);
  std::vector<float> x(32), a(32), b(32); x[0] = 1;
  CHECK(updated.process(x.data(), a.data(), x.size()));
  CHECK(unchanged.process(x.data(), b.data(), x.size()));
  CHECK(a == b);
  updated.coefficients(initial);
  x.assign(x.size(), 0);
  CHECK(updated.process(x.data(), a.data(), x.size()));
  CHECK(unchanged.process(x.data(), b.data(), x.size()));
  CHECK(a == b);
  float value = 7;
  CHECK(!updated.process(nullptr, &value, 1) && value == 7);
  CHECK(!updated.process(&value, nullptr, 1));
  CHECK(updated.process(nullptr, nullptr, 0));
  p.bands[0].gainDb = std::numeric_limits<float>::quiet_NaN();
  bool rejected = false;
  try { filterCoefficients(p, 48000); } catch (const std::invalid_argument&) { rejected = true; }
  CHECK(rejected);
}

int main() {
  try {
    for (unsigned i = 0; i < 5; ++i) reference(i);
    validityAndHistory();
    std::cout << "AUFilter original ARM coefficients and five-stage outputs match\n";
  } catch (const std::exception& e) {
    std::cerr << e.what() << '\n'; return 1;
  }
}

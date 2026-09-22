#include "lmg/automix/gain.h"
#include <array>
#include <cstring>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)
template<class T> void read(std::ifstream& f, T& v) {
  f.read(reinterpret_cast<char*>(&v), sizeof(v)); CHECK(f.good());
}
int main() {
  try {
    std::ifstream file(std::string(LMG_AUTOMIX_FIXTURES) + "/gain_ramp.bin", std::ios::binary);
    std::uint32_t cases; read(file, cases);
    alignas(16) std::array<float, 1032> input, output, expected, inPlace;
    for (unsigned test = 0; test < cases; ++test) {
      std::uint32_t frames, offset; float initial, step, end;
      read(file, frames); read(file, offset); read(file, initial); read(file, step); read(file, end);
      CHECK(frames <= 1024 && offset < 4);
      file.read(reinterpret_cast<char*>(expected.data()), frames * sizeof(float)); CHECK(file.good());
      input.fill(77); output.fill(77); inPlace.fill(77);
      for (unsigned i = 0; i < frames; ++i)
        input[i + 1] = inPlace[i + offset] = (int((i * 37) % 251) - 125) / 256.0f;
      float start = initial;
      CHECK(multiplyGainRamp(input.data() + 1, output.data() + offset, frames, start, step));
      if (std::memcmp(&start, &end, sizeof(float)) ||
          std::memcmp(output.data() + offset, expected.data(), frames * sizeof(float))) {
        std::cerr << "Ramp reference case " << test << " frames " << frames << " offset " << offset << '\n';
        CHECK(false);
      }
      start = initial;
      CHECK(multiplyGainRamp(inPlace.data() + offset, inPlace.data() + offset, frames, start, step));
      CHECK(std::memcmp(inPlace.data() + offset, expected.data(), frames * sizeof(float)) == 0);
      CHECK(output[offset + frames] == 77);
      if (offset) CHECK(output[offset - 1] == 77);
    }
    std::ifstream steady(std::string(LMG_AUTOMIX_FIXTURES) + "/gain_steady.bin", std::ios::binary);
    read(steady, cases);
    alignas(16) std::array<float, 1032> addend;
    for (unsigned test = 0; test < cases; ++test) {
      std::uint32_t frames, offset; float gain;
      read(steady, frames); read(steady, offset); read(steady, gain);
      CHECK(frames <= 1024 && offset < 4);
      for (unsigned i = 0; i < frames; ++i) {
        input[i + 1] = (int((i * 37) % 251) - 125) / 256.0f;
        addend[i] = (int((i * 19) % 127) - 63) / 128.0f;
      }
      steady.read(reinterpret_cast<char*>(expected.data()), frames * sizeof(float)); CHECK(steady.good());
      CHECK(multiplyGain(input.data() + 1, output.data() + offset, frames, gain));
      CHECK(std::memcmp(output.data() + offset, expected.data(), frames * sizeof(float)) == 0);
      steady.read(reinterpret_cast<char*>(expected.data()), frames * sizeof(float)); CHECK(steady.good());
      CHECK(multiplyAddGain(input.data() + 1, addend.data(), output.data() + offset, frames, gain));
      CHECK(std::memcmp(output.data() + offset, expected.data(), frames * sizeof(float)) == 0);
      CHECK(multiplyAddGain(input.data() + 1, addend.data(), addend.data(), frames, gain));
      CHECK(std::memcmp(addend.data(), expected.data(), frames * sizeof(float)) == 0);
    }
    GainSmoother control(48000);
    control.beginBlock(1); control.setGain(0);
    const auto block = control.beginBlock(1024);
    input.fill(1);
    CHECK(applyGainBlock(input.data(), output.data(), 1024, block));
    CHECK(output[0] == 1 && output[1023] == 0 && control.currentGain() == 0);
    CHECK(applyGainBlock(input.data(), inPlace.data(), 1024, block));
    CHECK(std::memcmp(output.data(), inPlace.data(), 1024 * sizeof(float)) == 0);
    CHECK(applyGainBlock(nullptr, output.data(), 1024, {0,0,0,0,true}));
    for (unsigned i = 0; i < 1024; ++i) CHECK(output[i] == 0);
    float start = .5f, out = 77;
    CHECK(!multiplyGainRamp(nullptr, &out, 1, start, .1f));
    CHECK(start == .5f && out == 77);
    CHECK(multiplyGainRamp(nullptr, nullptr, 0, start, .1f));
    std::cout << "Original ARM ramp/steady/mix cases and gain control application passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}

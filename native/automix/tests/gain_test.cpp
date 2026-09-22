#include "lmg/automix/gain.h"
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
using namespace lmg::automix;
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while(false)

int main() {
  try {
    GainSmoother gain(48000);
    CHECK(gain.setGain(.5f)); // preparation writes don't fade from default 1
    auto b = gain.beginBlock(128);
    CHECK(b.start == .5f && b.constantGain == .5f && b.rampFrames == 0);
    CHECK(gain.setGain(0));
    b = gain.beginBlock(240);
    CHECK(b.start == .5f && b.increment == -.5f / 960 && b.rampFrames == 240);
    CHECK(gain.currentGain() == std::fma(-.5f / 960, 240.0f, .5f));
    const float current = gain.currentGain();
    CHECK(gain.setGain(1)); // queued; does not reverse an ongoing fade
    b = gain.beginBlock(32, true);
    CHECK(b.inputSilent && b.rampFrames == 0 && gain.currentGain() == current);
    b = gain.beginBlock(2048);
    CHECK(b.constantGain == 0 && b.increment < 0 && b.rampFrames <= 721);
    CHECK(gain.currentGain() == 0);
    b = gain.beginBlock(100);
    CHECK(b.start == 0 && b.constantGain == 1 && b.increment == 1.0f / 960);
    gain.reset();
    CHECK(gain.currentGain() == 1);
    gain.setMuted(true); // reset restores first-write synchronization
    CHECK(gain.currentGain() == 0);
    gain.beginBlock(1);
    gain.setMuted(false);
    CHECK(gain.setSmoothingSeconds(0));
    b = gain.beginBlock(200);
    CHECK(b.increment == 1.0f / 96 && b.rampFrames == 96 && gain.currentGain() == 1);
    CHECK(!gain.setGain(-1));
    CHECK(!gain.setGain(std::numeric_limits<float>::infinity()));
    CHECK(!gain.setSmoothingSeconds(std::numeric_limits<double>::quiet_NaN()));
    CHECK(!gain.setSmoothingSeconds(1e100));
    std::cout << "Gain smoothing, queued writes, silence and reset checks passed\n";
  } catch (const std::exception& e) { std::cerr << e.what() << '\n'; return 1; }
}

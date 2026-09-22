#include "lmg/automix/reverb.h"

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <limits>
#include <new>
#include <stdexcept>
#include <string>
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
#define CHECK(x) do { if (!(x)) throw std::runtime_error(#x); } while (false)

void binaryReference(unsigned caseId, double rate, ReverbGeometry geometry,
                     ReverbParameters parameters, unsigned layout,
                     std::array<std::uint32_t, 16> expectedDelays) {
  constexpr unsigned count = 4096;
  ReverbKernel kernel(rate, geometry), split(rate, geometry);
  CHECK(kernel.delayFrames() == expectedDelays);
  std::vector<float> left(count), right(count), a(count), b(count), c(count), d(count);
  for (unsigned i = 0; i < count; ++i) {
    left[i] = i < 600 ? (int((i * 37) % 251) - 125) / 256.0f : 0;
    right[i] = i < 900 ? (int((i * 19) % 127) - 63) / 128.0f : 0;
  }
  watching = true;
  const bool ok = kernel.process(left.data(), layout == 2 ? right.data() : nullptr,
                                 a.data(), layout == 1 ? nullptr : b.data(), count, parameters);
  watching = false;
  CHECK(ok && allocations == 0);
  std::ifstream file(std::string(LMG_AUTOMIX_FIXTURES) + "/reverb_" + std::to_string(caseId) + ".bin",
                     std::ios::binary);
  CHECK(file.good());
  std::vector<float> expected(count * (layout == 1 ? 1 : 2));
  file.read(reinterpret_cast<char*>(expected.data()), expected.size() * sizeof(float));
  CHECK(file.gcount() == std::streamsize(expected.size() * sizeof(float)));
  // Fixtures execute the actual ARM sample kernel; host pow is used for both
  // coefficient preparations. Bit comparison deliberately catches arithmetic
  // reassociation, channel routing and delay-index differences.
  for (unsigned i = 0; i < expected.size(); ++i) {
    const float actual = i < count ? a[i] : b[i - count];
    if (std::memcmp(&actual, &expected[i], sizeof(float))) {
      std::cerr << "binary reference " << caseId << " sample " << i
                << ": " << std::hexfloat << actual << " != " << expected[i] << '\n';
      CHECK(false);
    }
  }
  for (unsigned offset = 0; offset < count;) {
    const unsigned frames = std::min(count - offset, 1u + offset % 97);
    CHECK(split.process(left.data() + offset, layout == 2 ? right.data() + offset : nullptr,
                        c.data() + offset, layout == 1 ? nullptr : d.data() + offset,
                        frames, parameters));
    offset += frames;
  }
  CHECK(a == c && (layout == 1 || b == d));
  kernel.reset();
  CHECK(kernel.process(left.data(), layout == 2 ? right.data() : nullptr,
                       left.data(), layout == 1 ? nullptr : right.data(), count, parameters));
  CHECK(left == a && (layout == 1 || right == b));
}

void limitsAndImpulse() {
  ReverbKernel tank(48000);
  ReverbParameters p{100, 0, 1, .5};
  const auto delays = tank.delayFrames();
  const auto first = *std::min_element(delays.begin(), delays.end());
  std::vector<float> x(first + 20), left(x.size()), right(x.size());
  x[0] = 1;
  CHECK(tank.process(x.data(), nullptr, left.data(), right.data(), x.size(), p));
  for (unsigned i = 0; i < first; ++i) CHECK(left[i] == 0 && right[i] == 0);
  CHECK(left[first] > 0 && right[first] > 0);
  tank.reset();
  p.wetPercent = 0;
  CHECK(tank.process(x.data(), nullptr, left.data(), nullptr, x.size(), p));
  CHECK(left == x);
  float a = .5, b = 7;
  p.wetPercent = 101;
  CHECK(!tank.process(&a, nullptr, &b, nullptr, 1, p) && b == 7);
  p.wetPercent = 50; p.lowDecaySeconds = 0;
  CHECK(!tank.process(&a, nullptr, &b, nullptr, 1, p) && b == 7);
  p.lowDecaySeconds = 1;
  CHECK(!tank.process(&a, &a, &b, nullptr, 1, p));
  CHECK(!tank.process(nullptr, nullptr, &b, nullptr, 1, p));
  CHECK(tank.process(nullptr, nullptr, nullptr, nullptr, 0, p));
  for (double rate : {0.0, 192001.0, std::numeric_limits<double>::infinity()}) {
    bool rejected = false;
    try { ReverbKernel invalid(rate); } catch (const std::invalid_argument&) { rejected = true; }
    CHECK(rejected);
  }
}

void parameterChanges() {
  ReverbKernel tank(48000, {.0001f, .0001f, 1});
  ReverbParameters p{100, 0, 1, .5f};
  std::vector<float> x(32), y(32); x[0] = 1;
  CHECK(tank.process(x.data(), nullptr, y.data(), nullptr, x.size(), p));
  x.assign(x.size(), 0);
  p.wetPercent = 50; p.gainDb = 3;
  CHECK(tank.process(x.data(), nullptr, y.data(), nullptr, x.size(), p));
  CHECK(std::any_of(y.begin(), y.end(), [](float v) { return v != 0; }));
  p.lowDecaySeconds = 2;
  watching = true;
  const bool ok = tank.process(x.data(), nullptr, y.data(), nullptr, x.size(), p);
  watching = false;
  CHECK(ok && allocations == 0);
  CHECK(std::all_of(y.begin(), y.end(), [](float v) { return v == 0; }));
}

void bypassLifecycle() {
  const ReverbGeometry geometry{.0001f, .0001f, 1};
  const ReverbParameters parameters{100, 0, 1, .5f};
  for (unsigned layout : {1u, 2u, 3u}) {
    ReverbEffect effect(48000, geometry);
    ReverbKernel uninterrupted(48000, geometry);
    std::array<float, 127> left{}, right{}, outLeft{}, outRight{}, refLeft{}, refRight{};
    left[0] = 1; right[0] = -.5f;
    auto process = [&](bool reference) {
      return reference
          ? uninterrupted.process(left.data(), layout == 2 ? right.data() : nullptr,
                                  refLeft.data(), layout == 1 ? nullptr : refRight.data(),
                                  left.size(), parameters)
          : effect.process(left.data(), layout == 2 ? right.data() : nullptr,
                           outLeft.data(), layout == 1 ? nullptr : outRight.data(),
                           left.size(), parameters);
    };
    CHECK(process(false) && process(true));
    CHECK(outLeft == refLeft && outRight == refRight);
    effect.setBypassed(true);
    CHECK(effect.bypassed());
    // Neither bypassed input nor time spent bypassed may enter/advance the tank.
    left.fill(.75f); right.fill(-.25f);
    watching = true;
    const bool copied = process(false) && process(false);
    watching = false;
    CHECK(copied && allocations == 0);
    CHECK(outLeft == left);
    if (layout != 1) CHECK(outRight == (layout == 2 ? right : left));
    CHECK(effect.process(left.data(), layout == 2 ? right.data() : nullptr,
                         left.data(), layout == 1 ? nullptr : right.data(), left.size(), parameters));
    CHECK(left[0] == .75f && (layout != 3 || right[0] == .75f));
    CHECK(effect.process(nullptr, nullptr, nullptr, nullptr, 0));
    CHECK(!effect.process(nullptr, nullptr, outLeft.data(), nullptr, 1));
    CHECK(!effect.process(left.data(), right.data(), outLeft.data(), nullptr, 1));
    effect.setBypassed(false);
    left.fill(0); right.fill(0);
    CHECK(process(false) && process(true));
    CHECK(outLeft == refLeft && outRight == refRight);
    CHECK(std::any_of(outLeft.begin(), outLeft.end(), [](float x) { return x != 0; }));
    effect.setBypassed(true);
    effect.reset();
    effect.setBypassed(false);
    CHECK(process(false));
    CHECK(std::all_of(outLeft.begin(), outLeft.end(), [](float x) { return x == 0; }));
  }
}

int main() {
  try {
    binaryReference(0, 48000, {.008f, .05f, 1}, {100, 0, 1, .5f}, 2,
                    {389,449,487,571,647,739,787,907,1049,1129,1319,1471,1693,1861,2129,2411});
    binaryReference(1, 44100, {.008f, .05f, 0}, {37, 0, 1.7f, .2f}, 1,
                    {353,397,457,491,557,641,769,821,907,1087,1151,1423,1531,1747,2027,2207});
    binaryReference(2, 48000, {.0001f, .0001f, 0xffffffffu}, {65, 0, .8f, 1.2f}, 3,
                    {5,7,5,7,5,7,5,7,5,7,5,7,5,7,5,7});
    limitsAndImpulse();
    parameterChanges();
    bypassLifecycle();
    std::cout << "Reverb binary references, routing, tails, reset and realtime checks passed\n";
  } catch (const std::exception& e) {
    std::cerr << e.what() << '\n'; return 1;
  }
}

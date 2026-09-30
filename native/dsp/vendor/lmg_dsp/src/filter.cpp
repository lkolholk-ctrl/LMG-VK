#include "filter.h"
#include <cmath>

#if defined(__FAST_MATH__)
#error "LMG DSP requires finite-value checks; do not compile with fast-math"
#endif

namespace lmg { namespace dsp { namespace detail {
namespace {
constexpr double pi = 3.141592653589793238462643383279502884;
double cleanState(double x) noexcept { return std::abs(x) < 1.0e-30 ? 0.0 : x; }
}

bool Coefficients::operator==(const Coefficients& o) const noexcept {
    return b0 == o.b0 && b1 == o.b1 && b2 == o.b2 && a1 == o.a1 && a2 == o.a2;
}
bool Coefficients::identity() const noexcept { return *this == Coefficients{}; }
bool Coefficients::stable() const noexcept {
    return std::isfinite(b0) && std::isfinite(b1) && std::isfinite(b2) &&
        std::isfinite(a1) && std::isfinite(a2) && std::abs(a2) < 1.0 &&
        (1.0 + a1 + a2) > 0.0 && (1.0 - a1 + a2) > 0.0;
}

bool design(FilterType type, double rate, double frequency, double gainDb,
            double q, double slope, Coefficients& out) noexcept {
    if ((type == FilterType::Peaking || type == FilterType::LowShelf ||
         type == FilterType::HighShelf) && gainDb == 0.0) {
        out = Coefficients{}; // exact wire, not pole-zero numerical cancellation
        return true;
    }
    const double w = 2.0 * pi * frequency / rate;
    const double cs = std::cos(w), sn = std::sin(w);
    const double A = std::pow(10.0, gainDb / 40.0);
    double alpha = sn / (2.0 * q);
    if (type == FilterType::LowShelf || type == FilterType::HighShelf)
        alpha = sn * 0.5 * std::sqrt((A + 1.0/A) * (1.0/slope - 1.0) + 2.0);
    const double beta = 2.0 * std::sqrt(A) * alpha;
    double b0, b1, b2, a0, a1, a2;
    switch (type) {
    case FilterType::Peaking:
        b0 = 1.0 + alpha*A; b1 = -2.0*cs; b2 = 1.0 - alpha*A;
        a0 = 1.0 + alpha/A; a1 = -2.0*cs; a2 = 1.0 - alpha/A;
        break;
    case FilterType::LowPass:
        b0 = (1.0-cs)*0.5; b1 = 1.0-cs; b2 = b0;
        a0 = 1.0+alpha; a1 = -2.0*cs; a2 = 1.0-alpha;
        break;
    case FilterType::HighPass:
        b0 = (1.0+cs)*0.5; b1 = -(1.0+cs); b2 = b0;
        a0 = 1.0+alpha; a1 = -2.0*cs; a2 = 1.0-alpha;
        break;
    case FilterType::Notch:
        b0 = 1.0; b1 = -2.0*cs; b2 = 1.0;
        a0 = 1.0+alpha; a1 = -2.0*cs; a2 = 1.0-alpha;
        break;
    case FilterType::LowShelf:
        b0 = A*((A+1.0)-(A-1.0)*cs+beta);
        b1 = 2.0*A*((A-1.0)-(A+1.0)*cs);
        b2 = A*((A+1.0)-(A-1.0)*cs-beta);
        a0 = (A+1.0)+(A-1.0)*cs+beta;
        a1 = -2.0*((A-1.0)+(A+1.0)*cs);
        a2 = (A+1.0)+(A-1.0)*cs-beta;
        break;
    case FilterType::HighShelf:
        b0 = A*((A+1.0)+(A-1.0)*cs+beta);
        b1 = -2.0*A*((A-1.0)+(A+1.0)*cs);
        b2 = A*((A+1.0)+(A-1.0)*cs-beta);
        a0 = (A+1.0)-(A-1.0)*cs+beta;
        a1 = 2.0*((A-1.0)-(A+1.0)*cs);
        a2 = (A+1.0)-(A-1.0)*cs-beta;
        break;
    default: return false;
    }
    if (!(a0 > 0.0) || !std::isfinite(a0)) return false;
    Coefficients result{b0/a0, b1/a0, b2/a0, a1/a0, a2/a0};
    if (!result.stable()) return false;
    out = result;
    return true;
}

double Biquad::tick(double input, std::uint32_t ch,
                    std::uint32_t& numericResets) noexcept {
    if (c.identity()) return input;
    const double y = c.b0 * input + z1[ch];
    const double s1 = c.b1 * input - c.a1 * y + z2[ch];
    const double s2 = c.b2 * input - c.a2 * y;
    if (!std::isfinite(y) || !std::isfinite(s1) || !std::isfinite(s2)) {
        z1[ch] = z2[ch] = 0.0;
        ++numericResets;
        return 0.0;
    }
    z1[ch] = cleanState(s1);
    z2[ch] = cleanState(s2);
    return y;
}

void Bank::configure(const std::array<Coefficients, kGraphicBands>& c,
                     const Bank* reference) noexcept {
    bool samePrefix = reference != nullptr;
    for (std::uint32_t i = 0; i < kGraphicBands; ++i) {
        samePrefix = samePrefix && (c[i] == reference->sections[i].c);
        sections[i].c = c[i];
        if (samePrefix) {
            sections[i].z1 = reference->sections[i].z1;
            sections[i].z2 = reference->sections[i].z2;
        } else {
            sections[i].z1.fill(0.0);
            sections[i].z2.fill(0.0);
        }
    }
}
double Bank::tick(double input, std::uint32_t ch,
                  std::uint32_t& numericResets) noexcept {
    for (auto& section : sections) input = section.tick(input, ch, numericResets);
    return input;
}
}}}

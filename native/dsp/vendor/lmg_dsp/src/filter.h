#ifndef LMG_DSP_FILTER_H
#define LMG_DSP_FILTER_H
#include "lmg_dsp/dsp.h"
#include <array>

namespace lmg { namespace dsp { namespace detail {
struct LMG_DSP_API Coefficients {
    double b0 = 1.0, b1 = 0.0, b2 = 0.0, a1 = 0.0, a2 = 0.0;
    bool operator==(const Coefficients& other) const noexcept;
    bool identity() const noexcept;
    bool stable() const noexcept;
};
// Input validation is performed before this function. Still checks normalization
// and strict second-order Schur/Jury inequalities on every designed section.
LMG_DSP_API bool design(FilterType type, double rate, double frequency, double gainDb,
            double q, double slope, Coefficients& out) noexcept;
struct Biquad {
    Coefficients c{};
    std::array<double, 2> z1{}, z2{};
    double tick(double input, std::uint32_t channel,
                std::uint32_t& numericResets) noexcept;
};
struct Bank {
    std::array<Biquad, kGraphicBands> sections{};
    void configure(const std::array<Coefficients, kGraphicBands>& coefficients,
                   const Bank* reference = nullptr) noexcept;
    double tick(double input, std::uint32_t channel,
                std::uint32_t& numericResets) noexcept;
};
}}}
#endif

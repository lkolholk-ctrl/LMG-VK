#include "lmg/automix/metadata_probe.h"
#include "lmg/automix/planner_scoring.h"
#include <cmath>
#include <iostream>
#include <limits>
#include <stdexcept>
#include <string>

using namespace lmg::automix;
namespace {
void require(bool ok, const char* message) { if (!ok) throw std::runtime_error(message); }
template<class F> void invalid(F run) {
  bool rejected = false;
  try { run(); } catch (const std::invalid_argument&) { rejected = true; }
  require(rejected, "invalid ABI input was accepted");
}
MetadataProbeInput complete() {
  return {180000, 180000, 120, 120, 90, 200000, 200000, 60, 180, 60};
}
}
int main() {
  try {
    constexpr auto all = 0x3ffu;
    constexpr auto resolvedSupported = 0xaau;
    auto input = complete();
    auto out = probeMetadataPair(input, all, resolvedSupported);
    require(out[0] == 1 && out[1] == regionsRequired, "complete probe issues/version");
    require(out[2] == 2 && out[3] == 2, "duration confidence");
    require(out[4] == 2 && out[5] == 2, "main double-time tag");
    require(out[6] == 0 && out[7] == 0, "edge half-time tag");

    input[1] = 182000;
    out = probeMetadataPair(input, all, resolvedSupported);
    require(out[2] == 0 && (out[1] & outDurationMismatch), "two-second boundary is exclusive");
    input[1] = 181999.5;
    require(probeMetadataPair(input, all, resolvedSupported)[2] == 2, "fractional milliseconds preserved");

    input = complete(); input[2] = 100; input[7] = 121;
    out = probeMetadataPair(input, all, resolvedSupported);
    require(out[4] == 252 && out[5] == 129, "normal/expanded tolerance separation");
    input[7] = 150;
    out = probeMetadataPair(input, all, resolvedSupported);
    require(out[4] == 252 && out[5] == 252, "incompatible source tag has no scale payload");

    input = complete(); input[4] = 0;
    out = probeMetadataPair(input, all & ~(1u << 4), resolvedSupported);
    require(out[4] == 2 && out[6] == -1 && (out[1] & edgeTempoMissing), "no edge fallback to main");
    input = complete(); input[2] = 0;
    out = probeMetadataPair(input, all, resolvedSupported);
    require((out[1] & invalidScalar) && !(out[1] & mainTempoMissing) && out[4] == -1,
            "explicit zero is invalid, not absent");

    out = probeMetadataPair({}, 0, 0);
    require(out[2] == -1 && out[3] == -1 && out[4] == -1 && out[7] == -1, "missing stays uncomputed");
    require((out[1] & outDurationUnknown) && (out[1] & inDurationUnknown) &&
            (out[1] & outAudioAbsent) && (out[1] & inAudioAbsent) &&
            (out[1] & outSupportUnknown) && (out[1] & inSupportUnknown), "missing issue flags");
    out = probeMetadataPair(complete(), all, 0x59); // audio=linkage/resolved, support=false/false
    require((out[1] & outAudioUnresolved) && (out[1] & outSupportFalse) &&
            (out[1] & inSupportFalse) && out[4] == -1, "linkage/false preserved");

    invalid([&] { probeMetadataPair(complete(), all | (1u << 10), resolvedSupported); });
    invalid([&] { probeMetadataPair(complete(), all, 0x100); });
    for (auto shift : {0u, 2u, 4u, 6u})
      invalid([&] { probeMetadataPair(complete(), all, 3u << shift); });
    invalid([&] { probeMetadataPair(complete(), 0, resolvedSupported); });
    input = complete(); input[2] = std::numeric_limits<double>::quiet_NaN();
    invalid([&] { probeMetadataPair(input, all, resolvedSupported); });
    input[2] = std::numeric_limits<double>::infinity();
    invalid([&] { probeMetadataPair(input, all, resolvedSupported); });
    input = complete(); input[7] = std::numeric_limits<double>::max();
    out = probeMetadataPair(input, all, resolvedSupported);
    require((out[1] & invalidScalar) && out[4] == -1 && out[5] == -1, "overflow is metadata failure");

    // Differential checks against the unchanged source-of-truth matcher.
    for (double a : {45., 60., 97.25, 120., 160., 200.}) {
      for (double b : {42., 59.9, 60., 100., 121., 180., 240.}) {
        input = complete(); input[2] = a; input[7] = b;
        out = probeMetadataPair(input, all, resolvedSupported);
        require(out[4] == matchPlannerTempos({a, false}, {b, false}, kPlannerTempoTolerance).sourceTag,
                "normal probe differs from native matcher");
        require(out[5] == matchPlannerTempos({a, false}, {b, false}, kPlannerExpandedTempoTolerance).sourceTag,
                "expanded probe differs from native matcher");
        require(out[1] & regionsRequired, "numeric probes must not grant a transition plan");
      }
    }
    std::cout << "metadata_probe: boundary, presence, traits, ABI rejection and 42 differential pairs passed\n";
    return 0;
  } catch (const std::exception& error) {
    std::cerr << "metadata_probe failed: " << error.what() << '\n';
    return 1;
  }
}

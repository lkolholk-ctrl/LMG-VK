#pragma once
#include "pcm_bench.h"
#include <string>
namespace lmg::automix::offline {
// Synthetic analysis fixtures only. This is not a catalog matcher or new runtime
// scheduling policy. The result is checked against Stage4a's raw-JSON entry point.
PlannerTransitionSchedule fixtureSchedule(const std::string& canonicalCatalog, bool stretch);
std::string readCatalog(const std::string& path);
}

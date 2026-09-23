#include "lmg/automix/planner_observation.h"
#include <iostream>
#include <sstream>
#include <stdexcept>

using namespace lmg::automix;
namespace {
void check(bool v) { if (!v) throw std::runtime_error("check failed"); }
// Synthetic resource, no copyrighted recording/real user data.
std::string song(const char* id = "a", int count = 33) {
  std::ostringstream s;
  s << "{\"data\":[{\"type\":\"songs\",\"id\":\"" << id << "\",\"attributes\":{\"durationInMillis\":16000},"
       "\"relationships\":{\"flexml-analysis\":{\"data\":[{\"type\":\"flexml-analysis\",\"id\":\"f\"}]}}}],"
       "\"included\":[{\"type\":\"flexml-analysis\",\"id\":\"f\",\"attributes\":{\"videoEvents\":{\"timeInSeconds\":[";
  for (int i = 0; i < count; ++i) { if (i) s << ','; s << i * .5; }
  s << "],\"score\":[";
  for (int i = 0; i < count; ++i) { if (i) s << ','; s << (i % 16 == 0 ? 800 : i % 4 == 0 ? 400 : 200); }
  s << "]}}}]}";
  return s.str();
}
}
int main() {
  try {
    const auto a = song(), b = song("b");
    auto r = preparePlannerPairObservationJson(a, "a", b, "b", 16000, 16000);
    check(r[8] == 1 && r[8 + 10] == 33 && r[8 + 12] == 8 && r[8 + 15] == 1 && r[7] == -1);
    const auto none = preparePlannerPairObservationJson(a, "a", b, "b", std::nullopt, std::nullopt);
    check(none[8] == 0 && none[8 + 6] == 0 && none[8 + 17] == 0);
    const auto empty = preparePlannerPairObservationJson(song("a", 0), "a", b, "b", 16000, 16000);
    check(empty[8] == 0 && empty[8 + 10] == 0 && (empty[8 + 5] & 48) == 48);
    const auto large = preparePlannerPairObservationJson(song("a", 4097), "a", b, "b", 16000, 16000);
    check(large[8] == 3 && (large[8 + 5] & 254) == 0);
    for (const auto& input : {std::string("{}"), std::string("not json"),
        std::string("{\"data\":[],\"data\":[]}")}) {
      bool rejected = false;
      try { preparePlannerPairObservationJson(input, "a", b, "b", 16000, 16000); }
      catch (const std::invalid_argument&) { rejected = true; }
      check(rejected);
    }
    bool mismatch = false;
    try { preparePlannerPairObservationJson(a, "wrong-id", b, "b", 16000, 16000); }
    catch (const std::invalid_argument&) { mismatch = true; }
    check(mismatch);
    std::cout << "Production JSON preparation: 8 cases passed\n";
    return 0;
  } catch (const std::exception& e) { std::cerr << "JSON preparation failed: " << e.what() << '\n'; return 1; }
}

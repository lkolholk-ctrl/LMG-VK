#include "lmg/automix/transition.h"
#include <stdexcept>

namespace lmg::automix {
std::uint8_t styleCurveByte(std::string_view name) {
  if (name == "linear") return 0x80;
  if (name == "ease-in-0.5") return 0;
  if (name == "ease-in-2") return 1;
  if (name == "ease-in-4") return 2;
  if (name == "ease-out-0.5") return 0x40;
  if (name == "ease-out-2") return 0x41;
  if (name == "ease-out-4") return 0x42;
  if (name == "logarithmic") return 0x81;
  throw std::invalid_argument("Unknown interpolation: " + std::string(name));
}

} // namespace lmg::automix

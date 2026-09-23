#pragma once

#include <string>
#include <string_view>

namespace lmg::automix {
// Versioned, UTF-8 transport snapshots for the control thread. These functions
// use the existing native decoders; they do not infer units, plan a transition,
// instantiate a DSP graph, or touch PCM. Optional values stay absent.
std::string describeSongAnalysis(std::string_view input, std::string_view songId);
std::string describeTransitionStyles(std::string_view input);
} // namespace lmg::automix

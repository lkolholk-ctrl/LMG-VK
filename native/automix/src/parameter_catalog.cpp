// Descriptor values recovered by tools/parameter_catalog_reference.py.
#include "lmg/automix/continuous_schedule.h"
#include <array>
#include <stdexcept>
namespace lmg::automix {
namespace {
const std::array<EffectParameterDescriptor,29>& catalog() {
  static const std::array<EffectParameterDescriptor, 29> parameters{{
    {"Ga1g",0.0,1.0,1.0,"player_gain"},
    {"Fbw1",0.05,3.0,2.0,"aufilter_center_bandwidth"},
    {"Fcg1",-18.0,18.0,0.0,"aufilter_center_gain"},
    {"Fcf1",10.0,21829.5,2500.0,"aufilter_center_freq"},
    {"HP1f",10.0,22050.0,10.0,"hp_cutoff_freq"},
    {"HP1r",-20.0,40.01,0.0,"hp_reso"},
    {"LP1f",10.0,21829.5,22000.0,"lp_cutoff_freq"},
    {"LP1r",-20.0,40.01,0.0,"lp_reso"},
    {"Ga2g",0.0,1.0,1.0,"send_mixer_gain"},
    {"DLdt",0.0001,2.01,1.0,"fx_delay_delay_time"},
    {"DLlf",10.0,22050.0,2500.0,"fx_delay_lp_cutoff_frequency"},
    {"DLdw",0.0,100.0,0.0,"fx_delay_dry_wet"},
    {"DLfb",-99.9,99.9,50.0,"fx_delay_feedback"},
    {"RVga",-20.0,20.01,1.0,"fx_reverb_gain"},
    {"RVdw",0.0,100.0,0.0,"fx_reverb_dry_wet"},
    {"RVmi",0.0001,1.0,0.008,"fx_reverb_min_delay_time"},
    {"RVma",0.0001,1.0,0.05,"fx_reverb_max_delay_time"},
    {"RVlf",0.001,20.0,1.0,"fx_reverb_low_frequency_decay_time"},
    {"RVhf",0.001,20.0,0.5,"fx_reverb_high_frequency_decay_time"},
    {"RVrr",1.0,1000.0,1.0,"fx_reverb_randomize_reflections"},
    {"HP2f",10.0,22050.0,10.0,"fx_hp_cutoff_freq"},
    {"HP2r",-20.0,40.0,0.0,"fx_hp_reso"},
    {"LP2f",10.0,21829.5,22000.0,"fx_lp_cutoff_freq"},
    {"LP2r",-20.0,40.0,0.0,"fx_lp_reso"},
    {"Ga3g",0.0,1.0,1.0,"fx_mixer_dry"},
    {"Ga4g",0.0,1.0,0.0,"fx_mixer_wet"},
    {"ts_rate",0.03125,32.0,1.0,"ts_rate"},
    {"out_gain",0.0,1.0,0.0,"out_gain"},
    {"bypa",0.0,1.0,1.0,"bypa"},
  }};
  return parameters;
}
}
const std::array<EffectParameterDescriptor, 29>& allEffectParameters() { return catalog(); }
const EffectParameterDescriptor& effectParameter(std::string_view id) {
  for (const auto& parameter:catalog()) if (parameter.id==id) return parameter;
  throw std::invalid_argument("Unknown effect parameter: " + std::string(id));
}
const EffectParameterDescriptor& styleEffectParameter(std::string_view id) {
  for (const auto& parameter:catalog()) if (parameter.styleParameterId==id) return parameter;
  throw std::invalid_argument("Unknown style parameter: " + std::string(id));
}
}

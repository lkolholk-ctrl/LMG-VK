#include "lmg_dsp/c_api.h"
#include "lmg_dsp/dsp.h"
#include <new>

namespace d = lmg::dsp;
struct lmg_dsp_handle { d::Processor processor; };

namespace {
lmg_dsp_status status(d::Status value) noexcept {
    return static_cast<lmg_dsp_status>(value);
}
bool header(uint32_t size, uint32_t version, std::size_t expected) noexcept {
    return size == expected && version == LMG_DSP_ABI_VERSION;
}
bool parametersFromC(const lmg_dsp_parameters* in, d::Parameters& out) noexcept {
    if (!in || !header(in->struct_size,in->abi_version,sizeof(*in)) ||
        in->bypass > 1 || in->limiter.enabled > 1) return false;
    out.bypass = in->bypass != 0;
    out.mode = static_cast<d::EqMode>(in->mode);
    out.preampDb = in->preamp_db;
    out.headroomDb = in->headroom_db;
    out.transitionMs = in->transition_ms;
    for (uint32_t i = 0; i < LMG_DSP_GRAPHIC_BANDS; ++i)
        out.graphicGainDb[i] = in->graphic_gain_db[i];
    for (uint32_t i = 0; i < LMG_DSP_PARAMETRIC_BANDS; ++i) {
        const auto& a = in->bands[i];
        if (a.enabled > 1) return false;
        out.bands[i] = {a.enabled != 0,static_cast<d::FilterType>(a.type),
                       a.frequency_hz,a.gain_db,a.q,a.slope};
    }
    const auto& a = in->limiter;
    out.limiter = {a.enabled != 0,a.threshold_db,a.ceiling_db,a.attack_ms,a.release_ms};
    return true;
}
void parametersToC(const d::Parameters& in, lmg_dsp_parameters& out) noexcept {
    out = lmg_dsp_parameters{};
    out.struct_size = sizeof(out);
    out.abi_version = LMG_DSP_ABI_VERSION;
    out.bypass = in.bypass ? 1u : 0u;
    out.mode = static_cast<uint32_t>(in.mode);
    out.preamp_db = in.preampDb;
    out.headroom_db = in.headroomDb;
    out.transition_ms = in.transitionMs;
    for (uint32_t i = 0; i < LMG_DSP_GRAPHIC_BANDS; ++i)
        out.graphic_gain_db[i] = in.graphicGainDb[i];
    for (uint32_t i = 0; i < LMG_DSP_PARAMETRIC_BANDS; ++i) {
        const auto& b = in.bands[i];
        out.bands[i] = {b.enabled ? 1u : 0u,static_cast<uint32_t>(b.type),
                       b.frequencyHz,b.gainDb,b.q,b.slope};
    }
    const auto& l = in.limiter;
    out.limiter = {l.enabled ? 1u : 0u,l.thresholdDb,l.ceilingDb,l.attackMs,l.releaseMs};
}
void writeInfo(const d::ProcessInfo& in, lmg_dsp_process_info* out) noexcept {
    if (out) *out = {in.primingFrames,in.sanitizedInputSamples,
                    in.saturatedOutputSamples,in.numericResets};
}
}

extern "C" {
lmg_dsp_handle* lmg_dsp_create(void) { return new (std::nothrow) lmg_dsp_handle; }
void lmg_dsp_destroy(lmg_dsp_handle* handle) { delete handle; }
uint32_t lmg_dsp_abi_version(void) { return LMG_DSP_ABI_VERSION; }
lmg_dsp_status lmg_dsp_default_parameters(lmg_dsp_parameters* out) {
    if (!out) return LMG_DSP_INVALID_ARGUMENT;
    parametersToC(d::defaultParameters(),*out);
    return LMG_DSP_OK;
}
lmg_dsp_status lmg_dsp_default_prepare_spec(lmg_dsp_prepare_spec* out) {
    if (!out) return LMG_DSP_INVALID_ARGUMENT;
    d::PrepareSpec s;
    *out = {sizeof(*out),LMG_DSP_ABI_VERSION,s.sampleRate,s.channels,
            s.maxBlockFrames,s.lookaheadMs};
    return LMG_DSP_OK;
}
lmg_dsp_status lmg_dsp_make_preset(uint32_t preset, lmg_dsp_parameters* out) {
    if (!out) return LMG_DSP_INVALID_ARGUMENT;
    d::Parameters p;
    if (!d::makePreset(static_cast<d::Preset>(preset),p)) return LMG_DSP_INVALID_ARGUMENT;
    parametersToC(p,*out);
    return LMG_DSP_OK;
}
lmg_dsp_status lmg_dsp_prepare(lmg_dsp_handle* handle,
    const lmg_dsp_prepare_spec* spec, const lmg_dsp_parameters* initial) {
    if (!handle || !spec || !header(spec->struct_size,spec->abi_version,sizeof(*spec)))
        return LMG_DSP_INVALID_ARGUMENT;
    d::Parameters p;
    if (!parametersFromC(initial,p)) return LMG_DSP_INVALID_ARGUMENT;
    return status(handle->processor.prepare({spec->sample_rate,spec->channels,
        spec->max_block_frames,spec->lookahead_ms},p));
}
lmg_dsp_status lmg_dsp_submit(lmg_dsp_handle* handle, const lmg_dsp_parameters* parameters) {
    if (!handle) return LMG_DSP_INVALID_ARGUMENT;
    d::Parameters p;
    if (!parametersFromC(parameters,p)) return LMG_DSP_INVALID_ARGUMENT;
    return status(handle->processor.submit(p));
}
lmg_dsp_status lmg_dsp_process(lmg_dsp_handle* handle, float* pcm, uint32_t frames,
                               lmg_dsp_process_info* optional_info) {
    d::ProcessInfo info;
    const auto s = handle ? status(handle->processor.process(pcm,frames,&info))
                          : LMG_DSP_INVALID_ARGUMENT;
    writeInfo(info,optional_info);
    return s;
}
lmg_dsp_status lmg_dsp_reset(lmg_dsp_handle* handle) {
    return handle ? status(handle->processor.reset()) : LMG_DSP_INVALID_ARGUMENT;
}
lmg_dsp_status lmg_dsp_end_input(lmg_dsp_handle* handle, uint32_t tail, uint32_t fade) {
    return handle ? status(handle->processor.endInput({tail,fade})) : LMG_DSP_INVALID_ARGUMENT;
}
lmg_dsp_status lmg_dsp_drain(lmg_dsp_handle* handle, float* output, uint32_t capacity,
    uint32_t* written, lmg_dsp_process_info* optional_info) {
    d::ProcessInfo info;
    if (written) *written = 0;
    const auto s = handle && written
        ? status(handle->processor.drain(output,capacity,*written,&info))
        : LMG_DSP_INVALID_ARGUMENT;
    writeInfo(info,optional_info);
    return s;
}
uint32_t lmg_dsp_latency_frames(const lmg_dsp_handle* handle) {
    return handle ? handle->processor.latencyFrames() : 0;
}
uint32_t lmg_dsp_finished(const lmg_dsp_handle* handle) {
    return handle && handle->processor.finished() ? 1u : 0u;
}
const char* lmg_dsp_status_string(lmg_dsp_status value) {
    return d::statusString(static_cast<d::Status>(value));
}
}

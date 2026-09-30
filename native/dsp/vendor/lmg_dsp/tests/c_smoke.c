/* Compiled as C99, not C++. No dependency on C++ headers or JNI. */
#include "lmg_dsp/c_api.h"
#include <math.h>
#include <stdio.h>
#include <string.h>

#define CHECK(x) do { if (!(x)) { fprintf(stderr,"C API check failed, line %d\n",__LINE__); return 1; } } while (0)
int main(void) {
    lmg_dsp_prepare_spec spec;
    lmg_dsp_parameters parameters;
    lmg_dsp_handle* dsp;
    lmg_dsp_process_info info;
    float pcm[32], original[32], drain[32];
    uint32_t i, written = 99;
    CHECK(lmg_dsp_abi_version() == LMG_DSP_ABI_VERSION);
    CHECK(lmg_dsp_default_prepare_spec(&spec) == LMG_DSP_OK);
    CHECK(lmg_dsp_default_parameters(&parameters) == LMG_DSP_OK);
    spec.lookahead_ms = 0;
    spec.max_block_frames = 16;
    parameters.limiter.attack_ms = 0;
    dsp = lmg_dsp_create(); CHECK(dsp != NULL);
    CHECK(lmg_dsp_prepare(dsp,&spec,&parameters) == LMG_DSP_OK);
    CHECK(lmg_dsp_latency_frames(dsp) == 0);
    for (i = 0; i < 32; ++i) pcm[i] = original[i] = (float)i/128.0f;
    CHECK(lmg_dsp_process(dsp,pcm,16,&info) == LMG_DSP_OK);
    CHECK(memcmp(pcm,original,sizeof(pcm)) == 0 && info.priming_frames == 0);
    parameters.bypass = 2;
    CHECK(lmg_dsp_submit(dsp,&parameters) == LMG_DSP_INVALID_ARGUMENT);
    parameters.bypass = 0; parameters.abi_version = 999;
    CHECK(lmg_dsp_submit(dsp,&parameters) == LMG_DSP_INVALID_ARGUMENT);
    CHECK(lmg_dsp_make_preset(LMG_DSP_WARM,&parameters) == LMG_DSP_OK);
    parameters.limiter.attack_ms = 0;
    CHECK(lmg_dsp_submit(dsp,&parameters) == LMG_DSP_OK);
    CHECK(lmg_dsp_reset(dsp) == LMG_DSP_OK);
    CHECK(lmg_dsp_process(dsp,pcm,16,NULL) == LMG_DSP_OK);
    CHECK(lmg_dsp_end_input(dsp,10,2) == LMG_DSP_OK);
    CHECK(!lmg_dsp_finished(dsp));
    CHECK(lmg_dsp_drain(dsp,drain,16,&written,&info) == LMG_DSP_OK && written == 10);
    CHECK(lmg_dsp_finished(dsp));
    for (i = 0; i < written*2; ++i) CHECK(isfinite(drain[i]));
    CHECK(lmg_dsp_process(dsp,pcm,1,NULL) == LMG_DSP_INVALID_STATE);
    CHECK(lmg_dsp_process(NULL,pcm,1,&info) == LMG_DSP_INVALID_ARGUMENT);
    CHECK(lmg_dsp_drain(dsp,drain,16,NULL,NULL) == LMG_DSP_INVALID_ARGUMENT);
    CHECK(lmg_dsp_make_preset(999,&parameters) == LMG_DSP_INVALID_ARGUMENT);
    lmg_dsp_destroy(dsp); lmg_dsp_destroy(NULL);
    puts("PASS C99 API lifecycle, ABI validation, PCM and drain");
    return 0;
}

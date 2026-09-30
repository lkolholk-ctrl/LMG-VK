#ifndef LMG_DSP_C_API_H
#define LMG_DSP_C_API_H
#include <stdint.h>
#include "lmg_dsp/export.h"

#ifdef __cplusplus
extern "C" {
#endif

#define LMG_DSP_ABI_VERSION 1u
#define LMG_DSP_GRAPHIC_BANDS 10u
#define LMG_DSP_PARAMETRIC_BANDS 8u

typedef struct lmg_dsp_handle lmg_dsp_handle;
typedef enum lmg_dsp_status {
    LMG_DSP_OK = 0, LMG_DSP_INVALID_ARGUMENT = 1, LMG_DSP_NOT_PREPARED = 2,
    LMG_DSP_QUEUE_FULL = 3, LMG_DSP_INVALID_STATE = 4, LMG_DSP_OUT_OF_MEMORY = 5
} lmg_dsp_status;
enum lmg_dsp_eq_mode { LMG_DSP_EQ_OFF = 0, LMG_DSP_EQ_GRAPHIC = 1, LMG_DSP_EQ_PARAMETRIC = 2 };
enum lmg_dsp_filter_type {
    LMG_DSP_PEAKING = 0, LMG_DSP_LOW_SHELF = 1, LMG_DSP_HIGH_SHELF = 2,
    LMG_DSP_LOW_PASS = 3, LMG_DSP_HIGH_PASS = 4, LMG_DSP_NOTCH = 5
};
enum lmg_dsp_preset { LMG_DSP_FLAT = 0, LMG_DSP_WARM = 1, LMG_DSP_VOICE = 2, LMG_DSP_BRIGHT = 3 };

typedef struct lmg_dsp_band {
    uint32_t enabled; /* exactly 0 or 1 */
    uint32_t type;
    double frequency_hz, gain_db, q, slope;
} lmg_dsp_band;
typedef struct lmg_dsp_limiter {
    uint32_t enabled;
    double threshold_db, ceiling_db, attack_ms, release_ms;
} lmg_dsp_limiter;
typedef struct lmg_dsp_parameters {
    uint32_t struct_size, abi_version;
    uint32_t bypass, mode;
    double preamp_db, headroom_db;
    double graphic_gain_db[LMG_DSP_GRAPHIC_BANDS];
    lmg_dsp_band bands[LMG_DSP_PARAMETRIC_BANDS];
    lmg_dsp_limiter limiter;
    double transition_ms;
} lmg_dsp_parameters;
typedef struct lmg_dsp_prepare_spec {
    uint32_t struct_size, abi_version;
    double sample_rate;
    uint32_t channels, max_block_frames;
    double lookahead_ms;
} lmg_dsp_prepare_spec;
typedef struct lmg_dsp_process_info {
    uint32_t priming_frames;
    uint32_t sanitized_input_samples;
    uint32_t saturated_output_samples;
    uint32_t numeric_resets;
} lmg_dsp_process_info;

/* Lifecycle: not realtime; both producer and consumer must be stopped. */
LMG_DSP_API lmg_dsp_handle* lmg_dsp_create(void); /* NULL on allocation failure */
LMG_DSP_API void lmg_dsp_destroy(lmg_dsp_handle* handle); /* NULL allowed */
LMG_DSP_API uint32_t lmg_dsp_abi_version(void);
LMG_DSP_API lmg_dsp_status lmg_dsp_default_parameters(lmg_dsp_parameters* out);
LMG_DSP_API lmg_dsp_status lmg_dsp_default_prepare_spec(lmg_dsp_prepare_spec* out);
LMG_DSP_API lmg_dsp_status lmg_dsp_make_preset(uint32_t preset, lmg_dsp_parameters* out);
LMG_DSP_API lmg_dsp_status lmg_dsp_prepare(lmg_dsp_handle* handle,
    const lmg_dsp_prepare_spec* spec, const lmg_dsp_parameters* initial);
/* ONE producer; copies input; caller can immediately reuse its parameter struct. */
LMG_DSP_API lmg_dsp_status lmg_dsp_submit(lmg_dsp_handle* handle,
    const lmg_dsp_parameters* parameters);
/* ONE consumer; realtime, no JNI calls, no allocation/locks/I/O/logging. */
LMG_DSP_API lmg_dsp_status lmg_dsp_process(lmg_dsp_handle* handle,
    float* interleaved_pcm, uint32_t frames, lmg_dsp_process_info* optional_info);
LMG_DSP_API lmg_dsp_status lmg_dsp_reset(lmg_dsp_handle* handle);
LMG_DSP_API lmg_dsp_status lmg_dsp_end_input(lmg_dsp_handle* handle,
    uint32_t tail_frames, uint32_t tail_fade_frames);
LMG_DSP_API lmg_dsp_status lmg_dsp_drain(lmg_dsp_handle* handle,
    float* output, uint32_t capacity_frames, uint32_t* written_frames,
    lmg_dsp_process_info* optional_info);
LMG_DSP_API uint32_t lmg_dsp_latency_frames(const lmg_dsp_handle* handle);
LMG_DSP_API uint32_t lmg_dsp_finished(const lmg_dsp_handle* handle); /* consumer only */
LMG_DSP_API const char* lmg_dsp_status_string(lmg_dsp_status status);

#ifdef __cplusplus
}
#endif
#endif

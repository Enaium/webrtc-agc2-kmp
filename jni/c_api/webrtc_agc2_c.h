/*
 *  Copyright (c) 2026 The WebRTC AGC2 Kotlin project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree.
 */

#ifndef WEBRTC_AGC2_C_H_
#define WEBRTC_AGC2_C_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* =========================================================================
 * Opaque handle types - actual C++ objects are hidden behind these pointers
 * ========================================================================= */
typedef struct webrtc_agc2_config_t              webrtc_agc2_config_t;
typedef struct webrtc_agc2_input_volume_config_t webrtc_agc2_input_volume_config_t;
typedef struct webrtc_agc2_environment_t         webrtc_agc2_environment_t;
typedef struct webrtc_agc2_gain_controller_t     webrtc_agc2_gain_controller_t;
typedef struct webrtc_agc2_audio_buffer_t        webrtc_agc2_audio_buffer_t;

/* =========================================================================
 * CPU features
 * ========================================================================= */
typedef struct {
    bool sse2;
    bool avx2;
    bool neon;
} webrtc_agc2_cpu_features_t;

/* =========================================================================
 * Version / Info
 * ========================================================================= */
const char* webrtc_agc2_version(void);

/* =========================================================================
 * GainController2 configuration
 *
 * Mirrors AudioProcessing::Config::GainController2.
 * ========================================================================= */
webrtc_agc2_config_t* webrtc_agc2_config_create_default(void);
void webrtc_agc2_config_destroy(webrtc_agc2_config_t* config);

/* Whether the configuration is accepted by GainController2. Creating a gain
 * controller with a configuration that fails this check is rejected. */
bool webrtc_agc2_config_validate(const webrtc_agc2_config_t* config);

bool webrtc_agc2_config_get_enabled(const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_enabled(webrtc_agc2_config_t* config, bool value);

bool webrtc_agc2_config_get_input_volume_controller_enabled(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_input_volume_controller_enabled(
    webrtc_agc2_config_t* config, bool value);

bool webrtc_agc2_config_get_adaptive_digital_enabled(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_adaptive_digital_enabled(
    webrtc_agc2_config_t* config, bool value);

float webrtc_agc2_config_get_adaptive_digital_headroom_db(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_adaptive_digital_headroom_db(
    webrtc_agc2_config_t* config, float value);

float webrtc_agc2_config_get_adaptive_digital_max_gain_db(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_adaptive_digital_max_gain_db(
    webrtc_agc2_config_t* config, float value);

float webrtc_agc2_config_get_adaptive_digital_initial_gain_db(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_adaptive_digital_initial_gain_db(
    webrtc_agc2_config_t* config, float value);

float webrtc_agc2_config_get_adaptive_digital_max_gain_change_db_per_second(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_adaptive_digital_max_gain_change_db_per_second(
    webrtc_agc2_config_t* config, float value);

float webrtc_agc2_config_get_adaptive_digital_max_output_noise_level_dbfs(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_adaptive_digital_max_output_noise_level_dbfs(
    webrtc_agc2_config_t* config, float value);

float webrtc_agc2_config_get_fixed_digital_gain_db(
    const webrtc_agc2_config_t* config);
void webrtc_agc2_config_set_fixed_digital_gain_db(
    webrtc_agc2_config_t* config, float value);

/* =========================================================================
 * InputVolumeController configuration
 *
 * Mirrors InputVolumeController::Config.
 * ========================================================================= */
webrtc_agc2_input_volume_config_t* webrtc_agc2_input_volume_config_create_default(void);
void webrtc_agc2_input_volume_config_destroy(
    webrtc_agc2_input_volume_config_t* config);

int webrtc_agc2_input_volume_config_get_min_input_volume(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_min_input_volume(
    webrtc_agc2_input_volume_config_t* config, int value);

int webrtc_agc2_input_volume_config_get_clipped_level_min(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_clipped_level_min(
    webrtc_agc2_input_volume_config_t* config, int value);

int webrtc_agc2_input_volume_config_get_clipped_level_step(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_clipped_level_step(
    webrtc_agc2_input_volume_config_t* config, int value);

float webrtc_agc2_input_volume_config_get_clipped_ratio_threshold(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_clipped_ratio_threshold(
    webrtc_agc2_input_volume_config_t* config, float value);

int webrtc_agc2_input_volume_config_get_clipped_wait_frames(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_clipped_wait_frames(
    webrtc_agc2_input_volume_config_t* config, int value);

bool webrtc_agc2_input_volume_config_get_enable_clipping_predictor(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_enable_clipping_predictor(
    webrtc_agc2_input_volume_config_t* config, bool value);

int webrtc_agc2_input_volume_config_get_target_range_max_dbfs(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_target_range_max_dbfs(
    webrtc_agc2_input_volume_config_t* config, int value);

int webrtc_agc2_input_volume_config_get_target_range_min_dbfs(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_target_range_min_dbfs(
    webrtc_agc2_input_volume_config_t* config, int value);

int webrtc_agc2_input_volume_config_get_update_input_volume_wait_frames(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_update_input_volume_wait_frames(
    webrtc_agc2_input_volume_config_t* config, int value);

float webrtc_agc2_input_volume_config_get_speech_probability_threshold(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_speech_probability_threshold(
    webrtc_agc2_input_volume_config_t* config, float value);

float webrtc_agc2_input_volume_config_get_speech_ratio_threshold(
    const webrtc_agc2_input_volume_config_t* config);
void webrtc_agc2_input_volume_config_set_speech_ratio_threshold(
    webrtc_agc2_input_volume_config_t* config, float value);

/* =========================================================================
 * Environment
 * ========================================================================= */
webrtc_agc2_environment_t* webrtc_agc2_environment_create(void);
void webrtc_agc2_environment_destroy(webrtc_agc2_environment_t* env);

/* =========================================================================
 * AudioBuffer
 *
 * Holds one 10 ms frame of deinterleaved capture audio at the FloatS16 scale
 * ([-32768, 32768] corresponds to full scale). Write the frame through
 * webrtc_agc2_audio_buffer_get_channel_data() before processing and read it
 * back after.
 * ========================================================================= */
webrtc_agc2_audio_buffer_t* webrtc_agc2_audio_buffer_create(
    int sample_rate_hz, int num_channels);

void webrtc_agc2_audio_buffer_destroy(webrtc_agc2_audio_buffer_t* buffer);

/* Get writable pointer to channel data (for filling data before processing).
 * Returns a pointer to float[samples_per_channel]; use
 * channel_data[sample] to access individual samples. */
float* webrtc_agc2_audio_buffer_get_channel_data(
    webrtc_agc2_audio_buffer_t* buffer, int channel);

/* Get read-only channel data (after processing). */
const float* webrtc_agc2_audio_buffer_get_channel_data_const(
    const webrtc_agc2_audio_buffer_t* buffer, int channel);

int webrtc_agc2_audio_buffer_num_channels(const webrtc_agc2_audio_buffer_t* buffer);
int webrtc_agc2_audio_buffer_samples_per_channel(
    const webrtc_agc2_audio_buffer_t* buffer);

/* =========================================================================
 * GainController2
 * ========================================================================= */

/* Creates a gain controller, or NULL when `config` fails
 * webrtc_agc2_config_validate() or the stream configuration is invalid. */
webrtc_agc2_gain_controller_t* webrtc_agc2_gain_controller_create(
    const webrtc_agc2_environment_t* env,
    const webrtc_agc2_config_t* config,
    const webrtc_agc2_input_volume_config_t* input_volume_config,
    int sample_rate_hz,
    int num_channels,
    bool use_internal_vad);

void webrtc_agc2_gain_controller_destroy(webrtc_agc2_gain_controller_t* gc);

/* Sets the fixed digital gain. */
void webrtc_agc2_gain_controller_set_fixed_gain_db(
    webrtc_agc2_gain_controller_t* gc, float gain_db);

/* Updates the input volume controller about whether the capture output is used
 * or not. */
void webrtc_agc2_gain_controller_set_capture_output_used(
    webrtc_agc2_gain_controller_t* gc, bool capture_output_used);

/* Analyzes the frame before process() so that the analysis runs before digital
 * processing operations take place (e.g., echo cancellation). The applied input
 * volume is clamped to [0, 255]. */
void webrtc_agc2_gain_controller_analyze(
    webrtc_agc2_gain_controller_t* gc,
    int applied_input_volume,
    const webrtc_agc2_audio_buffer_t* audio_buffer);

/* Updates the recommended input volume, applies the adaptive digital and the
 * fixed digital gains and runs a limiter on the buffer in place. */
void webrtc_agc2_gain_controller_process(
    webrtc_agc2_gain_controller_t* gc,
    bool input_volume_changed,
    webrtc_agc2_audio_buffer_t* audio_buffer);

/* Whether a new input volume recommendation is available. Cleared by analyze()
 * and process(). */
bool webrtc_agc2_gain_controller_has_recommended_input_volume(
    const webrtc_agc2_gain_controller_t* gc);

/* The recommended input volume in [0, 255], or 0 without a recommendation. */
int webrtc_agc2_gain_controller_recommended_input_volume(
    const webrtc_agc2_gain_controller_t* gc);

webrtc_agc2_cpu_features_t webrtc_agc2_gain_controller_get_cpu_features(
    const webrtc_agc2_gain_controller_t* gc);

#ifdef __cplusplus
}
#endif

#endif /* WEBRTC_AGC2_C_H_ */

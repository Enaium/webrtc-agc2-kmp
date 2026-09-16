/*
 *  Copyright (c) 2026 The WebRTC AGC2 Kotlin project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree.
 */

#include "webrtc_agc2_c.h"

#include <algorithm>
#include <memory>
#include <new>

#include "api/audio_processing.h"
#include "api/environment.h"
#include "audio_processing/agc2/cpu_features.h"
#include "audio_processing/agc2/input_volume_controller.h"
#include "audio_processing/audio_buffer.h"
#include "audio_processing/gain_controller2.h"

// ---------------------------------------------------------------------------
// Opaque struct definitions
// ---------------------------------------------------------------------------
struct webrtc_agc2_config_t {
  webrtc::AudioProcessing::Config::GainController2 impl;
};
struct webrtc_agc2_input_volume_config_t {
  webrtc::InputVolumeController::Config impl;
};
struct webrtc_agc2_environment_t { webrtc::Environment impl; };
struct webrtc_agc2_gain_controller_t {
  std::unique_ptr<webrtc::GainController2> impl;
};
struct webrtc_agc2_audio_buffer_t { webrtc::AudioBuffer impl; };

// ---------------------------------------------------------------------------
// Version / Info
// ---------------------------------------------------------------------------
const char* webrtc_agc2_version(void) {
  return "WebRTC AGC2 1.0.0";
}

// ---------------------------------------------------------------------------
// GainController2 configuration
// ---------------------------------------------------------------------------
webrtc_agc2_config_t* webrtc_agc2_config_create_default(void) {
  return new webrtc_agc2_config_t{};
}

void webrtc_agc2_config_destroy(webrtc_agc2_config_t* config) {
  delete config;
}

bool webrtc_agc2_config_validate(const webrtc_agc2_config_t* config) {
  return webrtc::GainController2::Validate(config->impl);
}

bool webrtc_agc2_config_get_enabled(const webrtc_agc2_config_t* config) {
  return config->impl.enabled;
}

void webrtc_agc2_config_set_enabled(webrtc_agc2_config_t* config, bool value) {
  config->impl.enabled = value;
}

bool webrtc_agc2_config_get_input_volume_controller_enabled(
    const webrtc_agc2_config_t* config) {
  return config->impl.input_volume_controller.enabled;
}

void webrtc_agc2_config_set_input_volume_controller_enabled(
    webrtc_agc2_config_t* config, bool value) {
  config->impl.input_volume_controller.enabled = value;
}

bool webrtc_agc2_config_get_adaptive_digital_enabled(
    const webrtc_agc2_config_t* config) {
  return config->impl.adaptive_digital.enabled;
}

void webrtc_agc2_config_set_adaptive_digital_enabled(
    webrtc_agc2_config_t* config, bool value) {
  config->impl.adaptive_digital.enabled = value;
}

float webrtc_agc2_config_get_adaptive_digital_headroom_db(
    const webrtc_agc2_config_t* config) {
  return config->impl.adaptive_digital.headroom_db;
}

void webrtc_agc2_config_set_adaptive_digital_headroom_db(
    webrtc_agc2_config_t* config, float value) {
  config->impl.adaptive_digital.headroom_db = value;
}

float webrtc_agc2_config_get_adaptive_digital_max_gain_db(
    const webrtc_agc2_config_t* config) {
  return config->impl.adaptive_digital.max_gain_db;
}

void webrtc_agc2_config_set_adaptive_digital_max_gain_db(
    webrtc_agc2_config_t* config, float value) {
  config->impl.adaptive_digital.max_gain_db = value;
}

float webrtc_agc2_config_get_adaptive_digital_initial_gain_db(
    const webrtc_agc2_config_t* config) {
  return config->impl.adaptive_digital.initial_gain_db;
}

void webrtc_agc2_config_set_adaptive_digital_initial_gain_db(
    webrtc_agc2_config_t* config, float value) {
  config->impl.adaptive_digital.initial_gain_db = value;
}

float webrtc_agc2_config_get_adaptive_digital_max_gain_change_db_per_second(
    const webrtc_agc2_config_t* config) {
  return config->impl.adaptive_digital.max_gain_change_db_per_second;
}

void webrtc_agc2_config_set_adaptive_digital_max_gain_change_db_per_second(
    webrtc_agc2_config_t* config, float value) {
  config->impl.adaptive_digital.max_gain_change_db_per_second = value;
}

float webrtc_agc2_config_get_adaptive_digital_max_output_noise_level_dbfs(
    const webrtc_agc2_config_t* config) {
  return config->impl.adaptive_digital.max_output_noise_level_dbfs;
}

void webrtc_agc2_config_set_adaptive_digital_max_output_noise_level_dbfs(
    webrtc_agc2_config_t* config, float value) {
  config->impl.adaptive_digital.max_output_noise_level_dbfs = value;
}

float webrtc_agc2_config_get_fixed_digital_gain_db(
    const webrtc_agc2_config_t* config) {
  return config->impl.fixed_digital.gain_db;
}

void webrtc_agc2_config_set_fixed_digital_gain_db(
    webrtc_agc2_config_t* config, float value) {
  config->impl.fixed_digital.gain_db = value;
}

// ---------------------------------------------------------------------------
// InputVolumeController configuration
// ---------------------------------------------------------------------------
webrtc_agc2_input_volume_config_t*
webrtc_agc2_input_volume_config_create_default(void) {
  return new webrtc_agc2_input_volume_config_t{};
}

void webrtc_agc2_input_volume_config_destroy(
    webrtc_agc2_input_volume_config_t* config) {
  delete config;
}

int webrtc_agc2_input_volume_config_get_min_input_volume(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.min_input_volume;
}

void webrtc_agc2_input_volume_config_set_min_input_volume(
    webrtc_agc2_input_volume_config_t* config, int value) {
  config->impl.min_input_volume = value;
}

int webrtc_agc2_input_volume_config_get_clipped_level_min(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.clipped_level_min;
}

void webrtc_agc2_input_volume_config_set_clipped_level_min(
    webrtc_agc2_input_volume_config_t* config, int value) {
  config->impl.clipped_level_min = value;
}

int webrtc_agc2_input_volume_config_get_clipped_level_step(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.clipped_level_step;
}

void webrtc_agc2_input_volume_config_set_clipped_level_step(
    webrtc_agc2_input_volume_config_t* config, int value) {
  config->impl.clipped_level_step = value;
}

float webrtc_agc2_input_volume_config_get_clipped_ratio_threshold(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.clipped_ratio_threshold;
}

void webrtc_agc2_input_volume_config_set_clipped_ratio_threshold(
    webrtc_agc2_input_volume_config_t* config, float value) {
  config->impl.clipped_ratio_threshold = value;
}

int webrtc_agc2_input_volume_config_get_clipped_wait_frames(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.clipped_wait_frames;
}

void webrtc_agc2_input_volume_config_set_clipped_wait_frames(
    webrtc_agc2_input_volume_config_t* config, int value) {
  config->impl.clipped_wait_frames = value;
}

bool webrtc_agc2_input_volume_config_get_enable_clipping_predictor(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.enable_clipping_predictor;
}

void webrtc_agc2_input_volume_config_set_enable_clipping_predictor(
    webrtc_agc2_input_volume_config_t* config, bool value) {
  config->impl.enable_clipping_predictor = value;
}

int webrtc_agc2_input_volume_config_get_target_range_max_dbfs(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.target_range_max_dbfs;
}

void webrtc_agc2_input_volume_config_set_target_range_max_dbfs(
    webrtc_agc2_input_volume_config_t* config, int value) {
  config->impl.target_range_max_dbfs = value;
}

int webrtc_agc2_input_volume_config_get_target_range_min_dbfs(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.target_range_min_dbfs;
}

void webrtc_agc2_input_volume_config_set_target_range_min_dbfs(
    webrtc_agc2_input_volume_config_t* config, int value) {
  config->impl.target_range_min_dbfs = value;
}

int webrtc_agc2_input_volume_config_get_update_input_volume_wait_frames(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.update_input_volume_wait_frames;
}

void webrtc_agc2_input_volume_config_set_update_input_volume_wait_frames(
    webrtc_agc2_input_volume_config_t* config, int value) {
  config->impl.update_input_volume_wait_frames = value;
}

float webrtc_agc2_input_volume_config_get_speech_probability_threshold(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.speech_probability_threshold;
}

void webrtc_agc2_input_volume_config_set_speech_probability_threshold(
    webrtc_agc2_input_volume_config_t* config, float value) {
  config->impl.speech_probability_threshold = value;
}

float webrtc_agc2_input_volume_config_get_speech_ratio_threshold(
    const webrtc_agc2_input_volume_config_t* config) {
  return config->impl.speech_ratio_threshold;
}

void webrtc_agc2_input_volume_config_set_speech_ratio_threshold(
    webrtc_agc2_input_volume_config_t* config, float value) {
  config->impl.speech_ratio_threshold = value;
}

// ---------------------------------------------------------------------------
// Environment
// ---------------------------------------------------------------------------
webrtc_agc2_environment_t* webrtc_agc2_environment_create(void) {
  return new webrtc_agc2_environment_t{};
}

void webrtc_agc2_environment_destroy(webrtc_agc2_environment_t* env) {
  delete env;
}

// ---------------------------------------------------------------------------
// AudioBuffer
// ---------------------------------------------------------------------------
webrtc_agc2_audio_buffer_t* webrtc_agc2_audio_buffer_create(
    int sample_rate_hz, int num_channels) {
  void* mem = operator new(sizeof(webrtc_agc2_audio_buffer_t));
  auto* buffer = static_cast<webrtc_agc2_audio_buffer_t*>(mem);
  ::new (&buffer->impl)
      webrtc::AudioBuffer(sample_rate_hz, num_channels, sample_rate_hz,
                          num_channels, sample_rate_hz, num_channels);
  return buffer;
}

void webrtc_agc2_audio_buffer_destroy(webrtc_agc2_audio_buffer_t* buffer) {
  if (buffer) {
    buffer->impl.~AudioBuffer();
    operator delete(buffer);
  }
}

float* webrtc_agc2_audio_buffer_get_channel_data(
    webrtc_agc2_audio_buffer_t* buffer, int channel) {
  return buffer->impl.channels()[channel];
}

const float* webrtc_agc2_audio_buffer_get_channel_data_const(
    const webrtc_agc2_audio_buffer_t* buffer, int channel) {
  return buffer->impl.channels_const()[channel];
}

int webrtc_agc2_audio_buffer_num_channels(
    const webrtc_agc2_audio_buffer_t* buffer) {
  return static_cast<int>(buffer->impl.num_channels());
}

int webrtc_agc2_audio_buffer_samples_per_channel(
    const webrtc_agc2_audio_buffer_t* buffer) {
  return static_cast<int>(buffer->impl.num_frames());
}

// ---------------------------------------------------------------------------
// GainController2
// ---------------------------------------------------------------------------
webrtc_agc2_gain_controller_t* webrtc_agc2_gain_controller_create(
    const webrtc_agc2_environment_t* env,
    const webrtc_agc2_config_t* config,
    const webrtc_agc2_input_volume_config_t* input_volume_config,
    int sample_rate_hz,
    int num_channels,
    bool use_internal_vad) {
  if (!env || !config || !input_volume_config) {
    return nullptr;
  }
  // The controller asserts these same constraints internally; rejecting here
  // keeps an out-of-contract stream configuration from reaching the C++ code
  // as a division by zero in the frame-size and limiter tables.
  if (sample_rate_hz <= 0 || num_channels <= 0) {
    return nullptr;
  }
  if (!webrtc::GainController2::Validate(config->impl)) {
    return nullptr;
  }

  auto* gc = new webrtc_agc2_gain_controller_t;
  gc->impl = std::make_unique<webrtc::GainController2>(
      env->impl, config->impl, input_volume_config->impl, sample_rate_hz,
      num_channels, use_internal_vad);
  return gc;
}

void webrtc_agc2_gain_controller_destroy(webrtc_agc2_gain_controller_t* gc) {
  delete gc;
}

void webrtc_agc2_gain_controller_set_fixed_gain_db(
    webrtc_agc2_gain_controller_t* gc, float gain_db) {
  gc->impl->SetFixedGainDb(gain_db);
}

void webrtc_agc2_gain_controller_set_capture_output_used(
    webrtc_agc2_gain_controller_t* gc, bool capture_output_used) {
  gc->impl->SetCaptureOutputUsed(capture_output_used);
}

void webrtc_agc2_gain_controller_analyze(
    webrtc_agc2_gain_controller_t* gc,
    int applied_input_volume,
    const webrtc_agc2_audio_buffer_t* audio_buffer) {
  gc->impl->Analyze(std::clamp(applied_input_volume, 0, 255),
                    audio_buffer->impl);
}

void webrtc_agc2_gain_controller_process(
    webrtc_agc2_gain_controller_t* gc,
    bool input_volume_changed,
    webrtc_agc2_audio_buffer_t* audio_buffer) {
  gc->impl->Process(input_volume_changed, &audio_buffer->impl);
}

bool webrtc_agc2_gain_controller_has_recommended_input_volume(
    const webrtc_agc2_gain_controller_t* gc) {
  return gc->impl->recommended_input_volume().has_value();
}

int webrtc_agc2_gain_controller_recommended_input_volume(
    const webrtc_agc2_gain_controller_t* gc) {
  return gc->impl->recommended_input_volume().value_or(0);
}

webrtc_agc2_cpu_features_t webrtc_agc2_gain_controller_get_cpu_features(
    const webrtc_agc2_gain_controller_t* gc) {
  const webrtc::AvailableCpuFeatures features = gc->impl->GetCpuFeatures();
  webrtc_agc2_cpu_features_t result;
  result.sse2 = features.sse2;
  result.avx2 = features.avx2;
  result.neon = features.neon;
  return result;
}

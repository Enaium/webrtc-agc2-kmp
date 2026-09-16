/*
 *  Copyright (c) 2026 The WebRTC AGC2 Kotlin project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree.
 */

#include <jni.h>

#include <algorithm>

#include "webrtc_agc2_c.h"

// ============================================================================
// Version
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_cn_enaium_webrtc_agc2_Jni_version(JNIEnv* env, jclass clazz) {
    return env->NewStringUTF(webrtc_agc2_version());
}

// ============================================================================
// Config
// ============================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configCreateDefault(JNIEnv* env, jclass clazz) {
    auto* config = webrtc_agc2_config_create_default();
    return reinterpret_cast<jlong>(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_destroy(config);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configValidate(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_validate(config) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetEnabled(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_enabled(config) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetEnabled(JNIEnv* env, jclass clazz, jlong ptr, jboolean value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_enabled(config, value != JNI_FALSE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetInputVolumeControllerEnabled(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_input_volume_controller_enabled(config) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetInputVolumeControllerEnabled(JNIEnv* env, jclass clazz, jlong ptr, jboolean value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_input_volume_controller_enabled(config, value != JNI_FALSE);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetAdaptiveDigitalEnabled(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_adaptive_digital_enabled(config) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetAdaptiveDigitalEnabled(JNIEnv* env, jclass clazz, jlong ptr, jboolean value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_adaptive_digital_enabled(config, value != JNI_FALSE);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetAdaptiveDigitalHeadroomDb(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_adaptive_digital_headroom_db(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetAdaptiveDigitalHeadroomDb(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_adaptive_digital_headroom_db(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetAdaptiveDigitalMaxGainDb(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_adaptive_digital_max_gain_db(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetAdaptiveDigitalMaxGainDb(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_adaptive_digital_max_gain_db(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetAdaptiveDigitalInitialGainDb(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_adaptive_digital_initial_gain_db(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetAdaptiveDigitalInitialGainDb(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_adaptive_digital_initial_gain_db(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetAdaptiveDigitalMaxGainChangeDbPerSecond(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_adaptive_digital_max_gain_change_db_per_second(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetAdaptiveDigitalMaxGainChangeDbPerSecond(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_adaptive_digital_max_gain_change_db_per_second(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetAdaptiveDigitalMaxOutputNoiseLevelDbfs(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_adaptive_digital_max_output_noise_level_dbfs(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetAdaptiveDigitalMaxOutputNoiseLevelDbfs(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_adaptive_digital_max_output_noise_level_dbfs(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configGetFixedDigitalGainDb(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    return webrtc_agc2_config_get_fixed_digital_gain_db(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_configSetFixedDigitalGainDb(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(ptr);
    webrtc_agc2_config_set_fixed_digital_gain_db(config, value);
}

// ============================================================================
// InputVolumeController config
// ============================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigCreateDefault(JNIEnv* env, jclass clazz) {
    auto* config = webrtc_agc2_input_volume_config_create_default();
    return reinterpret_cast<jlong>(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_destroy(config);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetMinInputVolume(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_min_input_volume(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetMinInputVolume(JNIEnv* env, jclass clazz, jlong ptr, jint value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_min_input_volume(config, value);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetClippedLevelMin(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_clipped_level_min(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetClippedLevelMin(JNIEnv* env, jclass clazz, jlong ptr, jint value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_clipped_level_min(config, value);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetClippedLevelStep(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_clipped_level_step(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetClippedLevelStep(JNIEnv* env, jclass clazz, jlong ptr, jint value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_clipped_level_step(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetClippedRatioThreshold(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_clipped_ratio_threshold(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetClippedRatioThreshold(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_clipped_ratio_threshold(config, value);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetClippedWaitFrames(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_clipped_wait_frames(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetClippedWaitFrames(JNIEnv* env, jclass clazz, jlong ptr, jint value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_clipped_wait_frames(config, value);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetEnableClippingPredictor(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_enable_clipping_predictor(config) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetEnableClippingPredictor(JNIEnv* env, jclass clazz, jlong ptr, jboolean value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_enable_clipping_predictor(config, value != JNI_FALSE);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetTargetRangeMaxDbfs(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_target_range_max_dbfs(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetTargetRangeMaxDbfs(JNIEnv* env, jclass clazz, jlong ptr, jint value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_target_range_max_dbfs(config, value);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetTargetRangeMinDbfs(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_target_range_min_dbfs(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetTargetRangeMinDbfs(JNIEnv* env, jclass clazz, jlong ptr, jint value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_target_range_min_dbfs(config, value);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetUpdateInputVolumeWaitFrames(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_update_input_volume_wait_frames(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetUpdateInputVolumeWaitFrames(JNIEnv* env, jclass clazz, jlong ptr, jint value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_update_input_volume_wait_frames(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetSpeechProbabilityThreshold(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_speech_probability_threshold(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetSpeechProbabilityThreshold(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_speech_probability_threshold(config, value);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigGetSpeechRatioThreshold(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    return webrtc_agc2_input_volume_config_get_speech_ratio_threshold(config);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_inputVolumeConfigSetSpeechRatioThreshold(JNIEnv* env, jclass clazz, jlong ptr, jfloat value) {
    auto* config = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(ptr);
    webrtc_agc2_input_volume_config_set_speech_ratio_threshold(config, value);
}

// ============================================================================
// Environment
// ============================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_cn_enaium_webrtc_agc2_Jni_environmentCreate(JNIEnv* env, jclass clazz) {
    auto* environment = webrtc_agc2_environment_create();
    return reinterpret_cast<jlong>(environment);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_environmentDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* environment = reinterpret_cast<webrtc_agc2_environment_t*>(ptr);
    webrtc_agc2_environment_destroy(environment);
}

// ============================================================================
// AudioBuffer
// ============================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_cn_enaium_webrtc_agc2_Jni_audioBufferCreate(JNIEnv* env, jclass clazz, jint sampleRate, jint channels) {
    auto* buffer = webrtc_agc2_audio_buffer_create(sampleRate, channels);
    return reinterpret_cast<jlong>(buffer);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_audioBufferDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* buffer = reinterpret_cast<webrtc_agc2_audio_buffer_t*>(ptr);
    webrtc_agc2_audio_buffer_destroy(buffer);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_audioBufferWriteChannel(JNIEnv* env, jclass clazz, jlong ptr, jint channel, jfloatArray data) {
    auto* buffer = reinterpret_cast<webrtc_agc2_audio_buffer_t*>(ptr);
    jfloat* elements = env->GetFloatArrayElements(data, nullptr);
    // The C++ side must not write past the channel: the array may be longer
    // than the frame.
    const jsize len = std::min<jsize>(
        env->GetArrayLength(data),
        webrtc_agc2_audio_buffer_samples_per_channel(buffer));
    float* dest = webrtc_agc2_audio_buffer_get_channel_data(buffer, channel);
    for (jsize i = 0; i < len; ++i) {
        dest[i] = elements[i];
    }
    env->ReleaseFloatArrayElements(data, elements, JNI_ABORT);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_cn_enaium_webrtc_agc2_Jni_audioBufferReadChannel(JNIEnv* env, jclass clazz, jlong ptr, jint channel) {
    auto* buffer = reinterpret_cast<webrtc_agc2_audio_buffer_t*>(ptr);
    int samples = webrtc_agc2_audio_buffer_samples_per_channel(buffer);
    const float* src = webrtc_agc2_audio_buffer_get_channel_data_const(buffer, channel);
    jfloatArray result = env->NewFloatArray(samples);
    env->SetFloatArrayRegion(result, 0, samples, src);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_audioBufferNumChannels(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* buffer = reinterpret_cast<webrtc_agc2_audio_buffer_t*>(ptr);
    return webrtc_agc2_audio_buffer_num_channels(buffer);
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_audioBufferSamplesPerChannel(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* buffer = reinterpret_cast<webrtc_agc2_audio_buffer_t*>(ptr);
    return webrtc_agc2_audio_buffer_samples_per_channel(buffer);
}

// ============================================================================
// GainController2
// ============================================================================

extern "C" JNIEXPORT jlong JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerCreate(
    JNIEnv* env, jclass clazz, jlong envPtr, jlong configPtr, jlong inputVolumeConfigPtr,
    jint sampleRate, jint channels, jboolean useInternalVad) {
    auto* environment = reinterpret_cast<webrtc_agc2_environment_t*>(envPtr);
    auto* config = reinterpret_cast<webrtc_agc2_config_t*>(configPtr);
    auto* inputVolumeConfig = reinterpret_cast<webrtc_agc2_input_volume_config_t*>(inputVolumeConfigPtr);
    auto* gc = webrtc_agc2_gain_controller_create(
        environment, config, inputVolumeConfig, sampleRate, channels,
        useInternalVad != JNI_FALSE);
    return reinterpret_cast<jlong>(gc);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerDestroy(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    webrtc_agc2_gain_controller_destroy(gc);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerSetFixedGainDb(JNIEnv* env, jclass clazz, jlong ptr, jfloat gainDb) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    webrtc_agc2_gain_controller_set_fixed_gain_db(gc, gainDb);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerSetCaptureOutputUsed(JNIEnv* env, jclass clazz, jlong ptr, jboolean captureOutputUsed) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    webrtc_agc2_gain_controller_set_capture_output_used(gc, captureOutputUsed != JNI_FALSE);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerAnalyze(JNIEnv* env, jclass clazz, jlong ptr, jint appliedInputVolume, jlong bufferPtr) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    auto* buffer = reinterpret_cast<webrtc_agc2_audio_buffer_t*>(bufferPtr);
    webrtc_agc2_gain_controller_analyze(gc, appliedInputVolume, buffer);
}

extern "C" JNIEXPORT void JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerProcess(JNIEnv* env, jclass clazz, jlong ptr, jboolean inputVolumeChanged, jlong bufferPtr) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    auto* buffer = reinterpret_cast<webrtc_agc2_audio_buffer_t*>(bufferPtr);
    webrtc_agc2_gain_controller_process(gc, inputVolumeChanged != JNI_FALSE, buffer);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerHasRecommendedInputVolume(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    return webrtc_agc2_gain_controller_has_recommended_input_volume(gc) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerRecommendedInputVolume(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    return webrtc_agc2_gain_controller_recommended_input_volume(gc);
}

extern "C" JNIEXPORT jbooleanArray JNICALL
Java_cn_enaium_webrtc_agc2_Jni_gainControllerGetCpuFeatures(JNIEnv* env, jclass clazz, jlong ptr) {
    auto* gc = reinterpret_cast<webrtc_agc2_gain_controller_t*>(ptr);
    webrtc_agc2_cpu_features_t features = webrtc_agc2_gain_controller_get_cpu_features(gc);
    const jboolean values[3] = {
        static_cast<jboolean>(features.sse2),
        static_cast<jboolean>(features.avx2),
        static_cast<jboolean>(features.neon),
    };
    jbooleanArray result = env->NewBooleanArray(3);
    env->SetBooleanArrayRegion(result, 0, 3, values);
    return result;
}

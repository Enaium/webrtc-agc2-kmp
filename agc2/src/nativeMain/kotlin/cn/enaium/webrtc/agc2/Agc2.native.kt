/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.webrtc.agc2

import kotlinx.cinterop.*
import webrtc_agc2.*

actual val agc2Version: String
    get() = webrtc_agc2_version()?.toKString() ?: ""

// =========================================================================
// Native (cinterop) actual implementations
// =========================================================================

class NativeAgc2Config(internal val ptr: CPointer<webrtc_agc2_config_t>) : Agc2Config {
    override fun close() {
        webrtc_agc2_config_destroy(ptr)
    }

    override var enabled: Boolean
        get() = webrtc_agc2_config_get_enabled(ptr)
        set(value) = webrtc_agc2_config_set_enabled(ptr, value)

    override var inputVolumeControllerEnabled: Boolean
        get() = webrtc_agc2_config_get_input_volume_controller_enabled(ptr)
        set(value) = webrtc_agc2_config_set_input_volume_controller_enabled(ptr, value)

    override var adaptiveDigitalEnabled: Boolean
        get() = webrtc_agc2_config_get_adaptive_digital_enabled(ptr)
        set(value) = webrtc_agc2_config_set_adaptive_digital_enabled(ptr, value)

    override var adaptiveDigitalHeadroomDb: Float
        get() = webrtc_agc2_config_get_adaptive_digital_headroom_db(ptr)
        set(value) = webrtc_agc2_config_set_adaptive_digital_headroom_db(ptr, value)

    override var adaptiveDigitalMaxGainDb: Float
        get() = webrtc_agc2_config_get_adaptive_digital_max_gain_db(ptr)
        set(value) = webrtc_agc2_config_set_adaptive_digital_max_gain_db(ptr, value)

    override var adaptiveDigitalInitialGainDb: Float
        get() = webrtc_agc2_config_get_adaptive_digital_initial_gain_db(ptr)
        set(value) = webrtc_agc2_config_set_adaptive_digital_initial_gain_db(ptr, value)

    override var adaptiveDigitalMaxGainChangeDbPerSecond: Float
        get() = webrtc_agc2_config_get_adaptive_digital_max_gain_change_db_per_second(ptr)
        set(value) = webrtc_agc2_config_set_adaptive_digital_max_gain_change_db_per_second(ptr, value)

    override var adaptiveDigitalMaxOutputNoiseLevelDbfs: Float
        get() = webrtc_agc2_config_get_adaptive_digital_max_output_noise_level_dbfs(ptr)
        set(value) = webrtc_agc2_config_set_adaptive_digital_max_output_noise_level_dbfs(ptr, value)

    override var fixedDigitalGainDb: Float
        get() = webrtc_agc2_config_get_fixed_digital_gain_db(ptr)
        set(value) = webrtc_agc2_config_set_fixed_digital_gain_db(ptr, value)

    override fun validate(): Boolean = webrtc_agc2_config_validate(ptr)
}

class NativeAgc2InputVolumeConfig(
    internal val ptr: CPointer<webrtc_agc2_input_volume_config_t>
) : Agc2InputVolumeConfig {
    override fun close() {
        webrtc_agc2_input_volume_config_destroy(ptr)
    }

    override var minInputVolume: Int
        get() = webrtc_agc2_input_volume_config_get_min_input_volume(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_min_input_volume(ptr, value)

    override var clippedLevelMin: Int
        get() = webrtc_agc2_input_volume_config_get_clipped_level_min(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_clipped_level_min(ptr, value)

    override var clippedLevelStep: Int
        get() = webrtc_agc2_input_volume_config_get_clipped_level_step(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_clipped_level_step(ptr, value)

    override var clippedRatioThreshold: Float
        get() = webrtc_agc2_input_volume_config_get_clipped_ratio_threshold(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_clipped_ratio_threshold(ptr, value)

    override var clippedWaitFrames: Int
        get() = webrtc_agc2_input_volume_config_get_clipped_wait_frames(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_clipped_wait_frames(ptr, value)

    override var enableClippingPredictor: Boolean
        get() = webrtc_agc2_input_volume_config_get_enable_clipping_predictor(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_enable_clipping_predictor(ptr, value)

    override var targetRangeMaxDbfs: Int
        get() = webrtc_agc2_input_volume_config_get_target_range_max_dbfs(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_target_range_max_dbfs(ptr, value)

    override var targetRangeMinDbfs: Int
        get() = webrtc_agc2_input_volume_config_get_target_range_min_dbfs(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_target_range_min_dbfs(ptr, value)

    override var updateInputVolumeWaitFrames: Int
        get() = webrtc_agc2_input_volume_config_get_update_input_volume_wait_frames(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_update_input_volume_wait_frames(ptr, value)

    override var speechProbabilityThreshold: Float
        get() = webrtc_agc2_input_volume_config_get_speech_probability_threshold(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_speech_probability_threshold(ptr, value)

    override var speechRatioThreshold: Float
        get() = webrtc_agc2_input_volume_config_get_speech_ratio_threshold(ptr)
        set(value) = webrtc_agc2_input_volume_config_set_speech_ratio_threshold(ptr, value)
}

class NativeAgc2Environment(
    internal val ptr: CPointer<webrtc_agc2_environment_t>
) : Agc2Environment {
    override fun close() {
        webrtc_agc2_environment_destroy(ptr)
    }
}

class NativeAgc2AudioBuffer(
    internal val ptr: CPointer<webrtc_agc2_audio_buffer_t>
) : Agc2AudioBuffer {
    override fun close() {
        webrtc_agc2_audio_buffer_destroy(ptr)
    }

    override val numChannels: Int
        get() = webrtc_agc2_audio_buffer_num_channels(ptr)

    override val samplesPerChannel: Int
        get() = webrtc_agc2_audio_buffer_samples_per_channel(ptr)

    override fun writeChannel(channel: Int, data: FloatArray) {
        require(channel in 0 until numChannels) {
            "channel $channel is outside [0, $numChannels)"
        }
        val samples = samplesPerChannel
        require(data.size >= samples) {
            "data holds ${data.size} samples, the frame holds $samples"
        }
        val channelPtr = webrtc_agc2_audio_buffer_get_channel_data(ptr, channel)
            ?: error("webrtc_agc2_audio_buffer_get_channel_data returned null")
        for (i in 0 until samples) {
            channelPtr[i] = data[i]
        }
    }

    override fun readChannel(channel: Int): FloatArray {
        require(channel in 0 until numChannels) {
            "channel $channel is outside [0, $numChannels)"
        }
        val channelPtr = webrtc_agc2_audio_buffer_get_channel_data_const(ptr, channel)
            ?: error("webrtc_agc2_audio_buffer_get_channel_data_const returned null")
        val samples = samplesPerChannel
        return FloatArray(samples) { i -> channelPtr[i] }
    }
}

class NativeAgc2GainController(
    internal val ptr: CPointer<webrtc_agc2_gain_controller_t>
) : Agc2GainController {
    override fun close() {
        webrtc_agc2_gain_controller_destroy(ptr)
    }

    override fun setFixedGainDb(gainDb: Float) {
        webrtc_agc2_gain_controller_set_fixed_gain_db(ptr, gainDb)
    }

    override fun setCaptureOutputUsed(captureOutputUsed: Boolean) {
        webrtc_agc2_gain_controller_set_capture_output_used(ptr, captureOutputUsed)
    }

    override fun analyze(appliedInputVolume: Int, buffer: Agc2AudioBuffer) {
        webrtc_agc2_gain_controller_analyze(
            ptr,
            appliedInputVolume,
            (buffer as NativeAgc2AudioBuffer).ptr,
        )
    }

    override fun process(inputVolumeChanged: Boolean, buffer: Agc2AudioBuffer) {
        webrtc_agc2_gain_controller_process(
            ptr,
            inputVolumeChanged,
            (buffer as NativeAgc2AudioBuffer).ptr,
        )
    }

    override val recommendedInputVolume: Int?
        get() = if (webrtc_agc2_gain_controller_has_recommended_input_volume(ptr)) {
            webrtc_agc2_gain_controller_recommended_input_volume(ptr)
        } else {
            null
        }

    override val cpuFeatures: Agc2CpuFeatures
        get() = webrtc_agc2_gain_controller_get_cpu_features(ptr).useContents {
            Agc2CpuFeatures(sse2 = sse2, avx2 = avx2, neon = neon)
        }
}

// =========================================================================
// actual factory functions
// =========================================================================

actual fun createAgc2Config(): Agc2Config {
    val ptr = webrtc_agc2_config_create_default()
        ?: error("webrtc_agc2_config_create_default returned null")
    return NativeAgc2Config(ptr)
}

actual fun createAgc2InputVolumeConfig(): Agc2InputVolumeConfig {
    val ptr = webrtc_agc2_input_volume_config_create_default()
        ?: error("webrtc_agc2_input_volume_config_create_default returned null")
    return NativeAgc2InputVolumeConfig(ptr)
}

actual fun createAgc2Environment(): Agc2Environment {
    val ptr = webrtc_agc2_environment_create()
        ?: error("webrtc_agc2_environment_create returned null")
    return NativeAgc2Environment(ptr)
}

actual fun createAgc2AudioBuffer(sampleRate: Int, channels: Int): Agc2AudioBuffer {
    val ptr = webrtc_agc2_audio_buffer_create(sampleRate, channels)
        ?: error("webrtc_agc2_audio_buffer_create returned null")
    return NativeAgc2AudioBuffer(ptr)
}

actual fun createAgc2GainController(
    env: Agc2Environment,
    config: Agc2Config,
    inputVolumeConfig: Agc2InputVolumeConfig,
    sampleRate: Int,
    channels: Int,
    useInternalVad: Boolean
): Agc2GainController {
    val ptr = webrtc_agc2_gain_controller_create(
        (env as NativeAgc2Environment).ptr,
        (config as NativeAgc2Config).ptr,
        (inputVolumeConfig as NativeAgc2InputVolumeConfig).ptr,
        sampleRate,
        channels,
        useInternalVad
    )
    if (ptr == null) {
        error(
            "createAgc2GainController: the configuration is invalid " +
                "(config.validate() = ${config.validate()}) or the stream configuration " +
                "(sampleRate=$sampleRate, channels=$channels) is not supported"
        )
    }
    return NativeAgc2GainController(ptr)
}

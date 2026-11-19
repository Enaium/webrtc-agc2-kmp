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

package cn.enaium.webrtc.agc2

// =========================================================================
// Web (js + wasmJs) actual implementations
//
// Neither target can use cinterop or embed a static library, so the same C API
// is compiled to a standalone Emscripten module (agc2_wasm.js/.wasm, see the
// wasm tasks in build.gradle.kts) and shipped as the @enaium/webrtc-agc2-kmp
// npm package. The page initializes it with initAgc2Kmp() before the Kotlin
// module runs; the glue publishes every webrtc_agc2_* export as a global, which
// is what the external declarations below resolve to.
//
// Handles are wasm32 heap pointers, so Int carries them. bool is an i32.
// =========================================================================

actual val agc2Version: String
    get() = agc2KmpVersion() ?: ""

/** Version string: a char* has to be decoded by the glue. */
private external fun agc2KmpVersion(): String?

/*
 * Wasm JS interop only accepts primitives, strings, externals and functions,
 * which is why the samples cross one at a time instead of as a FloatArray.
 */
private external fun agc2KmpHeapF32Get(ptr: Int, index: Int): Float

private external fun agc2KmpHeapF32Set(ptr: Int, index: Int, value: Float)

/** Copies [count] samples of [values] to the heap at [ptr]. */
private fun writeFloats(ptr: Int, values: FloatArray, count: Int) {
    for (i in 0 until count) agc2KmpHeapF32Set(ptr, i, values[i])
}

/** Copies the heap floats at [ptr] into [out]. */
private fun readFloats(ptr: Int, out: FloatArray) {
    for (i in out.indices) out[i] = agc2KmpHeapF32Get(ptr, i)
}

/** One bool field of the controller's CPU feature struct (0 sse2, 1 avx2, 2 neon). */
private external fun agc2KmpCpuFeature(controller: Int, index: Int): Int

private external fun webrtc_agc2_config_create_default(): Int
private external fun webrtc_agc2_config_destroy(ptr: Int)
private external fun webrtc_agc2_config_validate(ptr: Int): Int
private external fun webrtc_agc2_config_get_enabled(ptr: Int): Int
private external fun webrtc_agc2_config_set_enabled(ptr: Int, value: Int)
private external fun webrtc_agc2_config_get_input_volume_controller_enabled(ptr: Int): Int
private external fun webrtc_agc2_config_set_input_volume_controller_enabled(ptr: Int, value: Int)
private external fun webrtc_agc2_config_get_adaptive_digital_enabled(ptr: Int): Int
private external fun webrtc_agc2_config_set_adaptive_digital_enabled(ptr: Int, value: Int)
private external fun webrtc_agc2_config_get_adaptive_digital_headroom_db(ptr: Int): Float
private external fun webrtc_agc2_config_set_adaptive_digital_headroom_db(ptr: Int, value: Float)
private external fun webrtc_agc2_config_get_adaptive_digital_max_gain_db(ptr: Int): Float
private external fun webrtc_agc2_config_set_adaptive_digital_max_gain_db(ptr: Int, value: Float)
private external fun webrtc_agc2_config_get_adaptive_digital_initial_gain_db(ptr: Int): Float
private external fun webrtc_agc2_config_set_adaptive_digital_initial_gain_db(ptr: Int, value: Float)
private external fun webrtc_agc2_config_get_adaptive_digital_max_gain_change_db_per_second(ptr: Int): Float
private external fun webrtc_agc2_config_set_adaptive_digital_max_gain_change_db_per_second(ptr: Int, value: Float)
private external fun webrtc_agc2_config_get_adaptive_digital_max_output_noise_level_dbfs(ptr: Int): Float
private external fun webrtc_agc2_config_set_adaptive_digital_max_output_noise_level_dbfs(ptr: Int, value: Float)
private external fun webrtc_agc2_config_get_fixed_digital_gain_db(ptr: Int): Float
private external fun webrtc_agc2_config_set_fixed_digital_gain_db(ptr: Int, value: Float)

private external fun webrtc_agc2_input_volume_config_create_default(): Int
private external fun webrtc_agc2_input_volume_config_destroy(ptr: Int)
private external fun webrtc_agc2_input_volume_config_get_min_input_volume(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_min_input_volume(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_clipped_level_min(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_clipped_level_min(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_clipped_level_step(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_clipped_level_step(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_clipped_ratio_threshold(ptr: Int): Float
private external fun webrtc_agc2_input_volume_config_set_clipped_ratio_threshold(ptr: Int, value: Float)
private external fun webrtc_agc2_input_volume_config_get_clipped_wait_frames(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_clipped_wait_frames(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_enable_clipping_predictor(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_enable_clipping_predictor(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_target_range_max_dbfs(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_target_range_max_dbfs(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_target_range_min_dbfs(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_target_range_min_dbfs(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_update_input_volume_wait_frames(ptr: Int): Int
private external fun webrtc_agc2_input_volume_config_set_update_input_volume_wait_frames(ptr: Int, value: Int)
private external fun webrtc_agc2_input_volume_config_get_speech_probability_threshold(ptr: Int): Float
private external fun webrtc_agc2_input_volume_config_set_speech_probability_threshold(ptr: Int, value: Float)
private external fun webrtc_agc2_input_volume_config_get_speech_ratio_threshold(ptr: Int): Float
private external fun webrtc_agc2_input_volume_config_set_speech_ratio_threshold(ptr: Int, value: Float)

private external fun webrtc_agc2_environment_create(): Int
private external fun webrtc_agc2_environment_destroy(ptr: Int)

private external fun webrtc_agc2_audio_buffer_create(sampleRate: Int, channels: Int): Int
private external fun webrtc_agc2_audio_buffer_destroy(ptr: Int)
private external fun webrtc_agc2_audio_buffer_num_channels(ptr: Int): Int
private external fun webrtc_agc2_audio_buffer_samples_per_channel(ptr: Int): Int
private external fun webrtc_agc2_audio_buffer_get_channel_data(ptr: Int, channel: Int): Int
private external fun webrtc_agc2_audio_buffer_get_channel_data_const(ptr: Int, channel: Int): Int

private external fun webrtc_agc2_gain_controller_create(
    env: Int,
    config: Int,
    inputVolumeConfig: Int,
    sampleRate: Int,
    channels: Int,
    useInternalVad: Int,
): Int
private external fun webrtc_agc2_gain_controller_destroy(ptr: Int)
private external fun webrtc_agc2_gain_controller_set_fixed_gain_db(ptr: Int, gainDb: Float)
private external fun webrtc_agc2_gain_controller_set_capture_output_used(ptr: Int, used: Int)
private external fun webrtc_agc2_gain_controller_analyze(ptr: Int, appliedInputVolume: Int, buffer: Int)
private external fun webrtc_agc2_gain_controller_process(ptr: Int, inputVolumeChanged: Int, buffer: Int)
private external fun webrtc_agc2_gain_controller_has_recommended_input_volume(ptr: Int): Int
private external fun webrtc_agc2_gain_controller_recommended_input_volume(ptr: Int): Int

private fun Int.toBool(): Boolean = this != 0

private fun Boolean.toCInt(): Int = if (this) 1 else 0

class WebAgc2Config(internal val ptr: Int) : Agc2Config {
    override fun close() {
        webrtc_agc2_config_destroy(ptr)
    }

    override var enabled: Boolean
        get() = webrtc_agc2_config_get_enabled(ptr).toBool()
        set(value) = webrtc_agc2_config_set_enabled(ptr, value.toCInt())

    override var inputVolumeControllerEnabled: Boolean
        get() = webrtc_agc2_config_get_input_volume_controller_enabled(ptr).toBool()
        set(value) = webrtc_agc2_config_set_input_volume_controller_enabled(ptr, value.toCInt())

    override var adaptiveDigitalEnabled: Boolean
        get() = webrtc_agc2_config_get_adaptive_digital_enabled(ptr).toBool()
        set(value) = webrtc_agc2_config_set_adaptive_digital_enabled(ptr, value.toCInt())

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

    override fun validate(): Boolean = webrtc_agc2_config_validate(ptr).toBool()
}

class WebAgc2InputVolumeConfig(internal val ptr: Int) : Agc2InputVolumeConfig {
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
        get() = webrtc_agc2_input_volume_config_get_enable_clipping_predictor(ptr).toBool()
        set(value) = webrtc_agc2_input_volume_config_set_enable_clipping_predictor(ptr, value.toCInt())

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

class WebAgc2Environment(internal val ptr: Int) : Agc2Environment {
    override fun close() {
        webrtc_agc2_environment_destroy(ptr)
    }
}

class WebAgc2AudioBuffer(internal val ptr: Int) : Agc2AudioBuffer {
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
        check(channelPtr != 0) { "webrtc_agc2_audio_buffer_get_channel_data returned null" }
        writeFloats(channelPtr, data, samples)
    }

    override fun readChannel(channel: Int): FloatArray {
        require(channel in 0 until numChannels) {
            "channel $channel is outside [0, $numChannels)"
        }
        val channelPtr = webrtc_agc2_audio_buffer_get_channel_data_const(ptr, channel)
        check(channelPtr != 0) { "webrtc_agc2_audio_buffer_get_channel_data_const returned null" }
        val samples = samplesPerChannel
        val out = FloatArray(samples)
        readFloats(channelPtr, out)
        return out
    }
}

class WebAgc2GainController(internal val ptr: Int) : Agc2GainController {
    override fun close() {
        webrtc_agc2_gain_controller_destroy(ptr)
    }

    override fun setFixedGainDb(gainDb: Float) {
        webrtc_agc2_gain_controller_set_fixed_gain_db(ptr, gainDb)
    }

    override fun setCaptureOutputUsed(captureOutputUsed: Boolean) {
        webrtc_agc2_gain_controller_set_capture_output_used(ptr, captureOutputUsed.toCInt())
    }

    override fun analyze(appliedInputVolume: Int, buffer: Agc2AudioBuffer) {
        webrtc_agc2_gain_controller_analyze(
            ptr,
            appliedInputVolume,
            (buffer as WebAgc2AudioBuffer).ptr,
        )
    }

    override fun process(inputVolumeChanged: Boolean, buffer: Agc2AudioBuffer) {
        webrtc_agc2_gain_controller_process(
            ptr,
            inputVolumeChanged.toCInt(),
            (buffer as WebAgc2AudioBuffer).ptr,
        )
    }

    override val recommendedInputVolume: Int?
        get() = if (webrtc_agc2_gain_controller_has_recommended_input_volume(ptr).toBool()) {
            webrtc_agc2_gain_controller_recommended_input_volume(ptr)
        } else {
            null
        }

    override val cpuFeatures: Agc2CpuFeatures
        get() = Agc2CpuFeatures(
            sse2 = agc2KmpCpuFeature(ptr, 0).toBool(),
            avx2 = agc2KmpCpuFeature(ptr, 1).toBool(),
            neon = agc2KmpCpuFeature(ptr, 2).toBool(),
        )
}

// =========================================================================
// actual factory functions
// =========================================================================

actual fun createAgc2Config(): Agc2Config {
    val ptr = webrtc_agc2_config_create_default()
    check(ptr != 0) { "webrtc_agc2_config_create_default returned null" }
    return WebAgc2Config(ptr)
}

actual fun createAgc2InputVolumeConfig(): Agc2InputVolumeConfig {
    val ptr = webrtc_agc2_input_volume_config_create_default()
    check(ptr != 0) { "webrtc_agc2_input_volume_config_create_default returned null" }
    return WebAgc2InputVolumeConfig(ptr)
}

actual fun createAgc2Environment(): Agc2Environment {
    val ptr = webrtc_agc2_environment_create()
    check(ptr != 0) { "webrtc_agc2_environment_create returned null" }
    return WebAgc2Environment(ptr)
}

actual fun createAgc2AudioBuffer(sampleRate: Int, channels: Int): Agc2AudioBuffer {
    val ptr = webrtc_agc2_audio_buffer_create(sampleRate, channels)
    check(ptr != 0) { "webrtc_agc2_audio_buffer_create returned null" }
    return WebAgc2AudioBuffer(ptr)
}

actual fun createAgc2GainController(
    env: Agc2Environment,
    config: Agc2Config,
    inputVolumeConfig: Agc2InputVolumeConfig,
    sampleRate: Int,
    channels: Int,
    useInternalVad: Boolean,
): Agc2GainController {
    val ptr = webrtc_agc2_gain_controller_create(
        (env as WebAgc2Environment).ptr,
        (config as WebAgc2Config).ptr,
        (inputVolumeConfig as WebAgc2InputVolumeConfig).ptr,
        sampleRate,
        channels,
        useInternalVad.toCInt(),
    )
    if (ptr == 0) {
        error(
            "createAgc2GainController: the configuration is invalid " +
                "(config.validate() = ${config.validate()}) or the stream configuration " +
                "(sampleRate=$sampleRate, channels=$channels) is not supported",
        )
    }
    return WebAgc2GainController(ptr)
}

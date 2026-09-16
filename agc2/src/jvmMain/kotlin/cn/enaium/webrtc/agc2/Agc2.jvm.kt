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
// JNI bridge – loads the native library and provides external declarations
// =========================================================================

internal object Jni {
    init {
        NativeLoader.load()
    }

    // ---- Version ----
    external fun version(): String

    // ---- Config ----
    external fun configCreateDefault(): Long
    external fun configDestroy(ptr: Long)
    external fun configValidate(ptr: Long): Boolean
    external fun configGetEnabled(ptr: Long): Boolean
    external fun configSetEnabled(ptr: Long, value: Boolean)
    external fun configGetInputVolumeControllerEnabled(ptr: Long): Boolean
    external fun configSetInputVolumeControllerEnabled(ptr: Long, value: Boolean)
    external fun configGetAdaptiveDigitalEnabled(ptr: Long): Boolean
    external fun configSetAdaptiveDigitalEnabled(ptr: Long, value: Boolean)
    external fun configGetAdaptiveDigitalHeadroomDb(ptr: Long): Float
    external fun configSetAdaptiveDigitalHeadroomDb(ptr: Long, value: Float)
    external fun configGetAdaptiveDigitalMaxGainDb(ptr: Long): Float
    external fun configSetAdaptiveDigitalMaxGainDb(ptr: Long, value: Float)
    external fun configGetAdaptiveDigitalInitialGainDb(ptr: Long): Float
    external fun configSetAdaptiveDigitalInitialGainDb(ptr: Long, value: Float)
    external fun configGetAdaptiveDigitalMaxGainChangeDbPerSecond(ptr: Long): Float
    external fun configSetAdaptiveDigitalMaxGainChangeDbPerSecond(ptr: Long, value: Float)
    external fun configGetAdaptiveDigitalMaxOutputNoiseLevelDbfs(ptr: Long): Float
    external fun configSetAdaptiveDigitalMaxOutputNoiseLevelDbfs(ptr: Long, value: Float)
    external fun configGetFixedDigitalGainDb(ptr: Long): Float
    external fun configSetFixedDigitalGainDb(ptr: Long, value: Float)

    // ---- InputVolumeController config ----
    external fun inputVolumeConfigCreateDefault(): Long
    external fun inputVolumeConfigDestroy(ptr: Long)
    external fun inputVolumeConfigGetMinInputVolume(ptr: Long): Int
    external fun inputVolumeConfigSetMinInputVolume(ptr: Long, value: Int)
    external fun inputVolumeConfigGetClippedLevelMin(ptr: Long): Int
    external fun inputVolumeConfigSetClippedLevelMin(ptr: Long, value: Int)
    external fun inputVolumeConfigGetClippedLevelStep(ptr: Long): Int
    external fun inputVolumeConfigSetClippedLevelStep(ptr: Long, value: Int)
    external fun inputVolumeConfigGetClippedRatioThreshold(ptr: Long): Float
    external fun inputVolumeConfigSetClippedRatioThreshold(ptr: Long, value: Float)
    external fun inputVolumeConfigGetClippedWaitFrames(ptr: Long): Int
    external fun inputVolumeConfigSetClippedWaitFrames(ptr: Long, value: Int)
    external fun inputVolumeConfigGetEnableClippingPredictor(ptr: Long): Boolean
    external fun inputVolumeConfigSetEnableClippingPredictor(ptr: Long, value: Boolean)
    external fun inputVolumeConfigGetTargetRangeMaxDbfs(ptr: Long): Int
    external fun inputVolumeConfigSetTargetRangeMaxDbfs(ptr: Long, value: Int)
    external fun inputVolumeConfigGetTargetRangeMinDbfs(ptr: Long): Int
    external fun inputVolumeConfigSetTargetRangeMinDbfs(ptr: Long, value: Int)
    external fun inputVolumeConfigGetUpdateInputVolumeWaitFrames(ptr: Long): Int
    external fun inputVolumeConfigSetUpdateInputVolumeWaitFrames(ptr: Long, value: Int)
    external fun inputVolumeConfigGetSpeechProbabilityThreshold(ptr: Long): Float
    external fun inputVolumeConfigSetSpeechProbabilityThreshold(ptr: Long, value: Float)
    external fun inputVolumeConfigGetSpeechRatioThreshold(ptr: Long): Float
    external fun inputVolumeConfigSetSpeechRatioThreshold(ptr: Long, value: Float)

    // ---- Environment ----
    external fun environmentCreate(): Long
    external fun environmentDestroy(ptr: Long)

    // ---- AudioBuffer ----
    external fun audioBufferCreate(sampleRate: Int, channels: Int): Long
    external fun audioBufferDestroy(ptr: Long)
    external fun audioBufferWriteChannel(ptr: Long, channel: Int, data: FloatArray)
    external fun audioBufferReadChannel(ptr: Long, channel: Int): FloatArray
    external fun audioBufferNumChannels(ptr: Long): Int
    external fun audioBufferSamplesPerChannel(ptr: Long): Int

    // ---- GainController ----
    external fun gainControllerCreate(
        envPtr: Long,
        configPtr: Long,
        inputVolumeConfigPtr: Long,
        sampleRate: Int,
        channels: Int,
        useInternalVad: Boolean
    ): Long

    external fun gainControllerDestroy(ptr: Long)
    external fun gainControllerSetFixedGainDb(ptr: Long, gainDb: Float)
    external fun gainControllerSetCaptureOutputUsed(ptr: Long, captureOutputUsed: Boolean)
    external fun gainControllerAnalyze(ptr: Long, appliedInputVolume: Int, bufferPtr: Long)
    external fun gainControllerProcess(ptr: Long, inputVolumeChanged: Boolean, bufferPtr: Long)
    external fun gainControllerHasRecommendedInputVolume(ptr: Long): Boolean
    external fun gainControllerRecommendedInputVolume(ptr: Long): Int
    external fun gainControllerGetCpuFeatures(ptr: Long): BooleanArray
}

actual val agc2Version: String
    get() = Jni.version()

// =========================================================================
// JVM/Android actual implementations
// =========================================================================

class JvmAgc2Config(internal val ptr: Long) : Agc2Config {
    override fun close() {
        Jni.configDestroy(ptr)
    }

    override var enabled: Boolean
        get() = Jni.configGetEnabled(ptr)
        set(value) = Jni.configSetEnabled(ptr, value)

    override var inputVolumeControllerEnabled: Boolean
        get() = Jni.configGetInputVolumeControllerEnabled(ptr)
        set(value) = Jni.configSetInputVolumeControllerEnabled(ptr, value)

    override var adaptiveDigitalEnabled: Boolean
        get() = Jni.configGetAdaptiveDigitalEnabled(ptr)
        set(value) = Jni.configSetAdaptiveDigitalEnabled(ptr, value)

    override var adaptiveDigitalHeadroomDb: Float
        get() = Jni.configGetAdaptiveDigitalHeadroomDb(ptr)
        set(value) = Jni.configSetAdaptiveDigitalHeadroomDb(ptr, value)

    override var adaptiveDigitalMaxGainDb: Float
        get() = Jni.configGetAdaptiveDigitalMaxGainDb(ptr)
        set(value) = Jni.configSetAdaptiveDigitalMaxGainDb(ptr, value)

    override var adaptiveDigitalInitialGainDb: Float
        get() = Jni.configGetAdaptiveDigitalInitialGainDb(ptr)
        set(value) = Jni.configSetAdaptiveDigitalInitialGainDb(ptr, value)

    override var adaptiveDigitalMaxGainChangeDbPerSecond: Float
        get() = Jni.configGetAdaptiveDigitalMaxGainChangeDbPerSecond(ptr)
        set(value) = Jni.configSetAdaptiveDigitalMaxGainChangeDbPerSecond(ptr, value)

    override var adaptiveDigitalMaxOutputNoiseLevelDbfs: Float
        get() = Jni.configGetAdaptiveDigitalMaxOutputNoiseLevelDbfs(ptr)
        set(value) = Jni.configSetAdaptiveDigitalMaxOutputNoiseLevelDbfs(ptr, value)

    override var fixedDigitalGainDb: Float
        get() = Jni.configGetFixedDigitalGainDb(ptr)
        set(value) = Jni.configSetFixedDigitalGainDb(ptr, value)

    override fun validate(): Boolean = Jni.configValidate(ptr)
}

class JvmAgc2InputVolumeConfig(internal val ptr: Long) : Agc2InputVolumeConfig {
    override fun close() {
        Jni.inputVolumeConfigDestroy(ptr)
    }

    override var minInputVolume: Int
        get() = Jni.inputVolumeConfigGetMinInputVolume(ptr)
        set(value) = Jni.inputVolumeConfigSetMinInputVolume(ptr, value)

    override var clippedLevelMin: Int
        get() = Jni.inputVolumeConfigGetClippedLevelMin(ptr)
        set(value) = Jni.inputVolumeConfigSetClippedLevelMin(ptr, value)

    override var clippedLevelStep: Int
        get() = Jni.inputVolumeConfigGetClippedLevelStep(ptr)
        set(value) = Jni.inputVolumeConfigSetClippedLevelStep(ptr, value)

    override var clippedRatioThreshold: Float
        get() = Jni.inputVolumeConfigGetClippedRatioThreshold(ptr)
        set(value) = Jni.inputVolumeConfigSetClippedRatioThreshold(ptr, value)

    override var clippedWaitFrames: Int
        get() = Jni.inputVolumeConfigGetClippedWaitFrames(ptr)
        set(value) = Jni.inputVolumeConfigSetClippedWaitFrames(ptr, value)

    override var enableClippingPredictor: Boolean
        get() = Jni.inputVolumeConfigGetEnableClippingPredictor(ptr)
        set(value) = Jni.inputVolumeConfigSetEnableClippingPredictor(ptr, value)

    override var targetRangeMaxDbfs: Int
        get() = Jni.inputVolumeConfigGetTargetRangeMaxDbfs(ptr)
        set(value) = Jni.inputVolumeConfigSetTargetRangeMaxDbfs(ptr, value)

    override var targetRangeMinDbfs: Int
        get() = Jni.inputVolumeConfigGetTargetRangeMinDbfs(ptr)
        set(value) = Jni.inputVolumeConfigSetTargetRangeMinDbfs(ptr, value)

    override var updateInputVolumeWaitFrames: Int
        get() = Jni.inputVolumeConfigGetUpdateInputVolumeWaitFrames(ptr)
        set(value) = Jni.inputVolumeConfigSetUpdateInputVolumeWaitFrames(ptr, value)

    override var speechProbabilityThreshold: Float
        get() = Jni.inputVolumeConfigGetSpeechProbabilityThreshold(ptr)
        set(value) = Jni.inputVolumeConfigSetSpeechProbabilityThreshold(ptr, value)

    override var speechRatioThreshold: Float
        get() = Jni.inputVolumeConfigGetSpeechRatioThreshold(ptr)
        set(value) = Jni.inputVolumeConfigSetSpeechRatioThreshold(ptr, value)
}

class JvmAgc2Environment(internal val ptr: Long) : Agc2Environment {
    override fun close() {
        Jni.environmentDestroy(ptr)
    }
}

class JvmAgc2AudioBuffer(internal val ptr: Long) : Agc2AudioBuffer {
    override fun close() {
        Jni.audioBufferDestroy(ptr)
    }

    override val numChannels: Int
        get() = Jni.audioBufferNumChannels(ptr)

    override val samplesPerChannel: Int
        get() = Jni.audioBufferSamplesPerChannel(ptr)

    override fun writeChannel(channel: Int, data: FloatArray) {
        require(channel in 0 until numChannels) {
            "channel $channel is outside [0, $numChannels)"
        }
        require(data.size >= samplesPerChannel) {
            "data holds ${data.size} samples, the frame holds $samplesPerChannel"
        }
        Jni.audioBufferWriteChannel(ptr, channel, data)
    }

    override fun readChannel(channel: Int): FloatArray {
        require(channel in 0 until numChannels) {
            "channel $channel is outside [0, $numChannels)"
        }
        return Jni.audioBufferReadChannel(ptr, channel)
    }
}

class JvmAgc2GainController(internal val ptr: Long) : Agc2GainController {
    override fun close() {
        Jni.gainControllerDestroy(ptr)
    }

    override fun setFixedGainDb(gainDb: Float) {
        Jni.gainControllerSetFixedGainDb(ptr, gainDb)
    }

    override fun setCaptureOutputUsed(captureOutputUsed: Boolean) {
        Jni.gainControllerSetCaptureOutputUsed(ptr, captureOutputUsed)
    }

    override fun analyze(appliedInputVolume: Int, buffer: Agc2AudioBuffer) {
        Jni.gainControllerAnalyze(ptr, appliedInputVolume, (buffer as JvmAgc2AudioBuffer).ptr)
    }

    override fun process(inputVolumeChanged: Boolean, buffer: Agc2AudioBuffer) {
        Jni.gainControllerProcess(ptr, inputVolumeChanged, (buffer as JvmAgc2AudioBuffer).ptr)
    }

    override val recommendedInputVolume: Int?
        get() = if (Jni.gainControllerHasRecommendedInputVolume(ptr)) {
            Jni.gainControllerRecommendedInputVolume(ptr)
        } else {
            null
        }

    override val cpuFeatures: Agc2CpuFeatures
        get() {
            val raw = Jni.gainControllerGetCpuFeatures(ptr)
            return Agc2CpuFeatures(sse2 = raw[0], avx2 = raw[1], neon = raw[2])
        }
}

// =========================================================================
// actual factory functions
// =========================================================================

actual fun createAgc2Config(): Agc2Config =
    JvmAgc2Config(Jni.configCreateDefault())

actual fun createAgc2InputVolumeConfig(): Agc2InputVolumeConfig =
    JvmAgc2InputVolumeConfig(Jni.inputVolumeConfigCreateDefault())

actual fun createAgc2Environment(): Agc2Environment =
    JvmAgc2Environment(Jni.environmentCreate())

actual fun createAgc2AudioBuffer(sampleRate: Int, channels: Int): Agc2AudioBuffer =
    JvmAgc2AudioBuffer(Jni.audioBufferCreate(sampleRate, channels))

actual fun createAgc2GainController(
    env: Agc2Environment,
    config: Agc2Config,
    inputVolumeConfig: Agc2InputVolumeConfig,
    sampleRate: Int,
    channels: Int,
    useInternalVad: Boolean
): Agc2GainController {
    val ptr = Jni.gainControllerCreate(
        (env as JvmAgc2Environment).ptr,
        (config as JvmAgc2Config).ptr,
        (inputVolumeConfig as JvmAgc2InputVolumeConfig).ptr,
        sampleRate,
        channels,
        useInternalVad
    )
    if (ptr == 0L) {
        error(
            "createAgc2GainController: the configuration is invalid " +
                "(config.validate() = ${config.validate()}) or the stream configuration " +
                "(sampleRate=$sampleRate, channels=$channels) is not supported"
        )
    }
    return JvmAgc2GainController(ptr)
}

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

/** CPU features the loaded AGC2 implementation can dispatch to. */
data class Agc2CpuFeatures(
    val sse2: Boolean,
    val avx2: Boolean,
    val neon: Boolean
)

/** Version of the bundled WebRTC AGC2 build. */
expect val agc2Version: String

// =========================================================================
// Top-level expect factory functions
// =========================================================================

/** Creates the AGC2 configuration with WebRTC's default values. */
expect fun createAgc2Config(): Agc2Config

/** Creates the input volume controller configuration with WebRTC's defaults. */
expect fun createAgc2InputVolumeConfig(): Agc2InputVolumeConfig

expect fun createAgc2Environment(): Agc2Environment

/**
 * Creates a buffer holding one 10 ms frame of [channels] channels at
 * [sampleRate]; both are shared by the input, the internal buffer and the
 * output of the controller.
 */
expect fun createAgc2AudioBuffer(sampleRate: Int, channels: Int): Agc2AudioBuffer

/**
 * Creates the AGC2 gain controller.
 *
 * @param sampleRate sample rate in Hz; WebRTC processes capture audio at 8000,
 *   16000, 32000 or 48000.
 * @param channels number of capture channels.
 * @param useInternalVad whether the RNN voice activity detector inside the
 *   controller produces the speech probability. Leave it `true` unless another
 *   component of the pipeline already runs a VAD on the same audio.
 * @throws IllegalStateException if [config] fails [Agc2Config.validate] or the
 *   stream configuration is not supported.
 */
expect fun createAgc2GainController(
    env: Agc2Environment,
    config: Agc2Config,
    inputVolumeConfig: Agc2InputVolumeConfig,
    sampleRate: Int,
    channels: Int,
    useInternalVad: Boolean = true
): Agc2GainController

// =========================================================================
// Common interfaces
// =========================================================================

/**
 * Configuration of the AGC2 sub-modules, mirroring
 * `AudioProcessing::Config::GainController2`.
 */
interface Agc2Config : AutoCloseable {
    /** Whether AGC2 is enabled at all. */
    var enabled: Boolean

    /** Whether the input volume controller runs. */
    var inputVolumeControllerEnabled: Boolean

    /** Whether the adaptive digital gain controller runs. */
    var adaptiveDigitalEnabled: Boolean

    /** Headroom above the adaptive digital saturation point, in dB. */
    var adaptiveDigitalHeadroomDb: Float

    /** Maximum adaptive digital gain, in dB. Must be greater than 0. */
    var adaptiveDigitalMaxGainDb: Float

    /** Adaptive digital gain at the start of a stream, in dB. */
    var adaptiveDigitalInitialGainDb: Float

    /** Adaptation rate ceiling of the adaptive digital gain, in dB per second. */
    var adaptiveDigitalMaxGainChangeDbPerSecond: Float

    /** Ceiling of the noise level the adaptive digital gain aims for, in dBFS. */
    var adaptiveDigitalMaxOutputNoiseLevelDbfs: Float

    /** Gain applied by the fixed digital controller, in dB. */
    var fixedDigitalGainDb: Float

    /** Whether WebRTC accepts this configuration. */
    fun validate(): Boolean
}

/** Configuration of the input volume controller, mirroring `InputVolumeController::Config`. */
interface Agc2InputVolumeConfig : AutoCloseable {
    /** Lowest input volume the controller may recommend. */
    var minInputVolume: Int

    /** Lowest input volume the controller may recommend in response to clipping. */
    var clippedLevelMin: Int

    /** Amount the recommended input volume drops per clipping event; in (0, 255]. */
    var clippedLevelStep: Int

    /** Fraction of clipped samples that declares a clipping event; in (0, 1). */
    var clippedRatioThreshold: Float

    /** Frames to wait after a clipping event before checking for clipping again. */
    var clippedWaitFrames: Int

    /** Whether clipping prediction feeds the recommended input volume. */
    var enableClippingPredictor: Boolean

    /** Upper bound of the speech level range that requires no volume change, in dBFS. */
    var targetRangeMaxDbfs: Int

    /** Lower bound of the speech level range that requires no volume change, in dBFS. */
    var targetRangeMinDbfs: Int

    /** Frames between two recommended input volume updates. */
    var updateInputVolumeWaitFrames: Int

    /** Speech probability below which a frame counts as silence; in [0, 1]. */
    var speechProbabilityThreshold: Float

    /** Minimum ratio of speech frames for a volume update to be allowed; in [0, 1]. */
    var speechRatioThreshold: Float
}

/** Field trials the processing modules read their configuration from. */
interface Agc2Environment : AutoCloseable

/**
 * One 10 ms frame of deinterleaved capture audio.
 *
 * Samples are at WebRTC's FloatS16 scale: `[-32768, 32768]` maps to full scale.
 */
interface Agc2AudioBuffer : AutoCloseable {
    val numChannels: Int
    val samplesPerChannel: Int

    /**
     * Writes the first [samplesPerChannel] samples of [data] to [channel].
     *
     * @throws IllegalArgumentException if [channel] is not in
     *   `0 until numChannels`, or if [data] holds fewer samples than the frame.
     */
    fun writeChannel(channel: Int, data: FloatArray)

    /**
     * Reads [channel] as a new array of [samplesPerChannel] samples.
     *
     * @throws IllegalArgumentException if [channel] is not in
     *   `0 until numChannels`.
     */
    fun readChannel(channel: Int): FloatArray
}

/**
 * AGC2: brings capture audio to the target level with the input volume
 * controller, the adaptive digital controller, the fixed digital controller and
 * a limiter.
 */
interface Agc2GainController : AutoCloseable {
    /** Sets the fixed digital gain, in dB. */
    fun setFixedGainDb(gainDb: Float)

    /** Tells the input volume controller whether the capture output is used. */
    fun setCaptureOutputUsed(captureOutputUsed: Boolean)

    /**
     * Analyzes a frame before [process] so that clipping detection and
     * prediction run before any digital processing (e.g. echo cancellation).
     *
     * @param appliedInputVolume input volume currently applied by the caller,
     *   in [0, 255]; values outside the range are clamped.
     */
    fun analyze(appliedInputVolume: Int, buffer: Agc2AudioBuffer)

    /**
     * Updates the recommended input volume, applies the adaptive and the fixed
     * digital gains and runs the limiter on [buffer] in place.
     *
     * @param inputVolumeChanged whether the input volume changed since the
     *   previous call; pass `false` when unknown.
     */
    fun process(inputVolumeChanged: Boolean, buffer: Agc2AudioBuffer)

    /**
     * Input volume recommended by the input volume controller, or `null` when
     * there is none. Set by [analyze] and [process]; `null` while the input
     * volume controller is disabled.
     */
    val recommendedInputVolume: Int?

    /** CPU features the implementation dispatches to. */
    val cpuFeatures: Agc2CpuFeatures
}

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

package cn.enaium.webrtc.agc2.examples.basic

import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.enaium.webrtc.agc2.Agc2AudioBuffer
import cn.enaium.webrtc.agc2.Agc2Config
import cn.enaium.webrtc.agc2.Agc2Environment
import cn.enaium.webrtc.agc2.Agc2GainController
import cn.enaium.webrtc.agc2.Agc2InputVolumeConfig
import cn.enaium.webrtc.agc2.createAgc2AudioBuffer
import cn.enaium.webrtc.agc2.createAgc2Config
import cn.enaium.webrtc.agc2.createAgc2Environment
import cn.enaium.webrtc.agc2.createAgc2GainController
import cn.enaium.webrtc.agc2.createAgc2InputVolumeConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val SAMPLE_RATE = 16000
private const val CHANNELS = 1
private const val FRAME_SAMPLES = 160
private const val FULL_SCALE = 32768f

/**
 * Drives the AGC2 gain controller with synthetic capture frames and asserts on
 * the levels a consumer observes: what leaves [Agc2GainController.process] and
 * what [Agc2GainController.recommendedInputVolume] reports.
 *
 * The example ships no audio fixtures, so both signals are synthesized: the
 * test then measures the controller rather than a recording.
 */
@RunWith(AndroidJUnit4::class)
class Agc2GainControlTest {

    @Test
    fun adaptiveDigitalGainRaisesQuietSpeechTowardsTheTargetLevel() {
        withController { env, config, inputVolumeConfig ->
            config.enabled = true
            config.adaptiveDigitalEnabled = true

            withGainController(env, config, inputVolumeConfig, useInternalVad = true) { gc ->
                withBuffer { buffer ->
                    // 5 s, long enough for the adaptive gain to ramp up from its
                    // initial value towards the noise-limited ceiling.
                    val frames = 500
                    val input = speechLike(peak = 512f, samples = frames * FRAME_SAMPLES)

                    val output = processFrames(gc, buffer, input)

                    // The gain ramps up over the run, so the first and the last
                    // second show the whole adaptation.
                    val firstSecond = output.copyOfRange(0, 100 * FRAME_SAMPLES)
                    val lastSecond = output.copyOfRange(
                        output.size - 100 * FRAME_SAMPLES,
                        output.size,
                    )
                    val firstSecondRms = rmsDbfs(firstSecond)
                    val lastSecondRms = rmsDbfs(lastSecond)

                    assertTrue(
                        "adaptive digital gain peaked at ${peakDbfs(output)} dBFS",
                        peakDbfs(output) <= 0.0,
                    )
                    assertTrue(
                        "adaptive digital gain raised the level only from $firstSecondRms " +
                            "to $lastSecondRms dBFS",
                        lastSecondRms - firstSecondRms > 3.0,
                    )
                }
            }
        }
    }

    @Test
    fun sustainedClippingLowersTheRecommendedInputVolumeInSteps() {
        withController { env, config, inputVolumeConfig ->
            config.enabled = true
            config.inputVolumeControllerEnabled = true

            withGainController(env, config, inputVolumeConfig, useInternalVad = false) { gc ->
                withBuffer { buffer ->
                    // The controller waits clipped_wait_frames (300 by default)
                    // before it reacts again, so the run covers several steps.
                    val frames = 800
                    val input = clippedSine(1000f, frames * FRAME_SAMPLES)

                    // The caller applies the recommended input volume, exactly
                    // as an audio HAL integration would.
                    var appliedInputVolume = 255
                    val trace = mutableListOf(appliedInputVolume)
                    for (f in 0 until frames) {
                        writeFrame(buffer, input, f * FRAME_SAMPLES)
                        gc.analyze(appliedInputVolume, buffer)
                        gc.process(inputVolumeChanged = false, buffer = buffer)
                        gc.recommendedInputVolume?.let { appliedInputVolume = it }
                        trace += appliedInputVolume
                    }

                    val lowest = trace.min()
                    assertTrue(
                        "clipping left the recommended input volume at ${trace.last()}",
                        trace.last() < 255,
                    )
                    assertTrue(
                        "recommended input volume dropped to $lowest, below the " +
                            "configured minimum ${inputVolumeConfig.minInputVolume}",
                        lowest >= inputVolumeConfig.minInputVolume,
                    )
                    assertEquals(
                        "recommended input volume dropped by ${255 - lowest}, which is " +
                            "not a multiple of the configured step " +
                            inputVolumeConfig.clippedLevelStep,
                        0L,
                        ((255 - lowest) % inputVolumeConfig.clippedLevelStep).toLong(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Harness
// ---------------------------------------------------------------------------

private fun withController(
    block: (Agc2Environment, Agc2Config, Agc2InputVolumeConfig) -> Unit,
) {
    createAgc2Environment().use { env ->
        createAgc2Config().use { config ->
            createAgc2InputVolumeConfig().use { inputVolumeConfig ->
                block(env, config, inputVolumeConfig)
            }
        }
    }
}

private fun withGainController(
    env: Agc2Environment,
    config: Agc2Config,
    inputVolumeConfig: Agc2InputVolumeConfig,
    useInternalVad: Boolean,
    block: (Agc2GainController) -> Unit,
) {
    createAgc2GainController(
        env = env,
        config = config,
        inputVolumeConfig = inputVolumeConfig,
        sampleRate = SAMPLE_RATE,
        channels = CHANNELS,
        useInternalVad = useInternalVad,
    ).use(block)
}

private fun withBuffer(block: (Agc2AudioBuffer) -> Unit) {
    createAgc2AudioBuffer(SAMPLE_RATE, CHANNELS).use(block)
}

// ---------------------------------------------------------------------------
// Signal helpers (FloatS16 scale: +/-32768 corresponds to 0 dBFS)
// ---------------------------------------------------------------------------

/** A sine wave driven into hard clipping, so that samples sit at full scale. */
private fun clippedSine(frequencyHz: Float, samples: Int): FloatArray =
    FloatArray(samples) { i ->
        val value = (2.0 * FULL_SCALE * sin(2.0 * PI * frequencyHz * i / SAMPLE_RATE)).toFloat()
        value.coerceIn(-FULL_SCALE, FULL_SCALE - 1f)
    }

/** A voiced-speech-like signal: a harmonic pulse train under a syllable envelope. */
private fun speechLike(peak: Float, samples: Int): FloatArray {
    val harmonics = floatArrayOf(1f, 0.7f, 0.5f, 0.4f, 0.35f, 0.3f, 0.25f, 0.2f, 0.15f)
    return FloatArray(samples) { i ->
        val t = i.toDouble() / SAMPLE_RATE
        var value = 0.0
        for (h in harmonics.indices) {
            value += harmonics[h] * sin(2.0 * PI * 120.0 * (h + 1) * t)
        }
        val envelope = 0.5 * (1.0 - cos(2.0 * PI * 3.0 * t))
        (value * envelope * peak / 2.5f).toFloat()
    }
}

// ---------------------------------------------------------------------------
// Level helpers
// ---------------------------------------------------------------------------

private fun rmsDbfs(samples: FloatArray): Double {
    var sum = 0.0
    for (sample in samples) {
        sum += sample.toDouble() * sample
    }
    val rms = sqrt(sum / samples.size)
    return 20.0 * log10(max(rms, 1e-10) / FULL_SCALE)
}

private fun peakDbfs(samples: FloatArray): Double {
    var peak = 0.0
    for (sample in samples) {
        peak = max(peak, abs(sample.toDouble()))
    }
    return 20.0 * log10(max(peak, 1e-10) / FULL_SCALE)
}

// ---------------------------------------------------------------------------
// Frame plumbing
// ---------------------------------------------------------------------------

private fun writeFrame(buffer: Agc2AudioBuffer, source: FloatArray, offset: Int) {
    val frame = FloatArray(FRAME_SAMPLES)
    source.copyInto(frame, 0, offset, offset + FRAME_SAMPLES)
    buffer.writeChannel(0, frame)
}

/**
 * Feeds [input] frame by frame in the demo's call order, analyze before
 * process, and returns the processed audio.
 */
private fun processFrames(
    gc: Agc2GainController,
    buffer: Agc2AudioBuffer,
    input: FloatArray,
): FloatArray {
    val output = FloatArray(input.size)
    var offset = 0
    while (offset + FRAME_SAMPLES <= input.size) {
        writeFrame(buffer, input, offset)
        gc.analyze(255, buffer)
        gc.process(inputVolumeChanged = false, buffer = buffer)
        buffer.readChannel(0).copyInto(output, offset)
        offset += FRAME_SAMPLES
    }
    return output
}

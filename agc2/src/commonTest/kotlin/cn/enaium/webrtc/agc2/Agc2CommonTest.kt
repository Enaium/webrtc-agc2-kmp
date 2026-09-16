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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Agc2CommonTest {

    @Test
    fun versionComesFromTheNativeLibrary() {
        assertTrue(
            agc2Version.startsWith("WebRTC AGC2"),
            "agc2Version = $agc2Version",
        )
    }

    @Test
    fun audioBufferReportsTheFrameGeometry() {
        createAgc2AudioBuffer(16000, 1).use { mono ->
            assertEquals(1, mono.numChannels)
            assertEquals(160, mono.samplesPerChannel)
        }
        createAgc2AudioBuffer(48000, 2).use { stereo ->
            assertEquals(2, stereo.numChannels)
            assertEquals(480, stereo.samplesPerChannel)
        }
    }

    @Test
    fun audioBufferKeepsChannelsApart() {
        createAgc2AudioBuffer(16000, 2).use { buffer ->
            val left = FloatArray(160) { it.toFloat() }
            val right = FloatArray(160) { -it.toFloat() }
            buffer.writeChannel(0, left)
            buffer.writeChannel(1, right)

            assertTrue(left.contentEquals(buffer.readChannel(0)))
            assertTrue(right.contentEquals(buffer.readChannel(1)))
        }
    }

    @Test
    fun audioBufferRejectsOutOfRangeChannelsAndShortFrames() {
        createAgc2AudioBuffer(16000, 1).use { buffer ->
            assertFailsWith<IllegalArgumentException> {
                buffer.readChannel(1)
            }
            assertFailsWith<IllegalArgumentException> {
                buffer.writeChannel(0, FloatArray(159))
            }
        }
    }

    @Test
    fun configValuesRoundTrip() {
        createAgc2Config().use { config ->
            config.enabled = true
            config.inputVolumeControllerEnabled = true
            config.adaptiveDigitalEnabled = true
            config.adaptiveDigitalHeadroomDb = 4.5f
            config.adaptiveDigitalMaxGainDb = 42f
            config.adaptiveDigitalInitialGainDb = 12.5f
            config.adaptiveDigitalMaxGainChangeDbPerSecond = 7.5f
            config.adaptiveDigitalMaxOutputNoiseLevelDbfs = -46f
            config.fixedDigitalGainDb = 3.5f

            assertTrue(config.enabled)
            assertTrue(config.inputVolumeControllerEnabled)
            assertTrue(config.adaptiveDigitalEnabled)
            assertEquals(4.5f, config.adaptiveDigitalHeadroomDb)
            assertEquals(42f, config.adaptiveDigitalMaxGainDb)
            assertEquals(12.5f, config.adaptiveDigitalInitialGainDb)
            assertEquals(7.5f, config.adaptiveDigitalMaxGainChangeDbPerSecond)
            assertEquals(-46f, config.adaptiveDigitalMaxOutputNoiseLevelDbfs)
            assertEquals(3.5f, config.fixedDigitalGainDb)
            assertTrue(config.validate())
        }
    }

    @Test
    fun inputVolumeConfigValuesRoundTrip() {
        createAgc2InputVolumeConfig().use { config ->
            config.minInputVolume = 30
            config.clippedLevelMin = 65
            config.clippedLevelStep = 11
            config.clippedRatioThreshold = 0.2f
            config.clippedWaitFrames = 250
            config.enableClippingPredictor = false
            config.targetRangeMaxDbfs = -10
            config.targetRangeMinDbfs = -45
            config.updateInputVolumeWaitFrames = 80
            config.speechProbabilityThreshold = 0.6f
            config.speechRatioThreshold = 0.5f

            assertEquals(30, config.minInputVolume)
            assertEquals(65, config.clippedLevelMin)
            assertEquals(11, config.clippedLevelStep)
            assertEquals(0.2f, config.clippedRatioThreshold)
            assertEquals(250, config.clippedWaitFrames)
            assertFalse(config.enableClippingPredictor)
            assertEquals(-10, config.targetRangeMaxDbfs)
            assertEquals(-45, config.targetRangeMinDbfs)
            assertEquals(80, config.updateInputVolumeWaitFrames)
            assertEquals(0.6f, config.speechProbabilityThreshold)
            assertEquals(0.5f, config.speechRatioThreshold)
        }
    }

    @Test
    fun invalidConfigIsReportedAndRejected() {
        createAgc2Config().use { config ->
            // Validate() requires the fixed digital gain to be below 50 dB.
            config.fixedDigitalGainDb = 50f
            assertFalse(config.validate())

            createAgc2Environment().use { env ->
                createAgc2InputVolumeConfig().use { inputVolumeConfig ->
                    val failure = assertFailsWith<IllegalStateException> {
                        createAgc2GainController(env, config, inputVolumeConfig, 16000, 1)
                    }
                    assertTrue(
                        failure.message?.contains("configuration is invalid") == true,
                        "message = ${failure.message}",
                    )
                }
            }
        }
    }

    @Test
    fun unsupportedStreamConfigurationIsRejected() {
        createAgc2Config().use { config ->
            config.enabled = true
            createAgc2Environment().use { env ->
                createAgc2InputVolumeConfig().use { inputVolumeConfig ->
                    assertFailsWith<IllegalStateException> {
                        createAgc2GainController(env, config, inputVolumeConfig, 0, 1)
                    }
                }
            }
        }
    }

    @Test
    fun cpuFeaturesAreDetected() {
        createAgc2Environment().use { env ->
            createAgc2Config().use { config ->
                config.enabled = true
                createAgc2InputVolumeConfig().use { inputVolumeConfig ->
                    createAgc2GainController(env, config, inputVolumeConfig, 16000, 1).use { gc ->
                        val features = gc.cpuFeatures
                        assertTrue(
                            features.sse2 || features.avx2 || features.neon,
                            "no CPU feature detected: $features",
                        )
                    }
                }
            }
        }
    }

}

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

package cn.enaium.webrtc.agc2.examples.waveform

import kotlin.math.pow
import kotlin.random.Random

/**
 * Where the capture frame comes from: what AGC2 processes.
 *
 * The three synthesised sources *replace* the captured frame, so the example
 * demonstrates the gain controller on a machine with no microphone at all -
 * including a headless run.
 */
enum class SignalSource(val label: String) {
    /** The default capture device, scaled on the way in. */
    MICROPHONE("Microphone (capture device)"),

    /**
     * A voiced pulse train gated by a syllable envelope: the levels move the way
     * speech does, which is what the adaptive digital controller follows.
     */
    SPEECH("Synthetic speech"),

    /**
     * Flat spectrum at the level slider's setting: AGC2 raises a quiet noise
     * floor towards its target without ever finding speech in it.
     */
    NOISE("White noise"),

    /**
     * Digital silence: nothing to amplify, which is what the limiter and the
     * level readout look like when there is no input.
     */
    SILENCE("Silence");

    /** Whether the frame is synthesised rather than captured. */
    val isGenerated: Boolean get() = this != MICROPHONE

    /** Whether the source needs a capture device at all. */
    val needsCapture: Boolean get() = this == MICROPHONE
}

/**
 * Generates the capture frame of the three synthesised [SignalSource]s.
 *
 * The generator is seeded, so a given source and level always produce the same
 * samples - which makes screenshots and measurements comparable.
 */
class SignalGenerator(seed: Int = 0x5EED) {

    private val random = Random(seed)

    /** Position in the current pitch period, in `0..1`. */
    private var pitchPhase = 0.0

    /** Position in the current syllable, in `0..1`. */
    private var syllablePhase = 0.0

    /** Level the syllable envelope is heading for. */
    private var syllableTarget = 0f

    /** Current syllable envelope value, in `0..1`. */
    private var envelope = 0f

    /** One pole state of the glottal roll-off. */
    private var voiced = 0f

    /** Fills the first [count] samples of [destination] with [source] at [amplitude]. */
    fun generate(destination: FloatArray, count: Int, source: SignalSource, amplitude: Float) {
        when (source) {
            SignalSource.SPEECH -> speech(destination, count, amplitude)

            SignalSource.NOISE -> {
                for (i in 0 until count) {
                    destination[i] = (random.nextFloat() * 2f - 1f) * amplitude
                }
            }

            // The microphone is captured, not generated, and silence is the same
            // zero frame: the pipeline only asks for the synthesised sources.
            SignalSource.MICROPHONE, SignalSource.SILENCE -> destination.fill(0f, 0, count)
        }
    }

    /**
     * A voiced pulse train: a sawtooth at [PITCH_HZ] - one glottal pulse per
     * period, its harmonics falling off as `1/n` - gated by a syllable envelope
     * that moves to a new level several times a second. The moves are what AGC2
     * has to follow; a steady tone would settle at one gain and stay there.
     */
    private fun speech(destination: FloatArray, count: Int, amplitude: Float) {
        for (i in 0 until count) {
            pitchPhase += PITCH_HZ / SAMPLE_RATE
            if (pitchPhase >= 1.0) pitchPhase -= 1.0

            syllablePhase += SYLLABLE_HZ / SAMPLE_RATE
            if (syllablePhase >= 1.0) {
                syllablePhase -= 1.0
                syllableTarget = MIN_SYLLABLE + random.nextFloat() * (1f - MIN_SYLLABLE)
            }
            // One pole approach to the target: the onsets and offsets of real
            // speech are a few milliseconds long, not steps.
            envelope += (syllableTarget - envelope) * ENVELOPE_RATE

            // The sawtooth is the pulse train; the one pole takes the edge off
            // it, which is both the glottal roll-off and what keeps the corners
            // from aliasing at 48 kHz.
            voiced += ((2f * pitchPhase.toFloat() - 1f) - voiced) * VOICED_RATE
            destination[i] = voiced * envelope * amplitude
        }
    }

    private companion object {
        /** Fundamental of the voiced source: one pulse per period. */
        const val PITCH_HZ = 120.0

        /** Syllables per second: how fast the envelope moves. */
        const val SYLLABLE_HZ = 4.0

        /** Quietest syllable, so the envelope is not all one level. */
        const val MIN_SYLLABLE = 0.25f

        /** Per sample approach of the envelope to its target: roughly 8 ms. */
        const val ENVELOPE_RATE = 0.0025f

        /** Per sample roll-off of the pulse train: roughly 1 kHz. */
        const val VOICED_RATE = 0.12f
    }
}

/** The linear amplitude of [db] dBFS. */
fun dbfsToAmplitude(db: Float): Float = 10f.pow(db / 20f)
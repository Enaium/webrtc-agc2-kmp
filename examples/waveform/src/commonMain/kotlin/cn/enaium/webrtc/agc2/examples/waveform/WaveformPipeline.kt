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

import cn.enaium.audio.AudioFormat

/**
 * What [WaveformWindow] draws and drives.
 *
 * The window is the example's whole UI and lives in `commonMain`, but AGC2 -
 * and with it [Agc2Pipeline] - only exists for the targets that can link the
 * native library. The interface is what lets a backend without AGC2 (the
 * wasm/js build) reuse the same UI: it supplies the scopes, the histories and
 * the transport, and the plots cannot tell the difference.
 */
interface WaveformPipeline {

    /** Capture samples in `-1..1`, the input plot. */
    val inputScope: WaveformScope

    /** Gain controlled samples, aligned with [inputScope], the output plot. */
    val outputScope: WaveformScope

    /** Input level per frame, in dBFS. */
    val inputHistory: HistoryRing

    /** Output level per frame, in dBFS. */
    val outputHistory: HistoryRing

    /** Gain applied per frame, in dB. */
    val gainHistory: HistoryRing

    /** Format requested from the devices. */
    val format: AudioFormat

    /** Samples per processed frame. */
    val frameSize: Int

    /** Backend serving the devices, e.g. `"Core Audio"`. */
    val systemName: String

    /** Whether the capture goes through AGC2. */
    var agcEnabled: Boolean

    /** Where the capture frame comes from. */
    var signalSource: SignalSource

    /** Level of the synthesised source, in dBFS. */
    var sourceLevelDb: Float

    /** Level the output is played back with, in dBFS. */
    var playbackLevelDb: Float

    /** Whether what the controller produced is played back. */
    var monitoring: Boolean

    /** Gain of the fixed digital controller, in dB. */
    var fixedDigitalGainDb: Float

    /** Input volume the caller applies on the audio HAL, in `0..255`. */
    var appliedInputVolume: Int

    /** Whether the pump applies the recommended input volume itself. */
    var autoApplyRecommendedVolume: Boolean

    val adaptiveDigitalEnabled: Boolean

    val inputVolumeControllerEnabled: Boolean

    val useInternalVad: Boolean

    /** `true` between [startRecording] and [pauseRecording]. */
    val recording: Boolean

    /** `true` while produced audio is queued for the playback device. */
    val playing: Boolean

    /** `true` while a recorded window is being replayed. */
    val replaying: Boolean

    val captureDeviceName: String

    val playbackDeviceName: String

    val captureError: String?

    val playbackError: String?

    /** Input level of the last processed frame, in dBFS. */
    val inputDbfs: Float

    /** Output level of the last processed frame, in dBFS. */
    val outputDbfs: Float

    /** Gain the last processed frame was played back with, in dB. */
    val appliedGainDb: Float

    /** Latest recommended input volume, or `null` when there is none. */
    val recommendedInputVolume: Int?

    /** Frames processed since the last [startRecording]. */
    val processedFrames: Long

    /** Frames the playback path dropped. */
    val droppedPlaybackFrames: Long

    /** Captured frames dropped because the pump was behind. */
    val droppedCaptureFrames: Long

    /** Starts recording: counters and plots start from scratch. */
    fun startRecording()

    /** Stops moving audio and releases the capture device. */
    fun pauseRecording()

    /** Plays the newest [samples] of the output back once. */
    fun replayWindow(samples: Int)

    fun setAdaptiveDigitalEnabled(enabled: Boolean)

    fun setInputVolumeControllerEnabled(enabled: Boolean)

    fun setUseInternalVad(enabled: Boolean)

    /** Moves audio: called once per UI frame. */
    fun pump()
}

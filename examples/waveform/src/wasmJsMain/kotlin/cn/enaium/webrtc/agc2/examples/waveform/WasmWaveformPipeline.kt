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

import cn.enaium.audio.AudioBuffer
import cn.enaium.audio.AudioException
import cn.enaium.audio.AudioFormat
import cn.enaium.audio.AudioInput
import cn.enaium.audio.AudioOutput
import cn.enaium.audio.AudioSystem
import cn.enaium.audio.audioSystem
import cn.enaium.sdl.SDL
import cn.enaium.webrtc.agc2.Agc2GainController
import cn.enaium.webrtc.agc2.agc2Version
import cn.enaium.webrtc.agc2.createAgc2AudioBuffer
import cn.enaium.webrtc.agc2.createAgc2Config
import cn.enaium.webrtc.agc2.createAgc2Environment
import cn.enaium.webrtc.agc2.createAgc2GainController
import cn.enaium.webrtc.agc2.createAgc2InputVolumeConfig
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/** Samples per frame (480 = 10 ms at 48 kHz). */
private const val WASM_FRAME_SAMPLES = 48_000 / 100

/** Milliseconds of audio one generated frame covers. */
private const val WASM_FRAME_MILLIS = 10

/** Frames a single pump generates at most, so a stalled window cannot burst. */
private const val MAX_GENERATED_FRAMES = 25

/** Capture frames the device buffer holds, giving one slow UI frame some slack. */
private const val CAPTURE_BUFFER_FRAMES = 4

/** Playback frames queued ahead of the device. */
private const val PLAYBACK_CHUNK_FRAMES = 4

/** Channels the browser build captures, processes and plays. */
private const val WASM_CHANNELS = 1

/** Scale WebRTC's FloatS16 frames are in: full scale is 32768, not 1. */
private const val WASM_AGC_SCALE = 32_768f

/**
 * The waveform pipeline as a browser can run it.
 *
 * The same AGC2 controller the native targets link through cinterop runs here:
 * the browser build links the C API compiled to wasm by Emscripten (the
 * @enaium/webrtc-agc2-kmp npm package, initialized by the page) and the js /
 * wasmJs bindings call it. Only the device handling differs: Web Audio cannot
 * block, so the capture and playback streams are polled from the render loop
 * instead of running on a device thread.
 */
class WasmWaveformPipeline(
    windowSamples: Int,
) : WaveformPipeline {

    private val historyFrames: Int = maxOf(windowSamples / WASM_FRAME_SAMPLES, 1)

    override val inputScope = WaveformScope(windowSamples)

    override val outputScope = WaveformScope(windowSamples)

    override val inputHistory = HistoryRing(historyFrames)

    override val outputHistory = HistoryRing(historyFrames)

    override val gainHistory = HistoryRing(historyFrames)

    override val format: AudioFormat = AudioFormat.SPEECH

    override val frameSize: Int = WASM_FRAME_SAMPLES

    private val system: AudioSystem = audioSystem()

    override val systemName: String = system.name

    override var agcEnabled: Boolean = true

    override var signalSource: SignalSource = SignalSource.SPEECH
        set(value) {
            if (field == value) return
            field = value
            if (recording) {
                if (!value.needsCapture) closeCapture()
            }
            nextGeneratedFrame = 0uL
        }

    override var sourceLevelDb: Float = DEFAULT_SOURCE_DB

    override var playbackLevelDb: Float = DEFAULT_PLAYBACK_DB

    override var monitoring: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) closeOutput()
        }

    /**
     * Gain of the fixed digital controller, in dB. Unlike AGC2's, this one is
     * not estimated per frame: whatever the UI sets is applied to the capture.
     */
    override var fixedDigitalGainDb: Float = 0f

    override var adaptiveDigitalEnabled: Boolean = true
    override var inputVolumeControllerEnabled: Boolean = true
    override var useInternalVad: Boolean = true
    override var appliedInputVolume: Int = 255
    override var autoApplyRecommendedVolume: Boolean = false

    override var recording: Boolean = false
        private set

    override var replaying: Boolean = false
        private set

    override val playing: Boolean get() = output != null

    override var captureDeviceName: String = "system default"
        private set

    override var playbackDeviceName: String = "system default"
        private set

    override var captureError: String? = null
        private set

    override var playbackError: String? = null
        private set

    override var inputDbfs: Float = Float.NEGATIVE_INFINITY
        private set

    override var outputDbfs: Float = Float.NEGATIVE_INFINITY
        private set

    override var appliedGainDb: Float = 0f
        private set

    override var recommendedInputVolume: Int? = null
        private set

    override var processedFrames: Long = 0L
        private set

    override var droppedPlaybackFrames: Long = 0L
        private set

    override var droppedCaptureFrames: Long = 0L
        private set

    private val generator = SignalGenerator()

    private val generated = FloatArray(WASM_FRAME_SAMPLES)

    private val captureFrame = FloatArray(WASM_FRAME_SAMPLES)

    private val outputFrame = FloatArray(WASM_FRAME_SAMPLES)

    private val captured = FloatArray(WASM_FRAME_SAMPLES * CAPTURE_BUFFER_FRAMES)

    private val captureBuffer = AudioBuffer(format, WASM_FRAME_SAMPLES * CAPTURE_BUFFER_FRAMES)

    private val playbackBuffer = AudioBuffer(format, PLAYBACK_CHUNK_FRAMES)

    private val playbackChunk = FloatArray(PLAYBACK_CHUNK_FRAMES)

    private val replay = FloatArray(inputScope.capacity)

    private var replayRead = 0
    private var replayLength = 0


    private var input: AudioInput? = null

    private var output: AudioOutput? = null

    private var closed = false

    private var nextGeneratedFrame = 0uL

    // ==================== AGC2 ====================

    private val agc2Config = createAgc2Config().apply {
        // The switch is app level: it decides whether the controller runs at
        // all, so the configuration itself always says enabled.
        enabled = true
        adaptiveDigitalEnabled = this@WasmWaveformPipeline.adaptiveDigitalEnabled
        inputVolumeControllerEnabled = this@WasmWaveformPipeline.inputVolumeControllerEnabled
        fixedDigitalGainDb = this@WasmWaveformPipeline.fixedDigitalGainDb
    }

    private val agc2InputVolumeConfig = createAgc2InputVolumeConfig()

    private val agc2Environment = createAgc2Environment()

    private val agc2Buffer = createAgc2AudioBuffer(SAMPLE_RATE, WASM_CHANNELS)

    private var appliedFixedGainDb = 0f

    private var lastAppliedInputVolume = -1

    private var controller: Agc2GainController = buildController()

    init {
        report("AGC2 $agc2Version via $systemName, $format frame $WASM_FRAME_SAMPLES samples")
    }

    private fun buildController(): Agc2GainController = createAgc2GainController(
        env = agc2Environment,
        config = agc2Config,
        inputVolumeConfig = agc2InputVolumeConfig,
        sampleRate = SAMPLE_RATE,
        channels = WASM_CHANNELS,
        useInternalVad = useInternalVad,
    )

    private fun rebuildController() {
        val previous = controller
        controller = buildController()
        previous.close()
        report(
            "controller rebuilt: adaptive $adaptiveDigitalEnabled, " +
                "input volume controller $inputVolumeControllerEnabled, internal VAD $useInternalVad",
        )
    }

    private fun applyControls() {
        if (fixedDigitalGainDb != appliedFixedGainDb) {
            appliedFixedGainDb = fixedDigitalGainDb
            controller.setFixedGainDb(fixedDigitalGainDb)
        }
    }

    override fun startRecording() {
        if (closed || recording) return
        recording = true
        report("recording $signalSource")
    }

    override fun pauseRecording() {
        if (!recording) return
        recording = false
        closeCapture()
        report("recording paused")
    }

    override fun replayWindow(samples: Int) {
        if (closed) return
        val count = minOf(samples, outputScope.filled, replay.size)
        if (count <= 0) return
        val recorded = FloatArray(count)
        outputScope.snapshot(recorded)
        val gain = dbfsToAmplitude(playbackLevelDb)
        for (i in 0 until count) replay[i] = recorded[i] * gain
        replayRead = 0
        replayLength = count
        replaying = true
        // A replay needs the output even when monitoring is off.
        if (!monitoring) monitoring = true
        val stream = output ?: openPlaybackStream()
        output = stream
        if (stream == null) {
            replayLength = 0
            replaying = false
            return
        }
        report("replaying ${count / WASM_FRAME_SAMPLES} frames")
    }

    override fun setAdaptiveDigitalEnabled(enabled: Boolean) {
        if (adaptiveDigitalEnabled == enabled) return
        adaptiveDigitalEnabled = enabled
        agc2Config.adaptiveDigitalEnabled = enabled
        rebuildController()
    }

    override fun setInputVolumeControllerEnabled(enabled: Boolean) {
        if (inputVolumeControllerEnabled == enabled) return
        inputVolumeControllerEnabled = enabled
        agc2Config.inputVolumeControllerEnabled = enabled
        rebuildController()
    }

    override fun setUseInternalVad(enabled: Boolean) {
        if (useInternalVad == enabled) return
        useInternalVad = enabled
        rebuildController()
    }

    override fun pump() {
        if (closed || !recording) return
        if (replaying) {
            pumpReplay()
            return
        }
        if (signalSource.needsCapture) {
            val stream = input ?: openCaptureStream()
            if (stream != null) {
                input = stream
                drainCapture(stream)
            }
        } else {
            generate()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        recording = false
        closeCapture()
        closeOutput()
        controller.close()
        agc2Buffer.close()
        agc2Environment.close()
        agc2InputVolumeConfig.close()
        agc2Config.close()
        system.close()
    }

    private fun generate() {
        val now = SDL.getTicks()
        if (nextGeneratedFrame == 0uL) nextGeneratedFrame = now

        var budget = MAX_GENERATED_FRAMES
        while (budget-- > 0 && now >= nextGeneratedFrame) {
            generator.generate(generated, WASM_FRAME_SAMPLES, signalSource, dbfsToAmplitude(sourceLevelDb))
            processFrame(generated)
            nextGeneratedFrame += WASM_FRAME_MILLIS.toULong()
        }
        // A window that was not drawing for a while must not turn its backlog
        // into a burst of audio.
        if (nextGeneratedFrame + WASM_FRAME_MILLIS.toULong() < now) nextGeneratedFrame = now
    }

    private fun drainCapture(stream: AudioInput) {
        while (true) {
            val frames = stream.readNonBlocking(captureBuffer)
            if (frames <= 0) break
            val values = captureBuffer.toFloats(captured, frames)
            var offset = 0
            while (offset + WASM_FRAME_SAMPLES <= values) {
                captured.copyInto(captureFrame, 0, offset, offset + WASM_FRAME_SAMPLES)
                processFrame(captureFrame)
                offset += WASM_FRAME_SAMPLES
            }
            if (offset < values) droppedCaptureFrames++
        }
    }

    private fun processFrame(frame: FloatArray) {
        applyControls()

        for (i in 0 until WASM_FRAME_SAMPLES) captureFrame[i] = frame[i] * WASM_AGC_SCALE

        val outputSamples: FloatArray
        if (agcEnabled) {
            agc2Buffer.writeChannel(0, captureFrame)
            // There is no audio HAL here either, so "the input volume changed
            // since the previous call" is what the slider - or the
            // recommendation fed back into it - did since the last frame.
            val volume = appliedInputVolume.coerceIn(0, 255)
            val changed = volume != lastAppliedInputVolume
            lastAppliedInputVolume = volume
            controller.analyze(volume, agc2Buffer)
            controller.process(changed, agc2Buffer)
            outputSamples = agc2Buffer.readChannel(0)

            val recommended = controller.recommendedInputVolume
            recommendedInputVolume = recommended
            if (autoApplyRecommendedVolume && recommended != null) {
                appliedInputVolume = recommended.coerceIn(0, 255)
            }
        } else {
            // Bypassed: what the capture produced is what gets played.
            outputSamples = captureFrame
            recommendedInputVolume = null
        }

        for (i in 0 until WASM_FRAME_SAMPLES) outputFrame[i] = outputSamples[i] / WASM_AGC_SCALE

        inputScope.append(frame, WASM_FRAME_SAMPLES)
        outputScope.append(outputFrame, WASM_FRAME_SAMPLES)

        inputDbfs = rmsDbfs(captureFrame)
        outputDbfs = rmsDbfs(outputSamples)
        appliedGainDb = if (inputDbfs.isFinite()) outputDbfs - inputDbfs else 0f
        inputHistory.append(inputDbfs)
        outputHistory.append(outputDbfs)
        gainHistory.append(appliedGainDb)

        if (monitoring) queuePlayback()
        processedFrames++
    }

    private fun queuePlayback() {
        val stream = output ?: openPlaybackStream().also { output = it } ?: return
        val gain = dbfsToAmplitude(playbackLevelDb)
        for (i in 0 until WASM_FRAME_SAMPLES) {
            captureFrame[i] = outputFrame[i] * gain
        }
        playbackBuffer.putFloats(captureFrame, WASM_FRAME_SAMPLES)
        if (stream.writeNonBlocking(playbackBuffer) < 0) droppedPlaybackFrames++
    }

    private fun pumpReplay() {
        val stream = output ?: run {
            replaying = false
            return
        }
        while (replayRead < replayLength) {
            val count = minOf(PLAYBACK_CHUNK_FRAMES, replayLength - replayRead)
            replay.copyInto(playbackChunk, 0, replayRead, replayRead + count)
            playbackBuffer.putFloats(playbackChunk, count)
            if (stream.writeNonBlocking(playbackBuffer) <= 0) break
            replayRead += count
        }
        if (replayRead >= replayLength) {
            replaying = false
            replayLength = 0
            report("replay done")
        }
    }

    private fun openCaptureStream(): AudioInput? = try {
        val stream = system.openInput(format, bufferFrames = WASM_FRAME_SAMPLES * CAPTURE_BUFFER_FRAMES)
        if (stream.format != format) {
            stream.close()
            captureError = "device opened as ${stream.format}, expected $format"
            null
        } else {
            stream.start()
            captureDeviceName = stream.device?.name ?: "system default"
            captureError = null
            report("capturing $captureDeviceName [$systemName] $format")
            stream
        }
    } catch (e: AudioException) {
        captureError = e.message ?: "capture unavailable"
        println("capture unavailable: $captureError")
        null
    }

    private fun openPlaybackStream(): AudioOutput? = try {
        val stream = system.openOutput(format, bufferFrames = WASM_FRAME_SAMPLES * PLAYBACK_CHUNK_FRAMES)
        if (stream.format != format) {
            stream.close()
            playbackError = "device opened as ${stream.format}, expected $format"
            null
        } else {
            stream.start()
            playbackDeviceName = stream.device?.name ?: "system default"
            playbackError = null
            report("playing $playbackDeviceName [$systemName] $format")
            stream
        }
    } catch (e: AudioException) {
        playbackError = e.message ?: "playback unavailable"
        println("playback unavailable: $playbackError")
        null
    }

    private fun closeCapture() {
        input?.close()
        input = null
    }

    private fun closeOutput() {
        output?.close()
        output = null
        replaying = false
        replayLength = 0
    }

    private fun rmsDbfs(values: FloatArray): Float {
        var sum = 0.0
        for (value in values) {
            val normalized = value / WASM_AGC_SCALE
            sum += (normalized * normalized).toDouble()
        }
        val rms = sqrt(sum / values.size)
        return if (rms <= 0.0) Float.NEGATIVE_INFINITY else (20.0 * log10(rms)).toFloat()
    }

    private companion object {
        const val DEFAULT_SOURCE_DB = -50f
        const val DEFAULT_PLAYBACK_DB = 0f
    }
}

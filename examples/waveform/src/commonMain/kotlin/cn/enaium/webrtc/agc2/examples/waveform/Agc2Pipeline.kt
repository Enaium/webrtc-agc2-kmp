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
import cn.enaium.webrtc.agc2.Agc2AudioBuffer
import cn.enaium.webrtc.agc2.Agc2Config
import cn.enaium.webrtc.agc2.Agc2Environment
import cn.enaium.webrtc.agc2.Agc2GainController
import cn.enaium.webrtc.agc2.Agc2InputVolumeConfig
import cn.enaium.webrtc.agc2.agc2Version
import cn.enaium.webrtc.agc2.createAgc2AudioBuffer
import cn.enaium.webrtc.agc2.createAgc2Config
import cn.enaium.webrtc.agc2.createAgc2Environment
import cn.enaium.webrtc.agc2.createAgc2GainController
import cn.enaium.webrtc.agc2.createAgc2InputVolumeConfig
import kotlin.concurrent.Volatile
import kotlin.math.log10
import kotlin.math.sqrt

/** AGC2 processes capture audio at 48 kHz mono. */
internal const val SAMPLE_RATE = 48_000

/** Samples per AGC2 frame: 10 ms at 48 kHz, the size the buffers are built for. */
internal const val FRAME_SAMPLES = SAMPLE_RATE / 100

/** Milliseconds one frame covers, the pace a synthesised source is generated at. */
internal const val FRAME_MILLIS = 10

/** Channels the example captures, processes and plays. */
private const val CHANNELS = 1

/**
 * Frames the capture device buffers: 10 x 10 ms.
 *
 * Well above one UI frame, which is what lets the render loop pull the device
 * instead of the other way around: a slow frame is absorbed by the buffer
 * instead of dropping audio.
 */
private const val BUFFER_FRAMES = 10

/** Frames the playback queue holds before produced audio is dropped. */
private const val QUEUE_FRAMES = 10

/** Samples handed to the playback device in one non-blocking write. */
private const val PLAYBACK_CHUNK_FRAMES = 1024

/** Milliseconds between two passes of the device thread while a device is open. */
private const val DEVICE_POLL_MILLIS = 20

/** Milliseconds the device thread waits before retrying a device that failed to open. */
private const val DEVICE_RETRY_MILLIS = 250

/** AGC2 frames between two console status lines: one second of audio. */
private const val REPORT_FRAMES = 100

/**
 * Frames a single pump may generate for a synthesised source.
 *
 * The wall clock is what paces the source (one frame per [FRAME_MILLIS]); this
 * only bounds the catch-up, so a window that was not drawing for a second
 * cannot turn its backlog into a burst of audio.
 */
private const val MAX_GENERATED_FRAMES = 8

/**
 * AGC2 frames one pump processes from the capture device: the whole
 * [BUFFER_FRAMES] (100 ms) the device buffers.
 *
 * A device hands its buffer over whole, so the pump has to be able to take all
 * of it: the macOS input queue delivers 100 ms at a time, which is five frames
 * at a 20 fps render loop, and a smaller budget would drop audio the device has
 * already captured. Ten frames cost a couple of milliseconds (measured on a
 * debug native build), while the ceiling still bounds a stalled window: the
 * newest frames win and the rest is dropped, counted in
 * [droppedCaptureFrames].
 */
private const val MAX_FRAMES_PER_PUMP = 10

/**
 * WebRTC's `AudioBuffer` carries samples in the int16 range, not in `-1..1`
 * (`audio-io-kmp` and the plots use `-1..1`), so the frames are scaled on the
 * way in and out. Feeding `-1..1` floats instead makes AGC2 treat the capture
 * as silence, which keeps the gain at its initial value.
 */
private const val AGC_SCALE = 32_768f

/**
 * The audio half of the example:
 *
 *  - [pump] pulls the capture device with non-blocking reads, runs every 10 ms
 *    frame through AGC2 and queues what comes out for playback.
 *  - A synthesised [signalSource] replaces the captured frame, so the controller
 *    can be driven without a microphone at all.
 *  - The queue is pushed into the playback device with non-blocking writes, so
 *    the level AGC2 applied is audible and not just visible.
 *
 * Capture and playback are pulled from the render loop: [pump] reads with
 * non-blocking reads and writes with non-blocking writes, so no thread is
 * needed to move audio and a slow frame cannot drop it either - the devices
 * buffer [BUFFER_FRAMES] frames, far more than one UI frame.
 *
 * Opening and closing the devices is the one part of the audio path that can
 * block (a microphone permission prompt, a device that is still starting, a
 * sample rate that has to be negotiated), so it runs on [DeviceThread] instead:
 * the render loop only publishes what it wants and takes streams that are
 * already open.
 *
 * The playback differs from a recorder's transport on purpose: AGC2 is a
 * continuous gain stage rather than a gate, so what it produces is played as it
 * is produced ([monitoring]) instead of being replayed from a recorded window.
 * Record/pause still drives the capture device, which is what releases the
 * microphone.
 *
 * Playback never throttles the pipeline: a device that takes less than what
 * AGC2 produces makes [queuePlayback] drop frames (counted in
 * [droppedPlaybackFrames]) instead of holding up the capture, so the plots and
 * the level history keep moving even when nothing can be heard.
 */
class Agc2Pipeline(
    /**
     * Samples covered by one waveform window; the example sizes it for the
     * longest window its slider allows and lets the UI show a shorter part.
     */
    windowSamples: Int,
) : AutoCloseable {

    /** Frames the metric histories hold: one value per frame. */
    private val historyFrames: Int = maxOf(windowSamples / FRAME_SAMPLES, 1)

    /** Capture samples as AGC2 receives them, in `-1..1`. */
    val inputScope = WaveformScope(windowSamples)

    /** Gain controlled samples, aligned with [inputScope]. */
    val outputScope = WaveformScope(windowSamples)

    /** Input level per frame, in dBFS. */
    val inputHistory = HistoryRing(historyFrames)

    /** Output level per frame, in dBFS. */
    val outputHistory = HistoryRing(historyFrames)

    /** Gain applied per frame, in dB: the output level minus the input level. */
    val gainHistory = HistoryRing(historyFrames)

    /** Format requested from the devices: 48 kHz mono, the format AGC2 is fed. */
    val format: AudioFormat = AudioFormat.SPEECH

    /** Samples per AGC2 frame (480 = 10 ms at 48 kHz). */
    val frameSize: Int = FRAME_SAMPLES

    /** Backend serving the devices, e.g. `"Core Audio"`. */
    val systemName: String

    /** Whether the capture goes through AGC2; off plays the raw capture back. */
    var agcEnabled: Boolean = true

    /**
     * Where the capture frame comes from. [SignalSource.SPEECH] by default: the
     * gain is then seen ramping up towards its target on any machine, with or
     * without a microphone, instead of waiting for someone to talk into one.
     *
     * Switching to a source that needs the microphone opens the capture device,
     * switching away from it releases the device again.
     */
    var signalSource: SignalSource = SignalSource.SPEECH
        set(value) {
            if (field == value) return
            field = value
            if (recording) {
                captureWanted = value.needsCapture
                // Handing the stream back is what releases the microphone; the
                // device thread closes it, so the switch itself never blocks.
                if (!value.needsCapture) closeCapture()
            }
            nextGeneratedFrame = 0uL
        }

    /**
     * Level of the synthesised source in dBFS. The captured source is not
     * scaled: the microphone's own level is what AGC2 has to work with.
     */
    var sourceLevelDb: Float = DEFAULT_SOURCE_DB

    /** Level the AGC2 output is played back with, in dBFS. */
    var playbackLevelDb: Float = DEFAULT_PLAYBACK_DB

    /**
     * Whether what AGC2 produced is played back. Turning it off releases the
     * playback device and drops whatever was queued.
     */
    var monitoring: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            outputWanted = value
            if (!value) closePlayback()
        }

    /** Gain of the fixed digital controller, in dB. Live: [Agc2GainController.setFixedGainDb]. */
    var fixedDigitalGainDb: Float = 0f

    /**
     * Input volume the caller applies on the audio HAL, in `0..255`.
     *
     * The example has no HAL of its own, so this is the volume the frame is
     * reported as having been captured with - what the input volume controller
     * bases its recommendation on. Full volume by default: a zero applied
     * volume tells WebRTC the capture is muted, which is not what the example
     * starts from. When [autoApplyRecommendedVolume] is on, the pump writes the
     * last recommendation back into it.
     */
    var appliedInputVolume: Int = 255

    /** Whether the pump applies [Agc2GainController.recommendedInputVolume] itself. */
    var autoApplyRecommendedVolume: Boolean = false

    /**
     * Whether the adaptive digital controller runs. AGC2 reads this when the
     * controller is built, so the setter builds a new one (see
     * [setAdaptiveDigitalEnabled]).
     */
    var adaptiveDigitalEnabled: Boolean = true
        private set

    /** Whether the input volume controller runs. Reaches AGC2 the same way as [adaptiveDigitalEnabled]. */
    var inputVolumeControllerEnabled: Boolean = true
        private set

    /** Whether AGC2's own RNN VAD produces the speech probability. Reaches AGC2 like [adaptiveDigitalEnabled]. */
    var useInternalVad: Boolean = true
        private set

    /** `true` between [startRecording] and [pauseRecording]. */
    var recording: Boolean = false
        private set

    /** Whether the capture device is open, i.e. the microphone is in use. */
    val captureOpen: Boolean get() = input != null

    /** `true` while AGC2 output is queued for the playback device. */
    val playing: Boolean get() = output != null && queuedSamples > 0

    /** Human readable name of the capture endpoint, once one is open. */
    var captureDeviceName: String = "system default"
        private set

    /** Human readable name of the playback endpoint, once one is open. */
    var playbackDeviceName: String = "system default"
        private set

    /** Why no capture stream is open, or `null` while one is. */
    var captureError: String? = null
        private set

    /** Why no playback stream is open, or `null` while there is none. */
    var playbackError: String? = null
        private set

    /** Input level of the last processed frame, in dBFS; `-inf` for silence. */
    var inputDbfs: Float = Float.NEGATIVE_INFINITY
        private set

    /** Output level of the last processed frame, in dBFS; `-inf` for silence. */
    var outputDbfs: Float = Float.NEGATIVE_INFINITY
        private set

    /** Gain the last processed frame was played back with, in dB. */
    var appliedGainDb: Float = 0f
        private set

    /** Latest recommended input volume, or `null` when there is none. */
    var recommendedInputVolume: Int? = null
        private set

    /** AGC2 frames processed since the last [startRecording]. */
    var processedFrames: Long = 0L
        private set

    /** Frames the playback queue could not take, and frames AGC2 produced but the device refused. */
    var droppedPlaybackFrames: Long = 0L
        private set

    /**
     * Captured frames the pump dropped because it was behind: a live monitor
     * plays the newest audio, so the rest is skipped rather than processed late.
     */
    var droppedCaptureFrames: Long = 0L
        private set

    private val system: AudioSystem = audioSystem()

    private val config: Agc2Config

    private val inputVolumeConfig: Agc2InputVolumeConfig

    private val environment: Agc2Environment

    /** The controller the pump owns; replaced when a toggle that AGC2 reads at construction changes. */
    private var controller: Agc2GainController

    /** The open capture stream; only the render thread ever reads or writes it. */
    private var input: AudioInput? = null

    /** The open playback stream, owned the same way as [input]. */
    private var output: AudioOutput? = null

    private val generator = SignalGenerator()

    /** The thread that opens and closes devices; it never moves audio. */
    private val deviceThread = DeviceThread("agc2-waveform-devices") { deviceLoop() }

    /** Whether the capture device should be open. Written by the render thread. */
    @Volatile
    private var captureWanted = false

    /** Whether the playback device should be open. Written by the render thread. */
    @Volatile
    private var outputWanted = false

    /** Asks the device thread's loop to end. */
    @Volatile
    private var deviceLoopClosed = false

    /** Stream the device thread opened that the pump has not taken yet. */
    @Volatile
    private var openedCapture: AudioInput? = null

    @Volatile
    private var openedOutput: AudioOutput? = null

    /** Stream the render thread handed back for the device thread to close. */
    @Volatile
    private var closingCapture: AudioInput? = null

    @Volatile
    private var closingOutput: AudioOutput? = null

    /** Ticks the next synthesised frame is due, or `0` before the first one. */
    private var nextGeneratedFrame: ULong = 0uL

    // Scratch for one device read. It must fit whatever the device hands back in
    // a single call - a non-blocking read fills up to the whole buffer -
    // otherwise the conversion would silently clip the rest.
    private val captureBuffer: AudioBuffer

    private val captured: FloatArray

    private val playbackBuffer: AudioBuffer

    private val playbackChunk = FloatArray(PLAYBACK_CHUNK_FRAMES)

    /** The frame AGC2 processes, shared by the input, the internal buffer and the output. */
    private val buffer: Agc2AudioBuffer

    /** Synthesised capture frame in `-1..1`. */
    private val generated = FloatArray(FRAME_SAMPLES)

    /** Captured frame in `-1..1`. */
    private val microphone = FloatArray(FRAME_SAMPLES)

    /** Capture frame in the FloatS16 range AGC2 expects. */
    private val captureFrame = FloatArray(FRAME_SAMPLES)

    /** Gain controlled frame in `-1..1`, as published and played. */
    private val outputFrame = FloatArray(FRAME_SAMPLES)

    // Staging for the newest captured samples. It holds [MAX_FRAMES_PER_PUMP]
    // frames, so a pump that fell behind drops the older audio instead of
    // working through a backlog that keeps growing while it does.
    private val staging = FloatArray(FRAME_SAMPLES * MAX_FRAMES_PER_PUMP)

    private var staged = 0

    // Playback queue: [queueRead] is the oldest sample still to be played and
    // [queuedSamples] how many are queued after it.
    private val queue = FloatArray(QUEUE_FRAMES * FRAME_SAMPLES)

    private var queueRead = 0

    private var queuedSamples = 0

    /** Input volume of the previous frame, which is what `inputVolumeChanged` reports. */
    private var lastAppliedInputVolume = -1

    /** Gain the pump has already handed to the controller. */
    private var appliedFixedGainDb = Float.NaN

    private var closed = false

    init {
        // Both configurations are kept alive for as long as the pipeline: they
        // are the only place a toggle lives between two controller rebuilds.
        val config = createAgc2Config()
        // The AGC2 switch is app level - it decides whether the controller runs
        // at all - so the configuration itself always says enabled.
        config.enabled = true
        config.adaptiveDigitalEnabled = adaptiveDigitalEnabled
        config.inputVolumeControllerEnabled = inputVolumeControllerEnabled
        config.fixedDigitalGainDb = fixedDigitalGainDb

        val inputVolumeConfig = createAgc2InputVolumeConfig()
        val environment = createAgc2Environment()

        this.config = config
        this.inputVolumeConfig = inputVolumeConfig
        this.environment = environment
        appliedFixedGainDb = fixedDigitalGainDb
        controller = buildController(fixedDigitalGainDb)

        systemName = system.name
        captureBuffer = AudioBuffer(format, FRAME_SAMPLES * BUFFER_FRAMES)
        captured = FloatArray(FRAME_SAMPLES * BUFFER_FRAMES)
        playbackBuffer = AudioBuffer(format, PLAYBACK_CHUNK_FRAMES)
        // 10 ms at the capture format, which is the frame size AGC2 is fed (its
        // audio buffer sizes itself as rate / 100).
        buffer = createAgc2AudioBuffer(SAMPLE_RATE, CHANNELS)
        report("AGC2 $agc2Version via $systemName, ${format} frame ${FRAME_SAMPLES} samples")
        // The device thread only polls two flags until a device is wanted, so it
        // is started here and not on the first switch to the microphone: that
        // keeps the switch itself free of work.
        deviceThread.start()
    }

    /**
     * Starts a recording: the counters and the plots start from scratch, the
     * microphone is opened when the source needs it, and [pump] starts moving
     * audio.
     */
    fun startRecording() {
        if (closed || recording) return
        recording = true
        processedFrames = 0
        droppedPlaybackFrames = 0
        droppedCaptureFrames = 0
        staged = 0
        lastAppliedInputVolume = -1
        nextGeneratedFrame = 0uL
        inputScope.clear()
        outputScope.clear()
        inputHistory.clear()
        outputHistory.clear()
        gainHistory.clear()
        // Asking for the devices is all the transport does: the device thread
        // opens them, because that call can block.
        captureWanted = signalSource.needsCapture
        outputWanted = monitoring
        report("recording: ${signalSource.label}")
    }

    /**
     * Stops moving audio and releases the capture device - which is what hands
     * the microphone back to the system. The plots keep what was recorded.
     */
    fun pauseRecording() {
        if (!recording) return
        recording = false
        captureWanted = false
        closeCapture()
        report("paused after $processedFrames frames")
    }

    /**
     * Enables or disables the adaptive digital controller. AGC2 reads it when
     * the controller is built, so this builds a new one.
     */
    fun setAdaptiveDigitalEnabled(enabled: Boolean) {
        if (adaptiveDigitalEnabled == enabled) return
        adaptiveDigitalEnabled = enabled
        rebuildController()
    }

    /**
     * Enables or disables the input volume controller, which is what produces
     * [recommendedInputVolume]. AGC2 reads it when the controller is built.
     */
    fun setInputVolumeControllerEnabled(enabled: Boolean) {
        if (inputVolumeControllerEnabled == enabled) return
        inputVolumeControllerEnabled = enabled
        rebuildController()
    }

    /** Switches AGC2's own VAD on or off. It is a constructor argument, so this builds a new controller. */
    fun setUseInternalVad(enabled: Boolean) {
        if (useInternalVad == enabled) return
        useInternalVad = enabled
        rebuildController()
    }

    /**
     * Moves audio: reads everything the capture device has buffered through
     * AGC2, queues what comes out and hands as much of it to the playback
     * device as it takes. Called once per UI frame.
     */
    fun pump() {
        if (closed) return
        if (recording && signalSource.needsCapture) {
            // The device thread opens the microphone; until it has, the pump
            // simply has nothing to read and the window keeps drawing.
            val stream = input ?: takeCapture()
            if (stream != null) {
                input = stream
                drainCapture(stream)
            }
        } else if (recording && signalSource.isGenerated) {
            generate()
        }
        pumpPlayback()
    }

    /** Releases the devices and AGC2. */
    override fun close() {
        if (closed) return
        closed = true
        recording = false
        captureWanted = false
        outputWanted = false
        // Hand the streams to the device thread and let it finish before the
        // controller and the configuration it reads are torn down.
        closeCapture()
        closePlayback()
        deviceLoopClosed = true
        deviceThread.join()
        controller.close()
        buffer.close()
        inputVolumeConfig.close()
        environment.close()
        config.close()
        system.close()
    }

    /** Applies the control changes the render loop made. */
    private fun applyControls() {
        if (fixedDigitalGainDb != appliedFixedGainDb) {
            appliedFixedGainDb = fixedDigitalGainDb
            controller.setFixedGainDb(fixedDigitalGainDb)
        }
    }

    /**
     * Builds a controller from the current configuration.
     *
     * AGC2 copies the configuration in its constructor, so the toggles that
     * live in it cannot reach a controller that is already running - building a
     * new one is what applies them. The environment and the frame buffer are
     * shared with the controller that is replaced.
     */
    private fun buildController(gainDb: Float): Agc2GainController {
        config.adaptiveDigitalEnabled = adaptiveDigitalEnabled
        config.inputVolumeControllerEnabled = inputVolumeControllerEnabled
        config.fixedDigitalGainDb = gainDb
        return createAgc2GainController(
            environment,
            config,
            inputVolumeConfig,
            SAMPLE_RATE,
            CHANNELS,
            useInternalVad,
        ).also {
            // The example always plays what AGC2 produced, and the input volume
            // controller adapts differently when the capture output is unused.
            it.setCaptureOutputUsed(true)
        }
    }

    /** Replaces the controller with one built from the current configuration. */
    private fun rebuildController() {
        val previous = controller
        controller = buildController(appliedFixedGainDb)
        previous.close()
        report(
            "controller rebuilt: adaptive $adaptiveDigitalEnabled, " +
                "input volume controller $inputVolumeControllerEnabled, internal VAD $useInternalVad",
        )
    }

    /** Generates one frame per elapsed [FRAME_MILLIS] for a synthesised source. */
    private fun generate() {
        val now = SDL.getTicks()
        if (nextGeneratedFrame == 0uL) nextGeneratedFrame = now

        var budget = MAX_GENERATED_FRAMES
        while (budget-- > 0 && now >= nextGeneratedFrame) {
            generator.generate(generated, FRAME_SAMPLES, signalSource, dbfsToAmplitude(sourceLevelDb))
            processFrame(generated)
            nextGeneratedFrame += FRAME_MILLIS.toULong()
        }
        // A window that was not drawing for a while must not turn its backlog
        // into a burst of audio.
        if (nextGeneratedFrame + FRAME_MILLIS.toULong() < now) nextGeneratedFrame = now
    }

    /**
     * The device thread: opens what the render loop asked for, closes what it
     * handed back, and publishes the streams in between.
     *
     * Every stream has exactly one owner at a time - the device thread while it
     * is being opened or closed, the render loop while [pump] uses it - so
     * neither has to lock. The wanted flags are written before the loop reads
     * them, and a stream published for a request that was given up is closed
     * here instead.
     */
    private fun deviceLoop() {
        while (!deviceLoopClosed) {
            var waited = false

            closingCapture?.let { stream ->
                closingCapture = null
                stream.close()
                report("capture closed")
            }
            closingOutput?.let { stream ->
                closingOutput = null
                stream.close()
                report("playback closed")
            }

            if (captureWanted && openedCapture == null) {
                val stream = openCaptureStream()
                if (stream == null) {
                    waited = true
                } else if (captureWanted) {
                    openedCapture = stream
                } else {
                    stream.close()
                }
            }

            if (outputWanted && openedOutput == null) {
                val stream = openPlaybackStream()
                if (stream == null) {
                    waited = true
                } else if (outputWanted) {
                    openedOutput = stream
                } else {
                    stream.close()
                }
            }

            // A device that failed to open is retried on a slower cadence: an
            // attempt can take a while (a permission prompt) and hammering it
            // would keep the machine busy for nothing.
            SDL.delay(if (waited) DEVICE_RETRY_MILLIS else DEVICE_POLL_MILLIS)
        }

        // Whatever was in flight when the loop was asked to stop still belongs
        // to this thread.
        closingCapture?.close()
        closingOutput?.close()
        openedCapture?.close()
        openedOutput?.close()
        closingCapture = null
        closingOutput = null
        openedCapture = null
        openedOutput = null
    }

    /** Takes the capture stream the device thread opened, or `null` while there is none. */
    private fun takeCapture(): AudioInput? {
        val stream = openedCapture ?: return null
        openedCapture = null
        return stream
    }

    /** Takes the playback stream the device thread opened, or `null` while there is none. */
    private fun takeOutput(): AudioOutput? {
        val stream = openedOutput ?: return null
        openedOutput = null
        return stream
    }

    /** Opens the capture device. Runs on the device thread: this call can block. */
    private fun openCaptureStream(): AudioInput? = try {
        // bufferFrames: the device hands over up to 10 frames at a time, which
        // is enough slack for one slow UI frame and small enough to stay live.
        val stream = system.openInput(format, bufferFrames = FRAME_SAMPLES * BUFFER_FRAMES)
        if (stream.format != format) {
            stream.close()
            captureError = "device opened as ${stream.format}, expected $format"
            report("capture unavailable: $captureError")
            null
        } else {
            stream.start()
            captureDeviceName = stream.device?.name ?: system.defaultInputDevice()?.name ?: "system default"
            captureError = null
            report("capturing $captureDeviceName [$systemName] $format")
            stream
        }
    } catch (e: AudioException) {
        if (captureError == null) {
            val available = system.inputDevices().joinToString { it.name }
            captureError = "${e.message} - inputs: [$available]"
            report("capture unavailable: $captureError; retrying")
        }
        null
    }

    /** Opens the playback device. Runs on the device thread: this call can block. */
    private fun openPlaybackStream(): AudioOutput? = try {
        // Deeper than the capture: the pump drains whatever the capture
        // buffered in one go and writes it back, so a shallower playback buffer
        // would only overflow on every burst.
        val stream = system.openOutput(format, bufferFrames = FRAME_SAMPLES * QUEUE_FRAMES)
        if (stream.format != format) {
            stream.close()
            playbackError = "device opened as ${stream.format}, expected $format"
            report("playback unavailable: $playbackError")
            null
        } else {
            stream.start()
            playbackDeviceName = stream.device?.name ?: system.defaultOutputDevice()?.name ?: "system default"
            playbackError = null
            report("playing to $playbackDeviceName [$systemName] $format")
            stream
        }
    } catch (e: AudioException) {
        if (playbackError == null) {
            val available = system.outputDevices().joinToString { it.name }
            playbackError = "${e.message} - outputs: [$available]"
            report("playback unavailable: $playbackError; retrying")
        }
        null
    }

    /**
     * Hands the capture stream back to the device thread, which is what closes
     * it - closing a device can block as well.
     */
    private fun closeCapture() {
        input?.let { stream ->
            input = null
            closingCapture = stream
        }
        openedCapture?.let { stream ->
            openedCapture = null
            closingCapture = stream
        }
    }

    /** Hands the playback stream back and drops whatever was queued for it. */
    private fun closePlayback() {
        output?.let { stream ->
            output = null
            closingOutput = stream
        }
        openedOutput?.let { stream ->
            openedOutput = null
            closingOutput = stream
        }
        queuedSamples = 0
        queueRead = 0
    }

    /**
     * Moves what the device captured, newest first.
     *
     * The device is read until it is empty and only the newest
     * [MAX_FRAMES_PER_PUMP] frames are kept; the rest of the backlog is dropped
     * (counted in [droppedCaptureFrames]). Processing the whole backlog is what
     * turns one slow frame into a slower one: the device keeps filling its
     * buffer while the pump works through what was already there, so every
     * following frame would have even more to catch up with. A live monitor has
     * no use for that audio anyway.
     */
    private fun drainCapture(stream: AudioInput) {
        while (true) {
            val frames = stream.readNonBlocking(captureBuffer)
            if (frames <= 0) break
            stage(captured, captureBuffer.toFloats(captured, frames))
        }
        processStaged()
    }

    /** Appends captured samples, dropping the oldest ones when they no longer fit. */
    private fun stage(values: FloatArray, count: Int) {
        if (count <= 0) return
        if (count >= staging.size) {
            values.copyInto(staging, 0, count - staging.size, count)
            droppedCaptureFrames += ((staged + count - staging.size) / FRAME_SAMPLES).toLong()
            staged = staging.size
            return
        }
        val room = staging.size - staged
        if (count > room) {
            val drop = count - room
            staging.copyInto(staging, 0, drop, staged)
            staged -= drop
            droppedCaptureFrames += (drop / FRAME_SAMPLES).toLong()
        }
        values.copyInto(staging, staged, 0, count)
        staged += count
    }

    /** Runs AGC2 on every complete staged frame; a partial one stays for the next pump. */
    private fun processStaged() {
        var offset = 0
        while (offset + FRAME_SAMPLES <= staged) {
            staging.copyInto(microphone, 0, offset, offset + FRAME_SAMPLES)
            processFrame(microphone)
            offset += FRAME_SAMPLES
        }
        if (offset > 0) {
            staging.copyInto(staging, 0, offset, staged)
            staged -= offset
        }
    }

    /**
     * One 10 ms frame end to end: the capture frame is scaled into the FloatS16
     * range AGC2 works in, analysed and gain controlled in place, read back, and
     * the result is published and queued for playback.
     */
    private fun processFrame(frame: FloatArray) {
        applyControls()

        for (i in 0 until FRAME_SAMPLES) captureFrame[i] = frame[i] * AGC_SCALE

        val outputSamples: FloatArray
        if (agcEnabled) {
            buffer.writeChannel(0, captureFrame)
            // There is no audio HAL here, so "the input volume changed since the
            // previous call" is what the slider - or the recommendation fed back
            // into it - did since the last frame.
            val volume = appliedInputVolume.coerceIn(0, 255)
            val changed = volume != lastAppliedInputVolume
            lastAppliedInputVolume = volume
            controller.analyze(volume, buffer)
            controller.process(changed, buffer)
            outputSamples = buffer.readChannel(0)

            val recommended = controller.recommendedInputVolume
            recommendedInputVolume = recommended
            if (autoApplyRecommendedVolume && recommended != null) {
                appliedInputVolume = recommended.coerceIn(0, 255)
            }
        } else {
            // Bypassed: what the capture produced is what gets played, and the
            // input volume controller is not running, so it recommends nothing.
            outputSamples = captureFrame
            recommendedInputVolume = null
        }

        for (i in 0 until FRAME_SAMPLES) outputFrame[i] = outputSamples[i] / AGC_SCALE

        inputScope.append(frame, FRAME_SAMPLES)
        outputScope.append(outputFrame, FRAME_SAMPLES)

        inputDbfs = rmsDbfs(captureFrame)
        outputDbfs = rmsDbfs(outputSamples)
        // Silence has no level to compare, so there is no gain to report either:
        // `-inf - -inf` is not a number.
        appliedGainDb = if (inputDbfs.isFinite()) outputDbfs - inputDbfs else 0f
        inputHistory.append(inputDbfs)
        outputHistory.append(outputDbfs)
        gainHistory.append(appliedGainDb)

        if (monitoring) queuePlayback()

        processedFrames++
        // A line per second of audio keeps a headless run readable and shows
        // whether the gain is moving.
        if (processedFrames % REPORT_FRAMES == 0L) {
            report(
                "$processedFrames frames: input ${dbText(inputDbfs)} dBFS, " +
                    "output ${dbText(outputDbfs)} dBFS, gain ${fixed(appliedGainDb.toDouble(), 1)} dB, " +
                    "recommended volume ${recommendedInputVolume ?: "none"}, " +
                    "applied volume $appliedInputVolume, " +
                    "drops ${droppedCaptureFrames} capture / ${droppedPlaybackFrames} playback",
            )
        }
    }

    /** Queues the last frame, scaled by the playback level, for the output device. */
    private fun queuePlayback() {
        if (queuedSamples + FRAME_SAMPLES > queue.size) {
            // The device is not consuming: dropping is better than letting the
            // plots and the playback drift apart.
            droppedPlaybackFrames++
            return
        }
        val gain = dbfsToAmplitude(playbackLevelDb)
        val start = (queueRead + queuedSamples) % queue.size
        for (i in 0 until FRAME_SAMPLES) {
            queue[(start + i) % queue.size] = outputFrame[i] * gain
        }
        queuedSamples += FRAME_SAMPLES
    }

    /** Hands as much of the queue to the playback device as it takes right now. */
    private fun pumpPlayback() {
        if (!monitoring || queuedSamples == 0) return
        val stream = output ?: takeOutput()?.also { output = it } ?: return
        while (queuedSamples > 0) {
            val count = minOf(playbackChunk.size, queuedSamples)
            for (i in 0 until count) {
                playbackChunk[i] = queue[(queueRead + i) % queue.size]
            }
            playbackBuffer.putFloats(playbackChunk, count)
            val written = stream.writeNonBlocking(playbackBuffer)
            if (written <= 0) return
            queueRead = (queueRead + written) % queue.size
            queuedSamples -= written
        }
    }

    /**
     * RMS of [values] in dBFS. The samples are at AGC2's FloatS16 scale, so full
     * scale - 32768 - is 0 dBFS.
     */
    private fun rmsDbfs(values: FloatArray): Float {
        var sum = 0.0
        for (value in values) {
            val normalized = value / AGC_SCALE
            sum += (normalized * normalized).toDouble()
        }
        val rms = sqrt(sum / values.size)
        return if (rms <= 0.0) Float.NEGATIVE_INFINITY else (20.0 * log10(rms)).toFloat()
    }

    private companion object {
        /**
         * Level the synthesised source starts at, in dBFS.
         *
         * Quiet enough that AGC2 has something to do: at speech level the
         * adaptive digital controller ramps the gain up over the first seconds
         * (the library's own tests measure about +10 dB over five seconds of
         * this signal at this level), while a loud source would just settle at
         * unity gain.
         */
        const val DEFAULT_SOURCE_DB = -40f

        /** Playback level the slider starts at, in dBFS: audible, not painful. */
        const val DEFAULT_PLAYBACK_DB = -6f
    }
}

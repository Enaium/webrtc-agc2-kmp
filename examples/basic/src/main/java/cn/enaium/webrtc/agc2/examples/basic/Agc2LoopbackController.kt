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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.enaium.webrtc.agc2.Agc2Config
import cn.enaium.webrtc.agc2.Agc2Environment
import cn.enaium.webrtc.agc2.Agc2GainController
import cn.enaium.webrtc.agc2.Agc2InputVolumeConfig
import cn.enaium.webrtc.agc2.createAgc2AudioBuffer
import cn.enaium.webrtc.agc2.createAgc2Config
import cn.enaium.webrtc.agc2.createAgc2Environment
import cn.enaium.webrtc.agc2.createAgc2GainController
import cn.enaium.webrtc.agc2.createAgc2InputVolumeConfig
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Owns the real-time AGC2 loopback pipeline and exposes observable Compose
 * state that the UI collects:
 *
 *  - A 440 Hz reference tone is played out of the speaker via [AudioTrack] at a
 *    level the UI controls, so the microphone ([AudioRecord]) hears a known
 *    signal and the demo works on a phone nobody speaks into. The AEC3 example
 *    plays the same tone because it is the echo it cancels; AGC2 is
 *    capture-only, so here the tone is there to excite the microphone.
 *  - The captured frames are fed to AGC2 ([Agc2GainController.analyze] then
 *    [Agc2GainController.process]) at WebRTC's FloatS16 scale and played back
 *    through a second [AudioTrack].
 *  - The recommended input volume is written back into the applied input volume
 *    while [autoApplyRecommendedVolume] is on, which is what an audio HAL
 *    integration does; otherwise the applied volume comes from the UI slider.
 *
 * AGC2 copies the configuration when the controller is constructed, so the
 * config-level switches ([adaptiveDigitalEnabled],
 * [inputVolumeControllerEnabled], [useInternalVad]) only take effect on a new
 * controller: the audio thread builds one when it notices a change.
 * [setFixedGainDb] is applied to the running controller instead.
 */
class Agc2LoopbackController {

    companion object {
        private const val TAG = "AGC2Example"
        private const val SAMPLE_RATE = 16000
        private const val CHANNELS = 1
        private const val FRAME_SAMPLES = 160 // 10 ms @ 16 kHz
        private const val REFERENCE_FREQ = 440.0
        private const val DEFAULT_REFERENCE_LEVEL_DB = -12f
        private const val METRICS_INTERVAL_FRAMES = 50
        private const val FULL_SCALE = 32768.0
        private const val MIN_LEVEL_DB = -120f
    }

    // ---- Compose-observable state ----
    var isRunning by mutableStateOf(false)
        private set

    var agc2Enabled by mutableStateOf(true)

    var adaptiveDigitalEnabled by mutableStateOf(true)

    var inputVolumeControllerEnabled by mutableStateOf(true)

    var useInternalVad by mutableStateOf(true)

    var autoApplyRecommendedVolume by mutableStateOf(true)

    /**
     * Fixed digital gain in dB. The running controller takes it live through
     * [Agc2GainController.setFixedGainDb], which is the only gain setting that
     * does not need a new controller; WebRTC rejects 50 dB or more.
     */
    var fixedGainDb: Float
        get() = fixedGainDbState.floatValue
        set(value) {
            fixedGainDbState.floatValue = value.coerceIn(0f, 49f)
        }

    /** Level of the 440 Hz reference tone the speaker plays, in dBFS. */
    var referenceLevelDb: Float
        get() = referenceLevelDbState.floatValue
        set(value) {
            referenceLevelDbState.floatValue = value
        }

    /** Input volume used for the next frame, as the audio HAL would have applied it. */
    var appliedInputVolume: Int
        get() = appliedInputVolumeState.intValue
        set(value) {
            appliedInputVolumeState.intValue = value.coerceIn(0, 255)
        }

    private val fixedGainDbState = mutableFloatStateOf(0f)

    private val referenceLevelDbState = mutableFloatStateOf(DEFAULT_REFERENCE_LEVEL_DB)

    private val appliedInputVolumeState = mutableIntStateOf(255)

    /** Last input volume AGC2 recommended, or `null` while the controller has none. */
    var recommendedInputVolume by mutableStateOf<Int?>(null)
        private set

    var inputLevelDb by mutableFloatStateOf(MIN_LEVEL_DB)
        private set

    var outputLevelDb by mutableFloatStateOf(MIN_LEVEL_DB)
        private set

    /** Output level minus input level over the last [METRICS_INTERVAL_FRAMES] frames. */
    var gainDb by mutableFloatStateOf(0f)
        private set

    var frames by mutableLongStateOf(0L)
        private set

    var status by mutableStateOf("Stopped")

    var error by mutableStateOf<String?>(null)
        private set

    // ---- AGC2 object graph ----
    private var config: Agc2Config? = null
    private var inputVolumeConfig: Agc2InputVolumeConfig? = null
    private var environment: Agc2Environment? = null

    // ---- Audio ----
    private var audioRecord: AudioRecord? = null
    private var referenceTrack: AudioTrack? = null
    private var outputTrack: AudioTrack? = null

    private var worker: Thread? = null

    fun toggle() {
        if (isRunning) stop() else start()
    }

    fun start() {
        try {
            initAgc2()
            initAudio()
            isRunning = true
            error = null
            status = "Running…"
            worker = thread(start = true) { processingLoop() }
        } catch (t: Throwable) {
            stop()
            status = "Failed to start: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    fun stop() {
        isRunning = false
        worker?.join(500)
        worker = null

        runCatching { audioRecord?.stop() }
        runCatching { referenceTrack?.stop() }
        runCatching { outputTrack?.stop() }

        audioRecord?.release()
        audioRecord = null
        referenceTrack?.release()
        referenceTrack = null
        outputTrack?.release()
        outputTrack = null

        // The controller and the audio buffer are closed by the audio thread in
        // its finally block; only the configuration is owned by this thread.
        config?.close()
        inputVolumeConfig?.close()
        environment?.close()
        config = null
        inputVolumeConfig = null
        environment = null

        recommendedInputVolume = null
        status = "Stopped"
    }

    // =========================================================================
    // AGC2
    // =========================================================================

    private fun initAgc2() {
        val cfg = createAgc2Config()
        cfg.enabled = true
        cfg.inputVolumeControllerEnabled = inputVolumeControllerEnabled
        cfg.adaptiveDigitalEnabled = adaptiveDigitalEnabled
        cfg.fixedDigitalGainDb = fixedGainDb
        config = cfg

        inputVolumeConfig = createAgc2InputVolumeConfig()
        environment = createAgc2Environment()
    }

    // =========================================================================
    // Audio I/O
    // =========================================================================

    private fun initAudio() {
        val minRecordBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minRecordBuf, FRAME_SAMPLES * 2),
        )

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val minTrackBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val trackBufSize = maxOf(minTrackBuf, FRAME_SAMPLES * 8)

        referenceTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(trackBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        outputTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(trackBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    // =========================================================================
    // Processing loop
    // =========================================================================

    private fun processingLoop() {
        val cfg = config ?: return
        val volumeCfg = inputVolumeConfig ?: return
        val env = environment ?: return
        val record = audioRecord ?: return
        val refTrack = referenceTrack ?: return
        val outTrack = outputTrack ?: return

        val buffer = createAgc2AudioBuffer(SAMPLE_RATE, CHANNELS)

        val micFrame = ShortArray(FRAME_SAMPLES)
        val captureFloat = FloatArray(FRAME_SAMPLES)
        val referenceShort = ShortArray(FRAME_SAMPLES)
        val outShort = ShortArray(FRAME_SAMPLES)

        var controller: Agc2GainController? = null

        // What the current controller was built with; a difference means the
        // configuration has to be copied into a new controller.
        var builtAdaptiveDigital = adaptiveDigitalEnabled
        var builtInputVolumeController = inputVolumeControllerEnabled
        var builtInternalVad = useInternalVad

        var appliedVolume = appliedInputVolume
        var volumeChanged = false
        var appliedFixedGainDb = Float.NaN

        var phase = 0.0
        var frameCount = 0L
        var inputPower = 0.0
        var outputPower = 0.0
        var levelSamples = 0

        try {
            controller = createAgc2GainController(
                env = env,
                config = cfg,
                inputVolumeConfig = volumeCfg,
                sampleRate = SAMPLE_RATE,
                channels = CHANNELS,
                useInternalVad = builtInternalVad,
            )

            record.startRecording()
            refTrack.play()
            outTrack.play()

            while (isRunning) {
                // A config-level switch needs a new controller: the native side
                // copies the configuration in its constructor. Rebuilding here
                // keeps the controller on the thread that calls it.
                if (adaptiveDigitalEnabled != builtAdaptiveDigital ||
                    inputVolumeControllerEnabled != builtInputVolumeController ||
                    useInternalVad != builtInternalVad
                ) {
                    cfg.adaptiveDigitalEnabled = adaptiveDigitalEnabled
                    cfg.inputVolumeControllerEnabled = inputVolumeControllerEnabled
                    val stale = controller
                    controller = createAgc2GainController(
                        env = env,
                        config = cfg,
                        inputVolumeConfig = volumeCfg,
                        sampleRate = SAMPLE_RATE,
                        channels = CHANNELS,
                        useInternalVad = useInternalVad,
                    )
                    runCatching { stale?.close() }
                    builtAdaptiveDigital = adaptiveDigitalEnabled
                    builtInputVolumeController = inputVolumeControllerEnabled
                    builtInternalVad = useInternalVad
                    appliedFixedGainDb = Float.NaN
                    volumeChanged = true
                }

                val gc = controller ?: break

                val read = record.read(micFrame, 0, FRAME_SAMPLES)
                if (read <= 0) continue
                val n = minOf(read, FRAME_SAMPLES)

                // 1. Reference tone: 440 Hz sine wave played out of the speaker.
                //    AGC2 is capture-only, so unlike AEC3 the tone is not fed to
                //    the controller - it only gives the microphone something to
                //    pick up.
                val toneGain = 10.0.pow(referenceLevelDb / 20.0)
                for (i in 0 until n) {
                    val sample = sin(2.0 * PI * REFERENCE_FREQ * phase / SAMPLE_RATE)
                    phase += 1.0
                    if (phase >= SAMPLE_RATE) phase = 0.0
                    referenceShort[i] = (sample * toneGain * 32767).toInt().toShort()
                }
                refTrack.write(referenceShort, 0, n)

                // 2. Capture: PCM16 is already at WebRTC's FloatS16 scale.
                for (i in 0 until n) {
                    captureFloat[i] = micFrame[i].toFloat()
                }

                // 3. Fixed gain is the one setting the running controller accepts.
                if (appliedFixedGainDb != fixedGainDb) {
                    gc.setFixedGainDb(fixedGainDb)
                    appliedFixedGainDb = fixedGainDb
                }

                // 4. AGC2 is skipped entirely when switched off, which plays the
                //    raw capture through instead.
                val processed: FloatArray
                if (agc2Enabled) {
                    // The slider owns the applied volume while the recommendation
                    // is not applied; a change has to reach the controller.
                    if (!autoApplyRecommendedVolume && appliedInputVolume != appliedVolume) {
                        appliedVolume = appliedInputVolume
                        volumeChanged = true
                    }

                    buffer.writeChannel(0, captureFloat)
                    gc.analyze(appliedVolume, buffer)
                    gc.process(volumeChanged, buffer)
                    volumeChanged = false
                    processed = buffer.readChannel(0)

                    if (autoApplyRecommendedVolume) {
                        gc.recommendedInputVolume?.let { recommended ->
                            if (recommended != appliedVolume) {
                                appliedVolume = recommended
                                appliedInputVolume = recommended
                                volumeChanged = true
                            }
                        }
                    }
                } else {
                    processed = captureFloat
                }

                // 5. Play the processed (or raw) result back.
                for (i in 0 until n) {
                    var s = processed[i]
                    if (s > 32767f) s = 32767f
                    if (s < -32768f) s = -32768f
                    outShort[i] = s.toInt().toShort()
                }
                outTrack.write(outShort, 0, n)

                for (i in 0 until n) {
                    inputPower += captureFloat[i].toDouble() * captureFloat[i]
                    outputPower += processed[i].toDouble() * processed[i]
                }
                levelSamples += n

                frameCount++

                if (frameCount % METRICS_INTERVAL_FRAMES == 0L) {
                    val inputLevel = rmsDbfs(inputPower, levelSamples)
                    val outputLevel = rmsDbfs(outputPower, levelSamples)
                    inputLevelDb = inputLevel
                    outputLevelDb = outputLevel
                    gainDb = outputLevel - inputLevel
                    frames = frameCount
                    inputPower = 0.0
                    outputPower = 0.0
                    levelSamples = 0

                    recommendedInputVolume =
                        if (agc2Enabled) gc.recommendedInputVolume else null

                    status = buildString {
                        appendLine("Input  : %.1f dBFS".format(inputLevelDb))
                        appendLine("Output : %.1f dBFS".format(outputLevelDb))
                        appendLine("Gain   : %+.1f dB".format(gainDb))
                        appendLine(
                            "Volume : applied $appliedVolume, " +
                                "recommended ${recommendedInputVolume ?: "-"}",
                        )
                        appendLine("Frames : $frameCount")
                    }
                    Log.i(
                        TAG,
                        "AGC2 status: input=${"%.1f".format(inputLevelDb)} dBFS " +
                            "output=${"%.1f".format(outputLevelDb)} dBFS " +
                            "gain=${"%+.1f".format(gainDb)} dB " +
                            "volume=$appliedVolume/${recommendedInputVolume ?: "-"} " +
                            "frames=$frameCount",
                    )
                }
            }
        } catch (t: Throwable) {
            error = "${t.javaClass.simpleName}: ${t.message}"
            Log.e(TAG, "AGC2 processing error", t)
        } finally {
            runCatching { controller?.close() }
            runCatching { buffer.close() }
        }
    }

    /** RMS of [power] over [samples] samples, in dBFS relative to full scale. */
    private fun rmsDbfs(power: Double, samples: Int): Float {
        if (samples <= 0) return MIN_LEVEL_DB
        val rms = sqrt(power / samples)
        return (20.0 * log10(max(rms, 1e-10) / FULL_SCALE)).toFloat().coerceAtLeast(MIN_LEVEL_DB)
    }
}

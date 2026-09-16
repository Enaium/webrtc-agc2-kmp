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

import cn.enaium.imgui.ImGui
import cn.enaium.imgui.ImGuiCond
import cn.enaium.imgui.ImGuiWindowFlags
import cn.enaium.imgui.ImVec2
import cn.enaium.imgui.ImVec4
import cn.enaium.imgui.extensions.implot.ImPlot
import cn.enaium.imgui.extensions.implot.ImPlotAxisFlags
import cn.enaium.imgui.extensions.implot.ImPlotCond
import cn.enaium.imgui.extensions.implot.ImPlotSpec
import kotlin.math.log10
import kotlin.math.round

/** Shortest window the slider allows, in milliseconds. */
internal const val MIN_WINDOW_MILLIS = 100

/** Longest window the slider allows, in milliseconds. */
internal const val MAX_WINDOW_MILLIS = 10_000

/** Window the example starts with, in milliseconds. */
internal const val DEFAULT_WINDOW_MILLIS = 1_000

/** Lowest source level the slider allows, in dBFS. */
private const val MIN_SOURCE_DB = -60f

/** Highest source and playback level the slider allows, in dBFS. */
private const val MAX_SOURCE_DB = 0f

/** Highest fixed digital gain the slider allows, in dB. */
private const val MAX_FIXED_GAIN_DB = 30f

/** Input volume the slider spans: the range the binding clamps to. */
private const val MAX_INPUT_VOLUME = 255

/** Bottom of the level plot, in dB. */
private const val MIN_LEVEL_DB = -90f

/**
 * Top of the level plot, in dB. The applied gain is a difference and can sit
 * above full scale, and the fixed digital gain alone reaches +30 dB.
 */
private const val MAX_LEVEL_DB = 30f

/**
 * The example UI: the transport of the recording, the AGC2 settings, the
 * capture as AGC2 receives it and the gain controlled output over one sliding
 * window, and the measured levels with the applied gain as a history.
 *
 * Both waveform plots share one amplitude axis. A microphone running at speech
 * level is roughly 30 dB below full scale, so a fixed `-1..1` axis would draw a
 * flat line; the shared scale keeps the two signals comparable - which is the
 * whole point of showing them side by side - and the level readouts keep the
 * absolute values visible.
 *
 * The window length is a slider: the scopes always hold [MAX_WINDOW_MILLIS] and
 * the plots show the newest part of them.
 */
class WaveformWindow(
    private val pipeline: Agc2Pipeline,
    initialWindowMillis: Int = DEFAULT_WINDOW_MILLIS,
) {

    private val sliderValue = IntArray(1) { initialWindowMillis.coerceIn(MIN_WINDOW_MILLIS, MAX_WINDOW_MILLIS) }

    private val monitoring = BooleanArray(1) { pipeline.monitoring }

    private val agcEnabled = BooleanArray(1) { pipeline.agcEnabled }

    private val adaptiveDigitalEnabled = BooleanArray(1) { pipeline.adaptiveDigitalEnabled }

    private val volumeControllerEnabled = BooleanArray(1) { pipeline.inputVolumeControllerEnabled }

    private val internalVadEnabled = BooleanArray(1) { pipeline.useInternalVad }

    private val fixedGainDb = FloatArray(1) { pipeline.fixedDigitalGainDb }

    private val inputVolume = IntArray(1) { pipeline.appliedInputVolume }

    private val autoApplyVolume = BooleanArray(1) { pipeline.autoApplyRecommendedVolume }

    private val sourceIndex = IntArray(1) { pipeline.signalSource.ordinal }

    private val sourceLevelDb = FloatArray(1) { pipeline.sourceLevelDb }

    private val playbackLevelDb = FloatArray(1) { pipeline.playbackLevelDb }

    private var windowMillis = sliderValue[0]

    /**
     * Newest samples of both scopes, oldest first: what the plots draw. Sized to
     * the visible window rather than the scope, so a short window copies less.
     */
    private var input = FloatArray(windowSamples(windowMillis))

    private var output = FloatArray(windowSamples(windowMillis))

    /** One min/max pair per plot column, reused between frames. */
    private var envelope = FloatArray(0)

    /** Amplitude axis shared by both waveform plots, in `-1..1`. */
    private var signalScale = MIN_SCALE

    /**
     * Level history of the newest frames, oldest first, as the level plot draws
     * it: one value per frame, copied out of the rings the pump fills. Sized to
     * the frames the window shows, so a ten second window copies a thousand
     * values and a short one a hundred.
     */
    private var inputLevels = FloatArray(0)

    private var outputLevels = FloatArray(0)

    private var gainLevels = FloatArray(0)

    /**
     * One frame of the UI. Call between [ImGui.newFrame] and [ImGui.render],
     * after [Agc2Pipeline.pump] filled the scopes for this frame.
     */
    fun draw() {
        syncWindowSize()

        // One copy per scope, and only over what is drawn: the scopes hand back
        // the newest samples and their peak, so nothing is walked twice.
        val inputPeak = pipeline.inputScope.snapshot(input)
        val outputPeak = pipeline.outputScope.snapshot(output)
        // The capture and the output share their axis: that is the pair whose
        // difference is the point of the example. Fast attack, slow release, so
        // the axis follows a louder passage at once and shrinks back over roughly
        // a second. A synthesised source has a known level, which keeps the axis
        // from collapsing while it is silent.
        signalScale = maxOf(
            inputPeak * HEADROOM,
            outputPeak * HEADROOM,
            signalScale * RELEASE,
            if (pipeline.signalSource.isGenerated) dbfsToAmplitude(pipeline.sourceLevelDb) else MIN_SCALE,
            MIN_SCALE,
        )

        val displaySize = ImGui.getIO().displaySize
        // The window owns the whole viewport: pinned to the top-left corner,
        // resized to the display and stripped of decorations, so it cannot be
        // dragged, resized or collapsed and always fills the SDL window.
        ImGui.setNextWindowPos(ImVec2(0f, 0f), ImGuiCond.ALWAYS)
        ImGui.setNextWindowSize(displaySize, ImGuiCond.ALWAYS)
        if (ImGui.begin("webrtc-agc2-kmp waveform", null, WINDOW_FLAGS)) {
            val source = pipeline.signalSource
            transport()
            controls()
            // Everything the example reports lives in one folded section: what
            // stays on screen is the controls, so the plots get the room.
            details(source, inputPeak, outputPeak)
            ImGui.separator()

            // The window is fullscreen, so the three plots split whatever height
            // is left instead of leaving the lower part empty.
            val available = ImGui.getContentRegionAvail()
            val plotHeight = maxOf((available.y - PLOT_GAP * 2f) / 3f, MIN_PLOT_HEIGHT)
            plot("input (capture)", input, INPUT_COLOR, plotHeight)
            plot("output (gain controlled)", output, OUTPUT_COLOR, plotHeight)
            plotLevels(plotHeight)
        }
        ImGui.end()
    }

    /**
     * Record/pause and the monitoring switch: the two controls that drive the
     * audio devices.
     *
     * Record opens the capture device (or starts the synthesised source), pause
     * releases it - which is what hands the microphone back to the system.
     * Monitoring sends what AGC2 produces to the speaker; the queue is drained
     * by the pump, so turning it on or off takes effect on the next frame.
     */
    private fun transport() {
        val recording = pipeline.recording
        newRow()
        placeButton(if (recording) "Pause" else "Record") {
            if (recording) pipeline.pauseRecording() else pipeline.startRecording()
        }
        placeCheck("monitor", monitoring) { pipeline.monitoring = monitoring[0] }
        placeSlider("window", 240f) {
            if (ImGui.sliderInt("window", sliderValue, MIN_WINDOW_MILLIS, MAX_WINDOW_MILLIS, "%d ms")) {
                windowMillis = sliderValue[0]
                syncWindowSize()
            }
        }
    }

    /**
     * Everything the example reports, folded away behind one header: where the
     * audio comes from and goes to, what the last frame measured, and what the
     * controls do. The button and checkbox labels already carry the transport
     * state, so none of it has to stay on screen.
     */
    private fun details(source: SignalSource, inputPeak: Float, outputPeak: Float) {
        if (!ImGui.collapsingHeader("details")) return
        transportState()
        devices(source)
        readouts(inputPeak, outputPeak)
        help(source)
    }

    /** The state behind the transport buttons. */
    private fun transportState() {
        ImGui.text(
            when {
                pipeline.recording && pipeline.playing -> "recording, playing"
                pipeline.recording -> "recording"
                pipeline.processedFrames > 0L -> "paused"
                else -> "idle"
            },
        )
    }

    /** The endpoints the example is running on, and why it has none. */
    private fun devices(source: SignalSource) {
        if (source.needsCapture) {
            val captureError = pipeline.captureError
            if (captureError == null) {
                ImGui.text("capture: ${pipeline.captureDeviceName} via ${pipeline.systemName}")
            } else {
                // Android asks for the microphone permission while the app is
                // already running, so the device can appear a moment later.
                ImGui.text("no capture yet, retrying: $captureError")
            }
        } else {
            ImGui.text("capture: not used, ${source.label} replaces the captured frame")
        }

        if (!pipeline.monitoring) {
            ImGui.text("playback: off")
        } else if (pipeline.playbackError == null) {
            val dropped = pipeline.droppedPlaybackFrames
            ImGui.text(
                "playback: ${pipeline.playbackDeviceName} via ${pipeline.systemName}" +
                    // Audio the device refused or the queue could not hold is
                    // worth showing: the window keeps running, but it is audible.
                    if (dropped > 0L) " ($dropped frames dropped)" else "",
            )
        } else {
            ImGui.text("no playback yet, retrying: ${pipeline.playbackError}")
        }
    }

    /** What the last frame measured: levels, gain and the input volume recommendation. */
    private fun readouts(inputPeak: Float, outputPeak: Float) {
        ImGui.text("format: ${pipeline.format}, AGC2 frame: ${pipeline.frameSize} samples ($FRAME_MILLIS ms)")
        val droppedCapture = pipeline.droppedCaptureFrames
        val droppedPlayback = pipeline.droppedPlaybackFrames
        ImGui.text(
            "processed ${pipeline.processedFrames} frames" +
                // What the pump skipped because it was behind, and what the
                // output device refused: both are audible as glitches, so they
                // are worth showing next to the counters.
                if (droppedCapture > 0L || droppedPlayback > 0L) {
                    " ($droppedCapture capture / $droppedPlayback playback dropped)"
                } else {
                    ""
                },
        )

        val recommended = pipeline.recommendedInputVolume
        val recommendation = if (recommended == null) {
            "recommended input volume: none"
        } else {
            "recommended input volume: $recommended" +
                if (pipeline.autoApplyRecommendedVolume) " (auto-applied)" else ""
        }
        ImGui.text(
            "input ${dbText(pipeline.inputDbfs)} dBFS | output ${dbText(pipeline.outputDbfs)} dBFS | " +
                "gain ${fixed(pipeline.appliedGainDb.toDouble(), 1)} dB | $recommendation",
        )
        ImGui.text(
            "peak: input ${dbfs(inputPeak)} dBFS, output ${dbfs(outputPeak)} dBFS" +
                " | axis: +-${fixed(signalScale.toDouble(), 4)}",
        )
    }

    /** The AGC2 settings, and the source the capture frame comes from. */
    private fun controls() {
        // The AGC2 switch is the example's own: turning it off bypasses the
        // controller, so what the plots show and what is played is the raw
        // capture. The three toggles next to it are AGC2's own configuration,
        // which the pipeline applies by building a new controller.
        newRow()
        placeCheck("AGC2", agcEnabled) { pipeline.agcEnabled = agcEnabled[0] }
        placeCheck("adaptive digital", adaptiveDigitalEnabled) {
            pipeline.setAdaptiveDigitalEnabled(adaptiveDigitalEnabled[0])
        }
        placeCheck("input volume controller", volumeControllerEnabled) {
            pipeline.setInputVolumeControllerEnabled(volumeControllerEnabled[0])
        }
        placeCheck("internal VAD", internalVadEnabled) { pipeline.setUseInternalVad(internalVadEnabled[0]) }
        placeSlider("fixed gain", 160f) {
            if (ImGui.sliderFloat("fixed gain", fixedGainDb, 0f, MAX_FIXED_GAIN_DB, "%.1f dB")) {
                pipeline.fixedDigitalGainDb = fixedGainDb[0]
            }
        }

        // Auto-apply moves the volume from the pump, so the slider follows it
        // instead of fighting it.
        if (pipeline.autoApplyRecommendedVolume) inputVolume[0] = pipeline.appliedInputVolume
        newRow()
        placeSlider("applied input volume", 200f) {
            if (ImGui.sliderInt("applied input volume", inputVolume, 0, MAX_INPUT_VOLUME)) {
                pipeline.appliedInputVolume = inputVolume[0]
            }
        }
        placeCheck("auto apply", autoApplyVolume) {
            pipeline.autoApplyRecommendedVolume = autoApplyVolume[0]
        }
        placeCombo("source", 200f) {
            if (ImGui.combo("source", sourceIndex, SOURCE_LABELS)) {
                pipeline.signalSource = SignalSource.entries[sourceIndex[0]]
            }
        }
        // Labelled "source level", not "level": the level plot below carries
        // that label too, and two visible items whose labels hash to the same
        // ID make ImGui report an ID conflict.
        placeSlider("source level", 160f) {
            if (ImGui.sliderFloat("source level", sourceLevelDb, MIN_SOURCE_DB, MAX_SOURCE_DB, "%.0f dBFS")) {
                pipeline.sourceLevelDb = sourceLevelDb[0]
            }
        }
        placeSlider("playback level", 160f) {
            if (ImGui.sliderFloat("playback level", playbackLevelDb, MIN_SOURCE_DB, MAX_SOURCE_DB, "%.0f dBFS")) {
                pipeline.playbackLevelDb = playbackLevelDb[0]
            }
        }
    }

    // =========================================================================
    // Layout
    //
    // A row of controls does not fit on a phone (or in a narrow window), and a
    // widget that is cut off at the edge cannot be operated, so the controls are
    // placed into rows that wrap: every helper below measures what it is about
    // to draw and starts a new row when the rest of the current one is too
    // small. The width of a widget is its item width plus the label ImGui draws
    // next to it.
    // =========================================================================

    /** Space between two items of the same row. */
    private var rowWidth = 0f

    /** Starts a new row. */
    private fun newRow() {
        rowWidth = 0f
    }

    /** Reserves [width] on the current row, wrapping onto a new one when it does not fit. */
    private fun reserve(width: Float): Boolean {
        val available = ImGui.getContentRegionAvail().x
        val needed = if (rowWidth > 0f) ITEM_GAP + width else width
        return if (rowWidth > 0f && rowWidth + needed > available) {
            rowWidth = width
            false
        } else {
            if (rowWidth > 0f) {
                ImGui.sameLine()
                rowWidth += ITEM_GAP
            }
            rowWidth += width
            true
        }
    }

    /** A button: its label plus the padding ImGui frames it with. */
    private fun placeButton(label: String, onClick: () -> Unit) {
        reserve(ImGui.calcTextSize(label).x + BUTTON_PADDING * 2f)
        if (ImGui.button(label)) onClick()
    }

    /** A checkbox with its label. */
    private fun placeCheck(label: String, value: BooleanArray, onChange: () -> Unit) {
        reserve(ImGui.getFrameHeight() + LABEL_GAP + ImGui.calcTextSize(label).x)
        if (ImGui.checkbox(label, value)) onChange()
    }

    /** A slider of [itemWidth] with [label] drawn next to it. */
    private fun placeSlider(label: String, itemWidth: Float, block: () -> Unit) {
        reserve(itemWidth + LABEL_GAP + ImGui.calcTextSize(label).x)
        ImGui.setNextItemWidth(itemWidth)
        block()
    }

    /** A dropdown of [itemWidth] with [label] drawn next to it. */
    private fun placeCombo(label: String, itemWidth: Float, block: () -> Unit) {
        reserve(itemWidth + LABEL_GAP + ImGui.calcTextSize(label).x)
        ImGui.setNextItemWidth(itemWidth)
        block()
    }

    /** What the source and the knobs next to it do. */
    private fun help(source: SignalSource) {
        if (source.needsCapture) {
            ImGui.text(
                "microphone: the capture is processed as it arrives, and the level slider applies to the " +
                    "synthesised sources",
            )
        } else {
            ImGui.text(
                "synthetic sources replace the captured frame, so the gain controller runs without a " +
                    "microphone - speech is the default, quiet enough that the gain ramps up",
            )
        }
    }

    /**
     * Draws one waveform. Every pair of points covers [envelopeOf]'s samples per
     * column, which is what the x scale is derived from, and the cost of the
     * line is bounded by the plot width instead of the window length.
     */
    private fun plot(title: String, values: FloatArray, color: ImVec4, height: Float) {
        if (!ImPlot.beginPlot(title, ImVec2(-1f, height))) return
        ImPlot.setupAxes("milliseconds", "amplitude", ImPlotAxisFlags.NONE, ImPlotAxisFlags.NONE)
        // The window scrolls, so the x axis is pinned to the full sweep.
        ImPlot.setupAxesLimits(
            0.0,
            windowMillis.toDouble(),
            -signalScale.toDouble(),
            signalScale.toDouble(),
            ImPlotCond.ALWAYS,
        )

        // A ten second window holds 480k samples while the plot is only a few
        // thousand pixels wide, and every point costs a call into the C++ line
        // renderer - plotting the raw array is a hundred times the work for a
        // picture that cannot show the difference. One min/max pair per pixel
        // column keeps the envelope and the cost bounded by the width.
        val columns = maxOf(ImPlot.getPlotSize().x.toInt(), MIN_COLUMNS)
        val samplesPerColumn = (values.size + columns - 1) / columns
        val spec = ImPlotSpec(lineColor = color, lineWeight = 1f)
        if (samplesPerColumn <= 1) {
            ImPlot.plotLine("##$title", values, xScale = MILLIS_PER_SAMPLE, spec = spec)
        } else {
            if (envelope.size != columns * 2) envelope = FloatArray(columns * 2)
            envelopeOf(values, samplesPerColumn, envelope)
            ImPlot.plotLine(
                "##$title",
                envelope,
                xScale = samplesPerColumn / 2.0 * MILLIS_PER_SAMPLE,
                spec = spec,
            )
        }
        ImPlot.endPlot()
    }

    /**
     * Fills [destination] with one min/max pair per column, in sample order, so
     * the drawn line follows the signal instead of jumping between extremes.
     * Two points are always written per column, which is what makes the x scale
     * (`samplesPerColumn / 2` per point) line up with the time axis.
     */
    private fun envelopeOf(values: FloatArray, samplesPerColumn: Int, destination: FloatArray) {
        var written = 0
        var start = 0
        while (start < values.size) {
            val end = minOf(start + samplesPerColumn, values.size)
            var lowest = start
            var highest = start
            for (i in start until end) {
                if (values[i] < values[lowest]) lowest = i
                if (values[i] > values[highest]) highest = i
            }
            destination[written++] = values[minOf(lowest, highest)]
            destination[written++] = values[maxOf(lowest, highest)]
            start = end
        }
    }

    /**
     * Draws the per frame levels and the applied gain over the same window.
     *
     * All three share one axis: the binding exposes no second y axis, and the
     * three series are the same quantity expressed in dB - an absolute level in
     * two cases, a difference in the third - so the top of the axis reaches the
     * fixed gain the slider can ask for.
     */
    private fun plotLevels(height: Float) {
        val count = maxOf(windowMillis / FRAME_MILLIS, 1).coerceAtMost(pipeline.inputHistory.capacity)
        if (inputLevels.size != count) {
            inputLevels = FloatArray(count)
            outputLevels = FloatArray(count)
            gainLevels = FloatArray(count)
        }
        val copied = pipeline.inputHistory.copyTo(inputLevels, count)
        pipeline.outputHistory.copyTo(outputLevels, count)
        pipeline.gainHistory.copyTo(gainLevels, count)
        // Before the ring has a full window the line covers what there is, rather
        // than a stale tail the axis would stretch to.
        val xMax = if (copied < count) (copied * FRAME_MILLIS).toDouble() else windowMillis.toDouble()

        // The "##" suffix keeps the plot's ID out of the way of the controls:
        // ImPlot draws the plot title but hashes only what precedes the "##".
        if (!ImPlot.beginPlot("level##plot", ImVec2(-1f, height))) return
        ImPlot.setupAxes("milliseconds", "dB", ImPlotAxisFlags.NONE, ImPlotAxisFlags.NONE)
        ImPlot.setupAxesLimits(
            0.0,
            xMax,
            MIN_LEVEL_DB.toDouble(),
            MAX_LEVEL_DB.toDouble(),
            ImPlotCond.ALWAYS,
        )
        ImPlot.plotLine(
            "input",
            inputLevels,
            xScale = FRAME_MILLIS.toDouble(),
            spec = ImPlotSpec(lineColor = INPUT_COLOR, lineWeight = 1f),
        )
        ImPlot.plotLine(
            "output",
            outputLevels,
            xScale = FRAME_MILLIS.toDouble(),
            spec = ImPlotSpec(lineColor = OUTPUT_COLOR, lineWeight = 1f),
        )
        ImPlot.plotLine(
            "gain",
            gainLevels,
            xScale = FRAME_MILLIS.toDouble(),
            spec = ImPlotSpec(lineColor = GAIN_COLOR, lineWeight = 1f),
        )
        ImPlot.endPlot()
    }

    /** Resizes the plot windows when the window slider or the scope capacity changed. */
    private fun syncWindowSize() {
        val samples = windowSamples(windowMillis)
        if (input.size != samples) {
            input = FloatArray(samples)
            output = FloatArray(samples)
        }
    }

    private fun dbfs(value: Float): String =
        if (value <= 0f) "-inf" else fixed(20.0 * log10(value.toDouble()), 1)

    private companion object {
        /** Fullscreen host window: no title bar, nothing to drag or resize. */
        const val WINDOW_FLAGS = ImGuiWindowFlags.NO_TITLE_BAR or
            ImGuiWindowFlags.NO_RESIZE or
            ImGuiWindowFlags.NO_MOVE or
            ImGuiWindowFlags.NO_SCROLLBAR or
            ImGuiWindowFlags.NO_COLLAPSE or
            ImGuiWindowFlags.NO_SAVED_SETTINGS or
            ImGuiWindowFlags.NO_BRING_TO_FRONT_ON_FOCUS or
            ImGuiWindowFlags.NO_NAV_FOCUS

        /** Space kept between two plots. */
        const val PLOT_GAP = 8f

        /** Space kept between two controls of the same row. */
        const val ITEM_GAP = 10f

        /** Space ImGui keeps between an item and its label. */
        const val LABEL_GAP = 6f

        /** Horizontal frame padding ImGui draws a button with. */
        const val BUTTON_PADDING = 8f

        val SOURCE_LABELS = SignalSource.entries.map { it.label }.toTypedArray()

        /** Floor for very small windows. */
        const val MIN_PLOT_HEIGHT = 80f

        /** Guarantees the envelope has at least one column to fill. */
        const val MIN_COLUMNS = 1

        /** Keeps the peaks below the top of the axis. */
        const val HEADROOM = 1.15f

        /** Per-frame release factor of the amplitude scale. */
        const val RELEASE = 0.97f

        const val MIN_SCALE = 1e-4f

        val INPUT_COLOR = ImVec4(0.95f, 0.45f, 0.35f, 1f)

        val OUTPUT_COLOR = ImVec4(0.35f, 0.85f, 0.5f, 1f)

        val GAIN_COLOR = ImVec4(0.45f, 0.65f, 0.95f, 1f)
    }
}

/** Samples a window of [millis] holds at AGC2's fixed 48 kHz. */
internal fun windowSamples(millis: Int): Int = millis * SAMPLE_RATE / 1000

/** One sample expressed in milliseconds, for the time axis. */
private const val MILLIS_PER_SAMPLE = 1000.0 / SAMPLE_RATE

/**
 * Formats [value] with [decimals] digits after the point. `String.format` is
 * JVM only, and the example is shared with the native targets.
 */
internal fun fixed(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = round(value * factor).toLong()
    val magnitude = if (scaled < 0) -scaled else scaled
    val whole = magnitude / factor
    val fraction = (magnitude % factor).toString().padStart(decimals, '0')
    return "${if (scaled < 0) "-" else ""}$whole.$fraction"
}

/**
 * Formats a level in dB, with silence - the `-inf` of an empty frame - spelled
 * out instead of being run through the fixed point formatter.
 */
internal fun dbText(value: Float): String =
    if (value.isFinite()) fixed(value.toDouble(), 1) else "-inf"

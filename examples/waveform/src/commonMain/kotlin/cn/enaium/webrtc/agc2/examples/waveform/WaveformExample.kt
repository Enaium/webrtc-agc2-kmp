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

/**
 * Platform specific advice appended when SDL cannot open a video device, or
 * empty when the platform has nothing to add.
 */
internal expect val videoInitHint: String

/**
 * Whether SDL should make the window fullscreen as soon as it is created.
 *
 * On Android the SDL window always covers the whole display, and fullscreen is
 * what makes SDL hide the status and navigation bars, which would otherwise
 * draw on top of the ImGui window (SDLActivity owns that decision, so hiding
 * them from the activity is not enough).
 */
internal expect val windowStartsFullscreen: Boolean

/**
 * Live AGC2 visualization, shared by the JVM and native entry points: the
 * capture - the microphone, or one of the synthesised sources that replace it -
 * is gain controlled at 48 kHz mono by [Agc2Pipeline], the result is played
 * back, and both signals are drawn with ImPlot in an SDL window.
 *
 * The scopes are sized for the longest window the UI can show, so the slider
 * can widen it without touching the pipeline.
 *
 * [frames] bounds the run, which is what the automated (headless) runs use.
 * Returns `false` when no window could be opened.
 */
fun runWaveformExample(frames: Int = Int.MAX_VALUE): Boolean {
    println("webrtc-agc2-kmp waveform example (frames=$frames)")
    val displayed = Agc2Pipeline(windowSamples(MAX_WINDOW_MILLIS)).use { pipeline ->
        val window = WaveformWindow(pipeline)
        // Recording starts on its own: the default source is synthesised, so
        // the example shows the gain moving without anyone pressing anything.
        // The transport in the UI pauses and resumes it.
        pipeline.startRecording()
        val rendered = ImGuiSdlApp.run("webrtc-agc2-kmp waveform", frames) {
            // Moves audio every frame: reads what the devices captured and
            // writes what AGC2 produced, both without blocking. Opening and
            // closing the devices itself happens on the pipeline's device
            // thread, so a slow device cannot hold up the window.
            pipeline.pump()
            window.draw()
        }
        // What a headless run has to show for itself: how much audio was
        // processed and what AGC2 made of it.
        val droppedCapture = pipeline.droppedCaptureFrames
        val droppedPlayback = pipeline.droppedPlaybackFrames
        println(
            "processed ${pipeline.processedFrames} frames of ${pipeline.frameSize} samples" +
                (if (droppedCapture > 0L) ", $droppedCapture capture frames dropped" else "") +
                (if (droppedPlayback > 0L) ", $droppedPlayback playback frames dropped" else "") +
                ": input ${dbText(pipeline.inputDbfs)} dBFS, " +
                "output ${dbText(pipeline.outputDbfs)} dBFS, " +
                "gain ${fixed(pipeline.appliedGainDb.toDouble(), 1)} dB, " +
                "recommended input volume ${pipeline.recommendedInputVolume ?: "none"}",
        )
        rendered
    }
    println(if (displayed) "done" else "aborted")
    return displayed
}
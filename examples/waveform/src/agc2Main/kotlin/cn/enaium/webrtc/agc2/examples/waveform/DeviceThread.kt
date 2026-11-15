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
 * The thread that opens and closes audio devices.
 *
 * Opening a stream is the one call in the audio path that can block for as long
 * as the system takes: a microphone permission prompt waiting for an answer, a
 * device that is still starting, a sample rate the device has to negotiate. The
 * render loop cannot wait for that - a switch to the microphone would freeze
 * the window - so the blocking half lives here, and the pump only ever touches
 * streams that are already open, with non-blocking reads and writes.
 */
internal expect class DeviceThread(name: String, body: () -> Unit) {

    /** Starts the thread; calling it twice is a no-op. */
    fun start()

    /** Waits for the thread to finish; calling it without a start is a no-op. */
    fun join()
}

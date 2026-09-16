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
 * A sliding window of mono samples, written by the capture pump and read by
 * the render loop.
 *
 * Both run on the same thread - the render loop pulls from the device - so the
 * window needs no synchronization.
 */
class WaveformScope(val capacity: Int) {

    private val samples = FloatArray(capacity)

    private var writeIndex = 0

    private var size = 0

    /** Drops every sample, so the plots start from a blank window again. */
    fun clear() {
        writeIndex = 0
        size = 0
    }

    /** Appends [count] samples, overwriting the oldest ones once full. */
    fun append(values: FloatArray, count: Int = values.size) {
        for (i in 0 until count) {
            samples[writeIndex] = values[i]
            if (++writeIndex == capacity) writeIndex = 0
        }
        size = minOf(size + count, capacity)
    }

    /**
     * Copies the newest `destination.size` samples into [destination], oldest
     * sample first, and returns their peak magnitude. Fewer samples than that
     * are zeroed at the front, so the plot always draws a full sweep.
     *
     * The peak comes with the copy because both plots share one amplitude axis
     * and scanning the window twice would be pure overhead.
     */
    fun snapshot(destination: FloatArray): Float {
        val count = minOf(size, destination.size)
        val pad = destination.size - count
        destination.fill(0f, 0, pad)

        var index = writeIndex - count
        if (index < 0) index += capacity
        var peak = 0f
        for (i in 0 until count) {
            val value = samples[index]
            destination[pad + i] = value
            val magnitude = if (value < 0f) -value else value
            if (magnitude > peak) peak = magnitude
            if (++index == capacity) index = 0
        }
        return peak
    }
}

/**
 * A fixed capacity history of one value per processed frame, written by the
 * pump and read by the render loop.
 *
 * Same thread as the [WaveformScope], so it needs no synchronization either.
 * The oldest values are dropped once it is full, which is what makes the level
 * plot scroll with the waveform window.
 */
class HistoryRing(val capacity: Int) {

    private val values = FloatArray(capacity)

    private var writeIndex = 0

    private var size = 0

    /** Drops every value. */
    fun clear() {
        writeIndex = 0
        size = 0
    }

    /** Appends one value, dropping the oldest one once full. */
    fun append(value: Float) {
        values[writeIndex] = value
        if (++writeIndex == capacity) writeIndex = 0
        size = minOf(size + 1, capacity)
    }

    /**
     * Copies the newest `min(count, capacity)` values into the front of
     * [destination], oldest first, and returns how many were copied. The values
     * the ring does not hold yet are left as they are, so the caller can start
     * the line short instead of drawing a stale tail.
     */
    fun copyTo(destination: FloatArray, count: Int): Int {
        val copied = minOf(size, count, destination.size)
        var index = writeIndex - copied
        if (index < 0) index += capacity
        for (i in 0 until copied) {
            destination[i] = values[index]
            if (++index == capacity) index = 0
        }
        return copied
    }
}

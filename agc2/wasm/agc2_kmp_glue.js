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

/*
 * Glue between the Emscripten-compiled AGC2 module (agc2_wasm.js) and the
 * browser targets (js and wasmJs, both from src/webMain). Instantiates the
 * module and publishes the pieces the Kotlin external declarations call:
 *
 *   - every exported webrtc_agc2_* C function as a global of the same name
 *     (handles are wasm32 heap pointers, passed as plain numbers);
 *   - agc2KmpVersion(): the version string, since a char* has to be decoded on
 *     this side;
 *   - agc2KmpHeapF32Get/Set(): per-element access to a float channel, since
 *     wasm JS interop cannot pass a FloatArray;
 *   - agc2KmpCpuFeature(): webrtc_agc2_gain_controller_get_cpu_features
 *     returns a struct by value, which the wasm ABI returns through a hidden
 *     sret pointer, so it cannot be called as an export directly.
 */

export async function initAgc2Kmp() {
    const mod = await import('./agc2_wasm.js');
    const loadModule = typeof mod.default === 'function' ? mod.default : mod;
    const Module = await loadModule();

    for (const key of Object.keys(Module)) {
        if (key.startsWith('_webrtc_agc2_')) {
            globalThis[key.slice(1)] = Module[key];
        }
    }

    globalThis.agc2KmpVersion = () => Module.UTF8ToString(Module._webrtc_agc2_version());

    /* Element access into a float channel: wasm interop passes primitives only. */
    globalThis.agc2KmpHeapF32Get = (ptr, index) => Module.HEAPF32[(ptr >> 2) + index];
    globalThis.agc2KmpHeapF32Set = (ptr, index, value) => {
        Module.HEAPF32[(ptr >> 2) + index] = value;
    };

    globalThis.agc2KmpCpuFeature = (controller, index) => {
        const scratch = Module._malloc(4);
        try {
            Module._webrtc_agc2_gain_controller_get_cpu_features(scratch, controller);
            return Module.HEAPU8[scratch + index] ? 1 : 0;
        } finally {
            Module._free(scratch);
        }
    };

    return Module;
}

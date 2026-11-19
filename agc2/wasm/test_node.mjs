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
 * Smoke test for the Emscripten module and its glue, runnable without a
 * browser: it initializes the module the way a page does, then drives the same
 * C API the Kotlin web actuals call and checks that AGC2 amplifies a quiet
 * signal.
 *
 *   ./gradlew :agc2:assembleWasmNpm -Pwasm.emsdk=$HOME/emsdk
 *   node agc2/wasm/test_node.mjs agc2/build/npm
 */

import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const packageDir = resolve(process.argv[2] ?? 'agc2/build/npm');
assert.ok(existsSync(resolve(packageDir, 'agc2_kmp_glue.js')), `no glue in ${packageDir}: run :agc2:assembleWasmNpm first`);

const { initAgc2Kmp } = await import(pathToFileURL(resolve(packageDir, 'agc2_kmp_glue.js')).href);
const Module = await initAgc2Kmp();

const version = agc2KmpVersion();
assert.match(version, /^WebRTC AGC2 /, `unexpected version: ${version}`);
console.log('version:', version);

const config = webrtc_agc2_config_create_default();
const inputVolumeConfig = webrtc_agc2_input_volume_config_create_default();
const environment = webrtc_agc2_environment_create();
const buffer = webrtc_agc2_audio_buffer_create(48000, 1);
assert.notEqual(config, 0);
assert.notEqual(buffer, 0);

webrtc_agc2_config_set_enabled(config, 1);
webrtc_agc2_config_set_adaptive_digital_enabled(config, 1);
assert.equal(webrtc_agc2_config_validate(config), 1, 'the default configuration must validate');
assert.equal(webrtc_agc2_audio_buffer_samples_per_channel(buffer), 480);

const controller = webrtc_agc2_gain_controller_create(environment, config, inputVolumeConfig, 48000, 1, 1);
assert.notEqual(controller, 0, 'creating the controller must succeed');

const channel = webrtc_agc2_audio_buffer_get_channel_data(buffer, 0);
assert.notEqual(channel, 0);

const frames = 300; // 3 s of audio
const amplitude = 300; // about -41 dBFS
const inputRms = [];
const outputRms = [];
for (let frame = 0; frame < frames; frame++) {
    for (let i = 0; i < 480; i++) {
        agc2KmpHeapF32Set(channel, i, amplitude * Math.sin((frame * 480 + i) * 2 * Math.PI * 220 / 48000));
    }
    let sum = 0;
    for (let i = 0; i < 480; i++) sum += agc2KmpHeapF32Get(channel, i) ** 2;
    inputRms.push(Math.sqrt(sum / 480));

    webrtc_agc2_gain_controller_analyze(controller, 255, buffer);
    webrtc_agc2_gain_controller_process(controller, 0, buffer);

    sum = 0;
    for (let i = 0; i < 480; i++) sum += agc2KmpHeapF32Get(channel, i) ** 2;
    outputRms.push(Math.sqrt(sum / 480));
}

const mean = (values) => values.reduce((a, b) => a + b, 0) / values.length;
const input = mean(inputRms);
const output = mean(outputRms);
const gainDb = 20 * Math.log10(output / input);
assert.ok(gainDb > 3, `AGC2 must amplify a quiet signal, got ${gainDb.toFixed(2)} dB`);

const features = [
    agc2KmpCpuFeature(controller, 0),
    agc2KmpCpuFeature(controller, 1),
    agc2KmpCpuFeature(controller, 2),
];
assert.deepEqual(features, [0, 0, 0], 'wasm dispatches to no SIMD feature');

webrtc_agc2_gain_controller_destroy(controller);
webrtc_agc2_audio_buffer_destroy(buffer);
webrtc_agc2_environment_destroy(environment);
webrtc_agc2_input_volume_config_destroy(inputVolumeConfig);
webrtc_agc2_config_destroy(config);

console.log(`gain: ${gainDb.toFixed(2)} dB over ${frames} frames, cpu features: ${features.join(',')}`);
console.log('ok');

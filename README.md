# webrtc-agc2-kmp

![](https://img.cdn1.vip/i/6aaa7de200508_1789558242.webp)

[![Maven Central](https://img.shields.io/maven-central/v/cn.enaium.webrtc.agc2/webrtc-agc2-kmp?label=Maven%20Central)](https://central.sonatype.com/artifact/cn.enaium.webrtc.agc2/webrtc-agc2-kmp)
[![License](https://img.shields.io/github/license/Enaium/webrtc-agc2-kmp)](https://github.com/Enaium/webrtc-agc2-kmp/blob/main/LICENSE)
[![GitHub Actions](https://img.shields.io/github/actions/workflow/status/Enaium/webrtc-agc2-kmp/test.yml?label=test)](https://github.com/Enaium/webrtc-agc2-kmp/actions/workflows/test.yml)
[![GitHub Repo stars](https://img.shields.io/github/stars/Enaium/webrtc-agc2-kmp?style=social)](https://github.com/Enaium/webrtc-agc2-kmp)

Kotlin Multiplatform bindings for the [WebRTC AGC2](https://github.com/Enaium/webrtc-agc2) automatic gain control — the gain controller that replaces AGC1 in WebRTC's audio processing module. It brings capture audio to the target level by combining four stages:

- **Input volume controller** — recommends the input volume to apply on the audio HAL (microphone volume on a soundcard), reacting to clipping.
- **Adaptive digital controller** — applies a digital gain after echo cancellation and noise suppression, bounded by the noise floor.
- **Fixed digital controller** — applies a constant gain.
- **Limiter** — guarantees the output stays below full scale.

An RNN voice activity detector (`rnn_vad/`) inside the module drives the adaptive gain when `useInternalVad` is enabled.

## Supported Platforms

| Platform       | Targets                                                     | Mechanism                                  |
| -------------- | ----------------------------------------------------------- | ------------------------------------------ |
| **Android**    | arm64-v8a, armeabi-v7a, x86, x86_64                          | JNI (shared library via CMake)             |
| **Android (Kotlin/Native)** | arm64-v8a, armeabi-v7a, x86, x86_64             | Kotlin/Native cinterop (static library)    |
| **JVM**        | Linux x86_64/aarch64, macOS arm64/x86_64, Windows x86_64     | JNI (per-OS/arch JAR resource, auto-extracted by `NativeLoader`) |
| **iOS**        | arm64, x64, simulatorArm64                                   | Kotlin/Native cinterop (static library)    |
| **macOS**      | arm64, x86_64                                                | Kotlin/Native cinterop (static library)    |
| **Linux**      | x86_64                                                       | Kotlin/Native cinterop (static library)    |
| **Windows**    | mingwX64                                                     | Kotlin/Native cinterop (bindings)          |
| **tvOS**       | arm64, simulatorArm64                                        | Kotlin/Native cinterop (static library)    |
| **watchOS**    | arm64, simulatorArm64, deviceArm64                           | Kotlin/Native cinterop (static library)    |

## Gradle Dependency

**Kotlin Multiplatform / Android:**

```kotlin
implementation("cn.enaium.webrtc.agc2:webrtc-agc2-kmp:1.0.0")
```

> Built with Kotlin 2.4.10: consumers need a Kotlin 2.4+ compiler, since older ones cannot read the 2.4 metadata of the published artifacts.

**JVM:** the right native binary is resolved automatically — the `webrtc-agc2-kmp-jvm` artifact pulls in the matching `:jni-jvm-*` sibling on the classpath:

- `webrtc-agc2-kmp-jni-jvm-linux-x86_64`
- `webrtc-agc2-kmp-jni-jvm-linux-aarch64`
- `webrtc-agc2-kmp-jni-jvm-darwin-x86_64`
- `webrtc-agc2-kmp-jni-jvm-darwin-aarch64`
- `webrtc-agc2-kmp-jni-jvm-windows-x86_64`

`NativeLoader` detects `os.name`/`os.arch` at runtime, extracts the matching binary from the classpath to a temp directory, and `System.load`s it. No `java.library.path` setup is required for downstream JVM consumers.

## Quick Start

```kotlin
import cn.enaium.webrtc.agc2.createAgc2AudioBuffer
import cn.enaium.webrtc.agc2.createAgc2Config
import cn.enaium.webrtc.agc2.createAgc2Environment
import cn.enaium.webrtc.agc2.createAgc2GainController
import cn.enaium.webrtc.agc2.createAgc2InputVolumeConfig

// 1. Create the object graph
val env = createAgc2Environment()
val config = createAgc2Config().apply {
    enabled = true
    inputVolumeControllerEnabled = true
    adaptiveDigitalEnabled = true
    fixedDigitalGainDb = 0f
}
val inputVolumeConfig = createAgc2InputVolumeConfig()
val agc2 = createAgc2GainController(
    env = env,
    config = config,
    inputVolumeConfig = inputVolumeConfig,
    sampleRate = 16000,
    channels = 1,
    useInternalVad = true,
)

// 2. Process 10 ms frames (160 samples @ 16 kHz)
val frame = createAgc2AudioBuffer(16000, 1)
var appliedInputVolume = 255

// `captureFrame` holds one 10 ms frame at WebRTC's FloatS16 scale.
frame.writeChannel(0, captureFrame)
agc2.analyze(appliedInputVolume, frame)              // clipping detection and prediction
agc2.process(inputVolumeChanged = false, buffer = frame) // in-place gain + limiter
agc2.recommendedInputVolume?.let { appliedInputVolume = it } // apply it on the audio HAL

val processed = frame.readChannel(0)

// 3. Cleanup
frame.close()
agc2.close()
inputVolumeConfig.close()
config.close()
env.close()
```

## API Reference

```kotlin
val agc2Version: String
fun createAgc2Config(): Agc2Config
fun createAgc2InputVolumeConfig(): Agc2InputVolumeConfig
fun createAgc2Environment(): Agc2Environment
fun createAgc2AudioBuffer(sampleRate: Int, channels: Int): Agc2AudioBuffer
fun createAgc2GainController(
    env: Agc2Environment,
    config: Agc2Config,
    inputVolumeConfig: Agc2InputVolumeConfig,
    sampleRate: Int,
    channels: Int,
    useInternalVad: Boolean = true,
): Agc2GainController
```

### Agc2GainController

| Member                                              | Description                                                       |
| --------------------------------------------------- | ----------------------------------------------------------------- |
| `analyze(appliedInputVolume, buffer)`               | Clipping analysis, before any digital processing (e.g. before AEC) |
| `process(inputVolumeChanged, buffer)`               | Updates the recommendation, applies the adaptive/fixed gains and the limiter in place |
| `setFixedGainDb(gainDb)`                            | Sets the fixed digital gain in dB                                 |
| `setCaptureOutputUsed(captureOutputUsed)`           | Tells the input volume controller whether the capture output is used |
| `recommendedInputVolume: Int?`                      | Recommended input volume in `[0, 255]`, or `null` when there is none |
| `cpuFeatures: Agc2CpuFeatures`                      | SIMD features the loaded build dispatches to (SSE2/AVX2/NEON)      |

### Configuration

Both config objects are mutable views over the native configuration; every field maps one-to-one onto `AudioProcessing::Config::GainController2` and `InputVolumeController::Config`:

```kotlin
createAgc2Config().use { config ->
    config.enabled                            // AGC2 master switch
    config.inputVolumeControllerEnabled       // input volume controller
    config.adaptiveDigitalEnabled             // adaptive digital controller
    config.adaptiveDigitalHeadroomDb          // 5 dB        headroom above the saturation point
    config.adaptiveDigitalMaxGainDb           // 50 dB       gain ceiling
    config.adaptiveDigitalInitialGainDb       // 15 dB       gain at stream start
    config.adaptiveDigitalMaxGainChangeDbPerSecond // 6 dB/s adaptation rate ceiling
    config.adaptiveDigitalMaxOutputNoiseLevelDbfs  // -50 dBFS noise floor target
    config.fixedDigitalGainDb                 // 0 dB        fixed digital gain
    config.validate()                         // whether WebRTC accepts the configuration
}

createAgc2InputVolumeConfig().use { config ->
    config.minInputVolume                     // 20    lowest recommendation
    config.clippedLevelMin                    // 70    lowest recommendation after clipping
    config.clippedLevelStep                   // 15    drop per clipping event
    config.clippedRatioThreshold              // 0.1   clipped-sample fraction that declares a clipping event
    config.clippedWaitFrames                  // 300   frames between clipping checks
    config.enableClippingPredictor            // true
    config.targetRangeMaxDbfs                 // -12 dBFS speech level range ceiling
    config.targetRangeMinDbfs                 // -50 dBFS speech level range floor
    config.updateInputVolumeWaitFrames        // 100
    config.speechProbabilityThreshold         // 0.7   speech/silence threshold
    config.speechRatioThreshold               // 0.6   minimum speech frame ratio for an update
}
```

Creating a controller with a configuration that fails `validate()` throws `IllegalStateException`, as does a non-positive `sampleRate` or `channels`.

### Audio Formats

- **Frame size:** one 10 ms frame — `sampleRate / 100` samples per channel (`160` at 16 kHz, `480` at 48 kHz). `createAgc2AudioBuffer` reports it as `samplesPerChannel`.
- **Sample scale:** FloatS16 — `[-32768, 32768]` maps to full scale, the scale WebRTC's `AudioBuffer` uses internally. Not `-1..1`.
- **Sample rates:** WebRTC's AGC2 captures at 8000, 16000, 32000 or 48000 Hz.
- **Channels:** input, internal and output frames share the channel count passed to `createAgc2AudioBuffer` and `createAgc2GainController`.

## Examples

### [`examples/basic`](examples/basic/) — Android Compose demo

An Android app that runs the microphone through AGC2 in real time:

- **Start/Stop** — a real-time `AudioRecord → AGC2 → AudioTrack` loopback on a worker thread, 16 kHz mono
- **AGC2 switch** — bypass the controller and hear the raw capture
- **Adaptive digital / input volume controller / internal VAD switches** — each one rebuilds the controller on the audio thread, because WebRTC copies the configuration when the controller is constructed
- **Fixed gain slider** (0–40 dB) — the one gain setting a running controller takes live, through `setFixedGainDb`
- **Input volume** — the applied volume (0–255) with an auto-apply switch that feeds AGC2's recommendation back the way an audio HAL integration would, plus a manual slider
- **Reference tone** (440 Hz, level adjustable) — played through the speaker so the microphone has a known signal to work on. AGC2 is capture-only, so unlike the AEC3 example the tone is not fed to the controller; it only excites the microphone
- **Levels** — input/output RMS in dBFS, the gain AGC2 applied, the recommended input volume and the frame count

```bash
./gradlew :examples:basic:assembleDebug   # APK
./gradlew :examples:basic:connectedDebugAndroidTest   # on a device
```

The instrumentation test synthesizes its own signals (the repository ships no audio fixtures): quiet speech-like audio has to come out more than 3 dB louder, and sustained clipping has to pull the recommended input volume down in whole `clippedLevelStep` steps without ever going below `minInputVolume`.

### [`examples/waveform`](examples/waveform/) — live gain visualization

A Kotlin Multiplatform app (JVM desktop, macOS/Linux/Windows native executables and Android via [`examples/waveform/android`](examples/waveform/android/)) that draws what AGC2 does with Dear ImGui + ImPlot in an SDL3 window:

- **Two waveform traces** — the capture AGC2 was handed and what came out of it, over a 100 ms–10 s sliding window
- **A level history plot** — input and output RMS in dBFS and the gain AGC2 applied, frame by frame, with the recommended input volume as a readout
- **Record/pause transport** — recording opens the capture device (or starts the synthesised source) and pause releases the microphone; *monitor* sends what AGC2 produces to the speaker, and the drops count shows when the output device takes less than AGC2 produces
- **Controls stay on screen** — the transport and the knobs are all the UI shows, laid out in rows that wrap when the window is narrow (a phone cannot show one row of them); the device names, the format, the processed frame count, the levels, the peaks and the explanations sit in a collapsed *details* header, so the three plots get the room
- **Signal sources** — *Microphone* runs the real capture, or the synthetic *Synthetic speech* / *White noise* / *Silence* sources replace the captured frame, so the controller runs with no input device at all; speech is the default, which is what makes the gain visibly ramp up on any machine
- **AGC2 controls** — bypass, adaptive digital, input volume controller, internal VAD (each rebuilding the controller), fixed gain (live), applied input volume (0–255) with auto-apply, source level and playback level
- **Audio driven from the render loop** — the window pulls the capture device with non-blocking reads, runs every 10 ms frame through AGC2 and pushes what comes out to the playback device with non-blocking writes; the devices buffer ten frames, far more than one UI frame, so a slow frame cannot drop audio. A pump processes at most four frames and drops the older backlog (the *details* section counts it), which is what keeps one slow frame from turning into a slower one. Opening and closing a device is the one call in that path that can block (a microphone permission prompt, a device that is still starting, a sample rate to negotiate), so it runs on a thread of its own — switching to *Microphone* never waits for the device
- **Audible result** — the AGC2 output is played through the output device, so the gain is not just visible

```bash
./gradlew :examples:waveform:jvmRun                  # desktop JVM
./gradlew :examples:waveform:android:assembleDebug   # Android APK
```

Native executables (`./examples/waveform/build/bin/<target>/<mode>Executable/waveform.kexe`) take `AGC2_KMP_FRAMES` to bound a headless run; the JVM entry takes `--frames N`. A headless run (SDL's dummy video driver, no audio devices) still processes audio: the synthetic source paces itself at 10 ms per frame, and playback that cannot be delivered is dropped and counted instead of holding up the capture.

## Building from Source

### Prerequisites

- JDK 17+
- CMake 3.16+
- Android SDK + NDK (for Android targets)
- Xcode command-line tools (for iOS/macOS/tvOS/watchOS targets)

### Clone with submodules

```bash
git clone --recursive git@github.com:Enaium/webrtc-agc2-kmp.git
cd webrtc-agc2-kmp
```

### Publish to Maven Local

```bash
./gradlew :agc2:publishToMavenLocal
```

### Run tests

```bash
./gradlew :agc2:jvmTest         # JVM (JNI)
./gradlew :agc2:macosArm64Test  # macOS native (cinterop)
```

The suites are shared (`commonTest`), so the same scenarios run through both binding paths: fixed digital gain and the limiter, clipping-driven input volume recommendations, the adaptive digital gain with the internal RNN VAD, configuration round-trips and rejection of invalid configurations.

## Project Structure

```
webrtc-agc2-kmp/
├── webrtc-agc2/              # Git submodule (C++ library)
├── jni/
│   ├── CMakeLists.txt        # JNI shared library build
│   ├── jni_bridge.cpp        # JNI bridge (C++ → JVM/Android)
│   ├── c_api/                # C API (webrtc_agc2_c.h/.cc)
│   └── jvm/                  # Per-OS/arch JNI publication subprojects
│       ├── darwin-aarch64, darwin-x86_64
│       ├── linux-x86_64, linux-aarch64
│       └── windows-x86_64
├── agc2/                     # Kotlin Multiplatform module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/       # expect declarations + common interfaces
│       ├── commonTest/       # shared scenarios, run on JVM and native
│       ├── jvmMain/          # JVM actual (JNI) + NativeLoader
│       ├── androidMain/      # Android actual (JNI)
│       ├── nativeMain/       # Native actual (cinterop)
│       └── nativeInterop/cinterop/
├── examples/
│   ├── basic/                # Android Compose demo (live loopback + controls)
│   └── waveform/             # KMP ImGui/ImPlot gain visualization
│       └── android/          # APK wrapping the androidNative libmain.so
├── scripts/                  # Native build helpers
└── .github/workflows/        # publish + test
```

## License

[MIT](LICENSE) — the bundled WebRTC AGC2 source ([webrtc-agc2](https://github.com/Enaium/webrtc-agc2)) is BSD-3-Clause.

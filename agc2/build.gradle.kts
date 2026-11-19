import java.io.File
import java.util.Properties
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.maven.publish)
}

group = rootProject.group
version = rootProject.version

val jniDir = rootProject.projectDir.resolve("jni")

val hostOs = OperatingSystem.current()

// ==================== Android NDK resolution ====================
// Resolved before canBuildNativeTarget (and therefore before the kotlin {}
// block) so that the androidNative targets can embed the NDK-cross-compiled
// static library on hosts that have the NDK installed.
val androidApiLevel = 21

val pinnedAndroidNdkVersion = "27.0.12077973"

fun resolveAndroidSdkDir(): java.io.File? {
    listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { key ->
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let {
            val f = file(it)
            if (f.isDirectory) return f
        }
    }
    val localProps = rootProject.file("local.properties")
    if (localProps.isFile) {
        val props = Properties().apply { localProps.inputStream().use { load(it) } }
        props.getProperty("sdk.dir")?.takeIf { it.isNotBlank() }?.let {
            val f = file(it)
            if (f.isDirectory) return f
        }
    }
    return null
}

fun resolveAndroidNdkDir(): java.io.File? {
    listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "NDK_HOME").forEach { key ->
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let {
            val f = file(it)
            if (f.isDirectory) return f
        }
    }
    val sdk = resolveAndroidSdkDir() ?: return null
    val ndkParent = sdk.resolve("ndk")
    if (!ndkParent.isDirectory) return null
    val pinned = ndkParent.resolve(pinnedAndroidNdkVersion)
    if (pinned.isDirectory) return pinned
    return ndkParent.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
}

val resolvedAndroidNdk = resolveAndroidNdkDir()
val androidNdkToolchain = resolvedAndroidNdk?.resolve("build/cmake/android.toolchain.cmake")

// Whether the current host can cross-compile the C library for the given
// Kotlin/Native target. Apple targets build from macOS via Xcode; linuxX64 and
// mingwX64 build on Linux hosts (mingwX64 via the MinGW cross-compiler, which
// matches Kotlin/Native's own MinGW linker).
fun canBuildNativeTarget(targetName: String): Boolean {
    return when {
        hostOs.isMacOsX && targetName.startsWith("macos") -> true
        hostOs.isMacOsX && targetName.startsWith("ios") -> true
        hostOs.isMacOsX && targetName.startsWith("tvos") -> true
        hostOs.isMacOsX && targetName.startsWith("watchos") -> true
        hostOs.isLinux && (targetName == "linuxX64" || targetName == "mingwX64") -> true
        // On Windows, MinGW is the native toolchain, so mingw_x64 static
        // libraries build there too (the Windows CI job links and runs the
        // mingwX64 tests with it).
        hostOs.isWindows && targetName == "mingwX64" -> true
        // Android native (Kotlin/Native) static libraries are cross-compiled
        // with the NDK toolchain, which any host with the NDK can run.
        targetName.startsWith("androidNative") && androidNdkToolchain?.isFile == true -> true
        else -> false
    }
}

// Resolved once in the root build (which also covers the Android SDK's copy,
// for machines that have no standalone cmake).
val cmakeExecutable: String = rootProject.extra["cmakeExecutable"] as String

kotlin {
    // ==================== JVM ====================
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // ==================== Android ====================
    android {
        namespace = "cn.enaium.webrtc.agc2"
        compileSdk = 37
        minSdk = 21

        withHostTest {}

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_21)
                }
            }
        }
    }

    // ==================== Native ====================
    macosArm64()
    macosX64()

    linuxX64()

    mingwX64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()

    watchosArm64()
    watchosSimulatorArm64()
    watchosDeviceArm64()

    // Android native (Kotlin/Native) targets, used by Android apps that link a
    // libmain.so instead of the JVM AAR.
    androidNativeArm64()
    androidNativeArm32()
    androidNativeX64()
    androidNativeX86()

    // ==================== Web (js + wasmJs) ====================
    // Kotlin/Wasm and Kotlin/JS have no cinterop, so both targets share one
    // implementation (src/webMain) that drives the same C API compiled to
    // wasm by Emscripten. The module ships as the @enaium/webrtc-agc2-kmp npm
    // package (see the wasm tasks at the end of this file); the page has to
    // initialize it before the Kotlin code runs.
    js {
        browser()
    }

    wasmJs {
        browser()
    }

    // ==================== cinterop for all native targets ====================
    targets.withType<KotlinNativeTarget> {
        val targetName = this.name
        val canBuild = canBuildNativeTarget(targetName)
        compilations.getByName("main") {
            cinterops {
                create("webrtcAgc2") {
                    defFile(project.file("src/nativeInterop/cinterop/webrtc_agc2.def"))
                    includeDirs(
                        project.file("src/nativeInterop/cinterop"),
                        rootProject.file("jni/c_api"),
                    )
                    if (canBuild) {
                        // Embed the per-target static library into the produced
                        // cinterop klib. Targets that can't be built on this host
                        // still get bindings (for klib publishing); the static
                        // library is built and embedded when building on the
                        // matching host.
                        val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
                        extraOpts(
                            "-libraryPath", outputDir.absolutePath,
                            "-staticLibrary", "libwebrtc_agc2.a",
                        )
                    }
                }
            }
            defaultSourceSet.kotlin.srcDir("src/nativeMain/kotlin")
        }
    }

    // ==================== Source sets ====================
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        // Browser implementation shared by js and wasmJs: the external
        // declarations, the Emscripten heap access and the C API wrappers are
        // identical for both, only the module loading glue differs at runtime.
        val webMain by creating {
            dependsOn(getByName("commonMain"))
        }
        getByName("jsMain").dependsOn(webMain)
        getByName("wasmJsMain").dependsOn(webMain)

        getByName("jvmMain") {
            dependencies {
                // Bundle every JNI artifact so consumers get the right native
                // binary out of the box; NativeLoader picks one at runtime by
                // os.name/os.arch.
                runtimeOnly(project(":jni-jvm-linux-x86_64"))
                runtimeOnly(project(":jni-jvm-linux-aarch64"))
                runtimeOnly(project(":jni-jvm-darwin-x86_64"))
                runtimeOnly(project(":jni-jvm-darwin-aarch64"))
                runtimeOnly(project(":jni-jvm-windows-x86_64"))
            }
        }

        getByName("jvmTest") {
            dependencies {
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }

        getByName("androidMain") {
            // Share the same JNI source code with JVM. On Android the
            // NativeLoader falls back to System.loadLibrary since the .so is
            // bundled inside the AAR's jniLibs.
            kotlin.srcDir("src/jvmMain/kotlin")
        }
    }
}

// ==================== Desktop JVM ====================
// The JNI library is provided by the :jni-jvm-{linux,darwin}-{x86_64,aarch64}
// subprojects. Their JARs ship libwebrtc_agc2_jni as a classpath resource that
// NativeLoader extracts at runtime - so :jvmTest needs no java.library.path
// tweak.
//
// Android host-test targets (Android unit tests running on the JVM) call
// System.loadLibrary directly from Agc2.jvm.kt, so they still need a flat
// directory containing libwebrtc_agc2_jni.{so,dylib}. We reuse the host's
// :jni-jvm-* subproject build output for that.
val hostJniProjectName = run {
    val arch = System.getProperty("os.arch").lowercase()
    val os = OperatingSystem.current()
    val archClassifier = when (arch) {
        "amd64", "x86_64", "x64" -> "x86_64"
        "aarch64", "arm64" -> "aarch64"
        else -> null
    }
    val osClassifier = when {
        os.isLinux -> "linux"
        os.isMacOsX -> "darwin"
        os.isWindows -> "windows"
        else -> null
    }
    if (osClassifier != null && archClassifier != null) {
        ":jni-jvm-$osClassifier-$archClassifier"
    } else {
        null
    }
}

if (hostJniProjectName != null) {
    val hostJniProject = project(hostJniProjectName)
    val hostNativeDir = hostJniProject.layout.buildDirectory.dir(
        "jni-native/${hostJniProjectName.removePrefix(":jni-jvm-")}",
    )

    tasks.withType<Test>().configureEach {
        if (name.contains("AndroidHostTest", ignoreCase = true)) {
            dependsOn("$hostJniProjectName:buildJniLibrary")
            systemProperty("java.library.path", hostNativeDir.get().asFile.absolutePath)
        }
    }
}

// ==================== Native: build static C library for each target ====================
fun registerNativeBuildTasks(targetName: String, cmakeFlags: List<String> = emptyList()) {
    val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
    val cmakeBuildDir = layout.buildDirectory.dir("cmake-$targetName").get().asFile

    val configureTask = tasks.register<Exec>("configureNative_$targetName") {
        onlyIf { canBuildNativeTarget(targetName) }
        doFirst {
            cmakeBuildDir.mkdirs()
            outputDir.mkdirs()
            if (targetName == "mingwX64" && hostOs.isLinux) {
                // webRTC includes <Windows.h> (capital W), but on Linux the
                // mingw-w64 header is windows.h (lowercase) and the
                // case-sensitive filesystem can't resolve it. Create an
                // alias symlink in the mingw include dir (needs root).
                val includeDir = File("/usr/x86_64-w64-mingw32/include")
                val lower = File(includeDir, "windows.h")
                val upper = File(includeDir, "Windows.h")
                if (lower.isFile && !upper.exists()) {
                    val proc = ProcessBuilder("sudo", "ln", "-s", "windows.h", upper.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { logger.lifecycle(it) }
                    }
                    proc.waitFor()
                }
            }
        }
        workingDir = cmakeBuildDir
        commandLine(
            listOf(
                cmakeExecutable, jniDir.absolutePath,
                "-DCMAKE_BUILD_TYPE=Release",
                "-DBUILD_JNI=OFF",
                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=${outputDir.absolutePath}",
            ) + cmakeFlags,
        )
    }

    val buildTask = tasks.register<Exec>("buildNative_$targetName") {
        onlyIf { canBuildNativeTarget(targetName) }
        dependsOn(configureTask)
        workingDir = cmakeBuildDir
        commandLine(cmakeExecutable, "--build", ".", "--config", "Release")
    }

    tasks.matching {
        it.name.startsWith("cinteropWebrtcAgc2") &&
            it.name.endsWith(targetName.replaceFirstChar { c -> c.uppercase() })
    }.configureEach {
        dependsOn(buildTask)
    }
}

if (hostOs.isMacOsX) {
    registerNativeBuildTasks(
        "macosArm64",
        listOf(
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
        ),
    )
    registerNativeBuildTasks(
        "macosX64",
        listOf(
            "-DCMAKE_OSX_ARCHITECTURES=x86_64",
            "-DCMAKE_SYSTEM_PROCESSOR=x86_64",
        ),
    )
    registerNativeBuildTasks(
        "iosArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=iOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=iphoneos",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
        ),
    )
    registerNativeBuildTasks(
        "iosX64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=iOS",
            "-DCMAKE_SYSTEM_PROCESSOR=x86_64",
            "-DCMAKE_OSX_ARCHITECTURES=x86_64",
            "-DCMAKE_OSX_SYSROOT=iphonesimulator",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
        ),
    )
    registerNativeBuildTasks(
        "iosSimulatorArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=iOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=iphonesimulator",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
        ),
    )
    registerNativeBuildTasks(
        "tvosArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=tvOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=appletvos",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
        ),
    )
    registerNativeBuildTasks(
        "tvosSimulatorArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=tvOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=appletvsimulator",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.0",
        ),
    )
    registerNativeBuildTasks(
        "watchosArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=watchOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=watchos",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=5.0",
        ),
    )
    registerNativeBuildTasks(
        "watchosSimulatorArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=watchOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=watchsimulator",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=5.0",
        ),
    )
    registerNativeBuildTasks(
        "watchosDeviceArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=watchOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=watchos",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=5.0",
        ),
    )
} else if (hostOs.isLinux) {
    registerNativeBuildTasks("linuxX64")
    // mingwX64 is cross-compiled with MinGW-w64; install it with
    // `sudo apt-get install gcc-mingw-w64-x86-64 g++-mingw-w64-x86-64`.
    // CMAKE_SYSTEM_NAME=Windows makes CMake cross-compile; the MinGW sysroot
    // at /usr/x86_64-w64-mingw32 provides Windows.h and the CRT.
    registerNativeBuildTasks(
        "mingwX64",
        listOf(
            // CMAKE_SYSTEM_NAME=Windows makes CMake treat this as a Windows
            // cross-build (WIN32 branch, .a archive in MinGW format, WEBRTC_WIN
            // defined). The mingw-w64 include dir is added in jni/CMakeLists.txt.
            "-DCMAKE_SYSTEM_NAME=Windows",
            "-DCMAKE_SYSTEM_PROCESSOR=x86_64",
            "-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc",
            "-DCMAKE_CXX_COMPILER=x86_64-w64-mingw32-g++",
            "-DCMAKE_RC_COMPILER=x86_64-w64-mingw32-windres",
        ),
    )
} else if (hostOs.isWindows) {
    // MinGW is the native toolchain here, so no cross-compilation flags are
    // needed; the generator is picked the same way the Windows JNI subproject
    // does it (MSVC is avoided: webrtc-agc2 relies on GCC extensions).
    registerNativeBuildTasks(
        "mingwX64",
        listOf("-G", if (System.getenv("MSYSTEM") != null) "MSYS Makefiles" else "MinGW Makefiles"),
    )
}

// ==================== Android native (Kotlin/Native) static libraries ====================
// Cross-compiled with the NDK toolchain for the androidNative targets; the
// resulting libwebrtc_agc2.a is embedded into each target's cinterop klib, so a
// Kotlin/Native Android app (libmain.so) links WebRTC AGC2 statically.
androidNdkToolchain?.takeIf { it.isFile }?.let { toolchain ->
    listOf(
        "androidNativeArm64" to "arm64-v8a",
        "androidNativeArm32" to "armeabi-v7a",
        "androidNativeX64" to "x86_64",
        "androidNativeX86" to "x86",
    ).forEach { (targetName, abi) ->
        registerNativeBuildTasks(
            targetName,
            listOf(
                "-DCMAKE_TOOLCHAIN_FILE=${toolchain.absolutePath}",
                "-DANDROID_ABI=$abi",
                "-DANDROID_PLATFORM=android-$androidApiLevel",
            ),
        )
    }
}

// ==================== Android: build JNI shared library per ABI ====================
val androidJniAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

val androidJniLibsDir = layout.buildDirectory.dir("jniLibs")

val buildAndroidJniLibs by tasks.registering {
    group = "build"
    description = "Builds the JNI shared library for all Android ABIs."
}

androidJniAbis.forEach { abi ->
    val outputDir = layout.buildDirectory.dir("jniLibs/$abi")
    val cmakeBuildDir = layout.buildDirectory.dir("cmake-android-$abi").get().asFile

    val configureTask = tasks.register<Exec>("configureAndroidJni_$abi") {
        onlyIf { androidNdkToolchain?.isFile == true }
        doFirst {
            cmakeBuildDir.mkdirs()
            outputDir.get().asFile.mkdirs()
        }
        workingDir = cmakeBuildDir
        commandLine(
            cmakeExecutable, jniDir.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_TOOLCHAIN_FILE=${androidNdkToolchain?.absolutePath ?: ""}",
            "-DANDROID_ABI=$abi",
            "-DANDROID_PLATFORM=android-$androidApiLevel",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${outputDir.get().asFile.absolutePath}",
        )
    }

    val buildTask = tasks.register<Exec>("buildAndroidJni_$abi") {
        onlyIf { androidNdkToolchain?.isFile == true }
        dependsOn(configureTask)
        workingDir = cmakeBuildDir
        commandLine(cmakeExecutable, "--build", ".", "--config", "Release")
    }

    buildAndroidJniLibs.configure { dependsOn(buildTask) }
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addStaticSourceDirectory(
            androidJniLibsDir.get().asFile.absolutePath,
        )
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.contains("JniLibFolders") }
    .configureEach { dependsOn(buildAndroidJniLibs) }

// ==================== Publishing ====================
// Version is read from the project version (root build.gradle.kts or
// -Pversion on the command line).
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = "webrtc-agc2-kmp",
        // null -> the plugin falls back to project.version
        version = null,
    )

    pom {
        name.set("webrtc-agc2-kmp")
        description.set(
            "Kotlin Multiplatform bindings for the WebRTC AGC2 automatic gain control.",
        )
        url.set("https://github.com/Enaium/webrtc-agc2-kmp")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("BSD-3-Clause")
                url.set("https://opensource.org/licenses/BSD-3-Clause")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("Enaium")
            }
        }

        scm {
            url.set("https://github.com/Enaium/webrtc-agc2-kmp")
            connection.set("scm:git:git@github.com:Enaium/webrtc-agc2-kmp.git")
            developerConnection.set("scm:git:git@github.com:Enaium/webrtc-agc2-kmp.git")
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/Enaium/webrtc-agc2-kmp/issues")
        }
    }
}

// ==================== wasm: build the AGC2 module ====================
// Kotlin/Wasm has no cinterop and cannot embed the static library, so the same
// C API (jni/c_api) is compiled to a standalone Emscripten module
// (agc2_wasm.js + agc2_wasm.wasm) that the js and wasmJs actuals drive through
// the JS glue in wasm/agc2_kmp_glue.js.

fun emsdkPath(): String {
    System.getenv("EMSDK")?.takeIf { it.isNotBlank() }?.let { return it }
    providers.gradleProperty("wasm.emsdk").orNull?.takeIf { it.isNotBlank() }?.let { return it }
    val home = System.getProperty("user.home")
    listOf(File(home, "emsdk"), File(home, "sdk/emsdk"))
        .forEach { if (it.isDirectory()) return it.absolutePath }
    return ""
}

val wasmEmsdk = emsdkPath()
val wasmEmxx = File(wasmEmsdk, "upstream/emscripten/em++")
val wasmToolchain = File(wasmEmsdk, "upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake")
val agc2WasmDir = projectDir.resolve("wasm")
val agc2WasmOutput = layout.buildDirectory.dir("wasmAgc2").get().asFile

// Emscripten requires Python >= 3.10; the emsdk ships its own interpreter,
// which may not be on PATH (e.g. macOS ships Python 3.9 as /usr/bin/python3).
fun wasmEnv(): Map<String, String> {
    val pythonDir = File(wasmEmsdk, "python").listFiles()?.firstOrNull { it.isDirectory }
    return if (pythonDir != null) {
        mapOf("PATH" to "${pythonDir.absolutePath}/bin:${System.getenv("PATH")}")
    } else {
        emptyMap()
    }
}

/** Every `webrtc_agc2_*` function the C API header declares, as emcc exports. */
fun agc2CExports(): String = Regex("webrtc_agc2_\\w+(?=\\()")
    .findAll(rootProject.file("jni/c_api/webrtc_agc2_c.h").readText())
    .map { it.value }
    .distinct()
    .joinToString(",") { "_$it" }

tasks.register<Exec>("configureWasmAgc2") {
    onlyIf { wasmEmsdk.isNotEmpty() }
    doFirst {
        File(agc2WasmOutput, "cmake").mkdirs()
        agc2WasmOutput.mkdirs()
    }
    workingDir = File(agc2WasmOutput, "cmake")
    environment(wasmEnv())
    // A toolchain file disables the generator's normal make discovery, so the
    // make program must be passed explicitly (emcmake does the same).
    val makeProgram = if (hostOs.isWindows) "nmake" else "/usr/bin/make"
    commandLine(
        cmakeExecutable, jniDir.absolutePath,
        "-DBUILD_JNI=OFF",
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_TOOLCHAIN_FILE=${wasmToolchain.absolutePath}",
        "-DCMAKE_MAKE_PROGRAM=${makeProgram}",
    )
}

tasks.register<Exec>("buildWasmAgc2") {
    onlyIf { wasmEmsdk.isNotEmpty() }
    dependsOn("configureWasmAgc2")
    workingDir = File(agc2WasmOutput, "cmake")
    environment(wasmEnv())
    commandLine(cmakeExecutable, "--build", ".", "--config", "Release")
}

tasks.register<Exec>("linkWasmAgc2") {
    onlyIf { wasmEmsdk.isNotEmpty() }
    dependsOn("buildWasmAgc2")
    workingDir = agc2WasmDir
    environment(wasmEnv())
    doFirst {
        agc2WasmOutput.mkdirs()
        // em++ (not emcc): the library is C++ and needs libc++/libc++abi on
        // the link line.
        commandLine(
            wasmEmxx.absolutePath,
            File(agc2WasmOutput, "cmake/libwebrtc_agc2.a").absolutePath,
            "-o", File(agc2WasmOutput, "agc2_wasm.js").absolutePath,
            "-sMODULARIZE=1", "-sEXPORT_NAME=Agc2Module", "-sEXPORT_ES6=1",
            "-sEXPORTED_FUNCTIONS=${agc2CExports()},_malloc,_free",
            "-sEXPORTED_RUNTIME_METHODS=ccall,cwrap,UTF8ToString,stringToUTF8,lengthBytesUTF8,HEAPU8,HEAP32,HEAPU32,HEAPF32,HEAPF64",
            "-sALLOW_MEMORY_GROWTH=1", "-sINITIAL_MEMORY=67108864",
            "-sENVIRONMENT=web,worker,node",
            "--no-entry",
        )
    }
    inputs.file(rootProject.file("jni/c_api/webrtc_agc2_c.h"))
    inputs.file(File(agc2WasmOutput, "cmake/libwebrtc_agc2.a"))
    outputs.file(File(agc2WasmOutput, "agc2_wasm.js"))
    outputs.file(File(agc2WasmOutput, "agc2_wasm.wasm"))
}

// ==================== wasm: npm package ====================
// The Emscripten module and the JS glue ship as an npm package
// (@enaium/webrtc-agc2-kmp) instead of a Maven "assets" jar: web apps depend on
// it like any other npm package and `import { initAgc2Kmp } from
// "@enaium/webrtc-agc2-kmp"` before running the Kotlin module, so no asset
// extraction step is involved. The package version is the project version, so
// Maven and npm share it.

val npmPackageVersion: String by lazy { project.version.toString() }

val wasmNpmDir = layout.buildDirectory.dir("npm").get().asFile
val wasmGlue = agc2WasmDir.resolve("agc2_kmp_glue.js")

tasks.register("assembleWasmNpm") {
    group = "build"
    description = "Stages the @enaium/webrtc-agc2-kmp npm package in build/npm."
    dependsOn("linkWasmAgc2")
    inputs.file(wasmGlue)
    // The version comes from the Gradle version, so a bump must re-stage.
    inputs.property("npmPackageVersion", npmPackageVersion)
    inputs.file(File(agc2WasmOutput, "agc2_wasm.js"))
    inputs.file(File(agc2WasmOutput, "agc2_wasm.wasm"))
    outputs.dir(wasmNpmDir)
    doLast {
        wasmNpmDir.deleteRecursively()
        wasmNpmDir.mkdirs()
        wasmGlue.copyTo(wasmNpmDir.resolve("agc2_kmp_glue.js"), overwrite = true)
        File(agc2WasmOutput, "agc2_wasm.js").copyTo(wasmNpmDir.resolve("agc2_wasm.js"), overwrite = true)
        File(agc2WasmOutput, "agc2_wasm.wasm").copyTo(wasmNpmDir.resolve("agc2_wasm.wasm"), overwrite = true)
        wasmNpmDir.resolve("package.json").writeText(
            """
            {
              "name": "@enaium/webrtc-agc2-kmp",
              "version": "$npmPackageVersion",
              "description": "Emscripten-compiled WebRTC AGC2 module and JS glue for the webrtc-agc2-kmp js/wasmJs targets",
              "license": "BSD-3-Clause",
              "type": "module",
              "exports": {
                ".": "./agc2_kmp_glue.js",
                "./agc2_wasm.js": "./agc2_wasm.js",
                "./package.json": "./package.json"
              },
              "files": ["agc2_kmp_glue.js", "agc2_wasm.js", "agc2_wasm.wasm"],
              "homepage": "https://github.com/Enaium/webrtc-agc2-kmp",
              "repository": {
                "type": "git",
                "url": "git+https://github.com/Enaium/webrtc-agc2-kmp.git"
              }
            }
            """.trimIndent() + "\n",
        )
    }
}

tasks.register<Exec>("publishWasmNpm") {
    group = "publishing"
    description = "Publishes @enaium/webrtc-agc2-kmp to npm (token: npm.publish.key)."
    dependsOn("assembleWasmNpm")
    workingDir = wasmNpmDir
    // The token lives in the user-level Gradle properties file
    // (~/.gradle/gradle.properties, next to mavenCentralUsername and the
    // signing.* entries); CI writes the same key there from a repository
    // secret. `-Pnpm.publish.key=<token>` overrides it for one-off runs.
    val npmKey = providers.gradleProperty("npm.publish.key")
    doFirst {
        val key = npmKey.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException(
                "npm.publish.key is not set: add it to ~/.gradle/gradle.properties " +
                    "or pass -Pnpm.publish.key=<token>",
            )
        // NODE_AUTH_TOKEN is expanded by npm through the environment, so the
        // token never lands in the package.json/tarball; .npmrc is on npm's
        // never-publish list anyway.
        environment("NODE_AUTH_TOKEN", key)
        wasmNpmDir.resolve(".npmrc").writeText(
            "//registry.npmjs.org/:_authToken=\${NODE_AUTH_TOKEN}\n",
        )
    }
    // A rolling version (1.0.1-rolling.<tag>) is a semver prerelease, so npm
    // requires an explicit tag; `latest` keeps `npm i @enaium/webrtc-agc2-kmp`
    // working.
    commandLine("npm", "publish", "--access", "public", "--tag", "latest")
}

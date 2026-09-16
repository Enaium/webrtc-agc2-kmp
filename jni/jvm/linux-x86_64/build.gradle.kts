/*
 * Per-OS/arch JNI artifact: linux-x86_64.
 * Ships libwebrtc_agc2_jni.so as a classpath resource at
 * /cn/enaium/webrtc/agc2/native/linux-x86_64/, which NativeLoader
 * (in :agc2's jvmMain) extracts and System.load()s at runtime.
 */
import org.gradle.internal.os.OperatingSystem

plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

group = rootProject.group
version = rootProject.version

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

val jniOs = "linux"
val jniArch = "x86_64"
val classifier = "$jniOs-$jniArch"
val libFile = "libwebrtc_agc2_jni.so"
val resourceDir = "cn/enaium/webrtc/agc2/native/$classifier"

val host = OperatingSystem.current()
val hostArch = System.getProperty("os.arch").lowercase()
val canBuildHere = host.isLinux && (hostArch == "amd64" || hostArch == "x86_64")

// Resolved once in the root build: an IDE-launched Gradle daemon has a minimal
// PATH, on which `cmake` is not found.
val cmakeExecutable: String = rootProject.extra["cmakeExecutable"] as String

val nativeOutputDir = layout.buildDirectory.dir("jni-native/$classifier")
val cmakeBuildDir = layout.buildDirectory.dir("cmake-jni/$classifier")

val configureJniLibrary by tasks.registering(Exec::class) {
    group = "build"
    description = "cmake-configures libwebrtc_agc2_jni for $classifier."
    onlyIf { canBuildHere }
    val outDir = nativeOutputDir.get().asFile
    val buildDir = cmakeBuildDir.get().asFile
    doFirst {
        outDir.mkdirs()
        buildDir.mkdirs()
    }
    workingDir = buildDir
    val javaHome = System.getProperty("java.home") ?: System.getenv("JAVA_HOME") ?: ""
    val jniInclude = if (javaHome.isNotEmpty()) "$javaHome/include" else ""
    commandLine(
        cmakeExecutable,
        rootProject.file("jni").absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DJNI_INCLUDE_DIR=$jniInclude",
        "-DJNI_INCLUDE_DIR_PLATFORM=$jniInclude/linux",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${outDir.absolutePath}",
    )
}

val buildJniLibrary by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds libwebrtc_agc2_jni.so for $classifier."
    onlyIf { canBuildHere }
    dependsOn(configureJniLibrary)
    workingDir = cmakeBuildDir.get().asFile
    commandLine(cmakeExecutable, "--build", ".", "--config", "Release")
    inputs.files(rootProject.file("jni/CMakeLists.txt"), rootProject.file("jni/jni_bridge.cpp"))
    inputs.dir(rootProject.file("jni/c_api"))
    inputs.dir(rootProject.file("webrtc-agc2"))
    outputs.file(nativeOutputDir.map { it.file(libFile) })
}

tasks.named<Copy>("processResources") {
    dependsOn(buildJniLibrary)
    // Use the build task's declared outputs (lazily resolved at execution
    // time) instead of the directory Provider, which may be snapshotted
    // empty at configuration time.
    from(buildJniLibrary.map { it.outputs.files }) {
        include(libFile)
        into(resourceDir)
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates(
        groupId = rootProject.group.toString(),
        artifactId = "webrtc-agc2-kmp-jni-jvm-$classifier",
        version = rootProject.version.toString(),
    )
    pom {
        name.set("webrtc-agc2-kmp-jni-jvm-$classifier")
        description.set(
            "Prebuilt JNI shared library for webrtc-agc2-kmp on $jniOs/$jniArch. " +
                "Loaded automatically by NativeLoader; not intended to be depended on directly.",
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

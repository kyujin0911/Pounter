import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.github.kyujin0911.pounter"
    ndkVersion = "27.3.13750724"
    compileSdk = 35
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "io.github.kyujin0911.pounter"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-experimental"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation("io.insert-koin:koin-androidx-compose:4.1.1")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4")
    implementation("androidx.compose.ui:ui:1.8.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.8.3")
    implementation("androidx.compose.foundation:foundation:1.8.3")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.opencv:opencv:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.8.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.3")
}

// Same pinned OpenCV AAR supplies runtime libraries and matching native headers.
val edgeDrawingAar by configurations.creating

dependencies { edgeDrawingAar("org.opencv:opencv:4.12.0@aar") }

val edgeDrawingAppJni = layout.buildDirectory.dir("generated/edgeDrawing/jniLibs")

android.sourceSets.getByName("main").jniLibs.srcDir(edgeDrawingAppJni)

val edgeDrawingJni = layout.buildDirectory.dir("generated/edgeDrawingTest/jniLibs")

android.sourceSets.getByName("androidTest").jniLibs.srcDir(edgeDrawingJni)

// Share the pinned SDK NDK between app and instrumentation builds; allow explicit overrides.
val edgeDrawingNdk =
    providers
        .gradleProperty("edgeDrawingNdk")
        .filter { it.isNotBlank() }
        .orElse(
            androidComponents.sdkComponents.sdkDirectory.map {
                it.dir("ndk/${android.ndkVersion}").asFile.absolutePath
            }
        )

val buildEdgeDrawingNative by
    tasks.registering {
        inputs.dir("src/main/cpp/ed")
        inputs.files(edgeDrawingAar)
        val ndk = edgeDrawingNdk
        inputs.property("ndk", ndk.orElse("not-configured"))
        outputs.dir(edgeDrawingAppJni)
        doLast {
            val ndkDirectory = file(ndk.get())
            val revision =
                Properties()
                    .apply {
                        val sourceProperties = ndkDirectory.resolve("source.properties")
                        require(sourceProperties.isFile) {
                            "Install NDK ${android.ndkVersion} using SDK Manager, or set -PedgeDrawingNdk=<NDK r27d directory>"
                        }
                        sourceProperties.inputStream().use { load(it) }
                    }
                    .getProperty("Pkg.Revision")
            require(revision == android.ndkVersion) {
                "EdgeDrawing requires NDK ${android.ndkVersion} (r27d); found $revision"
            }
            val toolchain = file("${ndk.get()}/toolchains/llvm/prebuilt").listFiles()!!.single()
            val compiler = toolchain.resolve("bin/clang++")
            val aarDir = layout.buildDirectory.dir("generated/edgeDrawing/aar").get().asFile
            copy {
                from(zipTree(edgeDrawingAar.singleFile))
                into(aarDir)
            }
            val targets =
                mapOf(
                    "arm64-v8a" to "aarch64-linux-android21",
                    "armeabi-v7a" to "armv7a-linux-androideabi21",
                    "x86" to "i686-linux-android21",
                    "x86_64" to "x86_64-linux-android21",
                )
            for ((abi, target) in targets) {
                val output = edgeDrawingAppJni.get().asFile.resolve(abi).apply { mkdirs() }
                exec {
                    commandLine(
                        compiler,
                        "--target=$target",
                        "-std=c++17",
                        "-O2",
                        "-g0",
                        "-fPIC",
                        "-shared",
                        "-I${aarDir}/prefab/modules/opencv_java4/include",
                        "-Isrc/main/cpp/ed",
                        "-DPOUNTER_PRODUCTION",
                        "src/main/cpp/ed/edge_drawing.cpp",
                        "src/main/cpp/ed/bridge.cpp",
                        "-L${aarDir}/prefab/modules/opencv_java4/libs/android.$abi",
                        "-lopencv_java4",
                        "-Wl,--no-undefined",
                        "-Wl,-z,max-page-size=16384",
                        "-Wl,--build-id=none",
                        "-o",
                        output.resolve("libpounter_ed.so"),
                    )
                }
            }
        }
    }

tasks
    .matching { it.name.matches(Regex("merge(Debug|Release)JniLibFolders")) }
    .configureEach { dependsOn(buildEdgeDrawingNative) }

val buildEdgeDrawingTestNative by
    tasks.registering {
        inputs.dir("src/androidTest/cpp/ed")
        inputs.dir("src/main/cpp/ed")
        inputs.files(edgeDrawingAar)
        val ndk = edgeDrawingNdk
        inputs.property("ndk", ndk.orElse("not-configured"))
        outputs.dir(edgeDrawingJni)
        doLast {
            val ndkDirectory = file(ndk.get())
            val revision =
                Properties()
                    .apply {
                        val sourceProperties = ndkDirectory.resolve("source.properties")
                        require(sourceProperties.isFile) {
                            "Install NDK ${android.ndkVersion} using SDK Manager, or set -PedgeDrawingNdk=<NDK r27d directory>"
                        }
                        sourceProperties.inputStream().use { load(it) }
                    }
                    .getProperty("Pkg.Revision")
            require(revision == android.ndkVersion) {
                "EdgeDrawing requires NDK ${android.ndkVersion} (r27d); found $revision"
            }
            val toolchain = file("${ndk.get()}/toolchains/llvm/prebuilt").listFiles()!!.single()
            val compiler = toolchain.resolve("bin/clang++")
            val aarDir = layout.buildDirectory.dir("generated/edgeDrawingTest/aar").get().asFile
            copy {
                from(zipTree(edgeDrawingAar.singleFile))
                into(aarDir)
            }
            val targets =
                mapOf(
                    "arm64-v8a" to "aarch64-linux-android21",
                    "armeabi-v7a" to "armv7a-linux-androideabi21",
                    "x86" to "i686-linux-android21",
                    "x86_64" to "x86_64-linux-android21",
                )
            for ((abi, target) in targets) {
                val output = edgeDrawingJni.get().asFile.resolve(abi).apply { mkdirs() }
                exec {
                    commandLine(
                        compiler,
                        "--target=$target",
                        "-std=c++17",
                        "-O2",
                        "-g0",
                        "-fPIC",
                        "-shared",
                        "-I${aarDir}/prefab/modules/opencv_java4/include",
                        "-Isrc/main/cpp/ed",
                        "src/main/cpp/ed/edge_drawing.cpp",
                        "src/androidTest/cpp/ed/bridge.cpp",
                        "-L${aarDir}/prefab/modules/opencv_java4/libs/android.$abi",
                        "-lopencv_java4",
                        "-Wl,--no-undefined",
                        "-Wl,-z,max-page-size=16384",
                        "-Wl,--build-id=none",
                        "-o",
                        output.resolve("libpounter_ed_test.so"),
                    )
                }
            }
        }
    }

tasks
    .matching { it.name == "mergeDebugAndroidTestJniLibFolders" }
    .configureEach { dependsOn(buildEdgeDrawingTestNative) }

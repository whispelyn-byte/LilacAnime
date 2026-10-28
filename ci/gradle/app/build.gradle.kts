// CI only: app/module.toml as a Gradle build (same SDK levels and dependencies). The native Whisper library is the
// prebuilt one in src/main/jniLibs. The APK gets its own application id, so it installs beside the real app for testing.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lilac.anime"
    compileSdk = (findProperty("ciCompileSdk") as String?)?.toInt() ?: 37

    defaultConfig {
        applicationId = "com.lilac.anime"
        applicationIdSuffix = ".citest"
        minSdk = 26
        targetSdk = (findProperty("ciCompileSdk") as String?)?.toInt() ?: 37
        versionCode = 30
        versionName = "0.4.1-alpha-4-ci"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint { abortOnError = false; checkReleaseBuilds = false }

    // libmpv and the ASS renderer both bring the C++ runtime.
    packaging { jniLibs { pickFirsts += listOf("**/libc++_shared.so") } }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.navigation:navigation-compose:2.9.4")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.compose.foundation:foundation:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.material:material-ripple:1.7.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.23.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("dev.jdtech.mpv:libmpv:1.0.0")
    implementation("io.github.peerless2012:ass:0.5.1")
    implementation("io.github.peerless2012:ass-kt:0.5.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("androidx.mediarouter:mediarouter:1.8.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in JVM tests; the real one runs DeviceSyncMapping there.
    testImplementation("org.json:json:20250517")
}

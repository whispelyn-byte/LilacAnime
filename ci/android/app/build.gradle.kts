// Mirrors app/module.toml (dependencies, SDK levels, version, NDK library) for a Gradle build.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreFile = System.getenv("LILAC_KEYSTORE")?.let(::file)?.takeIf { it.isFile }

android {
    namespace = "com.lilac.anime"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lilac.anime"
        minSdk = 26
        targetSdk = 37
        versionCode = (System.getenv("LILAC_VERSION_CODE") ?: "30").toInt()
        versionName = System.getenv("LILAC_VERSION_NAME") ?: "0.4.1-alpha-5"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild { cmake { arguments += listOf("-DANDROID_STL=c++_static"); cFlags += listOf("-O2", "-pthread"); cppFlags += listOf("-O2", "-pthread") } }
    }

    signingConfigs {
        if (keystoreFile != null) create("personal") {
            storeFile = keystoreFile
            storeType = "pkcs12"
            storePassword = System.getenv("LILAC_KEYSTORE_PASSWORD")
            keyAlias = "lilac"
            keyPassword = System.getenv("LILAC_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("personal") ?: signingConfigs.getByName("debug")
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/main/AndroidManifest.xml")
        java.srcDirs("src/main/java", "src/main/kotlin")
    }

    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        jniLibs { pickFirsts += listOf("**/libc++_shared.so"); useLegacyPackaging = true }
        resources { excludes += listOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/*.kotlin_module") }
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
}

kotlin { jvmToolchain(17) }

dependencies {
    listOf(
        "androidx.activity:activity-compose:1.13.0", "androidx.fragment:fragment-ktx:1.8.9", "androidx.appcompat:appcompat:1.7.1",
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0", "androidx.work:work-runtime-ktx:2.10.2",
        "androidx.navigation:navigation-compose:2.9.4", "io.coil-kt.coil3:coil-compose:3.3.0", "io.coil-kt.coil3:coil-network-okhttp:3.3.0",
        "androidx.compose.foundation:foundation:1.7.0", "androidx.compose.material3:material3:1.3.0", "androidx.compose.material:material-ripple:1.7.0",
        "androidx.compose.material:material-icons-extended:1.6.8", "com.squareup.okhttp3:okhttp:4.12.0", "org.jsoup:jsoup:1.23.1",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2", "androidx.datastore:datastore-preferences:1.1.1",
        "com.squareup.retrofit2:retrofit:2.11.0", "com.squareup.retrofit2:converter-gson:2.11.0", "dev.jdtech.mpv:libmpv:1.0.0",
        "io.github.peerless2012:ass:0.5.1", "io.github.peerless2012:ass-kt:0.5.1", "com.google.android.gms:play-services-cast-framework:22.3.1",
        "androidx.mediarouter:mediarouter:1.8.1", "com.google.mlkit:translate:17.0.3", "com.microsoft.onnxruntime:onnxruntime-android:1.22.0",
        "androidx.media3:media3-exoplayer:1.8.0", "androidx.media3:media3-exoplayer-hls:1.8.0", "androidx.media3:media3-transformer:1.8.0",
        "androidx.media3:media3-ui:1.8.0", "androidx.media3:media3-effect:1.8.0", "com.google.ai.edge.litert:litert:2.3.2",
        "com.google.ai.edge.litert:litert-npu-runtime-qualcomm:2.3.2", "com.google.ai.edge.litert:litert-npu-runtime-mediatek:2.3.2",
        "com.google.ai.edge.litert:litert-npu-runtime-google-tensor:2.3.2", "com.qualcomm.qti:qnn-runtime:2.51.0",
        "com.google.ai.edge.litertlm:litertlm-android:0.19.0",
    ).forEach { implementation(it) }
}

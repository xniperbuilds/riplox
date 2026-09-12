import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xniperbuilds.downloader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.xniperbuilds.downloader"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Launch: arm64 + 32-bit (purane phones pe bhi install ho)
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Release signing — creds local.properties me (gitignored; repo me kabhi nahi jate).
    // Contributors ke paas key nahi ho to debug-sign fallback (build phir bhi chale).
    val localProps = Properties()
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
    val riploxKs: String? = localProps.getProperty("RIPLOX_KS")
    signingConfigs {
        if (riploxKs != null) {
            create("release") {
                storeFile = file(riploxKs)
                storePassword = localProps.getProperty("RIPLOX_KS_PASS")
                keyAlias = localProps.getProperty("RIPLOX_KEY_ALIAS")
                keyPassword = localProps.getProperty("RIPLOX_KEY_PASS")
                // Purane/OEM installers (MIUI etc.) v2-only APK reject kar dete hain →
                // v1 (JAR) bhi ON rakho taake har phone pe sideload install ho.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = if (riploxKs != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Engine (Python/yt-dlp) ko real files chahiye — legacy packaging on.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // yt-dlp engine (youtubedl-android by junkfood02 — same engine Seal uses)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:0.18.1")

    // In-app video player (ExoPlayer / Media3) + HLS/DASH streaming support
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Thumbnail preview (image loading) + local-video frame thumbnails (Recent/Vault)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // Secret Vault lock — fingerprint / device PIN
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    // Background download queue — survives app close / reboot + auto-retry (Phase 3)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // Settings/category ke proper icons (emoji ki jagah)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

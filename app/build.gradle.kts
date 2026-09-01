// PR 4 build.gradle.kts：MVP 全量 Android 客户端依赖
// §19：用到才引入 —— PR 1 仅 Compose 主链；PR 4 加网络/QR/音频/TTS/导航
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.elder.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.elder.android"
        minSdk = 26  // §11.10
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // §8.3：MVP 默认连本地 docker compose（host 模式 Android emulator → 10.0.2.2）
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8001/\"")
        buildConfigField("String", "ACCOUNT_BASE_URL", "\"http://10.0.2.2:8003/\"")
        buildConfigField("String", "REMINDER_BASE_URL", "\"http://10.0.2.2:8002/\"")
        buildConfigField("String", "AGENT_BASE_URL", "\"http://10.0.2.2:8001/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose 主链（PR 1）
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // PR 4 增量（§19 用到才引入）
    // 导航
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // 网络（Retrofit + Moshi）
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.moshi:moshi-adapters:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")

    // Token 存储（DataStore）
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // QR 扫码（CameraX + ML Kit 条码；为 MVP 简化用 ZXing + 系统相机）
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.zxing:core:3.5.3")

    // 音频播放（ExoPlayer for TTS 流）
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
}

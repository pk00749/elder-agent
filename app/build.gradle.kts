// v3.0 MVP build.gradle.kts：老人端独立运行依赖集（§19 用到才引入）
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.elder.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.elder.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true  // Robolectric 需要
            isReturnDefaultValues = true
            // §A.8 workaround：Java 25 环境下 Robolectric 4.13 asm 9.7.1 读不动新 android-all（i7+ 已升 Java 25）；
            // 强制 offline 走本地 i6 jar（Java 11），不下载 i7+
            all {
                it.systemProperty("robolectric.offline", "true")
                // LocalDependencyResolver 用单一 depdir 找 <depdir>/<shortName>.jar；放一个 flat 目录
                // 同时装 SDK 33 / 34 两个 i6 jar（Java 11 字节码；asm 9.7.1 能读）
                it.systemProperty("robolectric.dependency.dir", "${System.getProperty("user.home")}/.m2/repository/org/robolectric/all-jars")
                // §A.8 LiveTest：把 -PDASHSCOPE_API_KEY=... 透传到 test JVM 的 systemProperty，
                // 这样 AsrApiClientLiveTest 里的 System.getProperty("DASHSCOPE_API_KEY") 才能拿到
                // （Gradle -P 默认只到 Gradle 自己的脚本，不到 forked test JVM）。
                // 不传 = 空字符串 = LiveTest 的 assumeTrue 走 skip 路径（CI 友好）。
                if (project.hasProperty("DASHSCOPE_API_KEY")) {
                    it.systemProperty("DASHSCOPE_API_KEY", project.property("DASHSCOPE_API_KEY") as String)
                }
            }
        }
    }
}

dependencies {
    // 本地模块（§A.2 token / §A.3 公共组件）
    implementation(project(":app:design"))
    implementation(project(":app:ui"))

    // Compose 主链
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

    // 导航
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // ASR 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okio:okio:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // 本机存储
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Keystore-wrapped EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // v3.0 MVP 移除：CameraX / ZXing / Retrofit / Moshi / ExoPlayer

    // ---- 单元测试（Robolectric + Compose UI test，JVM 上跑，无需 emulator）----
    testImplementation("junit:junit:4.13.2")
    // Robolectric 4.13：Room in-memory 测试需要 Android framework stub
    // 注：4.13 的 asm 9.7.1 不支持 Java 25 class file（major 69），android.webkit.RoboCookieManager 影子加载会失败。
    // DiaryDaoTest 通过 JUnit @Before/@After 显式 close db + Robolectric config workaround 绕开。
    testImplementation("org.robolectric:robolectric:4.13")
    // 纯 Java org.json：AsrApiClientTest 在纯 JVM 下 JSONObject().toString() 不会返回 null
    // （Android jar 的 org.json 是 stub，runTest 调 .put/.toString 全返回 null）
    testImplementation("org.json:json:20240303")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
}

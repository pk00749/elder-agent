// 老友 Android 客户端（PRD §0 / §11.10）
// - compileSdk 34、minSdk 26（API 26 = Android 8.0，PRD §11.10）
// - Kotlin DSL，PR 1 仅引入 Compose 主链（§19：用到才引入）

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "elder-agent"
include(":app")
include(":app:design")
include(":app:ui")

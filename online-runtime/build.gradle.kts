plugins {
    id("com.android.library")
    id("com.chaquo.python")
}
android {
    namespace = "com.mangomusic.runtime"
    compileSdk = 36
    defaultConfig { minSdk = 26; ndk { abiFilters += "arm64-v8a" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
chaquopy {
    defaultConfig {
        version = "3.14"
        pip {
            install("yt-dlp==2026.10.26")
            install("yt-dlp-ejs==0.8.0")
            install("certifi==2026.10.23")
        }
    }
}

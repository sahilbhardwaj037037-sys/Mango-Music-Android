plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val releaseStore = providers.environmentVariable("MANGO_RELEASE_KEYSTORE").orNull
val releaseAlias = providers.environmentVariable("MANGO_RELEASE_ALIAS").orNull
val releasePassword = providers.environmentVariable("MANGO_RELEASE_PASSWORD").orNull
val releaseSigningReady = !releaseStore.isNullOrBlank() && !releaseAlias.isNullOrBlank() && !releasePassword.isNullOrBlank()

android {
    namespace = "com.mangomusic"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mangomusic"
        // Python and QuickJS are ARM64-only; do not advertise incidental x86 graphics libraries.
        ndk { abiFilters += "arm64-v8a" }
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        getByName("debug") { storeFile = rootProject.file(".local/android/debug.keystore") }
        create("release") {
            storeFile = rootProject.file(releaseStore ?: ".local/signing/mango-music-release.keystore")
            storeType = "PKCS12"
            keyAlias = releaseAlias ?: "mango-music-release"
            storePassword = releasePassword
            keyPassword = releasePassword
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isJniDebuggable = false
            // Keep Python/Java callback names intact in the first release; no unvalidated R8 changes.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    testBuildType = if (providers.gradleProperty("mangoReleaseTests").orNull == "true") "release" else "debug"
    packaging { jniLibs.useLegacyPackaging = true }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":online-runtime"))
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Never silently produce an unsigned distribution when local signing credentials are unavailable.
tasks.matching { it.name in setOf("packageRelease", "packageReleaseBundle", "signReleaseBundle", "packageReleaseAndroidTest") }.configureEach {
    doFirst {
        check(releaseSigningReady) { "Release signing is unavailable. Use python3 scripts/release.py build (macOS Keychain). Never place passwords in Gradle properties or source." }
    }
}

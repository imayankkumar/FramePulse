// Microbenchmarks of FramePulse's per-frame work. Run them on a real device:
// ./gradlew :benchmark:connectedReleaseAndroidTest
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.benchmark)
}

android {
    namespace = "com.mayank.framepulse.benchmark"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // androidx.benchmark needs API 23.
        minSdk = 23
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    // Benchmarks run against the release build: debuggable code is much slower.
    testBuildType = "release"
    buildTypes {
        release {
            isDefault = true
        }
    }

    testOptions {
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // The per-frame code is internal to the library, so it is compiled into this module instead
    // of being made public for the benchmarks. The library's API is checked by :framepulse.
    sourceSets["main"].java.srcDir("../framepulse/src/main/java")
}

dependencies {
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.junit)
}

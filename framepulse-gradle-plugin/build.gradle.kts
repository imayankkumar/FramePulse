import org.gradle.plugin.compatibility.compatibility

plugins {
    // Gradle's own Kotlin, so the plugin runs on the Kotlin that Gradle embeds.
    `kotlin-dsl`
    alias(libs.plugins.gradle.plugin.publish)
}

// Released together with the FramePulse libraries, with the same version. Also set it in
// FramePulsePlugin.VERSION, which picks the framepulse-report version to add.
group = "io.github.imayankkumar"
version = "0.2.0"

// AGP 9 needs JDK 17, so every build that applies this plugin runs on 17 or newer.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Provided by the Android Gradle Plugin of the app that applies this plugin.
    compileOnly(libs.android.gradle.api)
    testImplementation(libs.junit)
}

gradlePlugin {
    website = "https://github.com/imayankkumar/FramePulse"
    vcsUrl = "https://github.com/imayankkumar/FramePulse"
    plugins {
        create("framepulse") {
            id = "io.github.imayankkumar.framepulse"
            implementationClass = "com.mayank.framepulse.gradle.FramePulsePlugin"
            displayName = "FramePulse"
            description = "Adds the FramePulse frame report to debug builds, copies it from " +
                "connected devices into build/reports/framepulse and serves it on localhost."
            tags = listOf("android", "performance", "jank", "frames", "report")
            // Shown on the Plugin Portal. Checked with
            // ./gradlew :app:framepulseReport --configuration-cache
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

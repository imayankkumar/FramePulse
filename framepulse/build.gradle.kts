import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.mayank.framepulse"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // AGP 9 would otherwise require apps to compile against this module's compileSdk (36).
        // The public API only uses Android types from API 21, as in 0.1.0.
        aarMetadata {
            minCompileSdk = 21
        }
    }

    testOptions {
        // Without this the test APK targets minSdk (21). Android 14 then shows a permission
        // review screen over the test activity, and the instrumentation tests time out.
        targetSdk = 36
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    // Every declaration must state its visibility, so nothing becomes public API by accident.
    explicitApi()

    // Built with a new Kotlin compiler, but readable by apps on older ones: the library may only
    // use Kotlin 2.0 language features and standard library APIs, and depends on stdlib 2.0.21.
    coreLibrariesVersion = "2.0.21"
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_0
        apiVersion = KotlinVersion.KOTLIN_2_0
        // Kotlin 2.4 warns that language version 2.0 is deprecated; it is kept on purpose.
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

mavenPublishing {
    coordinates("io.github.imayankkumar", "framepulse", "0.1.0")

    // The release AAR with sources and javadoc jars (Maven Central requires both). AGP
    // generates the javadoc jar from the KDoc.
    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        ),
    )

    // Uploads go to the Central Portal and wait there until they are released by hand.
    publishToMavenCentral()
    // Only when a signing key is configured, so publishToMavenLocal works without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    pom {
        name = "FramePulse"
        description = "Measures frame times and jank in Android apps: FPS, percentiles, " +
            "frozen frames, per-frame jank details, tags and session summaries."
        inceptionYear = "2026"
        url = "https://github.com/imayankkumar/FramePulse"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "imayankkumar"
                name = "Mayank Kumar"
                url = "https://github.com/imayankkumar"
            }
        }
        scm {
            url = "https://github.com/imayankkumar/FramePulse"
            connection = "scm:git:git://github.com/imayankkumar/FramePulse.git"
            developerConnection = "scm:git:ssh://git@github.com/imayankkumar/FramePulse.git"
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
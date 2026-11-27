import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "com.mayank.framepulse.report"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21

        // Same as :framepulse: apps are not pushed to compile against SDK 36.
        aarMetadata {
            minCompileSdk = 21
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // Same Kotlin limits as :framepulse, so apps on older Kotlin compilers can use it.
    coreLibrariesVersion = "2.0.21"
    compilerOptions {
        languageVersion = KotlinVersion.KOTLIN_2_0
        apiVersion = KotlinVersion.KOTLIN_2_0
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

mavenPublishing {
    // Released together with :framepulse, with the same version.
    coordinates("io.github.imayankkumar", "framepulse-report", "0.1.0")

    configure(
        AndroidSingleVariantLibrary(
            javadocJar = JavadocJar.Javadoc(),
            sourcesJar = SourcesJar.Sources(),
            variant = "release",
        ),
    )

    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    pom {
        name = "FramePulse report"
        description = "Writes an HTML frame report for every screen of an Android app, with no " +
            "code: add it as a debug dependency next to FramePulse."
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
    // The public API returns FramePulse types, so apps see the library through this module.
    api(project(":framepulse"))
    testImplementation(libs.junit)
}

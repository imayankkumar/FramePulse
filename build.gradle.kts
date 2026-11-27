import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.androidx.benchmark) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    // Checks each library's public API against its api/<module>.api file on every build.
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
}

apiValidation {
    // The demo app and the benchmarks are not a published API.
    ignoredProjects += listOf("app", "benchmark")
}

// The validator adds its tasks only for the kotlin-android plugin. AGP 9 compiles Kotlin itself
// (built-in Kotlin), so the tasks are added here for each library module, from its release
// classes. Kotlin's own ABI validation (abiValidation {}) does not see these classes either.
subprojects {
    if (name in rootProject.apiValidation.ignoredProjects) return@subprojects

    pluginManager.withPlugin("com.android.library") {
        dependencies {
            // The validator reads the Kotlin metadata of the classes with this library.
            "bcv-rt-jvm-cp"(libs.kotlin.metadata.jvm)
        }

        val apiBuild = tasks.register<KotlinApiBuildTask>("apiBuild") {
            description = "Writes the public API of the release classes to the build directory."
            inputClassesDirs.from(
                tasks.named<KotlinCompile>("compileReleaseKotlin").flatMap { it.destinationDirectory },
                tasks.named<JavaCompile>("compileReleaseJavaWithJavac").flatMap { it.destinationDirectory },
            )
            outputApiFile = layout.buildDirectory.file("api/${project.name}.api")
            runtimeClasspath.from(configurations.named("bcv-rt-jvm-cp-resolver"))

            val settings = rootProject.apiValidation
            ignoredPackages.addAll(settings.ignoredPackages)
            ignoredClasses.addAll(settings.ignoredClasses)
            nonPublicMarkers.addAll(settings.nonPublicMarkers)
            publicPackages.addAll(settings.publicPackages)
            publicClasses.addAll(settings.publicClasses)
            publicMarkers.addAll(settings.publicMarkers)
        }

        val apiCheck = tasks.register<KotlinApiCompareTask>("apiCheck") {
            group = "verification"
            description = "Checks that the public API matches api/${project.name}.api."
            projectApiFile = layout.projectDirectory.file("api/${project.name}.api")
            generatedApiFile = apiBuild.flatMap { it.outputApiFile }
        }

        tasks.register<Copy>("apiDump") {
            description = "Records the public API in api/${project.name}.api."
            from(apiBuild.flatMap { it.outputApiFile })
            into(layout.projectDirectory.dir("api"))
        }

        tasks.named("check") { dependsOn(apiCheck) }
    }
}

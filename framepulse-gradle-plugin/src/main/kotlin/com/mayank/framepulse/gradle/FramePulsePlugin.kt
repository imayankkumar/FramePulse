package com.mayank.framepulse.gradle

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The `io.github.imayankkumar.framepulse` plugin, for Android app modules:
 *
 * - adds `framepulse-report` to `debugImplementation`, so debug builds write an HTML frame
 *   report of every screen on the device with no code in the app;
 * - registers `framepulseReport`, which copies that report from every connected device into
 *   `build/reports/framepulse` and serves it on `http://localhost:8765`.
 *
 * It does nothing in a module without `com.android.application`.
 */
class FramePulsePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("framepulse", FramePulseExtension::class.java)
        extension.addReportDependency.convention(true)
        extension.port.convention(DEFAULT_PORT)

        project.plugins.withId("com.android.application") {
            addReportDependency(project, extension)
            registerReportTask(project, extension)
        }
    }

    private fun addReportDependency(project: Project, extension: FramePulseExtension) {
        val report = project.dependencies.create("$GROUP:framepulse-report:$VERSION")
        project.configurations.matching { it.name == "debugImplementation" }.configureEach {
            // Runs when the dependencies are first needed, after the build script has set the
            // extension.
            withDependencies {
                if (extension.addReportDependency.get()) add(report)
            }
        }
    }

    private fun registerReportTask(project: Project, extension: FramePulseExtension) {
        val androidComponents =
            project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        val task = project.tasks.register("framepulseReport", FramePulseReportTask::class.java) {
            group = "reporting"
            description = "Copies the FramePulse frame report from every connected device into " +
                "build/reports/framepulse and serves it on localhost."
            adbExecutable.set(androidComponents.sdkComponents.adb)
            outputDir.set(project.layout.buildDirectory.dir("reports/framepulse"))
            port.set(extension.port)
            variant.convention("debug")
            serve.convention(true)
        }
        androidComponents.onVariants { variant ->
            task.configure { applicationIds.put(variant.name, variant.applicationId) }
        }
    }

    companion object {
        /** Also the version of `framepulse-report` it adds; released together. */
        const val VERSION: String = "0.1.0"
        const val GROUP: String = "io.github.imayankkumar"
        const val DEFAULT_PORT: Int = 8765
    }
}

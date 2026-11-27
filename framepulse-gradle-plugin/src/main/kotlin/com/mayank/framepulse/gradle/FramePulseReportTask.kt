package com.mayank.framepulse.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Copies the frame report that `framepulse-report` wrote on each connected device into
 * [outputDir], one folder per device, and then serves it on localhost until the build is
 * stopped (Ctrl+C). Run `./gradlew :app:framepulseReport --no-serve` to only copy it.
 */
@UntrackedTask(because = "The report is read from connected devices, which Gradle cannot track")
abstract class FramePulseReportTask : DefaultTask() {

    /** The Android SDK's `adb`. */
    @get:Internal
    abstract val adbExecutable: RegularFileProperty

    /** Application id of every variant, by variant name. */
    @get:Input
    abstract val applicationIds: MapProperty<String, String>

    /** The variant whose app is read. Default "debug". */
    @get:Input
    @get:Option(option = "variant", description = "The variant whose report to copy (default: debug).")
    abstract val variant: Property<String>

    /** The first localhost port to try. */
    @get:Input
    abstract val port: Property<Int>

    /** Serve the report after copying it. Default true. */
    @get:Input
    abstract val serve: Property<Boolean>

    /** Where the reports go: one folder per device and an index.html. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @Option(option = "no-serve", description = "Only copy the report; do not serve it on localhost.")
    fun noServe() {
        serve.set(false)
    }

    @TaskAction
    fun copyAndServe() {
        val variantName = variant.get()
        val ids = applicationIds.get()
        val applicationId = ids[variantName] ?: throw GradleException(
            "No variant named '$variantName'. Use --variant with one of: ${ids.keys.sorted().joinToString()}.",
        )
        val adb = adbExecutable.get().asFile.absolutePath
        val output = outputDir.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val devices = parseAdbDevices(run(adb, "devices").text)
        if (devices.isEmpty()) {
            throw GradleException("No device is connected. Connect one with USB or Wi-Fi debugging and check `adb devices`.")
        }

        val reports = mutableListOf<DeviceReport>()
        val taken = mutableSetOf<String>()
        devices.forEach { device ->
            reports += if (device.state != "device") {
                DeviceReport(device.serial, null, "skipped, the device is ${device.state}")
            } else {
                copyReport(adb, device.serial, applicationId, output, taken)
            }
        }
        File(output, "index.html").writeText(indexPage(applicationId, reports))
        reports.forEach { logger.lifecycle("${it.name}: ${it.message}") }

        if (reports.none { it.folder != null }) {
            throw GradleException(
                "No device has a report for $applicationId yet. Install the $variantName build, " +
                    "use the app, and leave a screen (the report is written when an activity stops).",
            )
        }
        logger.lifecycle("Saved in ${output.absolutePath}")
        if (serve.get()) serveUntilStopped(output)
    }

    private fun copyReport(
        adb: String,
        serial: String,
        applicationId: String,
        output: File,
        taken: MutableSet<String>,
    ): DeviceReport {
        val model = run(adb, "-s", serial, "shell", "getprop", "ro.product.model").text.trim()
        val folderName = deviceFolderName(model, serial, taken)
        val name = model.ifBlank { serial }
        val folder = File(output, folderName).apply { mkdirs() }
        val target = File(folder, "index.html")

        // The report is in the app's external files folder, which adb can read directly...
        val pulled = run(adb, "-s", serial, "pull", deviceReportPath(applicationId), target.absolutePath)
        // ...unless the device had no external storage, then it is in the internal one, which
        // only the debuggable app itself can read.
        if (pulled.exitValue != 0 || !target.isFile) {
            val internal = run(adb, "-s", serial, "exec-out", "run-as", applicationId, "cat", "files/framepulse-report.html")
            if (internal.exitValue == 0 && internal.text.startsWith("<!DOCTYPE html>")) {
                target.writeBytes(internal.bytes)
            }
        }
        if (!target.isFile || target.length() == 0L) {
            folder.deleteRecursively()
            return DeviceReport(name, null, "no report for $applicationId yet")
        }
        taken += folderName
        return DeviceReport(name, folderName, "copied to $folderName/index.html")
    }

    /**
     * Serves [output] from a child process until the build is stopped. Gradle cancels a build on
     * Ctrl+C by stopping the processes it started; it would not stop a server in this task.
     */
    private fun serveUntilStopped(output: File) {
        val classpath = listOf(ReportServerMain::class.java, KotlinVersion::class.java).map {
            File(it.protectionDomain.codeSource.location.toURI())
        }
        execOperations.javaexec {
            classpath(classpath)
            mainClass.set(ReportServerMain::class.java.name)
            args(output.absolutePath, port.get().toString())
        }
    }

    private class Result(val exitValue: Int, val bytes: ByteArray) {
        val text: String
            get() = String(bytes, Charsets.UTF_8)
    }

    private fun run(vararg command: String): Result {
        val stdout = ByteArrayOutputStream()
        val result = execOperations.exec {
            commandLine(*command)
            standardOutput = stdout
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return Result(result.exitValue, stdout.toByteArray())
    }
}

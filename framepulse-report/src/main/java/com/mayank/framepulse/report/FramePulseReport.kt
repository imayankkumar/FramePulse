package com.mayank.framepulse.report

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.mayank.framepulse.ActivityMonitor
import com.mayank.framepulse.FramePulse
import com.mayank.framepulse.FramePulseSession
import com.mayank.framepulse.FrameStages
import com.mayank.framepulse.report.internal.ReportPage
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Writes an HTML frame report for every screen of the app, with no code in the app.
 *
 * Adding `framepulse-report` as a dependency is the whole setup: it starts itself when the app
 * process starts and measures every activity with [FramePulse.monitorActivities]. Each time an
 * activity stops, it rewrites [reportFile] with the totals of every screen measured since the
 * process started: smooth frames, jank and frozen frames, the worst frame, which frame stage was
 * longest in the jank frames, and the tags set when they happened. Get the file with
 * `adb pull`; the command is logged under the `FramePulse` tag when the file is first written.
 *
 * Add it to debug builds only (`debugImplementation`): it measures every activity, keeps a
 * background thread for writing, and puts the file on the device.
 *
 * Its sessions are separate from any the app starts itself, so tags the app sets on its own
 * sessions do not show in the report. Set them on the report's session with [sessionFor].
 */
public object FramePulseReport {

    private const val FILE_NAME = "framepulse-report.html"
    private const val LOG_TAG = "FramePulse"

    private var monitor: ActivityMonitor? = null
    private val page = ReportPage()
    private var logged = false

    private val fileWriter: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "FramePulseReport").apply { isDaemon = true }
        }
    }

    /**
     * The report file: `framepulse-report.html` in the app's external files folder, or in its
     * internal files folder when there is no external storage. It exists once the first activity
     * has stopped.
     */
    @JvmStatic
    public fun reportFile(context: Context): File {
        val appContext = context.applicationContext ?: context
        return File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, FILE_NAME)
    }

    /**
     * The report's running session for [activity], or null when it is not started or the
     * report is not running. Tags set on it show in the report, for example
     * `FramePulseReport.sessionFor(this)?.setTag("tab", "Home")`.
     *
     * Main thread only: throws [IllegalStateException] on another thread.
     */
    @JvmStatic
    public fun sessionFor(activity: Activity): FramePulseSession? = monitor?.sessionFor(activity)

    /** Starts the report once per process. Called on the main thread by the content provider. */
    internal fun install(application: Application) {
        if (monitor != null) return
        val appName = application.applicationInfo.loadLabel(application.packageManager).toString()
        val file = reportFile(application)
        monitor = FramePulse.monitorActivities(
            application = application,
            jankFrameListener = { frame ->
                // Every jank frame carries the activity tag that names its screen.
                frame.tags[FramePulse.ACTIVITY_TAG]?.let { screen ->
                    page.addJankFrame(screen, frame.stages?.let(::longestStage), frame.tags)
                }
            },
        ) { activity, summary ->
            val screen = summary.tags.firstOrNull { it.name == FramePulse.ACTIVITY_TAG }?.value
                ?: activity.javaClass.name
            page.addVisit(
                screen = screen,
                frameCount = summary.frameCount,
                jankCount = summary.jankCount,
                frozenFrameCount = summary.frozenFrameCount,
                maxFrameTimeMs = summary.maxFrameTimeMs,
            )
            write(appName, file)
        }
    }

    /** Renders on the main thread, where [page] is updated, and writes the file off it. */
    private fun write(appName: String, file: File) {
        val html = page.toHtml(
            app = appName,
            device = "${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}",
            generatedAt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(Date()),
            excludedTag = FramePulse.ACTIVITY_TAG,
        )
        val logPath = !logged
        logged = true
        fileWriter.execute {
            try {
                // A reader never sees a half-written file.
                val partial = File(file.parentFile, "$FILE_NAME.tmp")
                partial.writeText(html)
                if (!partial.renameTo(file)) throw IOException("Could not replace $file")
                if (logPath) Log.i(LOG_TAG, "Frame report: adb pull ${file.absolutePath}")
            } catch (e: IOException) {
                Log.w(LOG_TAG, "Could not write the frame report", e)
            }
        }
    }

    private fun longestStage(stages: FrameStages): String = listOf(
        "unknown delay" to stages.unknownDelayMs,
        "input" to stages.inputHandlingMs,
        "animation" to stages.animationMs,
        "layout" to stages.layoutMeasureMs,
        "draw" to stages.drawMs,
        "sync" to stages.syncMs,
        "command issue" to stages.commandIssueMs,
        "swap buffers" to stages.swapBuffersMs,
        "GPU" to (stages.gpuMs ?: 0.0),
    ).maxBy { it.second }.first
}

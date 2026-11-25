package com.mayank.framepulse.sample

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import com.mayank.framepulse.ActivityMonitor
import com.mayank.framepulse.FramePulse
import com.mayank.framepulse.SessionSummary

/**
 * Measures every activity of the sample with [FramePulse.monitorActivities]: one call here, no
 * code in the activities. Each activity's summary is logged (`adb logcat -s FramePulse`) and
 * kept in [ActivityReports] for the dashboard.
 */
class SampleApplication : Application() {

    lateinit var activityMonitor: ActivityMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        activityMonitor = FramePulse.monitorActivities(this) { activity, summary ->
            val name = activity.javaClass.simpleName
            Log.i(LOG_TAG, "$name stopped: ${summaryText(summary)}")
            ActivityReports.add(name, summary)
        }
    }
}

/** The latest activity summaries, newest first. Main thread only. */
object ActivityReports {
    private const val MAX_REPORTS = 5

    val reports = mutableStateListOf<Pair<String, SessionSummary>>()

    fun add(activityName: String, summary: SessionSummary) {
        reports.add(0, activityName to summary)
        if (reports.size > MAX_REPORTS) reports.removeAt(reports.lastIndex)
    }
}

const val LOG_TAG = "FramePulse"

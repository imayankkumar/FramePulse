package com.mayank.framepulse.internal

import android.os.Looper
import android.util.Log
import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.core.FrameMonitor
import com.mayank.framepulse.internal.source.ChoreographerFrameSource
import com.mayank.framepulse.internal.source.FrameSource

/**
 * Default [FrameMonitor] implementation.
 *
 * Orchestrates [FrameSource] lifecycle and, during Milestone 3, logs each `frameTimeNanos`
 * to Logcat. Must be started and stopped on the main thread.
 *
 * Design details (lifecycle, threading, config roadmap): `docs/FRAME_MONITOR.md`.
 *
 * @see com.mayank.framepulse.core.FrameMonitor
 */
internal class FrameMonitorImpl(
    private val frameSource: FrameSource = ChoreographerFrameSource(),
) : FrameMonitor {

    private var state: MonitorState = MonitorState.IDLE

    /** Session config for Milestone 4+ metrics; not used as a lifecycle guard. */
    private var activeConfig: FramePulseConfig? = null

    override val isRunning: Boolean
        get() = state == MonitorState.STARTED

    override fun start(config: FramePulseConfig) {
        assertMainThread()
        requireIdleForStart()

        activeConfig = config
        state = MonitorState.STARTED

        try {
            frameSource.start(::onFrameTimeNanos)
        } catch (exception: Exception) {
            frameSource.stop()
            activeConfig = null
            state = MonitorState.IDLE
            throw exception
        }
    }

    override fun stop() {
        if (state != MonitorState.STARTED) return

        frameSource.stop()
        activeConfig = null
        state = MonitorState.STOPPED
    }

    /** Milestone 3: logs frame timestamps until the metrics pipeline is wired. */
    private fun onFrameTimeNanos(frameTimeNanos: Long) {
        if (!isRunning) return
        Log.d(LOG_TAG, "$LOG_FRAME_TIME_KEY=$frameTimeNanos")
    }

    private fun requireIdleForStart() {
        when (state) {
            MonitorState.STARTED -> throw IllegalStateException("FrameMonitor is already started")
            MonitorState.STOPPED -> throw IllegalStateException(
                "FrameMonitor cannot be restarted; create a new session via FramePulse.start()",
            )

            MonitorState.IDLE -> Unit
        }
    }

    private fun assertMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("FrameMonitor.start() must be called from the main thread")
        }
    }

    /** Lifecycle states for this monitor instance. See `docs/FRAME_MONITOR.md` (§6). */
    private enum class MonitorState {
        IDLE,
        STARTED,
        STOPPED,
    }

    private companion object {
        const val LOG_TAG = "FramePulse"
        const val LOG_FRAME_TIME_KEY = "frameTimeNanos"
    }
}

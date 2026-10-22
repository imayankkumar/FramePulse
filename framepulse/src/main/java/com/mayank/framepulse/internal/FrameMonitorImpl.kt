package com.mayank.framepulse.internal

import android.os.Looper
import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.core.FrameMonitor
import com.mayank.framepulse.internal.source.ChoreographerFrameSource
import com.mayank.framepulse.internal.source.FrameSource

/**
 * Default [FrameMonitor] implementation.
 *
 * Owns the [FrameSource] lifecycle and forwards each `frameTimeNanos` to [frameConsumer] while
 * started.
 *
 * Threading: main-thread-confined and not thread-safe. [start] checks the calling thread;
 * [stop] and frame delivery are expected on the same thread. No locks are used.
 *
 * Design details (lifecycle, threading): `docs/FRAME_MONITOR.md`.
 *
 * @param isMainThread Injected so the lifecycle can be unit tested on the JVM.
 * @see com.mayank.framepulse.core.FrameMonitor
 */
internal class FrameMonitorImpl(
    private val frameSource: FrameSource = ChoreographerFrameSource(),
    private val frameConsumer: (frameTimeNanos: Long) -> Unit,
    private val isMainThread: () -> Boolean = ::isMainLooperThread,
) : FrameMonitor {

    private var state: MonitorState = MonitorState.IDLE

    override val isRunning: Boolean
        get() = state == MonitorState.STARTED

    override fun start(config: FramePulseConfig) {
        assertMainThread()
        requireIdleForStart()

        state = MonitorState.STARTED

        try {
            frameSource.start(::onFrameTimeNanos)
        } catch (exception: Exception) {
            frameSource.stop()
            state = MonitorState.IDLE
            throw exception
        }
    }

    override fun stop() {
        if (state != MonitorState.STARTED) return

        state = MonitorState.STOPPED
        frameSource.stop()
    }

    private fun onFrameTimeNanos(frameTimeNanos: Long) {
        // Ignore stray callbacks delivered during or after shutdown.
        if (!isRunning) return
        frameConsumer(frameTimeNanos)
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
        if (!isMainThread()) {
            throw IllegalStateException("FrameMonitor.start() must be called from the main thread")
        }
    }

    /** Lifecycle states for this monitor instance. See `docs/FRAME_MONITOR.md` (§6). */
    private enum class MonitorState {
        IDLE,
        STARTED,
        STOPPED,
    }
}

private fun isMainLooperThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

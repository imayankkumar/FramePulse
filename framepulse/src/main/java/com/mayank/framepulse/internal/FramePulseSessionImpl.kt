package com.mayank.framepulse.internal

import com.mayank.framepulse.api.FramePulseSession
import com.mayank.framepulse.core.FrameMonitor

/**
 * Internal [FramePulseSession] backed by a [FrameMonitor].
 *
 * Responsibilities:
 * - Expose session lifecycle to the public API without leaking [FrameMonitor] types.
 * - Delegate [stop] to the monitor so Choreographer callbacks are torn down.
 *
 * Future role:
 * - Will also own listener dispatch and metrics throttling once the metrics pipeline exists.
 */
internal class FramePulseSessionImpl(
    private val monitor: FrameMonitor,
) : FramePulseSession {

    override fun stop() {
        monitor.stop()
    }

    override val isRunning: Boolean
        get() = monitor.isRunning
}

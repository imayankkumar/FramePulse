package com.mayank.framepulse.api

import com.mayank.framepulse.internal.FrameMonitorImpl
import com.mayank.framepulse.internal.FramePulseSessionImpl

/**
 * Public entry point for FramePulse.
 *
 * Responsibilities:
 * - Expose a small, stable facade for starting monitoring sessions.
 * - Validate caller expectations (e.g. thread and lifecycle rules) before delegating inward.
 * - Hide frame sources, aggregation, and metrics implementation details.
 *
 * Must not:
 * - Perform frame math or VSYNC registration directly.
 * - Hold long-lived references to Activities, Fragments, or Views.
 *
 * Future role:
 * - Primary discoverable API for library consumers and published artifacts.
 * - Delegates to [FramePulseSession] backed by [com.mayank.framepulse.core.FrameMonitor].
 */
object FramePulse {

    /**
     * Starts a new monitoring session with the given configuration.
     *
     * @param config Session parameters such as window duration and frame budget assumptions.
     * @param listener Receives immutable metrics during the session via [FramePulseListener].
     * @return A handle used to stop monitoring and release resources.
     */
    @Suppress("UNUSED_PARAMETER")
    fun start(
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        listener: FramePulseListener,
    ): FramePulseSession {
        val monitor = FrameMonitorImpl()
        monitor.start(config)
        return FramePulseSessionImpl(monitor)
    }
}

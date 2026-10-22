package com.mayank.framepulse.api

import com.mayank.framepulse.model.FrameMetrics

/**
 * Callback invoked when aggregated frame metrics are available.
 *
 * Responsibilities:
 * - Consume immutable [FrameMetrics] snapshots produced by the metrics layer.
 * - Stay lightweight; heavy work in listeners can itself cause UI-thread pressure.
 *
 * Must not:
 * - Drive monitoring lifecycle (use [FramePulseSession] instead).
 * - Mutate or retain metrics beyond the callback scope unless copied intentionally.
 *
 * Threading: invoked synchronously on the main thread from inside a `Choreographer` frame
 * callback. Time spent here is spent on the main thread, so keep it short. Calling
 * [FramePulseSession.stop] from inside [onMetrics] is allowed.
 */
fun interface FramePulseListener {

    /**
     * Called with the latest metrics for the configured rolling window, at most once per
     * [FramePulseConfig.reportIntervalMs].
     *
     * @param metrics Immutable snapshot; safe to keep or pass to other threads.
     */
    fun onMetrics(metrics: FrameMetrics)
}

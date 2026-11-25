package com.mayank.framepulse.model

/**
 * Immutable snapshot of frame pacing metrics for the rolling window that ends at
 * [lastFrameTimeNanos].
 *
 * What a "frame" and its "frame time" mean depends on how the session was started.
 *
 * Started with an `Activity` on API 24 or higher (drawn frames):
 * - A frame is one frame the activity's window drew. Its frame time is the duration the platform
 *   measured, from the start of the frame until it was handed to the display system.
 * - [fps] is the number of frames drawn in the window divided by the window length. An idle
 *   window draws nothing, so its FPS falls towards 0 and no new snapshot is delivered until it
 *   draws again.
 *
 * Any other session (`Choreographer` callbacks):
 * - A frame is one `Choreographer` frame callback on the main thread. Its frame time is the
 *   interval since the previous callback.
 * - FramePulse keeps its callback posted every frame, so callbacks keep arriving at the display
 *   refresh rate even when nothing on screen changes. These numbers therefore describe how
 *   regularly the main thread serviced vsync, not how many frames the app actually rendered. An
 *   idle app reports an FPS close to the refresh rate.
 *
 * All window fields cover the last
 * [com.mayank.framepulse.api.FramePulseConfig.windowDurationMs] milliseconds.
 *
 * @property lastFrameTimeNanos Time of the newest frame in the window, in nanoseconds: the start
 *   of the frame for drawn frames, the callback's `frameTimeNanos` otherwise.
 * @property fps Frames per second over the window (see above for each kind of session).
 * @property frameCount Number of frames in the window.
 * @property averageFrameTimeMs Mean frame time in the window, in milliseconds.
 * @property maxFrameTimeMs Longest frame time in the window, in milliseconds.
 * @property p50FrameTimeMs Median frame time in the window, in milliseconds.
 * @property p90FrameTimeMs 90th percentile frame time in the window, in milliseconds.
 * @property p95FrameTimeMs 95th percentile frame time in the window, in milliseconds.
 * @property p99FrameTimeMs 99th percentile frame time in the window, in milliseconds.
 *   Percentiles use the nearest-rank method: the value is always one of the frame times in the
 *   window, and with fewer than 100 frames [p99FrameTimeMs] equals [maxFrameTimeMs].
 * @property jankCount Frames in the window that were jank: over the system's deadline where the
 *   platform reports one, otherwise over the configured jank threshold.
 * @property frozenFrameCount Frames in the window that took longer than
 *   [FROZEN_FRAME_THRESHOLD_MS]. Counted independently of [jankCount], although a frozen
 *   frame is normally jank as well.
 * @property totalFrameCount Frames observed since the session started.
 * @property totalJankCount Jank frames observed since the session started.
 * @property totalFrozenFrameCount Frozen frames observed since the session started.
 */
public data class FrameMetrics(
    val lastFrameTimeNanos: Long,
    val fps: Double,
    val frameCount: Int,
    val averageFrameTimeMs: Double,
    val maxFrameTimeMs: Double,
    val p50FrameTimeMs: Double,
    val p90FrameTimeMs: Double,
    val p95FrameTimeMs: Double,
    val p99FrameTimeMs: Double,
    val jankCount: Int,
    val frozenFrameCount: Int,
    val totalFrameCount: Long,
    val totalJankCount: Long,
    val totalFrozenFrameCount: Long,
) {
    public companion object {
        /**
         * A frame that takes longer than this many milliseconds is frozen. This matches the
         * frozen frame definition used by Android vitals.
         */
        public const val FROZEN_FRAME_THRESHOLD_MS: Long = 700L
    }
}

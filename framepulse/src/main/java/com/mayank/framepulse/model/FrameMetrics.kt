package com.mayank.framepulse.model

/**
 * Immutable snapshot of frame pacing metrics for the rolling window that ends at
 * [lastFrameTimeNanos].
 *
 * What a "frame" means here: one `Choreographer` frame callback on the main thread. FramePulse
 * keeps its callback posted every frame, so callbacks keep arriving at the display refresh rate
 * even when nothing on screen changes. These numbers therefore describe how regularly the main
 * thread serviced vsync, not how many frames the app actually rendered. An idle app reports an
 * FPS close to the refresh rate.
 *
 * All window fields cover the frame intervals that ended within the last
 * [com.mayank.framepulse.api.FramePulseConfig.windowDurationMs] milliseconds.
 *
 * @property lastFrameTimeNanos `frameTimeNanos` of the newest frame in the window. Same time base
 *   as `System.nanoTime()`.
 * @property fps Frame intervals in the window divided by their total duration, in frames per
 *   second.
 * @property frameCount Number of frame intervals in the window.
 * @property averageFrameTimeMs Mean frame interval in the window, in milliseconds.
 * @property maxFrameTimeMs Longest frame interval in the window, in milliseconds.
 * @property jankCount Frame intervals in the window longer than the configured jank threshold.
 * @property totalFrameCount Frame intervals observed since the session started.
 * @property totalJankCount Jank intervals observed since the session started.
 */
data class FrameMetrics(
    val lastFrameTimeNanos: Long,
    val fps: Double,
    val frameCount: Int,
    val averageFrameTimeMs: Double,
    val maxFrameTimeMs: Double,
    val jankCount: Int,
    val totalFrameCount: Long,
    val totalJankCount: Long,
)

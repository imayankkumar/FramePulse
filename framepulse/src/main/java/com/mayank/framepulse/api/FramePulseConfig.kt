package com.mayank.framepulse.api

/**
 * Configuration for a FramePulse monitoring session.
 *
 * Responsibilities:
 * - Capture policy knobs that affect metrics interpretation (window size, frame budget,
 *   jank threshold, reporting rate).
 * - Remain immutable for the lifetime of a session.
 *
 * Must not:
 * - Hold runtime monitoring state or listener references.
 * - Reference Android UI components.
 *
 * The library does not read the display refresh rate itself (that needs a `Context` or
 * `Display`). The caller supplies it, either directly via [expectedFramePeriodMs] or with
 * [withRefreshRate]. The default assumes a 60 Hz display.
 *
 * @property windowDurationMs Length of the rolling window used for FPS, average and max frame
 *   time, and [com.mayank.framepulse.model.FrameMetrics.jankCount]. Must be > 0.
 * @property expectedFramePeriodMs Expected time between frames, i.e. `1000 / refreshRateHz`.
 *   Must be > 0.
 * @property jankThresholdMultiplier A frame interval counts as jank when it is strictly greater
 *   than `expectedFramePeriodMs * jankThresholdMultiplier`. Must be >= 1.0. Default 1.5.
 * @property reportIntervalMs Minimum time between two listener calls, measured on the frame
 *   clock. 0 reports on every frame. Must be >= 0.
 */
data class FramePulseConfig(
    val windowDurationMs: Long = DEFAULT_WINDOW_DURATION_MS,
    val expectedFramePeriodMs: Float = DEFAULT_EXPECTED_FRAME_PERIOD_MS,
    val jankThresholdMultiplier: Float = DEFAULT_JANK_THRESHOLD_MULTIPLIER,
    val reportIntervalMs: Long = DEFAULT_REPORT_INTERVAL_MS,
) {
    init {
        require(windowDurationMs > 0) { "windowDurationMs must be > 0, was $windowDurationMs" }
        require(expectedFramePeriodMs > 0f) {
            "expectedFramePeriodMs must be > 0, was $expectedFramePeriodMs"
        }
        require(jankThresholdMultiplier >= 1f) {
            "jankThresholdMultiplier must be >= 1.0, was $jankThresholdMultiplier"
        }
        require(reportIntervalMs >= 0) { "reportIntervalMs must be >= 0, was $reportIntervalMs" }
    }

    /** Frame intervals strictly longer than this many milliseconds are counted as jank. */
    val jankThresholdMs: Float
        get() = expectedFramePeriodMs * jankThresholdMultiplier

    /**
     * Returns a copy whose [expectedFramePeriodMs] matches [refreshRateHz]
     * (for example `display.refreshRate`).
     */
    fun withRefreshRate(refreshRateHz: Float): FramePulseConfig {
        require(refreshRateHz > 0f) { "refreshRateHz must be > 0, was $refreshRateHz" }
        return copy(expectedFramePeriodMs = MILLIS_PER_SECOND / refreshRateHz)
    }

    companion object {
        /** Default configuration for [FramePulse.start]. */
        val DEFAULT: FramePulseConfig = FramePulseConfig()

        const val DEFAULT_WINDOW_DURATION_MS: Long = 1_000L
        const val DEFAULT_EXPECTED_FRAME_PERIOD_MS: Float = 16.67f
        const val DEFAULT_JANK_THRESHOLD_MULTIPLIER: Float = 1.5f
        const val DEFAULT_REPORT_INTERVAL_MS: Long = 250L

        private const val MILLIS_PER_SECOND = 1_000f
    }
}

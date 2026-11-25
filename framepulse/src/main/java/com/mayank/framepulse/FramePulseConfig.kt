package com.mayank.framepulse

/**
 * Configuration for a FramePulse monitoring session. Immutable; create one with [Builder]:
 *
 * ```kotlin
 * val config = FramePulseConfig.Builder()
 *     .windowDurationMs(2_000)
 *     .reportIntervalMs(500)
 *     .build()
 * ```
 *
 * Use [DEFAULT] for the defaults and [newBuilder] to change a few values of an existing config.
 *
 * With `FramePulse.start(config, listener)` the frame budget is fixed: set it with
 * [Builder.expectedFramePeriodMs] or [Builder.refreshRate]. The default assumes a 60 Hz display.
 * With the overloads that take an `Activity` or a `Display`, the display's refresh rate replaces
 * [expectedFramePeriodMs] at start and whenever it changes.
 *
 * @property windowDurationMs Length of the rolling window used for FPS, average and max frame
 *   time, and [FramePulseMetrics.jankCount]. Default 1000.
 * @property expectedFramePeriodMs Expected time between frames, i.e. `1000 / refreshRateHz`.
 *   Default 16.67 (60 Hz).
 * @property jankThresholdMultiplier A frame counts as jank when its frame time is strictly
 *   greater than `expectedFramePeriodMs * jankThresholdMultiplier`. Default 1.5. Not used for a
 *   session started with an `Activity` on API 31 or higher: there a frame is jank when it took
 *   longer than the deadline the system gave it.
 * @property reportIntervalMs Minimum time between two listener calls, measured on the frame
 *   clock. 0 reports on every frame. Default 250.
 */
public class FramePulseConfig internal constructor(builder: Builder) {

    public val windowDurationMs: Long = builder.windowDurationMs
    public val expectedFramePeriodMs: Float = builder.expectedFramePeriodMs
    public val jankThresholdMultiplier: Float = builder.jankThresholdMultiplier
    public val reportIntervalMs: Long = builder.reportIntervalMs

    /** Frame intervals strictly longer than this many milliseconds are counted as jank. */
    public val jankThresholdMs: Float
        get() = expectedFramePeriodMs * jankThresholdMultiplier

    /** Returns a builder that starts with this config's values. */
    public fun newBuilder(): Builder = Builder(this)

    override fun equals(other: Any?): Boolean =
        this === other || other is FramePulseConfig &&
            windowDurationMs == other.windowDurationMs &&
            expectedFramePeriodMs == other.expectedFramePeriodMs &&
            jankThresholdMultiplier == other.jankThresholdMultiplier &&
            reportIntervalMs == other.reportIntervalMs

    override fun hashCode(): Int {
        var result = windowDurationMs.hashCode()
        result = 31 * result + expectedFramePeriodMs.hashCode()
        result = 31 * result + jankThresholdMultiplier.hashCode()
        result = 31 * result + reportIntervalMs.hashCode()
        return result
    }

    override fun toString(): String =
        "FramePulseConfig(windowDurationMs=$windowDurationMs, " +
            "expectedFramePeriodMs=$expectedFramePeriodMs, " +
            "jankThresholdMultiplier=$jankThresholdMultiplier, reportIntervalMs=$reportIntervalMs)"

    /**
     * Builds a [FramePulseConfig]. Starts with the default values; each setter checks its value
     * and throws [IllegalArgumentException] for an invalid one.
     */
    public class Builder {
        internal var windowDurationMs: Long = DEFAULT_WINDOW_DURATION_MS
        internal var expectedFramePeriodMs: Float = DEFAULT_EXPECTED_FRAME_PERIOD_MS
        internal var jankThresholdMultiplier: Float = DEFAULT_JANK_THRESHOLD_MULTIPLIER
        internal var reportIntervalMs: Long = DEFAULT_REPORT_INTERVAL_MS

        /** Starts with the default values. */
        public constructor()

        internal constructor(config: FramePulseConfig) {
            windowDurationMs = config.windowDurationMs
            expectedFramePeriodMs = config.expectedFramePeriodMs
            jankThresholdMultiplier = config.jankThresholdMultiplier
            reportIntervalMs = config.reportIntervalMs
        }

        /** Sets [FramePulseConfig.windowDurationMs]. Must be > 0. */
        public fun windowDurationMs(value: Long): Builder = apply {
            require(value > 0) { "windowDurationMs must be > 0, was $value" }
            windowDurationMs = value
        }

        /** Sets [FramePulseConfig.expectedFramePeriodMs]. Must be > 0. */
        public fun expectedFramePeriodMs(value: Float): Builder = apply {
            require(value > 0f && value.isFinite()) {
                "expectedFramePeriodMs must be > 0, was $value"
            }
            expectedFramePeriodMs = value
        }

        /**
         * Sets [FramePulseConfig.expectedFramePeriodMs] from a refresh rate in Hz (for example
         * `display.refreshRate`). Must be > 0.
         */
        public fun refreshRate(refreshRateHz: Float): Builder = apply {
            require(refreshRateHz > 0f && refreshRateHz.isFinite()) {
                "refreshRateHz must be > 0, was $refreshRateHz"
            }
            expectedFramePeriodMs = MILLIS_PER_SECOND / refreshRateHz
        }

        /** Sets [FramePulseConfig.jankThresholdMultiplier]. Must be >= 1.0. */
        public fun jankThresholdMultiplier(value: Float): Builder = apply {
            require(value >= 1f && value.isFinite()) {
                "jankThresholdMultiplier must be >= 1.0, was $value"
            }
            jankThresholdMultiplier = value
        }

        /** Sets [FramePulseConfig.reportIntervalMs]. Must be >= 0. */
        public fun reportIntervalMs(value: Long): Builder = apply {
            require(value >= 0) { "reportIntervalMs must be >= 0, was $value" }
            reportIntervalMs = value
        }

        /** Returns a config with the values set so far. */
        public fun build(): FramePulseConfig = FramePulseConfig(this)
    }

    public companion object {
        /** Default configuration for [FramePulse.start]. */
        @JvmField
        public val DEFAULT: FramePulseConfig = Builder().build()

        public const val DEFAULT_WINDOW_DURATION_MS: Long = 1_000L
        public const val DEFAULT_EXPECTED_FRAME_PERIOD_MS: Float = 16.67f
        public const val DEFAULT_JANK_THRESHOLD_MULTIPLIER: Float = 1.5f
        public const val DEFAULT_REPORT_INTERVAL_MS: Long = 250L

        private const val MILLIS_PER_SECOND = 1_000f
    }
}

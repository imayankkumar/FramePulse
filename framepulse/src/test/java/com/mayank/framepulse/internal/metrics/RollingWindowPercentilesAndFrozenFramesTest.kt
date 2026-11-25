package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.FramePulseMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

/** [RollingWindowFpsCalculator]: frame time percentiles and frozen frames, in both modes. */
class RollingWindowPercentilesAndFrozenFramesTest {

    private val calculator = RollingWindowFpsCalculator()

    /** Report on every frame so each call can be inspected. 60 Hz budget, 25 ms threshold. */
    private val everyFrame = FramePulseConfig.Builder().reportIntervalMs(0).build()

    private fun measured(timeNanos: Long, durationNanos: Long): FramePulseMetrics? =
        calculator.onMeasuredFrame(
            frameTimeNanos = timeNanos,
            durationNanos = durationNanos,
            deadlineNanos = NO_DEADLINE,
            config = everyFrame,
        )

    private fun tick(timeNanos: Long): FramePulseMetrics? =
        calculator.onFrame(timeNanos, everyFrame)

    @Test
    fun percentilesUseTheNearestRank() {
        // Durations 1..100 ms in a scrambled order, all inside one window.
        val durationsMs = (1..100L).sortedBy { (it * 37) % 101 }
        var metrics: FramePulseMetrics? = null
        durationsMs.forEachIndexed { i, durationMs ->
            metrics = measured(START + i * 5 * NANOS_PER_MS, durationMs * NANOS_PER_MS)
        }

        assertEquals(100, metrics!!.frameCount)
        assertEquals(50.0, metrics!!.p50FrameTimeMs, 0.0)
        assertEquals(90.0, metrics!!.p90FrameTimeMs, 0.0)
        assertEquals(95.0, metrics!!.p95FrameTimeMs, 0.0)
        assertEquals(99.0, metrics!!.p99FrameTimeMs, 0.0)
        assertEquals(100.0, metrics!!.maxFrameTimeMs, 0.0)
    }

    @Test
    fun withFewFramesTheHighPercentilesAreTheMaximum() {
        measured(START, 4 * NANOS_PER_MS)
        measured(START + PERIOD_60HZ, 8 * NANOS_PER_MS)
        val metrics = measured(START + 2 * PERIOD_60HZ, 6 * NANOS_PER_MS)!!

        assertEquals(6.0, metrics.p50FrameTimeMs, 0.0)
        assertEquals(8.0, metrics.p90FrameTimeMs, 0.0)
        assertEquals(8.0, metrics.p95FrameTimeMs, 0.0)
        assertEquals(8.0, metrics.p99FrameTimeMs, 0.0)
        assertEquals(8.0, metrics.maxFrameTimeMs, 0.0)
    }

    @Test
    fun aSingleFrameIsEveryPercentile() {
        val metrics = measured(START, 7 * NANOS_PER_MS)!!

        assertEquals(7.0, metrics.p50FrameTimeMs, 0.0)
        assertEquals(7.0, metrics.p99FrameTimeMs, 0.0)
    }

    @Test
    fun percentilesOnlyCoverTheWindow() {
        // Slow frames first, then a full second of fast ones that pushes them out.
        for (i in 0 until 30) measured(START + i * PERIOD_60HZ, 40 * NANOS_PER_MS)
        var metrics: FramePulseMetrics? = null
        for (i in 30 until 120) metrics = measured(START + i * PERIOD_60HZ, 5 * NANOS_PER_MS)

        assertEquals(5.0, metrics!!.p99FrameTimeMs, 0.0)
        assertEquals(5.0, metrics.maxFrameTimeMs, 0.0)
    }

    @Test
    fun aFrameIsFrozenOnlyWhenStrictlyLongerThanTheThreshold() {
        val threshold = FramePulseMetrics.FROZEN_FRAME_THRESHOLD_MS * NANOS_PER_MS

        val atThreshold = measured(START, threshold)!!
        val overThreshold = measured(START + threshold, threshold + 1)!!

        assertEquals(0L, atThreshold.totalFrozenFrameCount)
        assertEquals(1L, overThreshold.totalFrozenFrameCount)
        assertEquals(1, overThreshold.frozenFrameCount)
        // Both frames are far over the 25 ms jank threshold.
        assertEquals(2L, overThreshold.totalJankCount)
    }

    @Test
    fun frozenFramesLeaveTheWindowButStayInTheTotal() {
        measured(START, 900 * NANOS_PER_MS)
        val resume = START + 900 * NANOS_PER_MS
        var metrics: FramePulseMetrics? = null
        for (i in 0 until 90) metrics = measured(resume + i * PERIOD_60HZ, 5 * NANOS_PER_MS)

        assertEquals(0, metrics!!.frozenFrameCount)
        assertEquals(1L, metrics.totalFrozenFrameCount)
    }

    @Test
    fun frameTicksUseIntervalsForPercentilesAndFrozenFrames() {
        // 60 Hz, then the main thread is blocked for 800 ms, then 60 Hz again.
        var time = START
        for (i in 0 until 30) {
            tick(time)
            time += PERIOD_60HZ
        }
        time += 800 * NANOS_PER_MS - PERIOD_60HZ
        var metrics = tick(time)!!

        assertEquals(1, metrics.frozenFrameCount)
        assertEquals(1L, metrics.totalFrozenFrameCount)
        assertEquals(800.0, metrics.maxFrameTimeMs, 0.001)
        assertEquals(800.0, metrics.p99FrameTimeMs, 0.001)
        assertEquals(PERIOD_60HZ / 1e6, metrics.p50FrameTimeMs, 0.001)

        for (i in 0 until 70) {
            time += PERIOD_60HZ
            metrics = tick(time)!!
        }
        assertEquals(0, metrics.frozenFrameCount)
        assertEquals(1L, metrics.totalFrozenFrameCount)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val PERIOD_60HZ = 16_666_667L
        const val NO_DEADLINE = -1L

        /** An arbitrary non-zero clock value, like a real uptime. */
        const val START = 7_000_000_000_000L
    }
}

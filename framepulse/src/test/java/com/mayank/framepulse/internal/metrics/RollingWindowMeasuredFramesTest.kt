package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.FramePulseMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RollingWindowFpsCalculator.onMeasuredFrame]: frames with a platform-measured duration. */
class RollingWindowMeasuredFramesTest {

    private val calculator = RollingWindowFpsCalculator()

    /** Report on every frame so each call can be inspected. 60 Hz budget, 25 ms threshold. */
    private val everyFrame = FramePulseConfig.Builder().reportIntervalMs(0).build()

    private fun frame(
        timeNanos: Long,
        durationMs: Long,
        deadlineMs: Long = NO_DEADLINE,
        config: FramePulseConfig = everyFrame,
    ): FramePulseMetrics? = calculator.onMeasuredFrame(
        frameTimeNanos = timeNanos,
        durationNanos = durationMs * NANOS_PER_MS,
        deadlineNanos = if (deadlineMs == NO_DEADLINE) NO_DEADLINE else deadlineMs * NANOS_PER_MS,
        config = config,
    )

    @Test
    fun firstFrameIsReportedWithItsOwnDuration() {
        val metrics = frame(timeNanos = START, durationMs = 5)!!

        assertEquals(1, metrics.frameCount)
        assertEquals(1L, metrics.totalFrameCount)
        assertEquals(5.0, metrics.averageFrameTimeMs, 0.001)
        assertEquals(5.0, metrics.maxFrameTimeMs, 0.001)
        assertEquals(0, metrics.jankCount)
        assertEquals(START, metrics.lastFrameTimeNanos)
        // One frame in one refresh period (the default budget is 16.67 ms).
        assertEquals(60.0, metrics.fps, 0.02)
    }

    @Test
    fun steadySixtyFramesPerSecondReportsSixtyFpsFromTheFirstFrameOn() {
        var metrics: FramePulseMetrics? = null
        for (i in 0 until 180) {
            metrics = frame(timeNanos = START + i * PERIOD_60HZ, durationMs = 5)!!
            assertEquals("frame $i", 60.0, metrics.fps, 0.02)
        }

        assertEquals(60, metrics!!.frameCount)
        assertEquals(5.0, metrics.averageFrameTimeMs, 0.001)
        assertEquals(0L, metrics.totalJankCount)
        assertEquals(180L, metrics.totalFrameCount)
    }

    @Test
    fun aFrameExactlyOneWindowOldIsNotCounted() {
        // 100 Hz for two seconds: 10 ms divides the 1 s window exactly.
        var metrics: FramePulseMetrics? = null
        for (i in 0..200) {
            metrics = frame(timeNanos = START + i * 10 * NANOS_PER_MS, durationMs = 4)
        }

        assertEquals(100, metrics!!.frameCount)
        assertEquals(100.0, metrics.fps, 0.001)
    }

    @Test
    fun idleTimeLowersFpsAndIsNotJank() {
        // Half a second of drawing, 2 s of nothing, then ten frames.
        for (i in 0 until 30) frame(timeNanos = START + i * PERIOD_60HZ, durationMs = 5)
        val resume = START + 2_500 * NANOS_PER_MS
        var metrics: FramePulseMetrics? = null
        for (i in 0 until 10) {
            metrics = frame(timeNanos = resume + i * PERIOD_60HZ, durationMs = 5)
        }

        // Only the ten new frames are inside the last second.
        assertEquals(10, metrics!!.frameCount)
        assertEquals(10.0, metrics.fps, 0.001)
        assertEquals(5.0, metrics.maxFrameTimeMs, 0.001)
        assertEquals(0, metrics.jankCount)
        assertEquals(0L, metrics.totalJankCount)
        assertEquals(40L, metrics.totalFrameCount)
    }

    @Test
    fun withoutADeadlineTheConfiguredThresholdDecidesJank() {
        // 20 ms * 1.5 = 30 ms, exactly representable.
        val config = FramePulseConfig.Builder()
            .expectedFramePeriodMs(20f)
            .reportIntervalMs(0)
            .build()

        val atThreshold = frame(timeNanos = START, durationMs = 30, config = config)!!
        val overThreshold = calculator.onMeasuredFrame(
            frameTimeNanos = START + 40 * NANOS_PER_MS,
            durationNanos = 30 * NANOS_PER_MS + 1,
            deadlineNanos = NO_DEADLINE,
            config = config,
        )!!

        assertEquals(0L, atThreshold.totalJankCount)
        assertEquals(1L, overThreshold.totalJankCount)
        assertEquals(1, overThreshold.jankCount)
    }

    @Test
    fun aDeadlineDecidesJankInsteadOfTheConfiguredThreshold() {
        // The configured threshold is 25 ms.
        val underDeadline = frame(timeNanos = START, durationMs = 40, deadlineMs = 50)!!
        val atDeadline = frame(START + 100 * NANOS_PER_MS, durationMs = 50, deadlineMs = 50)!!
        val overDeadline = frame(START + 200 * NANOS_PER_MS, durationMs = 12, deadlineMs = 11)!!

        // 40 ms is over the 25 ms threshold but inside its 50 ms deadline.
        assertEquals(0L, underDeadline.totalJankCount)
        assertEquals(0L, atDeadline.totalJankCount)
        // 12 ms is under the 25 ms threshold but over its 11 ms deadline.
        assertEquals(1L, overDeadline.totalJankCount)
    }

    @Test
    fun jankLeavesTheWindowButStaysInTheTotal() {
        frame(timeNanos = START, durationMs = 100)
        var metrics: FramePulseMetrics? = null
        for (i in 1..90) {
            metrics = frame(timeNanos = START + i * PERIOD_60HZ, durationMs = 5)
        }

        assertEquals(0, metrics!!.jankCount)
        assertEquals(1L, metrics.totalJankCount)
        assertEquals(5.0, metrics.maxFrameTimeMs, 0.001)
    }

    @Test
    fun framesWithoutAPositiveDurationAreIgnored() {
        assertNull(frame(timeNanos = START, durationMs = 0))
        assertNull(frame(timeNanos = START + PERIOD_60HZ, durationMs = -1))

        val metrics = frame(timeNanos = START + 2 * PERIOD_60HZ, durationMs = 5)!!
        assertEquals(1L, metrics.totalFrameCount)
    }

    @Test
    fun aFrameTimeThatGoesBackwardsIsStillCounted() {
        frame(timeNanos = START + PERIOD_60HZ, durationMs = 5)

        val metrics = frame(timeNanos = START, durationMs = 7)!!

        assertEquals(2, metrics.frameCount)
        assertEquals(7.0, metrics.maxFrameTimeMs, 0.001)
        assertEquals(START + PERIOD_60HZ, metrics.lastFrameTimeNanos)
    }

    @Test
    fun reportsAreThrottledToReportInterval() {
        val config = FramePulseConfig.Builder().reportIntervalMs(50).build()
        val reportTimes = mutableListOf<Long>()

        // 100 Hz for one second: frames at 0, 10, 20 ... 1000 ms.
        for (i in 0..100) {
            val t = START + i * 10 * NANOS_PER_MS
            frame(timeNanos = t, durationMs = 4, config = config)
                ?.let { reportTimes += it.lastFrameTimeNanos }
        }

        assertEquals(START, reportTimes.first())
        assertEquals(21, reportTimes.size)
        reportTimes.zipWithNext { a, b -> assertEquals(50 * NANOS_PER_MS, b - a) }
    }

    @Test
    fun framesAfterTheLastSnapshotAreReportedWithoutAnotherFrame() {
        val config = FramePulseConfig.Builder().reportIntervalMs(250).build()
        frame(timeNanos = START, durationMs = 5, config = config)!!
        assertFalse(calculator.hasUnreportedFrames)
        // Five more frames, then the window stops drawing. None of them is due a snapshot.
        for (i in 1..4) {
            assertNull(frame(timeNanos = START + i * PERIOD_60HZ, durationMs = 5, config = config))
        }
        assertNull(frame(timeNanos = START + 5 * PERIOD_60HZ, durationMs = 40, config = config))
        assertTrue(calculator.hasUnreportedFrames)
        assertEquals(START + 250 * NANOS_PER_MS, calculator.nextReportDueNanos(config))

        val now = START + 400 * NANOS_PER_MS
        val metrics = calculator.reportUnreportedFrames(now, config)!!

        // The same window a due sixth frame would have reported: it ends at the newest frame.
        assertEquals(START + 5 * PERIOD_60HZ, metrics.lastFrameTimeNanos)
        assertEquals(6, metrics.frameCount)
        assertEquals(6L, metrics.totalFrameCount)
        assertEquals(40.0, metrics.maxFrameTimeMs, 0.001)
        assertEquals(1, metrics.jankCount)
        // Six frames over 5 periods plus the 40 ms newest frame.
        assertEquals(6 / (5 * PERIOD_60HZ + 40 * NANOS_PER_MS).toDouble() * 1e9, metrics.fps, 0.001)
        assertFalse(calculator.hasUnreportedFrames)
        assertNull(calculator.reportUnreportedFrames(now, config))
        // The next report is due one interval after this one.
        assertEquals(now + 250 * NANOS_PER_MS, calculator.nextReportDueNanos(config))
    }

    @Test
    fun aDueFrameLeavesNothingUnreported() {
        val config = FramePulseConfig.Builder().reportIntervalMs(250).build()
        frame(timeNanos = START, durationMs = 5, config = config)
        frame(timeNanos = START + PERIOD_60HZ, durationMs = 5, config = config)

        frame(timeNanos = START + 300 * NANOS_PER_MS, durationMs = 5, config = config)!!

        assertFalse(calculator.hasUnreportedFrames)
        assertNull(calculator.reportUnreportedFrames(START + 500 * NANOS_PER_MS, config))
    }

    @Test
    fun windowLargerThanInitialBufferStillRollsOverCorrectly() {
        // 5 s at 240 Hz needs about 1200 slots, well past the initial capacity.
        val config = FramePulseConfig.Builder()
            .windowDurationMs(5_000)
            .reportIntervalMs(0)
            .refreshRate(240f)
            .build()
        var metrics: FramePulseMetrics? = null
        for (i in 0 until 2_400) {
            metrics = frame(timeNanos = START + i * PERIOD_240HZ, durationMs = 2, config = config)
        }

        assertEquals(240.0, metrics!!.fps, 0.25)
        assertEquals(2_400L, metrics.totalFrameCount)
        assertEquals(0, metrics.jankCount)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val PERIOD_60HZ = 16_666_667L
        const val PERIOD_240HZ = 4_166_667L
        const val NO_DEADLINE = -1L

        /** An arbitrary non-zero clock value, like a real uptime. */
        const val START = 7_000_000_000_000L
    }
}

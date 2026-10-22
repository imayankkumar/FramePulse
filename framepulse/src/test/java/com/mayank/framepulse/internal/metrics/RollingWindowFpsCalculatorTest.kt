package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.model.FrameMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingWindowFpsCalculatorTest {

    private val calculator = RollingWindowFpsCalculator()

    /** Report on every frame so each call can be inspected. */
    private val everyFrame = FramePulseConfig(reportIntervalMs = 0)

    @Test
    fun firstFrameProducesNoMetrics() {
        assertNull(calculator.onFrame(1_000L, everyFrame))
    }

    @Test
    fun steady60HzReportsSixtyFpsAndNoJank() {
        val metrics = feedSteady(periodNanos = PERIOD_60HZ, frames = 180, config = everyFrame)

        assertEquals(60.0, metrics.fps, 0.01)
        assertEquals(16.667, metrics.averageFrameTimeMs, 0.001)
        assertEquals(16.667, metrics.maxFrameTimeMs, 0.001)
        assertEquals(0, metrics.jankCount)
        assertEquals(0L, metrics.totalJankCount)
        assertEquals(179L, metrics.totalFrameCount)
    }

    @Test
    fun steady120HzWithMatchingRefreshRateReportsNoJank() {
        val config = everyFrame.withRefreshRate(120f)

        val metrics = feedSteady(periodNanos = PERIOD_120HZ, frames = 240, config = config)

        assertEquals(120.0, metrics.fps, 0.01)
        assertEquals(0, metrics.jankCount)
    }

    @Test
    fun singleLongFrameCountsAsOneJank() {
        var t = 0L
        repeat(30) {
            calculator.onFrame(t, everyFrame)
            t += PERIOD_60HZ
        }
        t += 50 * NANOS_PER_MS - PERIOD_60HZ // the last interval becomes 50 ms
        val metrics = calculator.onFrame(t, everyFrame)!!

        assertEquals(1, metrics.jankCount)
        assertEquals(1L, metrics.totalJankCount)
        assertEquals(50.0, metrics.maxFrameTimeMs, 0.001)
    }

    @Test
    fun intervalExactlyAtThresholdIsNotJankButOneNanosecondMoreIs() {
        // 20 ms * 1.5 = 30 ms, exactly representable.
        val config = FramePulseConfig(expectedFramePeriodMs = 20f, reportIntervalMs = 0)

        calculator.onFrame(0L, config)
        val atThreshold = calculator.onFrame(30 * NANOS_PER_MS, config)!!
        val overThreshold = calculator.onFrame(60 * NANOS_PER_MS + 1, config)!!

        assertEquals(0L, atThreshold.totalJankCount)
        assertEquals(1L, overThreshold.totalJankCount)
    }

    @Test
    fun jankThresholdMultiplierIsConfigurable() {
        val strict = FramePulseConfig(expectedFramePeriodMs = 20f, reportIntervalMs = 0)
        val lenient = strict.copy(jankThresholdMultiplier = 2.5f)
        val strictCalculator = RollingWindowFpsCalculator()
        val lenientCalculator = RollingWindowFpsCalculator()

        strictCalculator.onFrame(0L, strict)
        lenientCalculator.onFrame(0L, lenient)
        val strictMetrics = strictCalculator.onFrame(40 * NANOS_PER_MS, strict)!!
        val lenientMetrics = lenientCalculator.onFrame(40 * NANOS_PER_MS, lenient)!!

        assertEquals(1, strictMetrics.jankCount)
        assertEquals(0, lenientMetrics.jankCount)
    }

    @Test
    fun jankLeavesTheWindowButStaysInTheTotal() {
        var t = 0L
        repeat(30) {
            calculator.onFrame(t, everyFrame)
            t += PERIOD_60HZ
        }
        t += 100 * NANOS_PER_MS
        val atJank = calculator.onFrame(t, everyFrame)!!
        assertEquals(1, atJank.jankCount)

        // Another 1.1 s of smooth frames pushes the long interval out of the 1 s window.
        var metrics: FrameMetrics = atJank
        repeat(66) {
            t += PERIOD_60HZ
            metrics = calculator.onFrame(t, everyFrame)!!
        }

        assertEquals(0, metrics.jankCount)
        assertEquals(1L, metrics.totalJankCount)
        assertEquals(16.667, metrics.maxFrameTimeMs, 0.001)
        assertEquals(60.0, metrics.fps, 0.01)
        assertTrue("window should hold about 1 s of frames", metrics.frameCount in 59..61)
    }

    @Test
    fun stallLongerThanTheWindowLeavesOnlyThatInterval() {
        calculator.onFrame(0L, everyFrame)
        calculator.onFrame(PERIOD_60HZ, everyFrame)
        val metrics = calculator.onFrame(PERIOD_60HZ + 3_000 * NANOS_PER_MS, everyFrame)!!

        assertEquals(1, metrics.frameCount)
        assertEquals(1.0 / 3.0, metrics.fps, 0.0001)
        assertEquals(1, metrics.jankCount)
    }

    @Test
    fun timestampsThatDoNotMoveForwardAreIgnored() {
        calculator.onFrame(0L, everyFrame)
        calculator.onFrame(PERIOD_60HZ, everyFrame)

        assertNull(calculator.onFrame(PERIOD_60HZ, everyFrame))
        assertNull(calculator.onFrame(PERIOD_60HZ - 1, everyFrame))

        val metrics = calculator.onFrame(2 * PERIOD_60HZ, everyFrame)!!
        assertEquals(2L, metrics.totalFrameCount)
        assertEquals(0, metrics.jankCount)
    }

    @Test
    fun reportsAreThrottledToReportInterval() {
        val config = FramePulseConfig(reportIntervalMs = 50)
        val reportTimes = mutableListOf<Long>()

        // 100 Hz for one second: frames at 0, 10, 20 ... 1000 ms.
        for (i in 0..100) {
            val t = i * 10 * NANOS_PER_MS
            calculator.onFrame(t, config)?.let { reportTimes += it.lastFrameTimeNanos }
        }

        assertEquals(10 * NANOS_PER_MS, reportTimes.first())
        assertEquals(20, reportTimes.size)
        reportTimes.zipWithNext { a, b -> assertEquals(50 * NANOS_PER_MS, b - a) }
    }

    @Test
    fun windowLargerThanInitialBufferStillRollsOverCorrectly() {
        // 5 s at 240 Hz needs about 1200 slots, well past the initial capacity.
        val config = FramePulseConfig(windowDurationMs = 5_000, reportIntervalMs = 0)
            .withRefreshRate(240f)

        val metrics = feedSteady(periodNanos = PERIOD_240HZ, frames = 2_400, config = config)

        assertEquals(240.0, metrics.fps, 0.01)
        assertTrue(metrics.frameCount in 1_199..1_201)
        assertEquals(0, metrics.jankCount)
        assertEquals(2_399L, metrics.totalFrameCount)
    }

    private fun feedSteady(periodNanos: Long, frames: Int, config: FramePulseConfig): FrameMetrics {
        var last: FrameMetrics? = null
        for (i in 0 until frames) {
            calculator.onFrame(i * periodNanos, config)?.let { last = it }
        }
        return assertNotNullAndGet(last)
    }

    private fun assertNotNullAndGet(metrics: FrameMetrics?): FrameMetrics {
        assertNotNull(metrics)
        return metrics!!
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val PERIOD_60HZ = 16_666_667L
        const val PERIOD_120HZ = 8_333_333L
        const val PERIOD_240HZ = 4_166_667L
    }
}

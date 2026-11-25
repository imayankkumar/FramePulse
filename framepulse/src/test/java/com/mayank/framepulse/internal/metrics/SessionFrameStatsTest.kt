package com.mayank.framepulse.internal.metrics

import org.junit.Assert.assertEquals
import org.junit.Test

/** [SessionFrameStats]: whole-session counts and histogram percentiles. */
class SessionFrameStatsTest {

    private val stats = SessionFrameStats()

    private fun record(count: Int, durationNanos: Long, isJank: Boolean = false) {
        repeat(count) { stats.record(durationNanos, isJank) }
    }

    @Test
    fun withoutFramesEverythingIsZero() {
        assertEquals(0L, stats.frameCount)
        assertEquals(0.0, stats.percentileMs(percent = 50), 0.0)
        assertEquals(0.0, stats.percentileMs(percent = 99), 0.0)
    }

    @Test
    fun identicalFramesReportTheirExactDuration() {
        record(100, 5 * MS)

        assertEquals(5.0, stats.percentileMs(percent = 50), 0.0)
        assertEquals(5.0, stats.percentileMs(percent = 99), 0.0)
    }

    @Test
    fun belowFiftyMillisecondsPercentilesRoundUpToTheNextTenthOfAMillisecond() {
        record(90, 8_333_333L)
        record(5, 120 * MS)
        record(5, 130_500_000L)

        assertEquals(8.4, stats.percentileMs(percent = 50), 1e-9)
        assertEquals(8.4, stats.percentileMs(percent = 90), 1e-9)
    }

    @Test
    fun fromFiftyMillisecondsToOneSecondPercentilesRoundUpToTheNextMillisecond() {
        record(90, 8_333_333L)
        record(5, 120 * MS)
        record(5, 130_500_000L)

        // 120 ms is exactly on a bucket edge, so it is reported unchanged.
        assertEquals(120.0, stats.percentileMs(percent = 95), 1e-9)
        // The bucket edge (131 ms) is capped at the real maximum.
        assertEquals(130.5, stats.percentileMs(percent = 99), 1e-9)
        assertEquals(130_500_000L, stats.maxDurationNanos)
    }

    @Test
    fun aboveOneSecondPercentilesReportTheMaximum() {
        record(1, 1_500 * MS)
        record(1, 2_500 * MS)

        assertEquals(2_500.0, stats.percentileMs(percent = 50), 0.0)
    }

    @Test
    fun percentilesUseTheNearestRank() {
        // 1 ms .. 100 ms: p50 is the 50th value, p99 the 99th.
        for (ms in 1..100L) stats.record(ms * MS, isJank = false)

        assertEquals(50.0, stats.percentileMs(percent = 50), 1e-9)
        assertEquals(99.0, stats.percentileMs(percent = 99), 1e-9)
        assertEquals(1.0, stats.percentileMs(percent = 1), 1e-9)
    }

    @Test
    fun jankAndFrozenFramesAreCounted() {
        record(10, 5 * MS)
        record(3, 40 * MS, isJank = true)
        record(1, 700 * MS, isJank = true)
        record(2, 700 * MS + 1, isJank = true)

        assertEquals(16L, stats.frameCount)
        assertEquals(6L, stats.jankCount)
        assertEquals(2L, stats.frozenFrameCount)
    }

    @Test
    fun framesWithoutAPositiveDurationAreIgnored() {
        stats.record(0L, isJank = true)
        stats.record(-1L, isJank = true)

        assertEquals(0L, stats.frameCount)
        assertEquals(0L, stats.jankCount)
    }

    private companion object {
        const val MS = 1_000_000L
    }
}

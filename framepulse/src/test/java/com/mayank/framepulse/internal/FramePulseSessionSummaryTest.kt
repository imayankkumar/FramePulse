package com.mayank.framepulse.internal

import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FakeFrameSource
import com.mayank.framepulse.TagSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FramePulseSessionImpl.summary]: totals for the whole session. */
class FramePulseSessionSummaryTest {

    private val frames = FakeFrameSource()
    private var now = START
    private var onMainThread = true

    /** 60 Hz budget, so frames over about 25 ms are jank. Reports throttled as by default. */
    private val session = FramePulseSessionImpl(
        config = FramePulseConfig(),
        listener = {},
        calculator = RollingWindowFpsCalculator(),
        monitorFactory = { consumer ->
            FrameMonitorImpl(frames, consumer, isMainThread = { true })
        },
        isMainThread = { onMainThread },
        nanoTime = { now },
    ).also { it.start() }

    @Test
    fun aNewSessionHasAnEmptySummary() {
        val summary = session.summary()

        assertEquals(0L, summary.frameCount)
        assertEquals(0.0, summary.jankRate, 0.0)
        assertEquals(0.0, summary.p99FrameTimeMs, 0.0)
        assertTrue(summary.tags.isEmpty())
    }

    @Test
    fun everyFrameIsCountedEvenWhenNoMetricsAreReported() {
        // 20 frames 16.7 ms apart: only a few of them produce a listener call.
        for (i in 0 until 18) frames.emitMeasured(START + i * PERIOD, 5 * MS)
        frames.emitMeasured(START + 18 * PERIOD, 40 * MS)
        frames.emitMeasured(START + 19 * PERIOD, 800 * MS)

        val summary = session.summary()
        assertEquals(20L, summary.frameCount)
        assertEquals(2L, summary.jankCount)
        assertEquals(1L, summary.frozenFrameCount)
        assertEquals(0.1, summary.jankRate, 1e-9)
        assertEquals(5.0, summary.p50FrameTimeMs, 1e-9)
        assertEquals(40.0, summary.p95FrameTimeMs, 1e-9)
        assertEquals(800.0, summary.maxFrameTimeMs, 1e-9)
    }

    @Test
    fun ignoredFramesAreNotCounted() {
        // The first tick has no interval yet; a tick that does not move forward is ignored.
        frames.emit(START)
        frames.emit(START + PERIOD)
        frames.emit(START + PERIOD)

        assertEquals(1L, session.summary().frameCount)
    }

    @Test
    fun durationRunsUntilNowAndStopsWithTheSession() {
        now = START + 1_500 * MS
        assertEquals(1_500.0, session.summary().durationMs, 1e-9)

        now = START + 2_000 * MS
        session.stop()
        now = START + 9_000 * MS

        assertEquals(2_000.0, session.summary().durationMs, 1e-9)
    }

    @Test
    fun theSummaryKeepsItsTotalsAfterStop() {
        frames.emitMeasured(START, 40 * MS)
        session.stop()
        frames.emitStrayMeasured(START + PERIOD, 40 * MS)

        assertEquals(1L, session.summary().jankCount)
    }

    @Test
    fun tagsAreBrokenDown() {
        session.setTag("screen", "Feed")
        frames.emitMeasured(START + MS, 5 * MS)
        now = START + 20 * MS
        session.setTag("scrolling", "true")
        frames.emitMeasured(START + 20 * MS, 40 * MS)
        now = START + 50 * MS
        session.removeTag("scrolling")
        frames.emitMeasured(START + 60 * MS, 5 * MS)

        assertEquals(
            listOf(
                TagSummary("screen", "Feed", frameCount = 3, jankCount = 1, frozenFrameCount = 0),
                TagSummary("scrolling", "true", frameCount = 1, jankCount = 1, frozenFrameCount = 0),
            ),
            session.summary().tags,
        )
    }

    @Test
    fun summaryThrowsOffTheMainThread() {
        onMainThread = false

        assertThrows(IllegalStateException::class.java) { session.summary() }
    }

    private companion object {
        const val MS = 1_000_000L
        const val PERIOD = 16_666_667L
        const val START = 7_000_000_000_000L
    }
}

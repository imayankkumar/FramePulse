package com.mayank.framepulse.internal

import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.FramePulseMetrics
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FakeFrameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A session with measured frames reports the frames drawn since its last snapshot once the
 * window stops drawing.
 */
class FramePulseSessionIdleReportTest {

    private val frames = FakeFrameSource()
    private val received = mutableListOf<FramePulseMetrics>()
    private var now = START

    /** The idle check the session posted and its delay, until the test runs it. */
    private var posted: Runnable? = null
    private var postedDelayMs = -1L
    private val cancelled = mutableListOf<Runnable>()

    /** Default config: reports at most every 250 ms, 60 Hz budget. */
    private fun newSession(config: FramePulseConfig = FramePulseConfig.DEFAULT) =
        FramePulseSessionImpl(
            config = config,
            listener = { received += it },
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(frames, consumer, isMainThread = { true })
            },
            isMainThread = { true },
            nanoTime = { now },
            postDelayedToMainThread = { runnable, delayMs ->
                check(posted == null) { "Only one idle check may be posted at a time" }
                posted = runnable
                postedDelayMs = delayMs
            },
            cancelOnMainThread = { cancelled += it },
        ).also { it.start() }

    /** Delivers a 5 ms frame that started at [startMs] after [START], as the platform would. */
    private fun drawFrame(startMs: Long, durationMs: Long = 5) {
        val startNanos = START + startMs * MS
        now = startNanos + durationMs * MS
        frames.emitMeasured(startNanos, durationMs * MS)
    }

    /** Runs the posted idle check at [atMs] after [START]. */
    private fun runPostedCheckAt(atMs: Long) {
        val runnable = posted ?: throw AssertionError("No idle check was posted")
        posted = null
        now = START + atMs * MS
        runnable.run()
    }

    @Test
    fun framesDrawnBeforeTheWindowGoesIdleAreReportedOnce() {
        newSession()
        drawFrame(startMs = 0)
        for (i in 1..5) drawFrame(startMs = i * 16L)
        assertEquals(1, received.size)
        // Posted at the first unreported frame (delivered at 21 ms): due at 250 ms.
        assertEquals(229L, postedDelayMs)

        runPostedCheckAt(atMs = 250)

        assertEquals(2, received.size)
        assertEquals(6L, received.last().totalFrameCount)
        assertEquals(START + 80 * MS, received.last().lastFrameTimeNanos)
        assertNull(posted)
    }

    @Test
    fun theCheckWaitsWhileTheWindowIsStillDrawing() {
        newSession()
        drawFrame(startMs = 0)
        for (i in 1..12) drawFrame(startMs = i * 16L)

        // The last frame (192 ms) was delivered at 197 ms: 63 ms ago at 260 ms.
        runPostedCheckAt(atMs = 260)

        assertEquals(1, received.size)
        assertEquals(37L, postedDelayMs)

        runPostedCheckAt(atMs = 297)
        assertEquals(2, received.size)
        assertEquals(13L, received.last().totalFrameCount)
    }

    @Test
    fun aFrameThatIsReportedItselfLeavesTheCheckNothingToDo() {
        newSession()
        drawFrame(startMs = 0)
        drawFrame(startMs = 16)
        drawFrame(startMs = 300)
        assertEquals(2, received.size)

        runPostedCheckAt(atMs = 400)

        assertEquals(2, received.size)
        assertNull(posted)
    }

    @Test
    fun theNextReportWaitsOneIntervalAfterTheIdleReport() {
        newSession()
        drawFrame(startMs = 0)
        drawFrame(startMs = 16)
        runPostedCheckAt(atMs = 300)
        assertEquals(2, received.size)

        // 400 ms is under 250 ms after the idle report at 300 ms.
        drawFrame(startMs = 400)

        assertEquals(2, received.size)
        assertEquals(145L, postedDelayMs)
        runPostedCheckAt(atMs = 550)
        assertEquals(3, received.size)
        assertEquals(3L, received.last().totalFrameCount)
    }

    @Test
    fun stopRemovesThePostedCheck() {
        val session = newSession()
        drawFrame(startMs = 0)
        drawFrame(startMs = 16)
        val check = posted!!

        session.stop()

        assertSame(check, cancelled.single())
        check.run()
        assertEquals(1, received.size)
    }

    @Test
    fun noCheckIsPostedWhenEveryFrameIsReported() {
        newSession(FramePulseConfig.Builder().reportIntervalMs(0).build())

        for (i in 0..5) drawFrame(startMs = i * 16L)

        assertEquals(6, received.size)
        assertNull(posted)
    }

    @Test
    fun frameTicksNeverPostACheck() {
        newSession()

        for (i in 0..5) frames.emit(START + i * 16 * MS)

        assertTrue(received.size < 5)
        assertNull(posted)
    }

    private companion object {
        const val MS = 1_000_000L
        const val START = 7_000_000_000_000L
    }
}

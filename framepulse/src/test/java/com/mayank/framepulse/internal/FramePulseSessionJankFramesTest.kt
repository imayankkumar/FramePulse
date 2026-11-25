package com.mayank.framepulse.internal

import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FakeFrameSource
import com.mayank.framepulse.internal.source.FrameStageNanos
import com.mayank.framepulse.FramePulseMetrics
import com.mayank.framepulse.FrameStages
import com.mayank.framepulse.JankFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A session with a [com.mayank.framepulse.JankFrameListener]. */
class FramePulseSessionJankFramesTest {

    private val frames = FakeFrameSource()
    private val events = mutableListOf<Any>()
    private val jankFrames get() = events.filterIsInstance<JankFrame>()
    private val metrics get() = events.filterIsInstance<FramePulseMetrics>()

    /** 60 Hz budget, so the threshold is 16.67 ms * 1.5, about 25 ms. Reports every frame. */
    private fun newSession(onJank: (JankFrame) -> Unit = {}): FramePulseSessionImpl =
        FramePulseSessionImpl(
            config = FramePulseConfig.Builder().reportIntervalMs(0).build(),
            listener = { events += it },
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(frames, consumer, isMainThread = { true })
            },
            jankFrameListener = { frame ->
                events += frame
                onJank(frame)
            },
        )

    @Test
    fun onlyJankFramesReachTheJankListener() {
        newSession().start()

        frames.emitMeasured(START, 5 * NANOS_PER_MS)
        frames.emitMeasured(START + PERIOD, 30 * NANOS_PER_MS)
        frames.emitMeasured(START + 3 * PERIOD, 25 * NANOS_PER_MS)

        val jank = jankFrames.single()
        assertEquals(START + PERIOD, jank.frameTimeNanos)
        assertEquals(30.0, jank.durationMs, 0.001)
        assertEquals(25.0, jank.budgetMs, 0.01)
        assertFalse(jank.isFrozen)
        assertEquals(3, metrics.size)
    }

    @Test
    fun theDeadlineIsTheBudgetWhenTheFrameHasOne() {
        newSession().start()

        frames.emitMeasured(START, 12 * NANOS_PER_MS, deadlineNanos = 11 * NANOS_PER_MS)

        assertEquals(11.0, jankFrames.single().budgetMs, 0.001)
    }

    @Test
    fun theJankFrameComesBeforeTheMetricsForTheSameFrame() {
        newSession().start()

        frames.emitMeasured(START, 40 * NANOS_PER_MS)

        assertTrue(events[0] is JankFrame)
        assertTrue(events[1] is FramePulseMetrics)
        assertEquals(1L, (events[1] as FramePulseMetrics).totalJankCount)
    }

    @Test
    fun stagesAreCopiedIntoTheJankFrame() {
        newSession().start()
        val stages = FrameStageNanos().apply {
            unknownDelay = 1 * NANOS_PER_MS
            inputHandling = 2 * NANOS_PER_MS
            animation = 3 * NANOS_PER_MS
            layoutMeasure = 4 * NANOS_PER_MS
            draw = 5 * NANOS_PER_MS
            sync = 6 * NANOS_PER_MS
            commandIssue = 7 * NANOS_PER_MS
            swapBuffers = 8 * NANOS_PER_MS
            gpu = 9 * NANOS_PER_MS
        }

        frames.emitMeasured(START, 40 * NANOS_PER_MS, stages = stages)
        // The source reuses its holder; the delivered frame must not change with it.
        stages.draw = 99 * NANOS_PER_MS

        assertEquals(
            FrameStages(
                unknownDelayMs = 1.0,
                inputHandlingMs = 2.0,
                animationMs = 3.0,
                layoutMeasureMs = 4.0,
                drawMs = 5.0,
                syncMs = 6.0,
                commandIssueMs = 7.0,
                swapBuffersMs = 8.0,
                gpuMs = 9.0,
            ),
            jankFrames.single().stages,
        )
    }

    @Test
    fun anUnreportedGpuStageIsNull() {
        newSession().start()
        val stages = FrameStageNanos().apply { draw = 5 * NANOS_PER_MS }

        frames.emitMeasured(START, 40 * NANOS_PER_MS, stages = stages)

        val delivered = jankFrames.single().stages!!
        assertNull(delivered.gpuMs)
        assertEquals(5.0, delivered.drawMs, 0.0)
        // Other unreported stages read as 0.
        assertEquals(0.0, delivered.syncMs, 0.0)
    }

    @Test
    fun aFrameOverTheFrozenThresholdIsFrozen() {
        newSession().start()

        frames.emitMeasured(START, 700 * NANOS_PER_MS)
        frames.emitMeasured(START + 700 * NANOS_PER_MS, 700 * NANOS_PER_MS + 1)

        assertEquals(listOf(false, true), jankFrames.map { it.isFrozen })
    }

    @Test
    fun frameTicksReportTheLongIntervalWithoutStages() {
        newSession().start()

        frames.emit(START)
        frames.emit(START + PERIOD)
        frames.emit(START + PERIOD + 200 * NANOS_PER_MS)

        val jank = jankFrames.single()
        assertEquals(START + PERIOD + 200 * NANOS_PER_MS, jank.frameTimeNanos)
        assertEquals(200.0, jank.durationMs, 0.001)
        assertEquals(25.0, jank.budgetMs, 0.01)
        assertNull(jank.stages)
    }

    @Test
    fun ignoredFramesAreNeverReportedAsJank() {
        newSession().start()
        frames.emit(START)
        frames.emit(START + 100 * NANOS_PER_MS)

        // A tick that does not move forward is ignored, so the previous jank is not repeated.
        frames.emit(START + 100 * NANOS_PER_MS)

        assertEquals(1, jankFrames.size)
    }

    @Test
    fun stoppingInsideTheJankListenerSkipsTheMetricsCall() {
        lateinit var session: FramePulseSessionImpl
        session = newSession(onJank = { session.stop() })
        session.start()

        frames.emitMeasured(START, 40 * NANOS_PER_MS)
        frames.emitStrayMeasured(START + PERIOD, 40 * NANOS_PER_MS)

        assertEquals(1, jankFrames.size)
        assertTrue(metrics.isEmpty())
        assertFalse(session.isRunning)
    }

    @Test
    fun withoutAJankListenerMetricsStillArrive() {
        val received = mutableListOf<FramePulseMetrics>()
        FramePulseSessionImpl(
            config = FramePulseConfig.Builder().reportIntervalMs(0).build(),
            listener = { received += it },
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(frames, consumer, isMainThread = { true })
            },
        ).start()

        frames.emitMeasured(START, 40 * NANOS_PER_MS)

        assertEquals(1L, received.single().totalJankCount)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val PERIOD = 16_666_667L
        const val START = 7_000_000_000_000L
    }
}

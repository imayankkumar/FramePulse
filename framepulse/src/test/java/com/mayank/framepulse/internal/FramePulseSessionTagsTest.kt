package com.mayank.framepulse.internal

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FakeFrameSource
import com.mayank.framepulse.model.JankFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tags set on a session and attached to its jank frames. */
class FramePulseSessionTagsTest {

    private val frames = FakeFrameSource()
    private val jankFrames = mutableListOf<JankFrame>()
    private var now = START
    private var onMainThread = true

    /** 60 Hz budget, so frames over about 25 ms are jank. */
    private val session = FramePulseSessionImpl(
        config = FramePulseConfig(reportIntervalMs = 0),
        listener = {},
        calculator = RollingWindowFpsCalculator(),
        monitorFactory = { consumer ->
            FrameMonitorImpl(frames, consumer, isMainThread = { true })
        },
        jankFrameListener = { jankFrames += it },
        isMainThread = { onMainThread },
        nanoTime = { now },
    ).also { it.start() }

    @Test
    fun aJankFrameCarriesTheTagsSetWhileItRan() {
        session.setTag("screen", "Feed")
        session.setTag("scrolling", "true")

        frames.emitMeasured(START + MS, 40 * MS)

        assertEquals(mapOf("screen" to "Feed", "scrolling" to "true"), jankFrames.single().tags)
    }

    @Test
    fun aTagRemovedBeforeTheReportArrivesStillShowsOnTheFrame() {
        session.setTag("scrolling", "true")
        // The frame runs from START + 1 ms to START + 41 ms; scrolling stops at 30 ms and the
        // platform reports the frame afterwards.
        now = START + 30 * MS
        session.removeTag("scrolling")

        frames.emitMeasured(START + MS, 40 * MS)

        assertEquals(mapOf("scrolling" to "true"), jankFrames.single().tags)
    }

    @Test
    fun aTagRemovedBeforeTheFrameStartedIsNotOnIt() {
        session.setTag("scrolling", "true")
        now = START + 10 * MS
        session.removeTag("scrolling")

        frames.emitMeasured(START + 20 * MS, 40 * MS)

        assertTrue(jankFrames.single().tags.isEmpty())
    }

    @Test
    fun aTagSetAfterTheFrameEndedIsNotOnIt() {
        now = START + 50 * MS
        session.setTag("screen", "Detail")

        frames.emitMeasured(START, 40 * MS)

        assertTrue(jankFrames.single().tags.isEmpty())
    }

    @Test
    fun frameTicksMatchTagsAgainstTheLongInterval() {
        frames.emit(START)
        now = START + 100 * MS
        session.setTag("loading", "true")
        now = START + 150 * MS
        session.removeTag("loading")

        // The 200 ms interval from START to START + 200 ms covers the whole tag.
        frames.emit(START + 200 * MS)

        assertEquals(mapOf("loading" to "true"), jankFrames.single().tags)
    }

    @Test
    fun withoutTagsAJankFrameHasAnEmptyMap() {
        frames.emitMeasured(START, 40 * MS)

        assertTrue(jankFrames.single().tags.isEmpty())
    }

    @Test
    fun tagMethodsThrowOffTheMainThread() {
        onMainThread = false

        assertThrows(IllegalStateException::class.java) { session.setTag("screen", "Feed") }
        assertThrows(IllegalStateException::class.java) { session.removeTag("screen") }

        onMainThread = true
        frames.emitMeasured(START, 40 * MS)
        assertTrue(jankFrames.single().tags.isEmpty())
    }

    private companion object {
        const val MS = 1_000_000L
        const val START = 7_000_000_000_000L
    }
}

package com.mayank.framepulse.internal

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FakeFrameSource
import com.mayank.framepulse.internal.source.FakeRefreshRateSource
import com.mayank.framepulse.model.FrameMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Frame budget that follows the display refresh rate, driven by [FakeRefreshRateSource]. */
class FramePulseSessionRefreshRateTest {

    private val frames = FakeFrameSource()
    private val received = mutableListOf<FrameMetrics>()
    private var onMainThread = true
    private var now = 0L

    private fun newSession(
        refreshRateSource: FakeRefreshRateSource?,
        config: FramePulseConfig = FramePulseConfig(reportIntervalMs = 0),
    ) = FramePulseSessionImpl(
        config = config,
        listener = { received += it },
        calculator = RollingWindowFpsCalculator(),
        monitorFactory = { consumer ->
            FrameMonitorImpl(frames, consumer, isMainThread = { onMainThread })
        },
        refreshRateSource = refreshRateSource,
    )

    /** Emits one frame [intervalNanos] after the previous one (the first frame is at 0). */
    private fun frameAfter(intervalNanos: Long) {
        now += intervalNanos
        frames.emit(now)
    }

    private val totalJank: Long
        get() = received.last().totalJankCount

    @Test
    fun fixedBudgetCountsEverySixtyHertzFrameAsJankAfterADrop() {
        // Without tracking, a 120 Hz budget (12.5 ms threshold) flags every 60 Hz frame.
        val session = newSession(
            refreshRateSource = null,
            config = FramePulseConfig(reportIntervalMs = 0).withRefreshRate(120f),
        )
        session.start()
        frames.emit(now)
        repeat(3) { frameAfter(HZ_120) }
        repeat(3) { frameAfter(HZ_60) }

        assertEquals(3L, totalJank)
    }

    @Test
    fun budgetFollowsADropFrom120To60Hz() {
        val display = FakeRefreshRateSource(120f)
        newSession(display).start()
        frames.emit(now)
        repeat(3) { frameAfter(HZ_120) }

        display.changeTo(60f)
        repeat(3) { frameAfter(HZ_60) }
        assertEquals(0L, totalJank)

        // A real miss at 60 Hz (threshold 25 ms) is still counted.
        frameAfter(40 * MS)
        assertEquals(1L, totalJank)
    }

    @Test
    fun budgetFollowsARiseFrom60To120Hz() {
        val display = FakeRefreshRateSource(60f)
        newSession(display).start()
        frames.emit(now)
        frameAfter(20 * MS)
        assertEquals(0L, totalJank)

        display.changeTo(120f)
        // 20 ms is under the 60 Hz threshold (25 ms) but over the 120 Hz one (12.5 ms).
        frameAfter(20 * MS)
        assertEquals(1L, totalJank)
    }

    @Test
    fun rateReadAtStartReplacesTheConfiguredPeriod() {
        // Config says 60 Hz; the display says 120 Hz.
        newSession(FakeRefreshRateSource(120f)).start()
        frames.emit(now)
        frameAfter(20 * MS)

        assertEquals(1L, totalJank)
    }

    @Test
    fun invalidRatesAreIgnored() {
        val display = FakeRefreshRateSource(0f)
        newSession(display).start()
        frames.emit(now)
        // Start rate 0 is ignored, so the configured 60 Hz budget (25 ms) applies.
        frameAfter(20 * MS)
        assertEquals(0L, totalJank)

        display.changeTo(120f)
        display.changeTo(Float.NaN)
        display.changeTo(Float.POSITIVE_INFINITY)
        display.changeTo(-60f)
        // Still the 120 Hz budget (12.5 ms).
        frameAfter(HZ_60)
        assertEquals(1L, totalJank)
    }

    @Test
    fun stopUnregistersTheRefreshSourceOnce() {
        val display = FakeRefreshRateSource(60f)
        val session = newSession(display)
        session.start()
        assertTrue(display.isListening)

        session.stop()
        session.stop()

        assertFalse(display.isListening)
        assertEquals(1, display.stopCount)
    }

    @Test
    fun stopBeforeStartDoesNotTouchTheRefreshSource() {
        val display = FakeRefreshRateSource(60f)
        newSession(display).stop()

        assertEquals(0, display.startCount)
        assertEquals(0, display.stopCount)
    }

    @Test
    fun startOffTheMainThreadRegistersNothing() {
        onMainThread = false
        val display = FakeRefreshRateSource(60f)
        val session = newSession(display)

        assertThrows(IllegalStateException::class.java) { session.start() }

        assertEquals(0, display.startCount)
        assertEquals(0, frames.startCount)
        assertFalse(session.isRunning)
    }

    @Test
    fun stopOffTheMainThreadKeepsTheRefreshSourceRegistered() {
        val display = FakeRefreshRateSource(60f)
        val session = newSession(display)
        session.start()

        onMainThread = false
        assertThrows(IllegalStateException::class.java) { session.stop() }
        assertTrue(display.isListening)
        assertEquals(0, display.stopCount)

        onMainThread = true
        session.stop()
        assertFalse(display.isListening)
        assertEquals(1, display.stopCount)
    }

    @Test
    fun refreshSourceStartFailureStopsFramesAndRethrows() {
        val failure = IllegalStateException("boom")
        val display = FakeRefreshRateSource(60f).apply { startFailure = failure }
        val session = newSession(display)

        val thrown = assertThrows(IllegalStateException::class.java) { session.start() }

        assertSame(failure, thrown)
        assertFalse(session.isRunning)
        assertEquals(1, frames.stopCount)
        assertFalse(frames.isRunning)
    }

    private companion object {
        const val MS = 1_000_000L
        const val HZ_60 = 16_666_667L
        const val HZ_120 = 8_333_333L
    }
}

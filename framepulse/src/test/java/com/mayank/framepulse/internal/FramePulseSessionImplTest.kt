package com.mayank.framepulse.internal

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.api.FramePulseListener
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FakeFrameSource
import com.mayank.framepulse.model.FrameMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session + monitor lifecycle and listener delivery, driven by [FakeFrameSource]. */
class FramePulseSessionImplTest {

    private val source = FakeFrameSource()
    private val received = mutableListOf<FrameMetrics>()
    private var onMainThread = true

    private fun newSession(
        listener: FramePulseListener = FramePulseListener { received += it },
    ) = FramePulseSessionImpl(
        config = FramePulseConfig(reportIntervalMs = 0),
        listener = listener,
        calculator = RollingWindowFpsCalculator(),
        monitorFactory = { consumer ->
            FrameMonitorImpl(source, consumer, isMainThread = { onMainThread })
        },
    )

    @Test
    fun startRegistersSourceAndListenerReceivesMetrics() {
        val session = newSession()
        assertFalse(session.isRunning)

        session.start()
        source.emit(0L)
        source.emit(PERIOD)
        source.emit(2 * PERIOD)

        assertTrue(session.isRunning)
        assertEquals(1, source.startCount)
        assertEquals(2, received.size)
        assertEquals(2L, received.last().totalFrameCount)
    }

    @Test
    fun secondStartThrowsAndDoesNotRegisterTwice() {
        val session = newSession()
        session.start()

        assertThrows(IllegalStateException::class.java) { session.start() }
        assertEquals(1, source.startCount)
        assertTrue(session.isRunning)
    }

    @Test
    fun startAfterStopThrows() {
        val session = newSession()
        session.start()
        session.stop()

        assertThrows(IllegalStateException::class.java) { session.start() }
        assertEquals(1, source.startCount)
        assertFalse(session.isRunning)
    }

    @Test
    fun stopIsIdempotentAndStopsTheSourceOnce() {
        val session = newSession()
        session.start()

        session.stop()
        session.stop()

        assertFalse(session.isRunning)
        assertEquals(1, source.stopCount)
    }

    @Test
    fun stopBeforeStartIsANoOp() {
        val session = newSession()

        session.stop()

        assertEquals(0, source.stopCount)
        assertFalse(session.isRunning)
    }

    @Test
    fun noListenerCallsAfterStopEvenForStrayCallbacks() {
        val session = newSession()
        session.start()
        source.emit(0L)
        source.emit(PERIOD)
        assertEquals(1, received.size)

        session.stop()
        source.emitStray(2 * PERIOD)
        source.emitStray(3 * PERIOD)

        assertEquals(1, received.size)
    }

    @Test
    fun listenerMayStopTheSessionFromInsideTheCallback() {
        var calls = 0
        lateinit var session: FramePulseSessionImpl
        session = newSession(listener = {
            calls++
            session.stop()
        })
        session.start()

        source.emit(0L)
        source.emit(PERIOD)
        source.emitStray(2 * PERIOD)

        assertEquals(1, calls)
        assertFalse(session.isRunning)
    }

    @Test
    fun startOffTheMainThreadThrowsWithoutRegistering() {
        onMainThread = false
        val session = newSession()

        assertThrows(IllegalStateException::class.java) { session.start() }
        assertEquals(0, source.startCount)
        assertFalse(session.isRunning)
    }

    @Test
    fun stopOffTheMainThreadThrowsAndLeavesTheSessionRunning() {
        val session = newSession()
        session.start()
        source.emit(0L)

        onMainThread = false
        assertThrows(IllegalStateException::class.java) { session.stop() }

        // Nothing changed: the source is still registered and frames still reach the listener.
        assertTrue(session.isRunning)
        assertEquals(0, source.stopCount)
        source.emit(PERIOD)
        assertEquals(1, received.size)

        // A retry on the main thread stops the source exactly once.
        onMainThread = true
        session.stop()

        assertFalse(session.isRunning)
        assertEquals(1, source.stopCount)
    }

    @Test
    fun stopOffTheMainThreadIsANoOpWhenNotRunning() {
        val idle = newSession()
        onMainThread = false
        idle.stop()
        assertEquals(0, source.stopCount)

        onMainThread = true
        val stopped = newSession()
        stopped.start()
        stopped.stop()
        onMainThread = false
        stopped.stop()

        assertFalse(stopped.isRunning)
        assertEquals(1, source.stopCount)
    }

    @Test
    fun sourceStartFailureIsRethrownAndLeavesSessionIdle() {
        val failure = IllegalStateException("boom")
        source.startFailure = failure
        val session = newSession()

        val thrown = assertThrows(IllegalStateException::class.java) { session.start() }

        assertSame(failure, thrown)
        assertFalse(session.isRunning)
        assertEquals(1, source.stopCount)
    }

    private companion object {
        const val PERIOD = 16_666_667L
    }
}

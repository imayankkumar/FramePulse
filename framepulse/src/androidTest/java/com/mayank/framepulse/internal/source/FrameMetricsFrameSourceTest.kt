package com.mayank.framepulse.internal.source

import android.content.Intent
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [FrameMetricsFrameSource] against a real window on API 24+.
 *
 * Tests run on the instrumentation thread. Source calls and frame state go through [onMain].
 * The window only reports frames it draws, so tests keep invalidating its content view.
 */
@RunWith(AndroidJUnit4::class)
class FrameMetricsFrameSourceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: FrameTestActivity? = null
    private var source: FrameMetricsFrameSource? = null

    private val durations = mutableListOf<Long>()
    private val deadlines = mutableListOf<Long>()
    private var tickCount = 0
    private var deliveredOffMainThread = false

    @Before
    fun launchWindow() {
        assumeTrue("FrameMetrics needs API 24", Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
        val intent = Intent(instrumentation.targetContext, FrameTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activity = instrumentation.startActivitySync(intent) as FrameTestActivity
    }

    @After
    fun tearDown() {
        onMain {
            source?.stop()
            activity?.finish()
        }
    }

    @Test
    fun deliversMeasuredFramesOnMainThreadWhileTheWindowDraws() {
        val frames = CountDownLatch(FRAMES_TO_WAIT_FOR)
        onMain { newSource().start(recordingConsumer(frames)) }

        redrawUntil(frames)

        assertTrue(onMain { source!!.isRunning })
        assertFalse(onMain { deliveredOffMainThread })
        assertEquals(0, onMain { tickCount })
        val seen = onMain { durations.toList() }
        assertTrue("durations must be positive: $seen", seen.all { it > 0L })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val seenDeadlines = onMain { deadlines.toList() }
            assertTrue("API 31+ reports a deadline: $seenDeadlines", seenDeadlines.all { it > 0L })
        }
    }

    @Test
    fun stopEndsFrameDelivery() {
        val frames = CountDownLatch(FRAMES_TO_WAIT_FOR)
        onMain { newSource().start(recordingConsumer(frames)) }
        redrawUntil(frames)

        val countAtStop = onMain {
            source!!.stop()
            durations.size
        }
        redrawFor(QUIET_PERIOD_MS)

        assertFalse(onMain { source!!.isRunning })
        assertEquals(countAtStop, onMain { durations.size })
    }

    @Test
    fun startWhileRunningThrows() {
        onMain { newSource().start(recordingConsumer(CountDownLatch(1))) }

        val error = onMain {
            runCatching { source!!.start(recordingConsumer(CountDownLatch(1))) }.exceptionOrNull()
        }

        assertTrue(error is IllegalStateException)
        assertTrue(onMain { source!!.isRunning })
    }

    @Test
    fun startOffMainThreadThrows() {
        val offMain = onMain { newSource() }

        assertThrows(IllegalStateException::class.java) {
            offMain.start(recordingConsumer(CountDownLatch(1)))
        }

        assertFalse(onMain { offMain.isRunning })
    }

    private fun newSource(): FrameMetricsFrameSource =
        FrameMetricsFrameSource(activity!!.window).also { source = it }

    private fun recordingConsumer(frames: CountDownLatch) = object : FrameConsumer {
        override fun onFrameTick(frameTimeNanos: Long) {
            tickCount++
        }

        override fun onFrameMeasured(
            frameTimeNanos: Long,
            durationNanos: Long,
            deadlineNanos: Long,
        ) {
            if (Looper.myLooper() !== Looper.getMainLooper()) deliveredOffMainThread = true
            durations += durationNanos
            deadlines += deadlineNanos
            frames.countDown()
        }
    }

    /** Keeps the window drawing until [latch] is done. */
    private fun redrawUntil(latch: CountDownLatch) {
        val giveUpAt = SystemClock.uptimeMillis() + FRAME_TIMEOUT_MS
        while (latch.count > 0 && SystemClock.uptimeMillis() < giveUpAt) {
            onMain { activity!!.content.invalidate() }
            latch.await(REDRAW_INTERVAL_MS, TimeUnit.MILLISECONDS)
        }
        assertEquals("no frame reports within $FRAME_TIMEOUT_MS ms", 0L, latch.count)
    }

    /** Keeps the window drawing for [durationMs]. */
    private fun redrawFor(durationMs: Long) {
        val until = SystemClock.uptimeMillis() + durationMs
        while (SystemClock.uptimeMillis() < until) {
            onMain { activity!!.content.invalidate() }
            SystemClock.sleep(REDRAW_INTERVAL_MS)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val FRAMES_TO_WAIT_FOR = 3
        const val FRAME_TIMEOUT_MS = 10_000L
        const val REDRAW_INTERVAL_MS = 32L
        const val QUIET_PERIOD_MS = 300L
    }
}

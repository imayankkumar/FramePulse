package com.mayank.framepulse.internal.source

import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [ChoreographerFrameSource] against the real main-thread [android.view.Choreographer].
 *
 * Tests run on the instrumentation thread, which has no looper. Source calls that must happen
 * on the main thread go through [onMain]. Frame state is only touched on the main thread.
 */
@RunWith(AndroidJUnit4::class)
class ChoreographerFrameSourceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val source = ChoreographerFrameSource()
    private val frameTimes = mutableListOf<Long>()
    private var deliveredOffMainThread = false

    @After
    fun tearDown() {
        onMain { source.stop() }
    }

    @Test
    fun deliversIncreasingFrameTimesOnMainThread() {
        val frames = CountDownLatch(FRAMES_TO_WAIT_FOR)
        onMain { source.start(recordingConsumer(frames)) }

        awaitFrames(frames)

        assertTrue(onMain { source.isRunning })
        val times = onMain { frameTimes.toList() }
        assertFalse(onMain { deliveredOffMainThread })
        for (i in 1 until times.size) {
            assertTrue("frame times must increase: $times", times[i] > times[i - 1])
        }
    }

    @Test
    fun stopEndsFrameDelivery() {
        val frames = CountDownLatch(FRAMES_TO_WAIT_FOR)
        onMain { source.start(recordingConsumer(frames)) }
        awaitFrames(frames)

        val countAtStop = onMain {
            source.stop()
            frameTimes.size
        }
        SystemClock.sleep(QUIET_PERIOD_MS)

        assertFalse(onMain { source.isRunning })
        assertEquals(countAtStop, onMain { frameTimes.size })
    }

    @Test
    fun stopInsideConsumerEndsFrameDelivery() {
        val firstFrame = CountDownLatch(1)
        onMain {
            source.start { frameTimeNanos ->
                frameTimes += frameTimeNanos
                source.stop()
                firstFrame.countDown()
            }
        }
        awaitFrames(firstFrame)

        SystemClock.sleep(QUIET_PERIOD_MS)

        assertFalse(onMain { source.isRunning })
        assertEquals(1, onMain { frameTimes.size })
    }

    @Test
    fun startWhileRunningThrows() {
        onMain { source.start { } }

        val error = onMain { runCatching { source.start { } }.exceptionOrNull() }

        assertTrue(error is IllegalStateException)
        assertTrue(onMain { source.isRunning })
    }

    @Test
    fun startOffLooperThreadThrows() {
        assertThrows(IllegalStateException::class.java) { source.start { } }

        assertFalse(onMain { source.isRunning })
    }

    @Test
    fun stopOffLooperThreadThrowsAndKeepsDelivering() {
        val firstFrames = CountDownLatch(FRAMES_TO_WAIT_FOR)
        onMain { source.start(recordingConsumer(firstFrames)) }
        awaitFrames(firstFrames)

        assertThrows(IllegalStateException::class.java) { source.stop() }

        val countAfterFailedStop = onMain { frameTimes.size }
        SystemClock.sleep(QUIET_PERIOD_MS)
        assertTrue(onMain { source.isRunning })
        assertTrue(onMain { frameTimes.size } > countAfterFailedStop)
    }

    private fun recordingConsumer(frames: CountDownLatch): (Long) -> Unit = { frameTimeNanos ->
        if (Looper.myLooper() !== Looper.getMainLooper()) deliveredOffMainThread = true
        frameTimes += frameTimeNanos
        frames.countDown()
    }

    private fun awaitFrames(latch: CountDownLatch) {
        assertTrue(
            "no frame callbacks within $FRAME_TIMEOUT_MS ms",
            latch.await(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS),
        )
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val FRAMES_TO_WAIT_FOR = 5
        const val FRAME_TIMEOUT_MS = 5_000L

        // Several vsync periods even at 30 Hz.
        const val QUIET_PERIOD_MS = 200L
    }
}

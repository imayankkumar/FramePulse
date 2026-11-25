package com.mayank.framepulse

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mayank.framepulse.internal.source.FrameTestActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sessions tied to a real activity's lifecycle: [FramePulse.monitorActivities], the automatic
 * stop when the activity is destroyed, and [FramePulseSession.stop] from another thread.
 */
@RunWith(AndroidJUnit4::class)
class ActivityLifecycleTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application
    private var activity: FrameTestActivity? = null
    private var monitor: ActivityMonitor? = null

    @After
    fun tearDown() {
        onMain {
            monitor?.stop()
            activity?.finish()
        }
    }

    @Test
    fun monitorActivitiesReportsAnActivityWhenItStops() {
        val ended = CountDownLatch(1)
        var endedActivity: Activity? = null
        var summary: SessionSummary? = null
        monitor = onMain {
            FramePulse.monitorActivities(application) { stopped, stoppedSummary ->
                endedActivity = stopped
                summary = stoppedSummary
                ended.countDown()
            }
        }

        val launched = launch()
        assertNotNull(onMain { monitor!!.sessionFor(launched) })
        redrawFor(REDRAW_MS)
        onMain { launched.finish() }

        assertTrue("no summary after the activity stopped", ended.await(TIMEOUT_S, TimeUnit.SECONDS))
        assertSame(launched, endedActivity)
        val tag = summary!!.tags.single { it.name == FramePulse.ACTIVITY_TAG }
        assertEquals("FrameTestActivity", tag.value)
        assertEquals(summary!!.frameCount, tag.frameCount)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertTrue("drawn frames were counted: $summary", summary!!.frameCount > 0)
        }
        assertEquals(null, onMain { monitor!!.sessionFor(launched) })
    }

    @Test
    fun aSessionStartedWithAnActivityStopsWhenItIsDestroyed() {
        val launched = launch()
        val session = onMain { FramePulse.start(launched) { } }
        assertTrue(session.isRunning)

        onMain { launched.finish() }

        assertTrue("session still running after onDestroy", waitUntil { !session.isRunning })
    }

    @Test
    fun stopFromAnotherThreadStopsTheSession() {
        val launched = launch()
        var calls = 0
        val session = onMain { FramePulse.start(launched) { calls++ } }
        redrawFor(REDRAW_MS)

        session.stop()
        assertFalse(session.isRunning)
        val callsAtStop = onMain { calls }
        redrawFor(REDRAW_MS)

        assertEquals(callsAtStop, onMain { calls })
    }

    private fun launch(): FrameTestActivity {
        val intent = Intent(instrumentation.targetContext, FrameTestActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return (instrumentation.startActivitySync(intent) as FrameTestActivity)
            .also { activity = it }
    }

    private fun redrawFor(durationMs: Long) {
        val until = SystemClock.uptimeMillis() + durationMs
        while (SystemClock.uptimeMillis() < until) {
            onMain { activity!!.redraw() }
            SystemClock.sleep(REDRAW_INTERVAL_MS)
        }
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val giveUpAt = SystemClock.uptimeMillis() + TIMEOUT_S * 1_000
        while (SystemClock.uptimeMillis() < giveUpAt) {
            if (onMain(condition)) return true
            SystemClock.sleep(REDRAW_INTERVAL_MS)
        }
        return false
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private companion object {
        const val REDRAW_MS = 500L
        const val REDRAW_INTERVAL_MS = 32L
        const val TIMEOUT_S = 5L
    }
}

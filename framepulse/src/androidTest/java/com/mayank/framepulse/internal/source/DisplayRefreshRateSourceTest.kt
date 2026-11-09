package com.mayank.framepulse.internal.source

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [DisplayRefreshRateSource] against the real default display.
 *
 * Refresh-rate switches cannot be triggered from a test, so this covers registration and the
 * start value only. Source calls go through [onMain], like the frame source tests.
 */
@RunWith(AndroidJUnit4::class)
class DisplayRefreshRateSourceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val displayManager = instrumentation.targetContext
        .getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val display: Display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
    private val source = DisplayRefreshRateSource(displayManager, display)

    @After
    fun tearDown() {
        onMain { source.stop() }
    }

    @Test
    fun startReturnsTheDisplayRefreshRate() {
        val rate = onMain { source.start { } }

        assertTrue("refresh rate must be positive: $rate", rate > 0f)
        assertEquals(display.refreshRate, rate, 0.01f)
    }

    @Test
    fun secondStartThrowsAndStopIsIdempotent() {
        onMain { source.start { } }

        assertThrows(IllegalStateException::class.java) { onMain { source.start { } } }
        onMain {
            source.stop()
            source.stop()
        }
        // Stopped sources can be started again.
        onMain { source.start { } }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}

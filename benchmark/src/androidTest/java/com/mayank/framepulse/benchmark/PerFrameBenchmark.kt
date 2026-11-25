package com.mayank.framepulse.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.FramePulseListener
import com.mayank.framepulse.JankFrameListener
import com.mayank.framepulse.internal.FrameMonitorImpl
import com.mayank.framepulse.internal.FramePulseSessionImpl
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.FrameConsumer
import com.mayank.framepulse.internal.source.FrameSource
import com.mayank.framepulse.internal.source.FrameStageNanos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The work FramePulse does for each frame, fed with fake frames at 120 Hz.
 *
 * Each iteration is one frame. Frame times advance, so the rolling window fills and the default
 * report interval (250 ms) produces a metrics snapshot every 30 frames, as in a real session.
 * The reported time per frame therefore includes that snapshot's cost, spread over 30 frames.
 */
@RunWith(AndroidJUnit4::class)
class PerFrameBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private var now = START

    /** Only the rolling window: FPS, percentiles, jank and frozen counts. */
    @Test
    fun calculatorDrawnFrame() {
        val calculator = RollingWindowFpsCalculator()
        val config = FramePulseConfig.DEFAULT
        benchmarkRule.measureRepeated {
            now += PERIOD
            calculator.onMeasuredFrame(now, ON_TIME, DEADLINE, config)
        }
    }

    /** A drawn frame through the whole session: window, session summary and listener. */
    @Test
    fun sessionDrawnFrame() {
        val frames = newSession()
        val stages = stages()
        benchmarkRule.measureRepeated {
            now += PERIOD
            frames.onFrameMeasured(now, ON_TIME, DEADLINE, stages)
        }
    }

    /** As [sessionDrawnFrame], with three tags set, which the session counts on every frame. */
    @Test
    fun sessionDrawnFrameWithThreeTags() {
        val session = ManualSession()
        session.impl.setTag("screen", "Feed")
        session.impl.setTag("scrolling", "true")
        session.impl.setTag("experiment", "B")
        val stages = stages()
        benchmarkRule.measureRepeated {
            now += PERIOD
            session.frames.onFrameMeasured(now, ON_TIME, DEADLINE, stages)
        }
    }

    /** A `Choreographer` tick through the whole session (the fallback below API 24). */
    @Test
    fun sessionChoreographerTick() {
        val frames = newSession()
        benchmarkRule.measureRepeated {
            now += PERIOD
            frames.onFrameTick(now)
        }
    }

    /**
     * Every frame is jank and a jank listener is set. Each jank frame allocates one JankFrame
     * with its stages and tags, so this is the cost of a jank frame, not of a normal one.
     */
    @Test
    fun sessionJankFrameWithListener() {
        val session = ManualSession(jankFrameListener = { })
        session.impl.setTag("screen", "Feed")
        val stages = stages()
        benchmarkRule.measureRepeated {
            now += PERIOD
            session.frames.onFrameMeasured(now, JANK, DEADLINE, stages)
        }
    }

    private fun newSession(): FrameConsumer = ManualSession().frames

    private fun stages() = FrameStageNanos().apply {
        unknownDelay = 100_000
        inputHandling = 200_000
        animation = 300_000
        layoutMeasure = 400_000
        draw = 1_000_000
        sync = 200_000
        commandIssue = 800_000
        swapBuffers = 300_000
        gpu = 1_500_000
    }

    /** A started session whose frames the benchmark delivers by hand through [frames]. */
    private inner class ManualSession(jankFrameListener: JankFrameListener? = null) {
        private val source = ManualFrameSource()

        val impl = FramePulseSessionImpl(
            config = FramePulseConfig.DEFAULT,
            listener = NO_OP_LISTENER,
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(source, consumer, isMainThread = { true })
            },
            jankFrameListener = jankFrameListener,
            isMainThread = { true },
            nanoTime = { now },
        ).also { it.start() }

        val frames: FrameConsumer
            get() = source.consumer!!
    }

    private class ManualFrameSource : FrameSource {
        var consumer: FrameConsumer? = null
            private set

        override val isRunning: Boolean
            get() = consumer != null

        override fun start(frameConsumer: FrameConsumer) {
            consumer = frameConsumer
        }

        override fun stop() {
            consumer = null
        }
    }

    private companion object {
        const val START = 1_000_000_000_000L
        const val PERIOD = 8_333_333L
        const val DEADLINE = 8_333_333L
        const val ON_TIME = 5_000_000L
        const val JANK = 20_000_000L
        val NO_OP_LISTENER = FramePulseListener { }
    }
}

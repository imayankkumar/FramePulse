package com.mayank.framepulse.api

import com.mayank.framepulse.internal.FrameMonitorImpl
import com.mayank.framepulse.internal.FramePulseSessionImpl
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.ChoreographerFrameSource

/**
 * Public entry point for FramePulse.
 *
 * Threading contract: FramePulse is main-thread-confined and is not thread-safe. Call [start]
 * and [FramePulseSession.stop] on the main thread. The listener is invoked on the main thread,
 * inside a `Choreographer` frame callback, so keep it cheap.
 *
 * Lifecycle contract: the caller owns the session. FramePulse does not observe Activity or
 * process lifecycle; call [FramePulseSession.stop] when monitoring is no longer needed
 * (for example in `onStop`). A stopped session cannot be restarted; call [start] again.
 */
object FramePulse {

    /**
     * Starts a new monitoring session.
     *
     * @param config Window, frame budget, jank threshold and report interval.
     * @param listener Receives immutable metrics on the main thread, at most once per
     *   [FramePulseConfig.reportIntervalMs].
     * @return A handle used to stop monitoring.
     * @throws IllegalStateException if called off the main thread.
     */
    fun start(
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        listener: FramePulseListener,
    ): FramePulseSession {
        val session = FramePulseSessionImpl(
            config = config,
            listener = listener,
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(
                    frameSource = ChoreographerFrameSource(),
                    frameConsumer = consumer,
                )
            },
        )
        session.start()
        return session
    }
}

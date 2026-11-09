package com.mayank.framepulse.api

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.mayank.framepulse.internal.FrameMonitorImpl
import com.mayank.framepulse.internal.FramePulseSessionImpl
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.ChoreographerFrameSource
import com.mayank.framepulse.internal.source.DisplayRefreshRateSource
import com.mayank.framepulse.internal.source.RefreshRateSource

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
     * Starts a new monitoring session with a fixed frame budget.
     *
     * The budget is [FramePulseConfig.expectedFramePeriodMs] for the whole session. If the
     * display can change its refresh rate while you measure (variable refresh rate, 60/120 Hz
     * switching), use the overload that takes a [Display] instead.
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
    ): FramePulseSession = startSession(config, listener, refreshRateSource = null)

    /**
     * Starts a new monitoring session whose frame budget follows [display]'s refresh rate.
     *
     * At start the budget is set from `display.refreshRate`, replacing
     * [FramePulseConfig.expectedFramePeriodMs]. While the session runs, a
     * [DisplayManager.DisplayListener] updates the budget whenever the display reports a new
     * refresh rate. Only frames after the change use the new budget. The other [config] values
     * are kept. [FramePulseSession.stop] unregisters the display listener.
     *
     * The session keeps a [DisplayManager] obtained from `context.applicationContext`, not
     * [context] itself.
     *
     * @param context Any context; used only to get the [DisplayManager].
     * @param display The display the measured window is on (for example `Activity.getDisplay()`
     *   on API 30+, or `WindowManager.getDefaultDisplay()` below).
     * @param config Window, jank threshold multiplier and report interval.
     * @param listener Receives immutable metrics on the main thread, at most once per
     *   [FramePulseConfig.reportIntervalMs].
     * @return A handle used to stop monitoring.
     * @throws IllegalStateException if called off the main thread.
     */
    fun start(
        context: Context,
        display: Display,
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        listener: FramePulseListener,
    ): FramePulseSession {
        val appContext = context.applicationContext ?: context
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return startSession(
            config = config,
            listener = listener,
            refreshRateSource = DisplayRefreshRateSource(displayManager, display),
        )
    }

    private fun startSession(
        config: FramePulseConfig,
        listener: FramePulseListener,
        refreshRateSource: RefreshRateSource?,
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
            refreshRateSource = refreshRateSource,
        )
        session.start()
        return session
    }
}

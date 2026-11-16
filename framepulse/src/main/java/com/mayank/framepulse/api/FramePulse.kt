package com.mayank.framepulse.api

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import com.mayank.framepulse.internal.FrameMonitorImpl
import com.mayank.framepulse.internal.FramePulseSessionImpl
import com.mayank.framepulse.internal.metrics.RollingWindowFpsCalculator
import com.mayank.framepulse.internal.source.ChoreographerFrameSource
import com.mayank.framepulse.internal.source.DisplayRefreshRateSource
import com.mayank.framepulse.internal.source.FrameMetricsFrameSource
import com.mayank.framepulse.internal.source.FrameSource
import com.mayank.framepulse.internal.source.RefreshRateSource

/**
 * Public entry point for FramePulse.
 *
 * Which `start` to use:
 * - `start(activity, config, listener)` measures the frames the activity's window draws, with
 *   the durations the platform reports (API 24+). Prefer this one.
 * - The other two overloads need no window. They time `Choreographer` callbacks on the main
 *   thread, which shows how regularly the main thread serviced vsync, not what was drawn.
 *
 * Threading contract: FramePulse is main-thread-confined and is not thread-safe. Call [start]
 * and [FramePulseSession.stop] on the main thread. The listener is invoked on the main thread,
 * so keep it cheap.
 *
 * Lifecycle contract: the caller owns the session. FramePulse does not observe Activity or
 * process lifecycle; call [FramePulseSession.stop] when monitoring is no longer needed
 * (for example in `onStop`). A stopped session cannot be restarted; call [start] again.
 */
public object FramePulse {

    /**
     * Starts a new monitoring session for the frames drawn by [activity]'s window.
     *
     * On API 24 and higher the platform reports each frame the window draws and how long it
     * took (`Window.OnFrameMetricsAvailableListener`):
     * - Only drawn frames are counted. An idle window draws nothing, so the listener is not
     *   called while it is idle, and idle time is never counted as jank.
     * - The first frame of a new window layout is skipped; the platform expects it to be slow.
     * - On API 31 and higher a frame is jank when it took longer than the deadline the system
     *   gave it. [FramePulseConfig.jankThresholdMultiplier] is not used there.
     * - On API 24 to 30 a frame is jank when it took longer than
     *   [FramePulseConfig.jankThresholdMs], with the frame budget following the display's
     *   refresh rate as in the overload that takes a [Display].
     * - The window must be hardware accelerated (the default). A software-rendered window
     *   reports no frames.
     *
     * On API 21 to 23 this falls back to `Choreographer` callbacks with a budget that follows
     * the display's refresh rate, exactly like the overload that takes a [Display].
     *
     * Call this after the activity has set its content view (for example in `onStart`): reading
     * the window's decor view before that fixes the window's features.
     *
     * The session keeps a reference to the activity's window until [FramePulseSession.stop], so
     * stop it no later than `onDestroy`.
     *
     * @param activity The activity whose window is measured.
     * @param config Window, jank threshold multiplier and report interval.
     *   [FramePulseConfig.expectedFramePeriodMs] is replaced by the display's refresh rate.
     * @param listener Receives immutable metrics on the main thread, at most once per
     *   [FramePulseConfig.reportIntervalMs].
     * @return A handle used to stop monitoring.
     * @throws IllegalStateException if called off the main thread.
     */
    public fun start(
        activity: Activity,
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        listener: FramePulseListener,
    ): FramePulseSession {
        val frameSource: FrameSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FrameMetricsFrameSource(activity.window)
        } else {
            ChoreographerFrameSource()
        }
        return startSession(
            config = config,
            listener = listener,
            frameSource = frameSource,
            refreshRateSource = displayOf(activity)?.let { display ->
                DisplayRefreshRateSource(displayManagerOf(activity), display)
            },
        )
    }

    /**
     * Starts a new monitoring session with a fixed frame budget, timing `Choreographer`
     * callbacks on the main thread.
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
    public fun start(
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        listener: FramePulseListener,
    ): FramePulseSession = startSession(
        config = config,
        listener = listener,
        frameSource = ChoreographerFrameSource(),
        refreshRateSource = null,
    )

    /**
     * Starts a new monitoring session that times `Choreographer` callbacks on the main thread,
     * with a frame budget that follows [display]'s refresh rate.
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
    public fun start(
        context: Context,
        display: Display,
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        listener: FramePulseListener,
    ): FramePulseSession = startSession(
        config = config,
        listener = listener,
        frameSource = ChoreographerFrameSource(),
        refreshRateSource = DisplayRefreshRateSource(displayManagerOf(context), display),
    )

    private fun startSession(
        config: FramePulseConfig,
        listener: FramePulseListener,
        frameSource: FrameSource,
        refreshRateSource: RefreshRateSource?,
    ): FramePulseSession {
        val session = FramePulseSessionImpl(
            config = config,
            listener = listener,
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(frameSource = frameSource, frameConsumer = consumer)
            },
            refreshRateSource = refreshRateSource,
        )
        session.start()
        return session
    }

    private fun displayManagerOf(context: Context): DisplayManager {
        val appContext = context.applicationContext ?: context
        return appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private fun displayOf(activity: Activity): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        }
}

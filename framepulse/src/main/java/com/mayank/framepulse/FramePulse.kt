package com.mayank.framepulse

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import com.mayank.framepulse.internal.ActivityDestroyWatcher
import com.mayank.framepulse.internal.ActivityMonitorImpl
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
 * The simplest setup is [monitorActivities]: one call in `Application.onCreate` measures every
 * activity from `onStart` to `onStop` and reports a [SessionSummary] for each.
 *
 * To control a session yourself, use `start`:
 * - `start(activity, config, listener)` measures the frames the activity's window draws, with
 *   the durations the platform reports (API 24+). Prefer this one.
 * - The other two overloads need no window. They time `Choreographer` callbacks on the main
 *   thread, which shows how regularly the main thread serviced vsync, not what was drawn.
 *
 * Every overload takes an optional [JankFrameListener] that is called once for each jank frame,
 * in addition to the [FramePulseListener] that receives the rolling-window metrics.
 *
 * From Java the `start` methods are static, with overloads that leave out the optional
 * arguments: `FramePulse.start(activity, metrics -> { ... })`.
 *
 * Threading contract: call [start] on the main thread. [FramePulseSession.stop] works on any
 * thread; the other session methods are main thread only. The listeners are invoked on the main
 * thread, so keep them cheap.
 *
 * Lifecycle contract: the caller owns a session from [start]. Call [FramePulseSession.stop] when
 * monitoring is no longer needed (for example in `onStop`). A session started with an `Activity`
 * also stops itself when the activity is destroyed. A stopped session cannot be restarted; call
 * [start] again.
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
     * - Each [JankFrame] carries its stage breakdown
     *   ([FrameStages]).
     *
     * On API 21 to 23 this falls back to `Choreographer` callbacks with a budget that follows
     * the display's refresh rate, exactly like the overload that takes a [Display].
     *
     * Call this after the activity has set its content view (for example in `onStart`): reading
     * the window's decor view before that fixes the window's features.
     *
     * The session keeps a reference to the activity's window until it stops. It stops itself when
     * the activity is destroyed, but stop it yourself as soon as you no longer need it, normally
     * in `onStop`: a stopped activity draws nothing, and on API 21 to 23 the time in the
     * background would count as one long frame.
     *
     * @param activity The activity whose window is measured.
     * @param config Window, jank threshold multiplier and report interval.
     *   [FramePulseConfig.expectedFramePeriodMs] is replaced by the display's refresh rate.
     * @param jankFrameListener Optional. Receives every jank frame on the main thread, as soon
     *   as it is measured.
     * @param listener Receives immutable metrics on the main thread, at most once per
     *   [FramePulseConfig.reportIntervalMs].
     * @return A handle used to stop monitoring.
     * @throws IllegalStateException if called off the main thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun start(
        activity: Activity,
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        jankFrameListener: JankFrameListener? = null,
        listener: FramePulseListener,
    ): FramePulseSession {
        val watcher = ActivityDestroyWatcher(activity)
        val session = startActivitySession(
            activity = activity,
            config = config,
            jankFrameListener = jankFrameListener,
            listener = listener,
            onStopped = watcher::unwatch,
        )
        watcher.watch(session::stop)
        return session
    }

    /**
     * Measures every activity of the app from its `onStart` to its `onStop`, and passes each
     * activity's [SessionSummary] to [listener] when it stops. Call it once, in
     * `Application.onCreate`:
     *
     * ```kotlin
     * FramePulse.monitorActivities(this) { activity, summary ->
     *     Log.d("FramePulse", "${activity.javaClass.simpleName}: ${summary.jankCount} jank")
     * }
     * ```
     *
     * Each activity gets its own session, started as by [start] with that activity, and tagged
     * with [ACTIVITY_TAG] = the activity's class name. The tag shows on every [JankFrame], so a
     * shared [jankFrameListener] can tell the activities apart; it also appears once in each
     * summary's [SessionSummary.tags]. Use [ActivityMonitor.sessionFor] to set more tags.
     *
     * An activity that is already started when this is called is measured from its next
     * `onStart`. A configuration change such as a rotation stops the old activity and starts a
     * new one, so it ends one session and starts another.
     *
     * @param application The app's [Application]; its activity lifecycle callbacks are used.
     * @param config Window, jank threshold multiplier and report interval for every session.
     * @param jankFrameListener Optional. Receives every jank frame of every activity on the main
     *   thread, as soon as it is measured.
     * @param listener Receives each activity's summary when it stops.
     * @return A handle to read an activity's session or to stop monitoring.
     */
    @JvmStatic
    @JvmOverloads
    public fun monitorActivities(
        application: Application,
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        jankFrameListener: JankFrameListener? = null,
        listener: ActivitySessionListener,
    ): ActivityMonitor {
        val monitor = ActivityMonitorImpl(
            application = application,
            startSession = { activity ->
                startActivitySession(
                    activity = activity,
                    config = config,
                    jankFrameListener = jankFrameListener,
                    listener = NO_METRICS_LISTENER,
                    onStopped = null,
                ).also { it.setTag(ACTIVITY_TAG, nameOf(activity)) }
            },
            listener = listener,
        )
        monitor.start()
        return monitor
    }

    /**
     * Tag name that [monitorActivities] sets on each session. The value is the activity's simple
     * class name (after R8 renaming in a minified build).
     */
    public const val ACTIVITY_TAG: String = "activity"

    /**
     * Starts a new monitoring session with a fixed frame budget, timing `Choreographer`
     * callbacks on the main thread.
     *
     * The budget is [FramePulseConfig.expectedFramePeriodMs] for the whole session. If the
     * display can change its refresh rate while you measure (variable refresh rate, 60/120 Hz
     * switching), use the overload that takes a [Display] instead.
     *
     * @param config Window, frame budget, jank threshold and report interval.
     * @param jankFrameListener Optional. Receives every jank frame on the main thread, as soon
     *   as it is measured.
     * @param listener Receives immutable metrics on the main thread, at most once per
     *   [FramePulseConfig.reportIntervalMs].
     * @return A handle used to stop monitoring.
     * @throws IllegalStateException if called off the main thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun start(
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        jankFrameListener: JankFrameListener? = null,
        listener: FramePulseListener,
    ): FramePulseSession = startSession(
        config = config,
        listener = listener,
        jankFrameListener = jankFrameListener,
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
     * @param jankFrameListener Optional. Receives every jank frame on the main thread, as soon
     *   as it is measured.
     * @param listener Receives immutable metrics on the main thread, at most once per
     *   [FramePulseConfig.reportIntervalMs].
     * @return A handle used to stop monitoring.
     * @throws IllegalStateException if called off the main thread.
     */
    @JvmStatic
    @JvmOverloads
    public fun start(
        context: Context,
        display: Display,
        config: FramePulseConfig = FramePulseConfig.DEFAULT,
        jankFrameListener: JankFrameListener? = null,
        listener: FramePulseListener,
    ): FramePulseSession = startSession(
        config = config,
        listener = listener,
        jankFrameListener = jankFrameListener,
        frameSource = ChoreographerFrameSource(),
        refreshRateSource = DisplayRefreshRateSource(displayManagerOf(context), display),
    )

    private fun startActivitySession(
        activity: Activity,
        config: FramePulseConfig,
        jankFrameListener: JankFrameListener?,
        listener: FramePulseListener,
        onStopped: (() -> Unit)?,
    ): FramePulseSession {
        val frameSource: FrameSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FrameMetricsFrameSource(activity.window)
        } else {
            ChoreographerFrameSource()
        }
        return startSession(
            config = config,
            listener = listener,
            jankFrameListener = jankFrameListener,
            frameSource = frameSource,
            refreshRateSource = displayOf(activity)?.let { display ->
                DisplayRefreshRateSource(displayManagerOf(activity), display)
            },
            onStopped = onStopped,
        )
    }

    private fun startSession(
        config: FramePulseConfig,
        listener: FramePulseListener,
        jankFrameListener: JankFrameListener?,
        frameSource: FrameSource,
        refreshRateSource: RefreshRateSource?,
        onStopped: (() -> Unit)? = null,
    ): FramePulseSession {
        val session = FramePulseSessionImpl(
            config = config,
            listener = listener,
            calculator = RollingWindowFpsCalculator(),
            monitorFactory = { consumer ->
                FrameMonitorImpl(frameSource = frameSource, frameConsumer = consumer)
            },
            refreshRateSource = refreshRateSource,
            jankFrameListener = jankFrameListener,
            onStopped = onStopped,
        )
        session.start()
        return session
    }

    private val NO_METRICS_LISTENER = FramePulseListener { }

    private fun nameOf(activity: Activity): String =
        activity.javaClass.simpleName.ifEmpty { activity.javaClass.name }

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

package com.mayank.framepulse.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.mayank.framepulse.ActivityMonitor
import com.mayank.framepulse.ActivitySessionListener
import com.mayank.framepulse.FramePulseSession
import java.util.concurrent.atomic.AtomicBoolean

/** Empty [Application.ActivityLifecycleCallbacks], so each user overrides only what it needs. */
internal abstract class ActivityCallbacksAdapter : Application.ActivityLifecycleCallbacks {
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * Calls a function when one activity is destroyed. Used to stop a session started with that
 * activity, so a missing `stop()` cannot keep its window alive.
 */
internal class ActivityDestroyWatcher(private val activity: Activity) : ActivityCallbacksAdapter() {

    private var onDestroyed: (() -> Unit)? = null

    fun watch(onDestroyed: () -> Unit) {
        this.onDestroyed = onDestroyed
        activity.application?.registerActivityLifecycleCallbacks(this)
    }

    fun unwatch() {
        onDestroyed = null
        activity.application?.unregisterActivityLifecycleCallbacks(this)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === this.activity) onDestroyed?.invoke()
    }
}

/** [ActivityMonitor] driven by the application's activity lifecycle callbacks. */
internal class ActivityMonitorImpl(
    private val application: Application,
    startSession: (Activity) -> FramePulseSession,
    listener: ActivitySessionListener,
) : ActivityMonitor {

    private val tracker = ActivitySessionTracker(startSession, listener::onActivitySessionEnded)
    private val stopRequested = AtomicBoolean(false)

    private val callbacks = object : ActivityCallbacksAdapter() {
        override fun onActivityStarted(activity: Activity) {
            if (!stopRequested.get()) tracker.onStarted(activity)
        }

        override fun onActivityStopped(activity: Activity) = tracker.onStopped(activity)

        override fun onActivityDestroyed(activity: Activity) = tracker.onStopped(activity)
    }

    fun start() {
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    override fun sessionFor(activity: Activity): FramePulseSession? {
        check(isMainLooperThread()) {
            "ActivityMonitor.sessionFor() must be called from the main thread"
        }
        return tracker.sessionFor(activity)
    }

    override fun stop() {
        if (!stopRequested.compareAndSet(false, true)) return
        if (isMainLooperThread()) close() else postToMainLooper(Runnable { close() })
    }

    private fun close() {
        application.unregisterActivityLifecycleCallbacks(callbacks)
        tracker.close()
    }
}

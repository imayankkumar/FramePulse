package com.mayank.framepulse

import android.app.Activity

/** Handle returned by [FramePulse.monitorActivities]. */
public interface ActivityMonitor {

    /**
     * Returns the running session of [activity], or null when it is not started or not
     * measured. Use it to set tags, for example `monitor.sessionFor(this)?.setTag("tab", "Home")`.
     *
     * Main thread only: throws [IllegalStateException] on another thread.
     */
    public fun sessionFor(activity: Activity): FramePulseSession?

    /**
     * Stops monitoring activities. Every session still running ends and is reported to the
     * [ActivitySessionListener]. Idempotent, and safe to call from any thread; from another
     * thread the work is posted to the main thread.
     */
    public fun stop()
}

package com.mayank.framepulse

/**
 * Handle for a running FramePulse monitoring session, returned by [FramePulse.start].
 *
 * Stop it with [stop] when monitoring is no longer needed. A session started with an `Activity`
 * also stops itself when that activity is destroyed. A stopped session cannot be restarted; call
 * [FramePulse.start] again. Sessions created by [FramePulse.monitorActivities] are started and
 * stopped for you.
 */
public interface FramePulseSession {

    /**
     * Stops monitoring. Idempotent, and safe to call from any thread.
     *
     * No listener call starts after this returns. On the main thread the frame and display
     * listeners are unregistered right away; from another thread that part is posted to the main
     * thread. [summary] covers the session up to the moment of the first call.
     */
    public fun stop()

    /** Whether the session is running: started and not yet stopped. Readable from any thread. */
    public val isRunning: Boolean

    /**
     * Sets a tag that describes what the app is doing, for example `setTag("screen", "Feed")`
     * or `setTag("scrolling", "true")`. Every [JankFrame] lists the
     * tags that were set while it ran.
     *
     * A tag applies to a frame if it was set at any time during the frame. Frames are reported
     * a little after they happen, so a tag removed right after a slow frame still shows on that
     * frame. Setting the value a tag already has changes nothing; setting a new value replaces
     * it from now on.
     *
     * Main thread only: throws [IllegalStateException] on another thread. After [stop] this has
     * no effect.
     */
    public fun setTag(name: String, value: String)

    /**
     * Removes a tag set with [setTag]. Removing a tag that is not set does nothing.
     *
     * Main thread only: throws [IllegalStateException] on another thread.
     */
    public fun removeTag(name: String)

    /**
     * Returns totals for the session so far: frames, jank, frozen frames, frame-time
     * percentiles and a breakdown per tag value. See [SessionSummary].
     *
     * Works at any time. After [stop] it returns the final totals; call it right before or after
     * [stop] to get the report for the whole session.
     *
     * Main thread only: throws [IllegalStateException] on another thread. Allocates a new
     * summary on each call, so do not call it on every frame.
     */
    public fun summary(): SessionSummary
}

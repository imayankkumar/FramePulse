package com.mayank.framepulse


/**
 * Handle for an active FramePulse monitoring session.
 *
 * Responsibilities:
 * - Represent session lifecycle (running vs stopped).
 * - Provide explicit [stop] so frame callbacks are unregistered and resources are released.
 *
 * Must not:
 * - Expose internal monitor or metrics types to callers.
 * - Restart itself; start a new session via [FramePulse.start] instead.
 *
 * Future role:
 * - Owned by host apps; stopped from lifecycle boundaries (e.g. when UI is no longer visible).
 */
public interface FramePulseSession {

    /**
     * Stops monitoring and prevents further listener invocations. Idempotent.
     *
     * Main thread only. On a running session, a call from another thread throws
     * [IllegalStateException] and changes nothing: the session keeps running, so it can still be
     * stopped from the main thread. On a session that is not running, this is a no-op on any thread.
     */
    public fun stop()

    /** Whether the session is actively receiving frame signals. */
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

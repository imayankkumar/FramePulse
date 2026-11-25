package com.mayank.framepulse


/**
 * Callback invoked once for every frame that was jank.
 *
 * Threading: invoked synchronously on the main thread, before the [FramePulseListener] call (if
 * any) for the same frame. Time spent here is spent on the main thread, so keep it short; a slow
 * listener makes later frames jank. Calling [FramePulseSession.stop] from inside [onJankFrame] is
 * allowed and prevents any further calls to both listeners.
 */
public fun interface JankFrameListener {

    /**
     * Called for a frame that went over its budget.
     *
     * @param frame Immutable; safe to keep or pass to other threads.
     */
    public fun onJankFrame(frame: JankFrame)
}

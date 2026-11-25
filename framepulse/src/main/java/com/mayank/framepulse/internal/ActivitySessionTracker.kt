package com.mayank.framepulse.internal

import com.mayank.framepulse.FramePulseSession
import com.mayank.framepulse.SessionSummary

/**
 * One session per started activity: starts it when the activity starts and ends it when the
 * activity stops (or is destroyed without stopping). Ending a session stops it and passes its
 * final summary to [onSessionEnded].
 *
 * Generic over the activity type so the rules can be unit tested on the JVM; the app uses
 * `android.app.Activity`. Main-thread-confined, like the lifecycle callbacks that drive it.
 */
internal class ActivitySessionTracker<A : Any>(
    private val startSession: (A) -> FramePulseSession,
    private val onSessionEnded: (A, SessionSummary) -> Unit,
) {
    private val sessions = LinkedHashMap<A, FramePulseSession>()
    private var closed = false

    fun onStarted(activity: A) {
        if (closed || sessions.containsKey(activity)) return
        sessions[activity] = startSession(activity)
    }

    fun onStopped(activity: A) {
        val session = sessions.remove(activity) ?: return
        end(activity, session)
    }

    fun sessionFor(activity: A): FramePulseSession? = sessions[activity]

    /** Ends every running session and ignores later lifecycle events. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        val running = sessions.entries.toList()
        sessions.clear()
        running.forEach { (activity, session) -> end(activity, session) }
    }

    private fun end(activity: A, session: FramePulseSession) {
        session.stop()
        onSessionEnded(activity, session.summary())
    }
}

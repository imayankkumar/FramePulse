package com.mayank.framepulse

import android.app.Activity

/**
 * Receives the summary of each activity measured by [FramePulse.monitorActivities].
 *
 * Threading: called on the main thread while the activity stops (inside its `onStop`), or when
 * [ActivityMonitor.stop] ends the sessions still running. Keep it short. Do not keep a reference
 * to [activity] after the call; it may be about to be destroyed.
 */
public fun interface ActivitySessionListener {

    /**
     * Called once when an activity's session ends.
     *
     * @param activity The activity that was measured.
     * @param summary Totals from the activity's `onStart` to its `onStop`.
     */
    public fun onActivitySessionEnded(activity: Activity, summary: SessionSummary)
}

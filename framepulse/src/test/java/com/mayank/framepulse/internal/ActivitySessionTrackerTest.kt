package com.mayank.framepulse.internal

import com.mayank.framepulse.FramePulseSession
import com.mayank.framepulse.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** [ActivitySessionTracker]: one session per started activity, reported when it stops. */
class ActivitySessionTrackerTest {

    private val started = mutableListOf<FakeSession>()
    private val ended = mutableListOf<Pair<String, SessionSummary>>()

    private val tracker = ActivitySessionTracker<String>(
        startSession = { activity -> FakeSession(activity).also { started += it } },
        onSessionEnded = { activity, summary -> ended += activity to summary },
    )

    @Test
    fun anActivityIsMeasuredFromStartToStop() {
        tracker.onStarted("Feed")
        val session = started.single()
        assertSame(session, tracker.sessionFor("Feed"))

        tracker.onStopped("Feed")

        assertFalse(session.isRunning)
        assertEquals(listOf("Feed" to session.summaryAtStop), ended)
        assertNull(tracker.sessionFor("Feed"))
    }

    @Test
    fun theSummaryIsTakenAfterTheSessionStopped() {
        tracker.onStarted("Feed")

        tracker.onStopped("Feed")

        assertTrue(started.single().summaryTakenAfterStop)
    }

    @Test
    fun eachStartGetsANewSession() {
        tracker.onStarted("Feed")
        tracker.onStopped("Feed")
        tracker.onStarted("Feed")
        tracker.onStopped("Feed")

        assertEquals(2, started.size)
        assertEquals(2, ended.size)
    }

    @Test
    fun aSecondStartWithoutStopKeepsTheSession() {
        tracker.onStarted("Feed")
        tracker.onStarted("Feed")

        assertEquals(1, started.size)
    }

    @Test
    fun aStopWithoutStartIsIgnored() {
        tracker.onStopped("Feed")
        tracker.onStarted("Feed")
        tracker.onStopped("Feed")
        tracker.onStopped("Feed")

        assertEquals(1, ended.size)
    }

    @Test
    fun activitiesAreMeasuredSeparately() {
        tracker.onStarted("Feed")
        tracker.onStarted("Detail")
        tracker.onStopped("Feed")

        assertEquals(listOf("Feed"), ended.map { it.first })
        assertTrue(tracker.sessionFor("Detail")!!.isRunning)
    }

    @Test
    fun closeEndsEveryRunningSessionOnceAndIgnoresLaterEvents() {
        tracker.onStarted("Feed")
        tracker.onStarted("Detail")

        tracker.close()
        tracker.close()
        tracker.onStopped("Feed")
        tracker.onStarted("Settings")

        assertEquals(listOf("Feed", "Detail"), ended.map { it.first })
        assertEquals(2, started.size)
        assertTrue(started.none { it.isRunning })
    }

    private class FakeSession(name: String) : FramePulseSession {
        override var isRunning: Boolean = true
            private set
        var summaryTakenAfterStop = false
            private set
        val summaryAtStop = SessionSummary(
            durationMs = name.length.toDouble(),
            frameCount = 0,
            jankCount = 0,
            frozenFrameCount = 0,
            p50FrameTimeMs = 0.0,
            p90FrameTimeMs = 0.0,
            p95FrameTimeMs = 0.0,
            p99FrameTimeMs = 0.0,
            maxFrameTimeMs = 0.0,
            tags = emptyList(),
        )

        override fun stop() {
            isRunning = false
        }

        override fun setTag(name: String, value: String) = Unit

        override fun removeTag(name: String) = Unit

        override fun summary(): SessionSummary {
            summaryTakenAfterStop = !isRunning
            return summaryAtStop
        }
    }
}

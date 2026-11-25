package com.mayank.framepulse.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TagTracker]: which tags belong to a frame that ran from a start to an end time. */
class TagTrackerTest {

    private val tracker = TagTracker()

    @Test
    fun aTagSetBeforeTheFrameAndStillSetApplies() {
        tracker.set("screen", "Feed", nowNanos = 10)

        assertEquals(mapOf("screen" to "Feed"), tracker.tagsDuring(100, 120))
    }

    @Test
    fun aTagRemovedDuringOrAfterTheFrameStillApplies() {
        tracker.set("scrolling", "true", nowNanos = 10)
        tracker.remove("scrolling", nowNanos = 110)

        // Removed during the frame.
        assertEquals(mapOf("scrolling" to "true"), tracker.tagsDuring(100, 120))
        // Removed exactly when the frame started.
        assertEquals(mapOf("scrolling" to "true"), tracker.tagsDuring(110, 130))
    }

    @Test
    fun aTagRemovedBeforeTheFrameStartedDoesNotApply() {
        tracker.set("scrolling", "true", nowNanos = 10)
        tracker.remove("scrolling", nowNanos = 99)

        assertTrue(tracker.tagsDuring(100, 120).isEmpty())
    }

    @Test
    fun aTagSetDuringTheFrameAppliesButNotOneSetAfterIt() {
        tracker.set("during", "yes", nowNanos = 110)
        tracker.set("atEnd", "yes", nowNanos = 120)
        tracker.set("after", "yes", nowNanos = 121)

        assertEquals(mapOf("during" to "yes", "atEnd" to "yes"), tracker.tagsDuring(100, 120))
    }

    @Test
    fun aValueChangedDuringTheFrameReportsTheNewestValue() {
        tracker.set("screen", "Feed", nowNanos = 10)
        tracker.set("screen", "Detail", nowNanos = 110)

        assertEquals(mapOf("screen" to "Detail"), tracker.tagsDuring(100, 120))
        // A frame that ended before the change still sees the old value.
        assertEquals(mapOf("screen" to "Feed"), tracker.tagsDuring(50, 60))
    }

    @Test
    fun settingTheSameValueAgainKeepsTheOriginalTime() {
        tracker.set("screen", "Feed", nowNanos = 10)
        tracker.set("screen", "Feed", nowNanos = 200)

        assertEquals(mapOf("screen" to "Feed"), tracker.tagsDuring(100, 120))
    }

    @Test
    fun removingATagThatIsNotSetDoesNothing() {
        tracker.remove("missing", nowNanos = 10)
        tracker.set("screen", "Feed", nowNanos = 20)
        tracker.remove("missing", nowNanos = 30)

        assertEquals(mapOf("screen" to "Feed"), tracker.tagsDuring(100, 120))
    }

    @Test
    fun forgottenTagsNoLongerApplyToEarlierFrames() {
        tracker.set("scrolling", "true", nowNanos = 10)
        tracker.remove("scrolling", nowNanos = 110)
        tracker.set("screen", "Feed", nowNanos = 20)

        tracker.forgetRemovedBefore(111)

        // Only frames that start at 111 or later are expected now; tags still set are kept.
        assertEquals(mapOf("screen" to "Feed"), tracker.tagsDuring(100, 120))
    }

    @Test
    fun forgetKeepsTagsRemovedAtOrAfterTheGivenTime() {
        tracker.set("scrolling", "true", nowNanos = 10)
        tracker.remove("scrolling", nowNanos = 110)

        tracker.forgetRemovedBefore(110)

        assertEquals(mapOf("scrolling" to "true"), tracker.tagsDuring(110, 120))
    }

    @Test
    fun onlyTheNewestRemovedTagsAreKeptWhenNoFramesArrive() {
        val small = TagTracker(maxRemovedEntries = 2)
        small.set("screen", "Feed", nowNanos = 0)
        for (i in 1..5L) {
            small.set("step", "$i", nowNanos = i * 10)
            small.remove("step", nowNanos = i * 10 + 5)
        }

        // Steps 1 to 3 were dropped; 4 and 5 are kept, and the tag still set is never dropped.
        assertEquals(mapOf("screen" to "Feed"), small.tagsDuring(10, 35))
        assertEquals(mapOf("screen" to "Feed", "step" to "4"), small.tagsDuring(40, 45))
        assertEquals(mapOf("screen" to "Feed", "step" to "5"), small.tagsDuring(50, 55))
    }

    @Test
    fun noTagsGivesAnEmptyMap() {
        assertTrue(tracker.tagsDuring(0, 100).isEmpty())
    }
}

package com.mayank.framepulse.internal

import com.mayank.framepulse.TagSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TagTracker.countFrame] and [TagTracker.summaries]: per-tag session counters. */
class TagTrackerCountersTest {

    private val tracker = TagTracker()

    @Test
    fun framesAreCountedForTheTagsSetWhileTheyRan() {
        tracker.set("screen", "Feed", nowNanos = 0)
        tracker.countFrame(10, 20, isJank = false, isFrozen = false)
        tracker.set("scrolling", "true", nowNanos = 30)
        tracker.countFrame(40, 50, isJank = true, isFrozen = false)
        tracker.remove("scrolling", nowNanos = 55)
        tracker.countFrame(60, 70, isJank = true, isFrozen = true)

        assertEquals(
            listOf(
                TagSummary("screen", "Feed", frameCount = 3, jankCount = 2, frozenFrameCount = 1),
                TagSummary("scrolling", "true", frameCount = 1, jankCount = 1, frozenFrameCount = 0),
            ),
            tracker.summaries(),
        )
    }

    @Test
    fun aTagRemovedAndSetAgainDuringOneFrameCountsItOnce() {
        tracker.set("scrolling", "true", nowNanos = 0)
        tracker.remove("scrolling", nowNanos = 12)
        tracker.set("scrolling", "true", nowNanos = 14)

        tracker.countFrame(10, 20, isJank = true, isFrozen = false)

        assertEquals(1L, tracker.summaries().single().frameCount)
    }

    @Test
    fun aValueChangedDuringAFrameCountsTheFrameForBothValues() {
        tracker.set("screen", "Feed", nowNanos = 0)
        tracker.set("screen", "Detail", nowNanos = 15)

        tracker.countFrame(10, 20, isJank = true, isFrozen = false)
        tracker.countFrame(30, 40, isJank = false, isFrozen = false)

        val byValue = tracker.summaries().associateBy { it.value }
        assertEquals(1L, byValue.getValue("Feed").frameCount)
        assertEquals(2L, byValue.getValue("Detail").frameCount)
        assertEquals(1L, byValue.getValue("Detail").jankCount)
    }

    @Test
    fun countsSurviveAfterTheTagIsForgotten() {
        tracker.set("loading", "true", nowNanos = 0)
        tracker.countFrame(10, 20, isJank = true, isFrozen = false)
        tracker.remove("loading", nowNanos = 25)

        tracker.forgetRemovedBefore(100)
        tracker.countFrame(100, 110, isJank = true, isFrozen = false)

        assertEquals(1L, tracker.summaries().single().frameCount)
    }

    @Test
    fun aValueSetAgainLaterKeepsCountingIntoTheSameSummary() {
        tracker.set("screen", "Feed", nowNanos = 0)
        tracker.countFrame(10, 20, isJank = false, isFrozen = false)
        tracker.set("screen", "Detail", nowNanos = 30)
        tracker.set("screen", "Feed", nowNanos = 50)
        tracker.countFrame(60, 70, isJank = false, isFrozen = false)

        assertEquals(
            listOf("Feed" to 2L, "Detail" to 0L),
            tracker.summaries().map { it.value to it.frameCount },
        )
    }

    @Test
    fun valuesPastTheLimitAreNotCountedButStillTagFrames() {
        val small = TagTracker(maxCountedValues = 2)
        small.set("a", "1", nowNanos = 0)
        small.set("b", "1", nowNanos = 0)
        small.set("c", "1", nowNanos = 0)

        small.countFrame(10, 20, isJank = false, isFrozen = false)

        assertEquals(listOf("a", "b"), small.summaries().map { it.name })
        assertEquals(setOf("a", "b", "c"), small.tagsDuring(10, 20).keys)
    }

    @Test
    fun withoutTagsThereAreNoSummaries() {
        tracker.countFrame(10, 20, isJank = true, isFrozen = false)

        assertTrue(tracker.summaries().isEmpty())
    }
}

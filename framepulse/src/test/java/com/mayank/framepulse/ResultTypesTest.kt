package com.mayank.framepulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The result types compare by value and print their fields, like the data classes they replace. */
class ResultTypesTest {

    @Test
    fun metricsCompareByValue() {
        assertValueSemantics(metrics(), metrics(), metrics(totalJankCount = 4))
        assertEquals(
            "FramePulseMetrics(lastFrameTimeNanos=1, fps=60.0, frameCount=60, " +
                "averageFrameTimeMs=16.7, maxFrameTimeMs=40.0, p50FrameTimeMs=16.0, " +
                "p90FrameTimeMs=17.0, p95FrameTimeMs=18.0, p99FrameTimeMs=40.0, jankCount=1, " +
                "frozenFrameCount=0, totalFrameCount=600, totalJankCount=3, " +
                "totalFrozenFrameCount=0)",
            metrics().toString(),
        )
    }

    @Test
    fun jankFramesCompareByValueIncludingStagesAndTags() {
        assertValueSemantics(jankFrame(), jankFrame(), jankFrame(tags = emptyMap()))
        assertNotEquals(jankFrame(), jankFrame(stages = stages(gpuMs = null)))
        assertEquals(
            "JankFrame(frameTimeNanos=5, durationMs=40.0, budgetMs=16.7, isFrozen=false, " +
                "stages=FrameStages(unknownDelayMs=0.5, inputHandlingMs=1.0, animationMs=2.0, " +
                "layoutMeasureMs=3.0, drawMs=4.0, syncMs=5.0, commandIssueMs=6.0, " +
                "swapBuffersMs=7.0, gpuMs=8.0), tags={screen=Feed})",
            jankFrame().toString(),
        )
    }

    @Test
    fun summariesCompareByValue() {
        assertValueSemantics(summary(), summary(), summary(tags = emptyList()))
        assertValueSemantics(tag(), tag(), tag(jankCount = 2))
        assertEquals(
            "TagSummary(name=screen, value=Feed, frameCount=10, jankCount=1, " +
                "frozenFrameCount=0)",
            tag().toString(),
        )
    }

    private fun assertValueSemantics(value: Any, equalValue: Any, otherValue: Any) {
        assertEquals(value, equalValue)
        assertEquals(value.hashCode(), equalValue.hashCode())
        assertNotEquals(value, otherValue)
    }

    private fun metrics(totalJankCount: Long = 3) = FramePulseMetrics(
        lastFrameTimeNanos = 1,
        fps = 60.0,
        frameCount = 60,
        averageFrameTimeMs = 16.7,
        maxFrameTimeMs = 40.0,
        p50FrameTimeMs = 16.0,
        p90FrameTimeMs = 17.0,
        p95FrameTimeMs = 18.0,
        p99FrameTimeMs = 40.0,
        jankCount = 1,
        frozenFrameCount = 0,
        totalFrameCount = 600,
        totalJankCount = totalJankCount,
        totalFrozenFrameCount = 0,
    )

    private fun stages(gpuMs: Double? = 8.0) = FrameStages(
        unknownDelayMs = 0.5,
        inputHandlingMs = 1.0,
        animationMs = 2.0,
        layoutMeasureMs = 3.0,
        drawMs = 4.0,
        syncMs = 5.0,
        commandIssueMs = 6.0,
        swapBuffersMs = 7.0,
        gpuMs = gpuMs,
    )

    private fun jankFrame(
        stages: FrameStages? = stages(),
        tags: Map<String, String> = mapOf("screen" to "Feed"),
    ) = JankFrame(
        frameTimeNanos = 5,
        durationMs = 40.0,
        budgetMs = 16.7,
        isFrozen = false,
        stages = stages,
        tags = tags,
    )

    private fun tag(jankCount: Long = 1) = TagSummary(
        name = "screen",
        value = "Feed",
        frameCount = 10,
        jankCount = jankCount,
        frozenFrameCount = 0,
    )

    private fun summary(tags: List<TagSummary> = listOf(tag())) = SessionSummary(
        durationMs = 1_000.0,
        frameCount = 60,
        jankCount = 1,
        frozenFrameCount = 0,
        p50FrameTimeMs = 16.0,
        p90FrameTimeMs = 17.0,
        p95FrameTimeMs = 18.0,
        p99FrameTimeMs = 40.0,
        maxFrameTimeMs = 40.0,
        tags = tags,
    )
}

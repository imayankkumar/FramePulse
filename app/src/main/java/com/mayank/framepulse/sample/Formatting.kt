package com.mayank.framepulse.sample

import com.mayank.framepulse.FrameStages
import com.mayank.framepulse.JankFrame
import com.mayank.framepulse.SessionSummary
import java.util.Locale

/** For example "212 ms (budget 16.7 ms), mostly input, action=block main thread". */
fun jankFrameText(frame: JankFrame): String = buildString {
    append("%.0f ms (budget %.1f ms)".format(Locale.US, frame.durationMs, frame.budgetMs))
    if (frame.isFrozen) append(", frozen")
    frame.stages?.let { append(", mostly ").append(longestStage(it)) }
    frame.tags.forEach { (name, value) -> append(", ").append(name).append('=').append(value) }
}

/** A few lines with the session totals and one line per tag value. */
fun summaryText(summary: SessionSummary): String = buildString {
    append(
        "%d frames in %.1f s, %d jank (%.1f%%), %d frozen\n".format(
            Locale.US,
            summary.frameCount,
            summary.durationMs / 1_000,
            summary.jankCount,
            summary.jankRate * 100,
            summary.frozenFrameCount,
        ),
    )
    append(
        "p50 / p95 / p99 / max: %.1f / %.1f / %.1f / %.1f ms".format(
            Locale.US,
            summary.p50FrameTimeMs,
            summary.p95FrameTimeMs,
            summary.p99FrameTimeMs,
            summary.maxFrameTimeMs,
        ),
    )
    summary.tags.forEach { tag ->
        append(
            "\n%s=%s: %d frames, %d jank (%.1f%%)".format(
                Locale.US,
                tag.name,
                tag.value,
                tag.frameCount,
                tag.jankCount,
                tag.jankRate * 100,
            ),
        )
    }
}

private fun longestStage(stages: FrameStages): String = listOf(
    "unknown delay" to stages.unknownDelayMs,
    "input" to stages.inputHandlingMs,
    "animation" to stages.animationMs,
    "layout" to stages.layoutMeasureMs,
    "draw" to stages.drawMs,
    "sync" to stages.syncMs,
    "command issue" to stages.commandIssueMs,
    "swap buffers" to stages.swapBuffersMs,
    "GPU" to (stages.gpuMs ?: 0.0),
).maxBy { it.second }.first

/** Keeps the calling (main) thread busy for [ms] milliseconds, like slow app code would. */
fun busyWait(ms: Long) {
    if (ms <= 0) return
    val end = System.nanoTime() + ms * 1_000_000L
    @Suppress("ControlFlowWithEmptyBody")
    while (System.nanoTime() < end) {
    }
}

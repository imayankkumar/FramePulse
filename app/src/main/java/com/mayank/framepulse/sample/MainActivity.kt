package com.mayank.framepulse.sample

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mayank.framepulse.api.FramePulse
import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.api.FramePulseSession
import com.mayank.framepulse.model.FrameMetrics
import com.mayank.framepulse.model.FrameStages
import com.mayank.framepulse.model.JankFrame
import com.mayank.framepulse.model.SessionSummary
import com.mayank.framepulse.sample.ui.theme.FramePulseTheme
import java.util.Locale

/**
 * Demo: shows live FramePulse metrics and lets you block the main thread on purpose.
 *
 * The session is started in [onStart] and stopped in [onStop]; FramePulse does not manage
 * lifecycle by itself.
 */
class MainActivity : ComponentActivity() {

    private var session: FramePulseSession? = null
    private val config = FramePulseConfig.DEFAULT
    private var metrics by mutableStateOf<FrameMetrics?>(null)
    private var lastJankFrame by mutableStateOf<JankFrame?>(null)
    private var summary by mutableStateOf<SessionSummary?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FramePulseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MetricsScreen(
                        config = config,
                        metrics = metrics,
                        lastJankFrame = lastJankFrame,
                        summary = summary,
                        onBlockMainThread = ::blockMainThread,
                        onShowSummary = { summary = session?.summary() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        metrics = null
        lastJankFrame = null
        summary = null
        // Passing the activity measures the frames its window draws (API 24+) and keeps the
        // frame budget in step with the display's refresh rate.
        session = FramePulse.start(
            activity = this,
            config = config,
            jankFrameListener = { lastJankFrame = it },
        ) { metrics = it }
        session?.setTag("screen", "demo")
    }

    override fun onStop() {
        session?.stop()
        session = null
        super.onStop()
    }

    /**
     * Deliberately freezes the main thread so the next frame takes long (jank). The tag shows
     * on that jank frame, although it is removed before the frame is reported.
     */
    private fun blockMainThread(durationMs: Long) {
        session?.setTag("action", "block main thread")
        Thread.sleep(durationMs)
        session?.removeTag("action")
    }
}

@Composable
private fun MetricsScreen(
    config: FramePulseConfig,
    metrics: FrameMetrics?,
    lastJankFrame: JankFrame?,
    summary: SessionSummary?,
    onBlockMainThread: (Long) -> Unit,
    onShowSummary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("FramePulse demo", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (MEASURES_DRAWN_FRAMES) {
                "Measuring drawn frames"
            } else {
                "Measuring Choreographer callbacks"
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (metrics == null) {
            Text("Waiting for frames…")
        } else {
            Metric("FPS (last %d ms)".format(Locale.US, config.windowDurationMs),
                "%.1f".format(Locale.US, metrics.fps))
            Metric("Jank in window", metrics.jankCount.toString())
            Metric("Jank since start", metrics.totalJankCount.toString())
            Metric("Frozen frames since start", metrics.totalFrozenFrameCount.toString())
            Metric(
                "p50 / p95 / max frame time",
                "%.1f / %.1f / %.1f ms".format(
                    Locale.US,
                    metrics.p50FrameTimeMs,
                    metrics.p95FrameTimeMs,
                    metrics.maxFrameTimeMs,
                ),
            )
        }

        Column {
            Text("Last jank frame", style = MaterialTheme.typography.labelMedium)
            Text(
                lastJankFrame?.let(::describe) ?: "None yet",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Button(onClick = { onBlockMainThread(BLOCK_DURATION_MS) }) {
            Text("Block main thread for $BLOCK_DURATION_MS ms")
        }

        Button(onClick = onShowSummary) {
            Text("Show session summary")
        }
        if (summary != null) {
            Text(summaryText(summary), style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            if (MEASURES_DRAWN_FRAMES) {
                "FramePulse counts the frames this window draws. An idle screen draws almost " +
                    "nothing, so the FPS is low until something animates. These numbers only " +
                    "update when a frame is drawn."
            } else {
                "FramePulse counts main-thread Choreographer callbacks, which keep arriving at " +
                    "the refresh rate even when nothing redraws. An idle screen therefore shows " +
                    "FPS close to the refresh rate."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.displaySmall)
    }
}

/** For example "212 ms (budget 16.7 ms), mostly input, action=block main thread". */
private fun describe(frame: JankFrame): String = buildString {
    append("%.0f ms (budget %.1f ms)".format(Locale.US, frame.durationMs, frame.budgetMs))
    frame.stages?.let { append(", mostly ").append(longestStage(it)) }
    frame.tags.forEach { (name, value) -> append(", ").append(name).append('=').append(value) }
}

/** A few lines with the session totals and one line per tag value. */
private fun summaryText(summary: SessionSummary): String = buildString {
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
            "\n%s=%s: %d frames, %d jank".format(
                Locale.US,
                tag.name,
                tag.value,
                tag.frameCount,
                tag.jankCount,
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

private const val BLOCK_DURATION_MS = 200L

/** FramePulse.start(activity, ...) reads the platform's frame metrics from API 24 on. */
private val MEASURES_DRAWN_FRAMES = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

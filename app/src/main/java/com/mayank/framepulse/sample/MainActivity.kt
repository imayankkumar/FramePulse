package com.mayank.framepulse.sample

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mayank.framepulse.FramePulse
import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.FramePulseMetrics
import com.mayank.framepulse.FramePulseSession
import com.mayank.framepulse.JankFrame
import com.mayank.framepulse.SessionSummary
import com.mayank.framepulse.sample.ui.theme.FramePulseTheme
import java.util.Locale

/**
 * Test bench dashboard: live metrics for this screen, buttons that cause jank on purpose, and
 * the summaries [SampleApplication] collects for every activity.
 *
 * This screen starts its own session in [onStart] and stops it in [onStop], because it shows live
 * metrics. The app-wide monitor measures it as well, independently.
 */
class MainActivity : ComponentActivity() {

    private var session: FramePulseSession? = null
    private val config = FramePulseConfig.DEFAULT
    private var metrics by mutableStateOf<FramePulseMetrics?>(null)
    private var lastJankFrame by mutableStateOf<JankFrame?>(null)
    private var summary by mutableStateOf<SessionSummary?>(null)
    private var refreshRateHz by mutableStateOf(0f)
    private var animating by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FramePulseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Dashboard(
                        config = config,
                        refreshRateHz = refreshRateHz,
                        metrics = metrics,
                        lastJankFrame = lastJankFrame,
                        summary = summary,
                        animating = animating,
                        onAnimatingChange = { animating = it },
                        onBlockMainThread = ::blockMainThread,
                        onShowSummary = { summary = session?.summary() },
                        onOpenJankyList = {
                            startActivity(Intent(this, JankyListActivity::class.java))
                        },
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
        refreshRateHz = currentDisplay()?.refreshRate ?: 0f
        // Passing the activity measures the frames its window draws (API 24+) and keeps the
        // frame budget in step with the display's refresh rate.
        session = FramePulse.start(
            activity = this,
            config = config,
            jankFrameListener = { lastJankFrame = it },
        ) {
            metrics = it
            refreshRateHz = currentDisplay()?.refreshRate ?: 0f
        }
        session?.setTag("screen", "dashboard")
    }

    override fun onStop() {
        session?.stop()
        session = null
        super.onStop()
    }

    /**
     * Deliberately freezes the main thread so the next frame takes long. The tag shows on that
     * jank frame, although it is removed before the frame is reported.
     */
    private fun blockMainThread(durationMs: Long) {
        session?.setTag("action", "block ${durationMs}ms")
        Thread.sleep(durationMs)
        session?.removeTag("action")
    }

    private fun currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(
    config: FramePulseConfig,
    refreshRateHz: Float,
    metrics: FramePulseMetrics?,
    lastJankFrame: JankFrame?,
    summary: SessionSummary?,
    animating: Boolean,
    onAnimatingChange: (Boolean) -> Unit,
    onBlockMainThread: (Long) -> Unit,
    onShowSummary: () -> Unit,
    onOpenJankyList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("FramePulse test bench", style = MaterialTheme.typography.headlineSmall)
        Text(
            buildString {
                // The demo needs API 24, where FramePulse.start(activity, ...) measures drawn frames.
                append("Drawn frames")
                if (refreshRateHz > 0f) append(", display at %.0f Hz".format(Locale.US, refreshRateHz))
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (metrics == null) {
            Text("Waiting for frames…")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(
                    "FPS (last %d ms)".format(Locale.US, config.windowDurationMs),
                    "%.1f".format(Locale.US, metrics.fps),
                )
                Metric("Jank", "${metrics.jankCount} / ${metrics.totalJankCount}")
                Metric("Frozen", metrics.totalFrozenFrameCount.toString())
            }
            Text(
                "p50 / p95 / max frame time: %.1f / %.1f / %.1f ms".format(
                    Locale.US,
                    metrics.p50FrameTimeMs,
                    metrics.p95FrameTimeMs,
                    metrics.maxFrameTimeMs,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Column {
            Text("Last jank frame", style = MaterialTheme.typography.labelMedium)
            Text(
                lastJankFrame?.let(::jankFrameText) ?: "None yet",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider()
        Text("Cause work", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onBlockMainThread(JANK_BLOCK_MS) }) {
                Text("Block $JANK_BLOCK_MS ms")
            }
            FilledTonalButton(onClick = { onBlockMainThread(FROZEN_BLOCK_MS) }) {
                Text("Block $FROZEN_BLOCK_MS ms (frozen)")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = animating, role = Role.Switch, onValueChange = onAnimatingChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Switch(checked = animating, onCheckedChange = null)
            Text("Animate (draws every frame)", modifier = Modifier.weight(1f))
            if (animating) Spinner()
        }
        Button(onClick = onOpenJankyList) { Text("Open the janky list") }

        HorizontalDivider()
        Text("This screen's session", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onShowSummary) { Text("Show session summary") }
        if (summary != null) {
            Text(summaryText(summary), style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider()
        Text("Activity summaries", style = MaterialTheme.typography.titleMedium)
        Text(
            "Reported by FramePulse.monitorActivities when an activity stops.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (ActivityReports.reports.isEmpty()) {
            Text("None yet. Open the janky list and come back.", style = MaterialTheme.typography.bodyMedium)
        }
        ActivityReports.reports.forEach { (name, activitySummary) ->
            Column {
                Text(name, style = MaterialTheme.typography.labelMedium)
                Text(summaryText(activitySummary), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

/** A square that rotates forever, so the window draws a new frame every vsync. */
@Composable
private fun Spinner() {
    val rotation by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    Box(
        Modifier
            .size(24.dp)
            .graphicsLayer { rotationZ = rotation }
            .background(MaterialTheme.colorScheme.primary),
    )
}

private const val JANK_BLOCK_MS = 200L
private const val FROZEN_BLOCK_MS = 800L


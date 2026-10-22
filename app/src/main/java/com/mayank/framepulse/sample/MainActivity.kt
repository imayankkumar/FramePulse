package com.mayank.framepulse.sample

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
    private var config by mutableStateOf(FramePulseConfig.DEFAULT)
    private var metrics by mutableStateOf<FrameMetrics?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FramePulseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MetricsScreen(
                        config = config,
                        metrics = metrics,
                        onBlockMainThread = ::blockMainThread,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        config = FramePulseConfig.DEFAULT.withRefreshRate(requestHighestRefreshRate())
        metrics = null
        session = FramePulse.start(config) { metrics = it }
    }

    override fun onStop() {
        session?.stop()
        session = null
        super.onStop()
    }

    /**
     * FramePulse compares frame intervals against a fixed expected period. Displays with a
     * variable refresh rate (for example Pixel "Smooth Display") drop to a lower rate when the
     * screen is idle, and those slower frames would then all count as jank. The demo therefore
     * asks the window for the highest-refresh mode at the current resolution and uses that
     * rate as the frame budget. The system treats this as a preference, not a guarantee.
     */
    private fun requestHighestRefreshRate(): Float {
        val display = currentDisplay() ?: return DEFAULT_REFRESH_RATE_HZ
        val current = display.mode
        val best = display.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: current
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
        return best.refreshRate.takeIf { it > 0f } ?: DEFAULT_REFRESH_RATE_HZ
    }

    private fun currentDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

    /** Deliberately freezes the main thread so the next frame interval is long (jank). */
    private fun blockMainThread(durationMs: Long) {
        Thread.sleep(durationMs)
    }

    private companion object {
        const val DEFAULT_REFRESH_RATE_HZ = 60f
    }
}

@Composable
private fun MetricsScreen(
    config: FramePulseConfig,
    metrics: FrameMetrics?,
    onBlockMainThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("FramePulse demo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Refresh rate %.0f Hz · jank threshold %.1f ms".format(
                Locale.US,
                1_000f / config.expectedFramePeriodMs,
                config.jankThresholdMs,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (metrics == null) {
            Text("Waiting for frames…")
        } else {
            Metric("FPS (last %d ms)".format(Locale.US, config.windowDurationMs),
                "%.1f".format(Locale.US, metrics.fps))
            Metric("Jank in window", metrics.jankCount.toString())
            Metric("Jank since start", metrics.totalJankCount.toString())
            Metric("Max frame interval", "%.1f ms".format(Locale.US, metrics.maxFrameTimeMs))
        }

        Button(onClick = { onBlockMainThread(BLOCK_DURATION_MS) }) {
            Text("Block main thread for $BLOCK_DURATION_MS ms")
        }

        Text(
            "FramePulse counts main-thread Choreographer callbacks, which keep arriving at the " +
                "refresh rate even when nothing redraws. An idle screen therefore shows FPS " +
                "close to the refresh rate.",
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

private const val BLOCK_DURATION_MS = 200L

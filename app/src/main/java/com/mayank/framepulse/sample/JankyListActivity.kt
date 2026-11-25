package com.mayank.framepulse.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mayank.framepulse.FramePulseSession
import com.mayank.framepulse.sample.ui.theme.FramePulseTheme

/**
 * A long list whose rows do slow work when they appear, so scrolling it janks on purpose.
 *
 * This activity has no FramePulse code of its own: [SampleApplication] measures it with
 * `monitorActivities`. It only adds tags to that session, `work` (the chosen work per row) and
 * `scrolling` (while the list moves), so the summary logged when it stops splits jank by them.
 */
class JankyListActivity : ComponentActivity() {

    /** The session `monitorActivities` started for this activity; null before onStart. */
    private val session: FramePulseSession?
        get() = (application as SampleApplication).activityMonitor.sessionFor(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FramePulseTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    JankyList(
                        onWorkChange = { session?.setTag("work", "$it ms per row") },
                        onScrollingChange = { scrolling ->
                            if (scrolling) {
                                session?.setTag("scrolling", "true")
                            } else {
                                session?.removeTag("scrolling")
                            }
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun JankyList(
    onWorkChange: (Long) -> Unit,
    onScrollingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var workMs by rememberSaveable { mutableLongStateOf(WORK_CHOICES_MS[1]) }
    val listState = rememberLazyListState()

    LaunchedEffect(workMs) { onWorkChange(workMs) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect(onScrollingChange)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Janky list", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Each row busy-waits on the main thread when it appears. Scroll, then go back " +
                    "to see this screen's summary.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WORK_CHOICES_MS.forEach { choice ->
                    FilterChip(
                        selected = workMs == choice,
                        onClick = { workMs = choice },
                        label = { Text("$choice ms per row") },
                    )
                }
            }
        }
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(ROW_COUNT) { index ->
                busyWait(workMs)
                ListItem(
                    headlineContent = { Text("Row ${index + 1}") },
                    supportingContent = { Text("Took $workMs ms to compose") },
                )
            }
        }
    }
}

private const val ROW_COUNT = 1_000
private val WORK_CHOICES_MS = longArrayOf(0, 2, 8)

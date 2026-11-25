# FramePulse

A Kotlin Android library that measures frame times and jank: the frames a window draws on Android 7+, and main-thread frame pacing with `Choreographer` below that. It comes with rolling-window FPS, percentiles, frozen frames, per-frame jank details, tags, session summaries, tests, benchmarks and a demo app.

**You can add it to your app to see when and where it janks: on which screen, during which action, and in which stage of the frame.**

> **Status: alpha, educational.** The API may change between versions. It is not a replacement for Perfetto, Macrobenchmark or JankStats, and it has not been validated for production use.

Most Android performance libraries hand you an FPS number and hide how it was produced. FramePulse goes the other way: every piece (frame source, rolling-window math, jank rule, session lifecycle) is written from scratch, tested, and explained.

Learn about the design in detail from the documents below:

- [Overall library architecture](docs/ARCHITECTURE.md)
- [FrameMonitor lifecycle](docs/FRAME_MONITOR.md)
- [Looper, Handler and MessageQueue](docs/AndroidInternals-Looper.md)

## Modules of the project

- **framepulse**: the library. The root package `com.mayank.framepulse` is the public API; `internal` is the implementation.
- **app**: the demo app, a test bench that causes jank on purpose.
- **benchmark**: microbenchmarks of FramePulse's own work per frame.

```
App
 │  FramePulse.start(activity, config, listener)
 ▼
FramePulseSessionImpl ──────────────► FramePulseListener.onMetrics(FramePulseMetrics)
 │                                          ▲
 ▼                                          │
FrameMonitorImpl (IDLE → STARTED → STOPPED) │
 │  frames                                  │
 ├──────────────► RollingWindowFpsCalculator┘
 ▼
FrameSource, one of:
 ├─ FrameMetricsFrameSource ──► Window.addOnFrameMetricsAvailableListener (drawn frames, API 24+)
 └─ ChoreographerFrameSource ──► Choreographer.postFrameCallback (re-posted every frame)
```

## Main features

- **Drawn frames**: frame times from the platform's frame metrics (API 24+), with a `Choreographer` fallback.
- **Metrics**: rolling-window FPS, average, max and p50 / p90 / p95 / p99 frame times.
- **Frozen frames**: counts of frames over 700 ms.
- **Jank**: judged against the system's frame deadline (API 31+) or a frame budget that follows the display's refresh rate.
- **Jank frames**: an optional callback for each jank frame, with a stage breakdown (input, animation, layout, draw, sync, GPU) for drawn frames.
- **Tags**: say what the app was doing (for example the screen, or "scrolling") during each jank frame.
- **Session summary**: totals, jank rate, frozen frames, whole-session percentiles and a per-tag breakdown.
- **Every activity**: `FramePulse.monitorActivities` measures each activity from `onStart` to `onStop` with one call.
- **Lifecycle**: explicit start and stop, no restart; `stop()` works from any thread.
- **Stable API**: one public package, usable from Kotlin and Java; result types can gain fields without breaking compiled apps.
- **Tests**: JVM unit tests, and instrumentation tests on emulators (API 21, 24, 34) in CI, also passing on a Pixel 6 Pro (Android 14).
- **Low overhead**: about 65 ns and no allocations per frame on a Pixel 6 Pro.
- **CI**: GitHub Actions builds, tests and checks the public API against `framepulse/api/framepulse.api`.

## What FramePulse measures and what it doesn't

FramePulse measures in one of two ways, depending on how you start it.

| You call | Android version | What is measured |
|----------|-----------------|------------------|
| `FramePulse.start(activity, …)` | 7.0+ (API 24+) | Frames the activity's window drew |
| `FramePulse.start(activity, …)` | 5.0–6.0 (API 21–23) | `Choreographer` callbacks |
| `FramePulse.start(config, …)` or `FramePulse.start(context, display, …)` | any | `Choreographer` callbacks |

### Drawn frames (API 24+)

FramePulse registers a `Window.OnFrameMetricsAvailableListener` on the activity's window. The platform then reports every frame the window draws, with the time it took.

```
Window draws a frame ──► platform FrameMetrics (TOTAL_DURATION, DEADLINE)
                                                │
                              rolling window ──► FPS, jank, max frame time
```

**It measures**

- **Frame time**: the platform's `TOTAL_DURATION`, from the start of the frame until it is handed to the display system. This includes work off the main thread.
- **FPS**: the number of frames drawn in the last `windowDurationMs` divided by the window length. An idle window draws nothing, so its FPS falls towards 0.
- **Jank** on API 31+: a frame that took longer than the `DEADLINE` the system gave it. `jankThresholdMultiplier` is not used.
- **Jank** on API 24–30: a frame that took longer than `expectedFramePeriodMs × jankThresholdMultiplier`, where the frame budget follows the display's refresh rate.

**It does not**

- **Report while the window is idle.** No frame means no listener call, so the last snapshot stays as it was until the window draws again. Idle time is never counted as jank.
- Count the first frame of a new window layout. The platform expects that frame to be slow.
- Work on a software-rendered window. The window must be hardware accelerated, which is the default.
- See other windows, such as dialogs. Only the activity's own window is measured.
- Say which code caused a slow frame. The stage breakdown on each jank frame tells you which stage was slow (for example layout or draw), not which code. Use Perfetto or Android Studio's profiler for that.

This mode has been checked with unit tests only, not yet on a device.

### Choreographer callbacks

FramePulse posts a `Choreographer.FrameCallback` on the main thread and re-posts it every frame. Each callback delivers `frameTimeNanos`, the vsync time of the frame being processed.

```
VSYNC ──► Choreographer (main thread) ──► FrameCallback.doFrame(frameTimeNanos)
                                                │
                                   interval = frameTimeNanos − previous
                                                │
                              rolling window ──► FPS, jank, max interval
```

**It measures**

- How regularly the main thread serviced vsync. If the main thread is busy (layout, a slow `onClick`, I/O on main, GC pauses), the next callback arrives late and the interval grows.
- **FPS**: the number of frame intervals in the last `windowDurationMs` divided by their total duration.
- **Jank**: an interval strictly longer than `expectedFramePeriodMs × jankThresholdMultiplier` (default 1.5×, so 25 ms at 60 Hz and 12.5 ms at 120 Hz).

**It does not measure**

- **Rendered frames.** Because the callback re-posts itself, Choreographer keeps requesting vsync, so callbacks arrive at the refresh rate **even when nothing on screen changes**. An idle app reports FPS close to the refresh rate. FramePulse cannot tell an idle frame from a drawn one.
- RenderThread, GPU, or SurfaceFlinger composition time. A frame can be on time on the main thread and still be late on screen.
- Which code caused a slow frame. Use Perfetto or Android Studio's profiler for that.

### Known limitations

- **A pause counts as one long frame.** If no callback arrives for a while (for example the screen turns off while the session is running), the gap is recorded as a single janky frame. Stop the session in `onStop`, or use `FramePulse.monitorActivities`, which does that for you.
- **Refresh-rate changes need the `Activity` or `Display` overload.** `FramePulse.start(config, listener)` uses a fixed budget for the whole session. On variable-refresh-rate displays (for example Pixel "Smooth Display"), the system can drop from 120 Hz to 60 Hz when the screen is idle. With a fixed 120 Hz budget, every 60 Hz frame then counts as jank. On a Pixel 6 Pro this showed up as ~65 FPS and dozens of "janks" per second on an idle screen. `FramePulse.start(context, display, config, listener)` follows the rate reported by `Display.getRefreshRate()` through `DisplayManager.DisplayListener` instead. Changes the system does not report there (for example per-app frame-rate overrides) are not seen, and the interval that spans a switch is judged against the new budget. This has been checked with unit tests only, not yet on a device.
- **Observer effect.** The listener runs on the main thread, and whatever it does (for example updating UI) is measured too. In the debug build of the demo app on a Pixel 6 Pro, the demo's own metric updates (4 per second) were slow enough to register as about 4 janks per second while idle; `adb shell dumpsys gfxinfo` reported the same frames as janky ("Slow UI thread").
- **Power.** Keeping a frame callback posted keeps vsync running. Stop the session when you don't need it.

## Requirements

| Requirement          | Value  |
|----------------------|--------|
| Min SDK              | 21     |
| Language             | Kotlin |
| Bytecode target      | Java 11 |
| JDK to build         | 17–23 (AGP 8.13 / Gradle 8.13) |
| Compose dependency   | No     |
| Threading            | Main thread only |

## How to add FramePulse to your project?

FramePulse is not on Maven Central yet. Its first release will be:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.imayankkumar:framepulse:0.1.0")
}
```

Until then, include it as a module:

```kotlin
// settings.gradle.kts
include(":framepulse")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":framepulse"))
}
```

## How to start a session?

Start a session when your UI becomes visible and stop it when it isn't:

```kotlin
import com.mayank.framepulse.FramePulse
import com.mayank.framepulse.FramePulseConfig
import com.mayank.framepulse.FramePulseSession

class MainActivity : ComponentActivity() {

    private var session: FramePulseSession? = null

    override fun onStart() {
        super.onStart()
        // Measures the frames this activity's window draws (API 24+).
        session = FramePulse.start(this, FramePulseConfig.DEFAULT) { metrics ->
            Log.d("FramePulse", "fps=%.1f jank=%d max=%.1fms".format(
                metrics.fps, metrics.jankCount, metrics.maxFrameTimeMs))
        }
    }

    override fun onStop() {
        session?.stop()
        session = null
        super.onStop()
    }
}
```

`FramePulseMetrics` fields:

| Field | Meaning |
|-------|---------|
| `fps` | Frames per second over the window (see the two modes above) |
| `frameCount` | Frames in the window |
| `averageFrameTimeMs` / `maxFrameTimeMs` | Mean / longest frame time in the window |
| `p50FrameTimeMs` / `p90FrameTimeMs` / `p95FrameTimeMs` / `p99FrameTimeMs` | Frame time percentiles in the window (nearest rank) |
| `jankCount` | Frames in the window that were jank |
| `frozenFrameCount` | Frames in the window that took longer than 700 ms (`FramePulseMetrics.FROZEN_FRAME_THRESHOLD_MS`) |
| `totalFrameCount` / `totalJankCount` / `totalFrozenFrameCount` | Since the session started |
| `lastFrameTimeNanos` | Time of the newest frame, in nanoseconds |

### How to change the defaults?

Build a `FramePulseConfig`:

```kotlin
val config = FramePulseConfig.Builder()
    .windowDurationMs(2_000)
    .reportIntervalMs(500)
    .build()
```

| Option | Default | Meaning |
|--------|---------|---------|
| `windowDurationMs` | 1000 | Rolling window length |
| `expectedFramePeriodMs` | 16.67 | Frame budget; or set it with `refreshRate(hz)`. Replaced by the display's rate when you pass an `Activity` or a `Display` |
| `jankThresholdMultiplier` | 1.5 | Jank if frame time > budget × multiplier (must be ≥ 1.0). Not used for drawn frames on API 31+ |
| `reportIntervalMs` | 250 | Minimum time between listener calls; 0 = every frame |

`config.newBuilder()` starts a builder from an existing config.

### How to use it from Java?

`start` is static and has overloads without the optional arguments:

```java
session = FramePulse.start(this, metrics -> Log.d("FramePulse", "fps=" + metrics.getFps()));
```

## How to see each jank frame?

Pass a `jankFrameListener`. It works with every `start` overload and is called on the main thread, before the metrics listener for the same frame:

```kotlin
session = FramePulse.start(
    activity = this,
    jankFrameListener = { frame ->
        Log.w("FramePulse", "jank: %.1f ms (budget %.1f ms), stages=%s".format(
            frame.durationMs, frame.budgetMs, frame.stages))
    },
) { metrics -> /* rolling-window metrics as before */ }
```

| `JankFrame` field | Meaning |
|-------------------|---------|
| `durationMs` | How long the frame took |
| `budgetMs` | The limit it went over: the system's deadline (API 31+), otherwise the jank threshold |
| `isFrozen` | Whether it took longer than 700 ms |
| `frameTimeNanos` | When it happened, in the same time base as `FramePulseMetrics.lastFrameTimeNanos` |
| `stages` | Where the time went, for drawn frames (API 24+); `null` for `Choreographer` frames |
| `tags` | Tags that were set while the frame ran (see below); empty when none |

`FrameStages` holds the platform's stage durations in milliseconds: `unknownDelayMs` (the main thread was busy before the frame could start), `inputHandlingMs`, `animationMs`, `layoutMeasureMs`, `drawMs`, `syncMs`, `commandIssueMs`, `swapBuffersMs` and `gpuMs` (API 31+, otherwise `null`). The stages partly overlap, so they do not add up to `durationMs`. A `JankFrame` is only allocated for a jank frame; smooth frames allocate nothing.

## How to tag what the app was doing?

Tags say what the app was doing, so a jank frame tells you where it happened. Set them on the session from the main thread:

```kotlin
session?.setTag("screen", "Feed")

recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            session?.removeTag("scrolling")
        } else {
            session?.setTag("scrolling", "true")
        }
    }
})
```

A tag belongs to every frame that ran while it was set, even partly. Drawn frames are reported a little after they happen, so a tag removed right when scrolling stops still shows on the last frames of the scroll. Setting a new value for a name replaces the old one from then on.

## How to get a session summary?

`session.summary()` returns the totals for the whole session so far. It works while the session runs and after `stop()`, so you can log a report when a screen goes away:

```kotlin
override fun onStop() {
    session?.let { s ->
        s.stop()
        val summary = s.summary()
        Log.i("FramePulse", "%d frames, %.1f%% jank, %d frozen, p95 %.1f ms".format(
            summary.frameCount, summary.jankRate * 100, summary.frozenFrameCount,
            summary.p95FrameTimeMs))
        summary.tags.forEach { tag ->
            Log.i("FramePulse", "${tag.name}=${tag.value}: %.1f%% jank".format(tag.jankRate * 100))
        }
    }
    session = null
    super.onStop()
}
```

| `SessionSummary` field | Meaning |
|------------------------|---------|
| `durationMs` | Time from start until now, or until `stop()` |
| `frameCount` / `jankCount` / `frozenFrameCount` | Totals for the session |
| `jankRate` | `jankCount / frameCount`, from 0.0 to 1.0 |
| `p50FrameTimeMs` … `p99FrameTimeMs` / `maxFrameTimeMs` | Frame time percentiles and the exact maximum over the whole session |
| `tags` | One `TagSummary` per tag value: `name`, `value`, `frameCount`, `jankCount`, `frozenFrameCount`, `jankRate` |

Memory stays constant however long the session runs: the session percentiles come from a fixed histogram instead of a list of frames. They are rounded up to a multiple of 0.1 ms up to 50 ms and of 1 ms up to 1 s, and frames over 1 s report the maximum. The rolling-window percentiles in `FramePulseMetrics` stay exact. A frame counts for every tag value that was set while it ran, so tag counts can add up to more than `frameCount`. Up to 256 distinct tag values are counted per session.

## How to measure every activity?

Call `FramePulse.monitorActivities` once in your `Application`. Every activity is measured from its `onStart` to its `onStop`, and its session summary is passed to your listener when it stops:

```kotlin
class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FramePulse.monitorActivities(this) { activity, summary ->
            Log.d("FramePulse", "%s: %d frames, %.1f%% jank, p95 %.1f ms".format(
                activity.javaClass.simpleName,
                summary.frameCount, summary.jankRate * 100, summary.p95FrameTimeMs))
        }
    }
}
```

- Each session is tagged `activity` = the activity's class name (`FramePulse.ACTIVITY_TAG`), so jank frames from an optional shared `jankFrameListener` say which activity they came from.
- `monitor.sessionFor(activity)` returns an activity's running session, for example to set more tags. `monitor.stop()` stops monitoring and reports the sessions still running.
- A rotation stops the old activity and starts a new one, so it ends one session and starts another. An activity already started when you call `monitorActivities` is measured from its next `onStart`.

## Threading and lifecycle

- **Main thread, except `stop()`.** Call `FramePulse.start()`, `setTag()`, `removeTag()` and `summary()` on the main thread; they throw `IllegalStateException` otherwise. `session.stop()` works on any thread: no listener call starts after it returns, and from another thread the unregistering is posted to the main thread.
- **The listener runs on the main thread.** Keep it cheap. `FramePulseMetrics` is immutable, so you can hand it to another thread.
- **You own a session from `start()`.** Stop it when you no longer need it, normally in `onStop`. A session started with an `Activity` also stops itself when the activity is destroyed, so a missing `stop()` cannot keep the window alive. Calling `stop()` more than once is fine, and you can call it from inside the listener. To have FramePulse start and stop sessions for you, use `FramePulse.monitorActivities` (see [How to measure every activity?](#how-to-measure-every-activity)).
- **No restart.** A stopped session stays stopped; call `FramePulse.start()` again for a new one.

## Overhead

FramePulse's own work per frame, measured with [Jetpack Microbenchmark](https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview) on a Pixel 6 Pro (Android 14, release build, CPU clocks not locked), with frames at 120 Hz and the default config:

| Per frame | Median time | Allocations |
|-----------|-------------|-------------|
| Drawn frame through the session (window, summary, listener) | 65 ns | none¹ |
| Same, with 3 tags set | 83 ns | none¹ |
| `Choreographer` tick through the session | 66 ns | none¹ |
| Jank frame with a `jankFrameListener` | 308 ns | 6 (the `JankFrame`, its stages and tags) |

¹ Apart from one `FramePulseMetrics` snapshot per report interval (every 30 frames at 120 Hz with the default 250 ms), which the times include.

65 ns is under 0.001% of an 8.3 ms frame at 120 Hz. Not included: the platform's own cost of delivering frame metrics or `Choreographer` callbacks, and whatever your listeners do. See [How to run the benchmarks?](#how-to-run-the-benchmarks) to measure it yourself.

## How to run the demo app?

`:app` is a test bench: screens that cause jank on purpose, so you can see what FramePulse reports for it.

```bash
# Install the demo app on a connected device
./gradlew :app:installDebug

# Watch the summaries it logs
adb logcat -s FramePulse
```

- **Dashboard** (`MainActivity`) runs its own session and shows live FPS, jank, frozen frames, p50 / p95 / max and the last jank frame with its slowest stage and tags. **Block 200 ms** and **Block 800 ms (frozen)** stall the main thread, tagged `action`. **Animate** draws a new frame every vsync; without it the FPS is low, because the window only draws when something changes.
- **Janky list** (`JankyListActivity`) has 1,000 rows that each busy-wait 0, 2 or 8 ms on the main thread when they appear. It has no FramePulse code: `SampleApplication` measures every activity with `FramePulse.monitorActivities`, and the list only sets the tags `work` and `scrolling` on the session `sessionFor(activity)` returns.

When an activity stops, its summary goes to logcat and to the dashboard's **Activity summaries**.

The debug build is slower than a release build, and the dashboard's own updates show up as a few jank frames per second (see the observer effect under [Known limitations](#known-limitations)).

### Example output

Scrolling the janky list at 8 ms per row on a Pixel 6 Pro, then going back:

```
JankyListActivity stopped: 709 frames in 16.5 s, 40 jank (5.6%), 0 frozen
p50 / p95 / p99 / max: 6.5 / 20.2 / 45.8 / 169.6 ms
activity=JankyListActivity: 709 frames, 40 jank (5.6%)
work=2 ms per row: 3 frames, 1 jank (33.3%)
work=8 ms per row: 708 frames, 40 jank (5.6%)
scrolling=true: 613 frames, 38 jank (6.2%)
```

## How to run the tests?

```bash
# Build everything, run the unit tests and check the public API
./gradlew build test

# Library only
./gradlew :framepulse:assembleDebug :framepulse:testDebugUnitTest
```

Instrumentation tests (frame sources, activity monitoring and lifecycle stops) need a connected device or emulator, with the screen on and unlocked. CI runs them on emulators with API 21, 24 and 34.

```bash
# Run the instrumentation tests
./gradlew :framepulse:connectedDebugAndroidTest
```

Gradle 8.13 runs on JDK 17–23. If your IDE or `JAVA_HOME` points to a newer JDK (for example 25), point Gradle at a JDK 17 or 21 instead.

## How to run the benchmarks?

The benchmarks of the per-frame work need a real device (API 23+); emulator results are not meaningful. Results are printed and saved as JSON under `benchmark/build/outputs/`.

```bash
# Run the benchmarks on a connected device
./gradlew :benchmark:connectedReleaseAndroidTest
```

## How to check the public API?

The library's public API is recorded in [`framepulse/api/framepulse.api`](framepulse/api/framepulse.api). `./gradlew build` (and CI) compares the compiled library with it and fails on any difference, so a change that would break apps compiled against an earlier version cannot slip in unnoticed.

When you change the public API on purpose, update the file and commit it with the change:

```bash
# Record the current public API
./gradlew :framepulse:apiDump
```

Adding to the API is safe. Removing or changing anything already listed breaks binary compatibility and needs a new major version.

## How to publish a release?

`framepulse/build.gradle.kts` sets the coordinates (`io.github.imayankkumar:framepulse`), the version and the POM, using the [Gradle Maven Publish Plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/). A release contains the AAR, a sources jar and a javadoc jar generated from the KDoc.

To try the artifacts in another project first, publish them to your local Maven repository (`~/.m2`) and add `mavenLocal()` to that project's repositories:

```bash
# Publish to the local Maven repository
./gradlew :framepulse:publishToMavenLocal
```

Releasing to Maven Central needs, once:

- An account on the [Central Portal](https://central.sonatype.com) signed in with GitHub, which verifies the `io.github.imayankkumar` namespace.
- A user token from the portal, and a GPG key whose public key is on a key server.
- Both in `~/.gradle/gradle.properties` (never in this repository):

```properties
mavenCentralUsername=<token username>
mavenCentralPassword=<token password>
signing.keyId=<last 8 characters of the key ID>
signing.password=<key password>
signing.secretKeyRingFile=<path to the exported secret key ring>
```

Then set the version in `framepulse/build.gradle.kts`, and:

```bash
# Upload the signed release; it waits in the Central Portal until you publish it there
./gradlew :framepulse:publishToMavenCentral
```

Publications are signed only when a signing key is configured, and Maven Central rejects unsigned uploads.

## The complete project folder structure

```
.
├── app
│   ├── src
│   │   └── main
│   │       ├── java
│   │       │   └── com
│   │       │       └── mayank
│   │       │           └── framepulse
│   │       │               └── sample
│   │       │                   ├── ui
│   │       │                   │   └── theme
│   │       │                   │       ├── Color.kt
│   │       │                   │       ├── Theme.kt
│   │       │                   │       └── Type.kt
│   │       │                   ├── Formatting.kt
│   │       │                   ├── JankyListActivity.kt
│   │       │                   ├── MainActivity.kt
│   │       │                   └── SampleApplication.kt
│   │       └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── benchmark
│   ├── src
│   │   ├── androidTest
│   │   │   └── java
│   │   │       └── com
│   │   │           └── mayank
│   │   │               └── framepulse
│   │   │                   └── benchmark
│   │   │                       └── PerFrameBenchmark.kt
│   │   └── main
│   │       └── AndroidManifest.xml
│   └── build.gradle.kts
├── docs
│   ├── AndroidInternals-Looper.md
│   ├── ARCHITECTURE.md
│   └── FRAME_MONITOR.md
├── framepulse
│   ├── api
│   │   └── framepulse.api
│   ├── src
│   │   ├── androidTest
│   │   │   ├── java
│   │   │   │   └── com
│   │   │   │       └── mayank
│   │   │   │           └── framepulse
│   │   │   │               ├── internal
│   │   │   │               │   └── source
│   │   │   │               │       ├── ChoreographerFrameSourceTest.kt
│   │   │   │               │       ├── DisplayRefreshRateSourceTest.kt
│   │   │   │               │       ├── FrameMetricsFrameSourceTest.kt
│   │   │   │               │       └── FrameTestActivity.kt
│   │   │   │               └── ActivityLifecycleTest.kt
│   │   │   └── AndroidManifest.xml
│   │   ├── main
│   │   │   ├── java
│   │   │   │   └── com
│   │   │   │       └── mayank
│   │   │   │           └── framepulse
│   │   │   │               ├── internal
│   │   │   │               │   ├── metrics
│   │   │   │               │   │   ├── FPSCalculator.kt
│   │   │   │               │   │   ├── FrameTimes.kt
│   │   │   │               │   │   ├── RollingWindowFpsCalculator.kt
│   │   │   │               │   │   └── SessionFrameStats.kt
│   │   │   │               │   ├── source
│   │   │   │               │   │   ├── ChoreographerFrameSource.kt
│   │   │   │               │   │   ├── DisplayRefreshRateSource.kt
│   │   │   │               │   │   ├── FrameConsumer.kt
│   │   │   │               │   │   ├── FrameMetricsFrameSource.kt
│   │   │   │               │   │   ├── FrameSource.kt
│   │   │   │               │   │   ├── FrameStageNanos.kt
│   │   │   │               │   │   ├── MeasuredFrame.kt
│   │   │   │               │   │   └── RefreshRateSource.kt
│   │   │   │               │   ├── ActivityLifecycle.kt
│   │   │   │               │   ├── ActivitySessionTracker.kt
│   │   │   │               │   ├── FrameMonitor.kt
│   │   │   │               │   ├── FrameMonitorImpl.kt
│   │   │   │               │   ├── FramePulseSessionImpl.kt
│   │   │   │               │   └── TagTracker.kt
│   │   │   │               ├── ActivityMonitor.kt
│   │   │   │               ├── ActivitySessionListener.kt
│   │   │   │               ├── FramePulse.kt
│   │   │   │               ├── FramePulseConfig.kt
│   │   │   │               ├── FramePulseListener.kt
│   │   │   │               ├── FramePulseMetrics.kt
│   │   │   │               ├── FramePulseSession.kt
│   │   │   │               ├── FrameStages.kt
│   │   │   │               ├── JankFrame.kt
│   │   │   │               ├── JankFrameListener.kt
│   │   │   │               └── SessionSummary.kt
│   │   │   └── AndroidManifest.xml
│   │   └── test
│   │       └── java
│   │           └── com
│   │               └── mayank
│   │                   └── framepulse
│   │                       ├── internal
│   │                       │   ├── metrics
│   │                       │   │   ├── RollingWindowFpsCalculatorTest.kt
│   │                       │   │   ├── RollingWindowMeasuredFramesTest.kt
│   │                       │   │   ├── RollingWindowPercentilesAndFrozenFramesTest.kt
│   │                       │   │   └── SessionFrameStatsTest.kt
│   │                       │   ├── source
│   │                       │   │   ├── FakeFrameSource.kt
│   │                       │   │   ├── FakeRefreshRateSource.kt
│   │                       │   │   └── MeasuredFrameTest.kt
│   │                       │   ├── ActivitySessionTrackerTest.kt
│   │                       │   ├── FramePulseSessionImplTest.kt
│   │                       │   ├── FramePulseSessionJankFramesTest.kt
│   │                       │   ├── FramePulseSessionMeasuredFramesTest.kt
│   │                       │   ├── FramePulseSessionRefreshRateTest.kt
│   │                       │   ├── FramePulseSessionSummaryTest.kt
│   │                       │   ├── FramePulseSessionTagsTest.kt
│   │                       │   ├── TagTrackerCountersTest.kt
│   │                       │   └── TagTrackerTest.kt
│   │                       ├── FramePulseConfigTest.kt
│   │                       ├── JavaCallersTest.java
│   │                       └── ResultTypesTest.kt
│   ├── build.gradle.kts
│   ├── consumer-rules.pro
│   └── proguard-rules.pro
├── gradle
│   └── libs.versions.toml
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── LICENSE
├── README.md
└── settings.gradle.kts
```

## Design principles

- Public API should remain small and stable.
- Android framework code stays inside `internal`.
- Every component has a single responsibility.
- Testability through abstractions (`FrameSource` lets tests run on the JVM without Choreographer).
- Only claim in docs what the code does and tests verify.
- Incremental development over premature complexity.

## TODO

- Release 0.1.0 to Maven Central
- Compose integration

## If this project helps you, give it a ⭐

## License

```
   Copyright (C) 2026 Mayank Kumar

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

## Contributing to FramePulse

All pull requests are welcome.

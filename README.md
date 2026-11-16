# FramePulse

> A small Android library that measures frame times and jank: the frames a window draws on Android 7+, and main-thread frame pacing with `Choreographer` below that.

FramePulse is a Kotlin Android library built to understand Android's rendering pipeline from first principles. Every piece (frame source, rolling-window math, jank heuristic, session lifecycle) is written from scratch and the design decisions are documented in [`docs/`](docs).

> **Status: alpha, educational.** The API may change between versions. It is not a replacement for Perfetto, Macrobenchmark or JankStats, and it has not been validated for production use.

---

# Why FramePulse?

Most Android performance libraries hand you an FPS number and hide how it was produced.

FramePulse goes the other way. Its goals are to:

- Learn Android rendering internals deeply
- Show every step from `frameTimeNanos` to an FPS and jank number
- Document every architectural decision
- Demonstrate clean library engineering: small public API, internal implementation, tests
- Become an educational reference for Android developers

---

# Current Status

## ✅ Done

- Drawn-frame source from the platform's frame metrics (API 24+), with a `Choreographer` fallback
- Rolling-window FPS, average and max frame time
- Jank detection against the system's frame deadline (API 31+) or a configurable frame budget
- Optional frame budget that follows display refresh-rate changes during a session
- Listener API delivering immutable `FrameMetrics` snapshots
- Explicit session lifecycle (start / stop, no restart)
- JVM unit tests for the metrics math and the session state machine
- GitHub Actions CI (`./gradlew build test`)
- Demo app with live metrics and a "cause jank" button

## 📋 Planned

- Percentiles (p50 / p95 / p99)
- Per-stage timings for slow frames (input, layout, draw)
- Benchmark module (measure FramePulse's own overhead)
- Instrumentation tests
- Maven Central publishing
- Compose integration

---

# What this measures and what it doesn't

FramePulse measures in one of two ways, depending on how you start it.

| You call | Android version | What is measured |
|----------|-----------------|------------------|
| `FramePulse.start(activity, …)` | 7.0+ (API 24+) | Frames the activity's window drew |
| `FramePulse.start(activity, …)` | 5.0–6.0 (API 21–23) | `Choreographer` callbacks |
| `FramePulse.start(config, …)` or `FramePulse.start(context, display, …)` | any | `Choreographer` callbacks |

## Drawn frames (API 24+)

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
- Give a per-stage breakdown (input, animation, measure/layout, draw). The platform reports it, but FramePulse does not expose it yet.
- Say which code caused a slow frame. Use Perfetto or Android Studio's profiler for that.

This mode has been checked with unit tests only, not yet on a device.

## Choreographer callbacks

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

**Known limitations**

- **A pause counts as one long frame.** If no callback arrives for a while (for example the screen turns off while the session is running), the gap is recorded as a single janky frame. Stop the session in `onStop`.
- **Refresh-rate changes need the `Activity` or `Display` overload.** `FramePulse.start(config, listener)` uses a fixed budget for the whole session. On variable-refresh-rate displays (for example Pixel "Smooth Display"), the system can drop from 120 Hz to 60 Hz when the screen is idle. With a fixed 120 Hz budget, every 60 Hz frame then counts as jank. On a Pixel 6 Pro this showed up as ~65 FPS and dozens of "janks" per second on an idle screen. `FramePulse.start(context, display, config, listener)` follows the rate reported by `Display.getRefreshRate()` through `DisplayManager.DisplayListener` instead. Changes the system does not report there (for example per-app frame-rate overrides) are not seen, and the interval that spans a switch is judged against the new budget. This has been checked with unit tests only, not yet on a device.
- **Observer effect.** The listener runs on the main thread, and whatever it does (for example updating UI) is measured too. In the debug build of the demo app on a Pixel 6 Pro, the demo's own metric updates (4 per second) were slow enough to register as about 4 janks per second while idle; `adb shell dumpsys gfxinfo` reported the same frames as janky ("Slow UI thread").
- **Power.** Keeping a frame callback posted keeps vsync running. Stop the session when you don't need it.

---

# Requirements

| Requirement          | Value  |
|----------------------|--------|
| Min SDK              | 21     |
| Language             | Kotlin |
| Bytecode target      | Java 11 |
| JDK to build         | 17–23 (AGP 8.13 / Gradle 8.13) |
| Compose dependency   | No     |
| Threading            | Main thread only |

---

# Quick Start

FramePulse is not published to Maven yet. Include it as a module:

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

Start a session when your UI becomes visible and stop it when it isn't:

```kotlin
import com.mayank.framepulse.api.FramePulse
import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.api.FramePulseSession

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

`FrameMetrics` fields:

| Field | Meaning |
|-------|---------|
| `fps` | Frames per second over the window (see the two modes above) |
| `frameCount` | Frames in the window |
| `averageFrameTimeMs` / `maxFrameTimeMs` | Mean / longest frame time in the window |
| `jankCount` | Frames in the window that were jank |
| `totalFrameCount` / `totalJankCount` | Since the session started |
| `lastFrameTimeNanos` | Time of the newest frame, in nanoseconds |

`FramePulseConfig` options:

| Option | Default | Meaning |
|--------|---------|---------|
| `windowDurationMs` | 1000 | Rolling window length |
| `expectedFramePeriodMs` | 16.67 | Frame budget; set it with `withRefreshRate(hz)`. Replaced by the display's rate when you pass an `Activity` or a `Display` |
| `jankThresholdMultiplier` | 1.5 | Jank if frame time > budget × multiplier (must be ≥ 1.0). Not used for drawn frames on API 31+ |
| `reportIntervalMs` | 250 | Minimum time between listener calls; 0 = every frame |

---

# Threading and lifecycle

- **Main-thread-confined.** Call `FramePulse.start()` and `session.stop()` on the main thread; both throw `IllegalStateException` otherwise. A rejected `stop()` leaves the session running, so you can still stop it from the main thread. FramePulse is not thread-safe and uses no locks.
- **The listener runs on the main thread.** Keep it cheap. `FrameMetrics` is immutable, so you can hand it to another thread.
- **You own the lifecycle.** FramePulse does not observe Activity or process lifecycle. Every `start()` needs a matching `stop()`. A session started with an `Activity` holds its window until you stop it. Calling `stop()` more than once is fine, and you can call it from inside the listener.
- **No restart.** A stopped session stays stopped; call `FramePulse.start()` again for a new one.

---

# Demo app

`:app` shows live FPS, jank in the window, jank since start and the max frame time for the frames its window draws. It also has a **Block main thread for 200 ms** button: press it and the next frame takes about 200 ms, so the jank count goes up. On an idle screen the FPS is low, because the window only draws when the numbers change.

```bash
./gradlew :app:installDebug
```

---

# Architecture Overview

```
App
 │  FramePulse.start(activity, config, listener)
 ▼
FramePulseSessionImpl ──────────────► FramePulseListener.onMetrics(FrameMetrics)
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

---

# Module Structure

```
FramePulse
│
├── app/                 Demo application (Compose)
│
├── framepulse/
│   ├── api/             Public API: FramePulse, config, listener, session
│   ├── internal/        Implementation (not public): FrameMonitor, FPSCalculator, session,
│   │                    frame and refresh-rate sources
│   └── model/           Immutable FrameMetrics
│
├── docs/
│
└── README.md
```

---

# Design Principles

- Public API should remain small and stable.
- Android framework code stays inside `internal`.
- Every component has a single responsibility.
- Testability through abstractions (`FrameSource` lets tests run on the JVM without Choreographer).
- Only claim in docs what the code does and tests verify.
- Incremental development over premature complexity.

---

# Documentation

| Document                          | Description                      |
|-----------------------------------|----------------------------------|
| `docs/ARCHITECTURE.md`            | Overall library architecture     |
| `docs/FRAME_MONITOR.md`           | FrameMonitor lifecycle           |
| `docs/AndroidInternals-Looper.md` | Looper, Handler and MessageQueue |

---

# Development

Build everything and run the unit tests:

```bash
./gradlew build test
```

Library only:

```bash
./gradlew :framepulse:assembleDebug :framepulse:testDebugUnitTest
```

Gradle 8.13 runs on JDK 17–23. If your IDE or `JAVA_HOME` points to a newer JDK (for example 25), point Gradle at a JDK 17 or 21 instead.

---

# License

Licensed under the Apache License 2.0.

See the [LICENSE](LICENSE) file for details.

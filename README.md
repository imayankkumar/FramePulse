# FramePulse

> A small Android library that measures main-thread frame pacing and jank using `Choreographer`.

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

- `Choreographer`-based frame source behind a `FrameSource` abstraction
- Rolling-window FPS, average and max frame interval
- Jank detection against a configurable frame budget
- Listener API delivering immutable `FrameMetrics` snapshots
- Explicit session lifecycle (start / stop, no restart)
- JVM unit tests for the metrics math and the session state machine
- GitHub Actions CI (`./gradlew build test`)
- Demo app with live metrics and a "cause jank" button

## 📋 Planned

- Percentiles (p50 / p95 / p99)
- `FrameMetrics` (API 24+) frame source with per-stage timings
- Handling refresh-rate changes during a session
- Benchmark module (measure FramePulse's own overhead)
- Instrumentation tests
- Maven Central publishing
- Compose integration

---

# What this measures and what it doesn't

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
- Per-stage breakdown (input, animation, measure/layout, draw). That needs `FrameMetrics` (API 24+) or JankStats.
- Which code caused a slow frame. Use Perfetto or Android Studio's profiler for that.

**Known limitations**

- **The frame budget is fixed for the session.** The library does not read the display's refresh rate; you pass it in (`withRefreshRate(display.refreshRate)`). On variable-refresh-rate displays (for example Pixel "Smooth Display"), the system can drop from 120 Hz to 60 Hz when the screen is idle. With a 120 Hz budget, every 60 Hz frame then counts as jank. On a Pixel 6 Pro this showed up as ~65 FPS and dozens of "janks" per second on an idle screen. The demo app works around it by requesting the highest refresh-rate display mode for its window.
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
        val refreshRate = display?.refreshRate ?: 60f // Activity.getDisplay() is API 30+
        val config = FramePulseConfig.DEFAULT.withRefreshRate(refreshRate)

        session = FramePulse.start(config) { metrics ->
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
| `fps` | Frame intervals in the window ÷ their total duration |
| `frameCount` | Frame intervals in the window |
| `averageFrameTimeMs` / `maxFrameTimeMs` | Mean / longest interval in the window |
| `jankCount` | Intervals in the window longer than the jank threshold |
| `totalFrameCount` / `totalJankCount` | Since the session started |
| `lastFrameTimeNanos` | `frameTimeNanos` of the newest frame (same clock as `System.nanoTime()`) |

`FramePulseConfig` options:

| Option | Default | Meaning |
|--------|---------|---------|
| `windowDurationMs` | 1000 | Rolling window length |
| `expectedFramePeriodMs` | 16.67 | Frame budget; set it with `withRefreshRate(hz)` |
| `jankThresholdMultiplier` | 1.5 | Jank if interval > budget × multiplier (must be ≥ 1.0) |
| `reportIntervalMs` | 250 | Minimum time between listener calls; 0 = every frame |

---

# Threading and lifecycle

- **Main-thread-confined.** Call `FramePulse.start()` and `session.stop()` on the main thread; both throw `IllegalStateException` otherwise. A rejected `stop()` leaves the session running, so you can still stop it from the main thread. FramePulse is not thread-safe and uses no locks.
- **The listener runs on the main thread**, synchronously inside the frame callback. Keep it cheap. `FrameMetrics` is immutable, so you can hand it to another thread.
- **You own the lifecycle.** FramePulse does not observe Activity or process lifecycle. Every `start()` needs a matching `stop()`. Calling `stop()` more than once is fine, and you can call it from inside the listener.
- **No restart.** A stopped session stays stopped; call `FramePulse.start()` again for a new one.

---

# Demo app

`:app` shows live FPS, jank in the window, jank since start and the max frame interval. It also has a **Block main thread for 200 ms** button: press it and you'll see a ~200 ms interval and the jank count go up.

```bash
./gradlew :app:installDebug
```

---

# Architecture Overview

```
App
 │  FramePulse.start(config, listener)
 ▼
FramePulseSessionImpl ──────────────► FramePulseListener.onMetrics(FrameMetrics)
 │                                          ▲
 ▼                                          │
FrameMonitorImpl (IDLE → STARTED → STOPPED) │
 │  frameTimeNanos                          │
 ├──────────────► RollingWindowFpsCalculator┘
 ▼
ChoreographerFrameSource ──► Choreographer.postFrameCallback (re-posted every frame)
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
│   ├── core/            FrameMonitor contract
│   ├── internal/        Implementation: monitor, session, Choreographer source, calculator
│   ├── metrics/         FPSCalculator contract
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

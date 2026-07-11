# FramePulse

> Real-time Android rendering and jank analyzer powered by `Choreographer`.

FramePulse is a lightweight, Kotlin-first Android performance library built to understand Android's rendering pipeline from first principles.

Instead of simply exposing FPS metrics, FramePulse focuses on building every component of a rendering performance library from scratch while documenting the engineering decisions behind it.

The project is being developed incrementally with production-grade engineering practices and serves both as a real-world open-source library and as a deep dive into Android internals.

> **Current Version:** `v0.1.0-alpha01`

---

# Why FramePulse?

Most Android performance libraries provide FPS values but abstract away how those values are actually produced.

FramePulse takes a different approach.

Its mission is to:

- Learn Android rendering internals deeply
- Build a production-grade Android performance library
- Document every architectural decision
- Demonstrate clean library engineering practices
- Become an educational reference for Android developers

FramePulse is intentionally built milestone-by-milestone so every architectural decision remains understandable and maintainable.

---

# Current Status

## ✅ Completed

- Repository setup
- Production-grade Gradle project
- Library architecture
- Public API design
- Frame monitoring lifecycle
- Choreographer integration
- Frame callback pipeline
- Architecture documentation

## 🚧 Currently Building

- FPS Calculation Engine

## 📋 Planned

- Rolling FPS calculation
- Frame statistics
- Jank detection
- FrameMetrics pipeline
- Sample visualization
- Benchmark module
- Unit tests
- Instrumentation tests
- GitHub Actions
- Maven Central publishing
- Compose integration

---

# Features (Current)

FramePulse currently provides:

- Frame monitoring using Android `Choreographer`
- Lifecycle-aware monitoring sessions
- Clean separation between public API and Android framework
- Production-ready architecture
- Thread-safe monitoring lifecycle
- Comprehensive architecture documentation

---

# What FramePulse Measures Today

Every VSYNC event generates a callback through Android's rendering pipeline.

FramePulse currently captures:

```
VSYNC
    ↓
Choreographer
    ↓
FrameCallback
    ↓
frameTimeNanos
```

Example Logcat output:

```text
D/FramePulse: frameTimeNanos=1353462345123
D/FramePulse: frameTimeNanos=1353479012345
D/FramePulse: frameTimeNanos=1353495678901
```

Filter Logcat by:

```
tag:FramePulse
```

to verify frame delivery.

---

# Not Yet Implemented

The following features are intentionally deferred to future milestones.

- FPS calculation
- Rolling frame statistics
- Jank detection
- Dropped frame analysis
- FrameMetrics callbacks
- Rendering reports
- GPU timing
- SurfaceFlinger analysis
- Compose integration

---

# Requirements

| Requirement          | Value  |
|----------------------|--------|
| Min SDK              | 21     |
| Language             | Kotlin |
| Java                 | 11     |
| AndroidX             | Yes    |
| Compose Dependency   | No     |
| Main Thread Required | Yes    |

---

# Quick Start

## Add the module

```kotlin
// settings.gradle.kts
include(":framepulse")
```

```kotlin
dependencies {
    implementation(project(":framepulse"))
}
```

---

## Start Monitoring

```kotlin
private var session: FramePulseSession? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    session = FramePulse.start(
        listener = FramePulseListener {
            // Metrics pipeline coming in future milestone
        }
    )
}
```

---

## Stop Monitoring

```kotlin
override fun onDestroy() {
    session?.stop()
    super.onDestroy()
}
```

Always stop the session to unregister `Choreographer` callbacks.

---

# Architecture Overview

```
                    App
                     │
                     ▼
             FramePulse.start()
                     │
                     ▼
            FramePulseSession
                     │
                     ▼
             FrameMonitorImpl
                     │
                     ▼
         ChoreographerFrameSource
                     │
                     ▼
             Choreographer
                     │
                     ▼
            FrameCallback
                     │
                     ▼
            frameTimeNanos
```

---

# Module Structure

```
FramePulse
│
├── app/
│   └── Demo application
│
├── framepulse/
│   ├── api/
│   ├── core/
│   ├── internal/
│   ├── metrics/
│   └── model/
│
├── docs/
│
└── README.md
```

---

# Package Responsibilities

| Package    | Responsibility                   |
|------------|----------------------------------|
| `api`      | Public library API               |
| `core`     | Core monitoring contracts        |
| `internal` | Android framework implementation |
| `metrics`  | FPS calculation (upcoming)       |
| `model`    | Immutable metric models          |

---

# Design Principles

FramePulse follows a few core engineering principles.

- Public API should remain stable.
- Android framework code stays inside `internal`.
- Every component has a single responsibility.
- Architecture before implementation.
- Documentation before optimization.
- Testability through abstractions.
- Incremental development over premature complexity.

---

# Documentation

| Document                          | Description                      |
|-----------------------------------|----------------------------------|
| `docs/ARCHITECTURE.md`            | Overall library architecture     |
| `docs/FRAME_MONITOR.md`           | FrameMonitor lifecycle           |
| `docs/AndroidInternals-Looper.md` | Looper, Handler and MessageQueue |

More documentation will be added as the library evolves.

---

# Roadmap

## Phase 1 — Foundation ✅

- Repository
- Architecture
- Public API
- Frame monitoring
- Choreographer integration

---

## Phase 2 — Metrics Engine 🚧

- FPSCalculator
- Rolling window algorithm
- FrameMetrics
- Statistics

---

## Phase 3 — Performance Analysis

- Jank detection
- Skipped frames
- Frame classification

---

## Phase 4 — Developer Experience

- Sample app
- Visualization
- Benchmarks
- Documentation
- CI/CD

---

## Phase 5 — Open Source

- Maven Central
- Semantic Versioning
- GitHub Releases
- Contribution Guide
- API Stability

---

# Development

Build the library:

```bash
./gradlew :framepulse:assembleDebug
```

Build the sample application:

```bash
./gradlew :app:assembleDebug
```

Run all checks:

```bash
./gradlew build
```

---

# Vision

FramePulse is more than an FPS library.

It is an engineering project designed to deeply understand Android rendering internals while demonstrating production-grade library architecture.

The long-term goal is to become an educational reference for Android performance engineering.

---

# License

Licensed under the Apache License 2.0.

See the [LICENSE](LICENSE) file for details.

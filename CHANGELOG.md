# Changelog

All notable changes to FramePulse are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/). Before 1.0.0, a minor version (0.x.0) may change the public API.

## Unreleased

### Added

- `framepulse-report`, a new artifact for debug builds. With no code, it measures every activity and writes an HTML report of every screen to the app's files folder: smooth frames, jank and frozen frames, the worst frame, the longest frame stage in jank frames, and the tags set when they happened. `FramePulseReport.sessionFor(activity)` sets tags on its sessions, and `FramePulseReport.reportFile(context)` returns the file.

### Fixed

- A session started with an activity (API 24+) no longer leaves out the frames drawn just before the window goes idle. The listener gets one more snapshot with them about 100 ms after the window stops drawing, no sooner than `reportIntervalMs` after the previous one.

## 0.1.0 - 2026-11-25

The first release, on Maven Central as `io.github.imayankkumar:framepulse:0.1.0`.

### Added

- `FramePulse.start(activity, ...)` measures the frames an activity's window draws, from the platform's frame metrics (API 24+), and falls back to `Choreographer` callbacks on API 21–23.
- `FramePulse.start(config, ...)` and `FramePulse.start(context, display, ...)` measure `Choreographer` callbacks, with a fixed frame budget or one that follows the display's refresh rate.
- `FramePulseMetrics`: rolling-window FPS, average, max and p50 / p90 / p95 / p99 frame times, jank and frozen frame counts, and totals since the start.
- Jank judged against the system's frame deadline (API 31+) or the frame budget times `jankThresholdMultiplier`.
- `JankFrameListener`: a callback for each jank frame, with a stage breakdown (`FrameStages`) for drawn frames and the tags that were set.
- Tags (`setTag`, `removeTag`) that record what the app was doing during each frame.
- `SessionSummary`: totals, jank rate, frozen frames, whole-session percentiles and a per-tag breakdown (`TagSummary`).
- `FramePulse.monitorActivities`: measures every activity from `onStart` to `onStop` and reports its summary to an `ActivitySessionListener`.
- `FramePulseConfig.Builder` for the window length, frame budget, jank threshold and report interval.
- `FramePulseSession.stop()` works from any thread, and a session started with an activity stops itself when the activity is destroyed.
- Java support: static `start` overloads without the optional arguments.

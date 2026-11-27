# Contributing to FramePulse

All pull requests are welcome: bug fixes, tests, docs and new features.

## Before you start

- **Bug:** open an issue with the device, the Android version, how you started the session, what you saw and what you expected.
- **New feature or public API change:** open an issue first, so we can agree on the API before you write the code.
- **Security problem:** do not open an issue. See [SECURITY.md](SECURITY.md).

## How to build and test

You need JDK 17 or newer.

```bash
# Build everything, run the unit tests and check the public API
./gradlew build test

# Test the Gradle plugin
./gradlew :framepulse-gradle-plugin:check

# Run the instrumentation tests on a connected device or emulator (screen on and unlocked)
./gradlew :framepulse:connectedDebugAndroidTest

# Run the benchmarks on a real device (API 23+); results go to benchmark/build/outputs/
./gradlew :benchmark:connectedReleaseAndroidTest
```

CI runs the same build, and the instrumentation tests on emulators with API 21, 24 and 34. A pull request needs a green CI before it is merged.

## Public API

The public API of each library is recorded in `framepulse/api/framepulse.api` and `framepulse-report/api/framepulse-report.api`, and the build fails when a compiled library differs from its file. If you change the public API on purpose, update the files and commit them with your change:

```bash
# Record the current public API of both libraries
./gradlew apiDump
```

Adding to the API is fine. Removing or changing something already in the file breaks apps compiled against an earlier version, so discuss it in an issue first.

## Code

- Every public declaration states its visibility (explicit API mode) and has KDoc.
- Android framework code and implementation details go in the `internal` package.
- New behaviour comes with a test: JVM unit tests where possible, instrumentation tests for code that needs a real window or `Choreographer`.
- Only claim in docs what the code does and a test verifies.

## Commit messages

Follow [How to Write a Git Commit Message](https://cbea.ms/git-commit):

- Separate the subject from the body with a blank line.
- Keep the subject to 50 characters, capitalized, in the imperative mood ("Add", not "Added"), with no period at the end.
- Wrap the body at 72 characters, and use it to explain what changed and why.

## Pull requests

- One change per pull request.
- The title is a short imperative sentence, like a commit subject.
- The description is a few bullets starting with `* New:`, `* Update:`, `* Fix:` or `* Remove:`.
- Add a line under `Unreleased` in [CHANGELOG.md](CHANGELOG.md) for any change users will notice.

## How to publish a release

Set the same version in `framepulse/build.gradle.kts`, `framepulse-report/build.gradle.kts`, `framepulse-gradle-plugin/build.gradle.kts` and `FramePulsePlugin.VERSION`, and give it a dated heading in [CHANGELOG.md](CHANGELOG.md). The Central Portal token, the signing key and the Gradle Plugin Portal keys (`gradle.publish.key`, `gradle.publish.secret`) go in `~/.gradle/gradle.properties`, never in this repository.

```bash
# Publish to the local Maven repository to try it first
./gradlew :framepulse:publishToMavenLocal :framepulse-report:publishToMavenLocal

# Upload the signed release; it waits in the Central Portal until you publish it there
./gradlew :framepulse:publishToMavenCentral :framepulse-report:publishToMavenCentral

# Publish the Gradle plugin to the Gradle Plugin Portal
./gradlew :framepulse-gradle-plugin:publishPlugins
```

## Project folder structure

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
│   ├── images
│   │   ├── frame-report-dark.png
│   │   └── frame-report-light.png
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
│   │                       │   ├── FramePulseSessionIdleReportTest.kt
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
├── framepulse-gradle-plugin
│   ├── src
│   │   ├── main
│   │   │   └── kotlin
│   │   │       └── com
│   │   │           └── mayank
│   │   │               └── framepulse
│   │   │                   └── gradle
│   │   │                       ├── Devices.kt
│   │   │                       ├── FramePulseExtension.kt
│   │   │                       ├── FramePulsePlugin.kt
│   │   │                       ├── FramePulseReportTask.kt
│   │   │                       ├── ReportServer.kt
│   │   │                       └── ReportServerMain.kt
│   │   └── test
│   │       └── kotlin
│   │           └── com
│   │               └── mayank
│   │                   └── framepulse
│   │                       └── gradle
│   │                           ├── DevicesTest.kt
│   │                           └── ReportServerTest.kt
│   ├── build.gradle.kts
│   └── settings.gradle.kts
├── framepulse-report
│   ├── api
│   │   └── framepulse-report.api
│   ├── src
│   │   ├── main
│   │   │   ├── java
│   │   │   │   └── com
│   │   │   │       └── mayank
│   │   │   │           └── framepulse
│   │   │   │               └── report
│   │   │   │                   ├── internal
│   │   │   │                   │   ├── ReportInstaller.kt
│   │   │   │                   │   └── ReportPage.kt
│   │   │   │                   └── FramePulseReport.kt
│   │   │   └── AndroidManifest.xml
│   │   └── test
│   │       └── java
│   │           └── com
│   │               └── mayank
│   │                   └── framepulse
│   │                       └── report
│   │                           └── internal
│   │                               └── ReportPageTest.kt
│   └── build.gradle.kts
├── gradle
│   └── libs.versions.toml
├── build.gradle.kts
├── CHANGELOG.md
├── CONTRIBUTING.md
├── gradle.properties
├── gradlew
├── gradlew.bat
├── LICENSE
├── README.md
├── SECURITY.md
└── settings.gradle.kts
```

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).

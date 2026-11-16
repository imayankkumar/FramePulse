package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.model.FrameMetrics

/**
 * Default [FPSCalculator]: a rolling time window over frames.
 *
 * One instance is fed by one frame source, through [onFrame] or [onMeasuredFrame], never both.
 *
 * Frame ticks ([onFrame]):
 * - A frame time is the difference between two consecutive `frameTimeNanos` values.
 * - The window holds every interval whose end timestamp is within
 *   [FramePulseConfig.windowDurationMs] of the newest frame.
 * - `fps = intervalsInWindow / sumOfIntervalsInWindow`.
 * - An interval is jank when it is strictly longer than [FramePulseConfig.jankThresholdMs].
 * - Timestamps that do not move forward are ignored.
 *
 * Measured frames ([onMeasuredFrame]):
 * - A frame time is the duration the platform measured for that frame.
 * - The window holds every frame that started within the last
 *   [FramePulseConfig.windowDurationMs].
 * - `fps = framesInWindow / windowLength`. Time in which nothing was drawn lowers the FPS and is
 *   never counted as jank. While the session is younger than the window, the time since the
 *   first frame is used as the length.
 * - A frame is jank when it took longer than its deadline. Without a deadline, when it took
 *   longer than [FramePulseConfig.jankThresholdMs].
 *
 * Snapshots are produced on the first frame time and then at most once per
 * [FramePulseConfig.reportIntervalMs] (measured on the frame clock). Other calls return null.
 *
 * Threading: not thread-safe. Called only from the frame source's thread (the main thread).
 *
 * Allocation: the ring buffer grows when the window holds more frames than it can store and is
 * then reused, so steady-state frames do not allocate. A [FrameMetrics] object is allocated only
 * when a snapshot is reported.
 */
internal class RollingWindowFpsCalculator : FPSCalculator {

    // Ring buffer of the frames currently in the window, oldest at [head]. A time is the end of
    // an interval for frame ticks and the start of the frame for measured frames.
    private var times = LongArray(INITIAL_CAPACITY)
    private var durations = LongArray(INITIAL_CAPACITY)
    private var janks = BooleanArray(INITIAL_CAPACITY)
    private var head = 0
    private var size = 0

    private var windowDurationSumNanos = 0L
    private var windowJankCount = 0

    private var lastFrameTimeNanos = NO_TIMESTAMP
    private var firstMeasuredTimeNanos = NO_TIMESTAMP
    private var lastReportTimeNanos = NO_TIMESTAMP
    private var totalFrameCount = 0L
    private var totalJankCount = 0L

    override fun onFrame(frameTimeNanos: Long, config: FramePulseConfig): FrameMetrics? {
        val previous = lastFrameTimeNanos
        if (previous != NO_TIMESTAMP && frameTimeNanos <= previous) return null
        lastFrameTimeNanos = frameTimeNanos
        if (previous == NO_TIMESTAMP) return null

        val interval = frameTimeNanos - previous
        record(frameTimeNanos, interval, isJank = interval > jankThresholdNanos(config))

        // Keep intervals that ended inside the window, the boundary included.
        evictBefore(frameTimeNanos - windowNanos(config))

        if (!isReportDue(frameTimeNanos, config)) return null
        lastReportTimeNanos = frameTimeNanos
        return snapshot(frameTimeNanos, spanNanos = windowDurationSumNanos)
    }

    override fun onMeasuredFrame(
        frameTimeNanos: Long,
        durationNanos: Long,
        deadlineNanos: Long,
        config: FramePulseConfig,
    ): FrameMetrics? {
        if (durationNanos <= 0L) return null

        // Eviction walks the buffer from the oldest entry, so times must not go backwards.
        val previous = lastFrameTimeNanos
        val wentBackwards = previous != NO_TIMESTAMP && frameTimeNanos < previous
        val time = if (wentBackwards) previous else frameTimeNanos
        lastFrameTimeNanos = time
        if (firstMeasuredTimeNanos == NO_TIMESTAMP) firstMeasuredTimeNanos = time

        val isJank = if (deadlineNanos > 0L) {
            durationNanos > deadlineNanos
        } else {
            durationNanos > jankThresholdNanos(config)
        }
        record(time, durationNanos, isJank)

        // Keep frames that started inside the window. A frame exactly one window old is dropped,
        // otherwise a steady 100 Hz stream would count 101 frames in one second.
        val windowNanos = windowNanos(config)
        evictBefore(time - windowNanos + 1)

        if (!isReportDue(time, config)) return null
        lastReportTimeNanos = time

        // The newest frame takes up at least one refresh period of the session's age.
        val periodNanos = (config.expectedFramePeriodMs.toDouble() * NANOS_PER_MILLI).toLong()
        val newest = if (durationNanos > periodNanos) durationNanos else periodNanos
        val sessionAgeNanos = time - firstMeasuredTimeNanos + newest
        val span = if (sessionAgeNanos < windowNanos) sessionAgeNanos else windowNanos
        return snapshot(time, spanNanos = span)
    }

    private fun windowNanos(config: FramePulseConfig): Long =
        config.windowDurationMs * NANOS_PER_MILLI

    private fun jankThresholdNanos(config: FramePulseConfig): Double =
        config.jankThresholdMs.toDouble() * NANOS_PER_MILLI

    private fun isReportDue(frameTimeNanos: Long, config: FramePulseConfig): Boolean {
        if (lastReportTimeNanos == NO_TIMESTAMP) return true
        return frameTimeNanos - lastReportTimeNanos >= config.reportIntervalMs * NANOS_PER_MILLI
    }

    private fun record(time: Long, duration: Long, isJank: Boolean) {
        if (size == times.size) grow()
        val index = (head + size) % times.size
        times[index] = time
        durations[index] = duration
        janks[index] = isJank
        size++
        windowDurationSumNanos += duration
        totalFrameCount++
        if (isJank) {
            windowJankCount++
            totalJankCount++
        }
    }

    private fun evictBefore(cutoffNanos: Long) {
        while (size > 0 && times[head] < cutoffNanos) {
            windowDurationSumNanos -= durations[head]
            if (janks[head]) windowJankCount--
            head = (head + 1) % times.size
            size--
        }
    }

    private fun grow() {
        val newCapacity = times.size * 2
        val newTimes = LongArray(newCapacity)
        val newDurations = LongArray(newCapacity)
        val newJanks = BooleanArray(newCapacity)
        for (i in 0 until size) {
            val from = (head + i) % times.size
            newTimes[i] = times[from]
            newDurations[i] = durations[from]
            newJanks[i] = janks[from]
        }
        times = newTimes
        durations = newDurations
        janks = newJanks
        head = 0
    }

    /** @param spanNanos The length of time the frames in the window are counted over. */
    private fun snapshot(frameTimeNanos: Long, spanNanos: Long): FrameMetrics {
        var maxDuration = 0L
        for (i in 0 until size) {
            val duration = durations[(head + i) % durations.size]
            if (duration > maxDuration) maxDuration = duration
        }
        return FrameMetrics(
            lastFrameTimeNanos = frameTimeNanos,
            fps = size * NANOS_PER_SECOND / spanNanos,
            frameCount = size,
            averageFrameTimeMs = windowDurationSumNanos.toDouble() / size / NANOS_PER_MILLI,
            maxFrameTimeMs = maxDuration.toDouble() / NANOS_PER_MILLI,
            jankCount = windowJankCount,
            totalFrameCount = totalFrameCount,
            totalJankCount = totalJankCount,
        )
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
        const val NO_TIMESTAMP = Long.MIN_VALUE
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

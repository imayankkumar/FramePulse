package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.api.FramePulseConfig
import com.mayank.framepulse.metrics.FPSCalculator
import com.mayank.framepulse.model.FrameMetrics

/**
 * Default [FPSCalculator]: a rolling time window over frame intervals.
 *
 * Model:
 * - A frame interval is the difference between two consecutive `frameTimeNanos` values.
 * - The window holds every interval whose end timestamp is within
 *   [FramePulseConfig.windowDurationMs] of the newest frame.
 * - `fps = intervalsInWindow / sumOfIntervalsInWindow`.
 * - An interval is jank when it is strictly longer than [FramePulseConfig.jankThresholdMs].
 * - Timestamps that do not move forward are ignored.
 *
 * Snapshots are produced on the first interval and then at most once per
 * [FramePulseConfig.reportIntervalMs] (measured on the frame clock). Other calls return null.
 *
 * Threading: not thread-safe. Called only from the frame source's thread (the main thread).
 *
 * Allocation: the ring buffer grows when the window holds more intervals than it can store and
 * is then reused, so steady-state frames do not allocate. A [FrameMetrics] object is allocated
 * only when a snapshot is reported.
 */
internal class RollingWindowFpsCalculator : FPSCalculator {

    // Ring buffer of the intervals currently in the window, oldest at [head].
    private var endTimes = LongArray(INITIAL_CAPACITY)
    private var durations = LongArray(INITIAL_CAPACITY)
    private var janks = BooleanArray(INITIAL_CAPACITY)
    private var head = 0
    private var size = 0

    private var windowDurationSumNanos = 0L
    private var windowJankCount = 0

    private var lastFrameTimeNanos = NO_TIMESTAMP
    private var lastReportTimeNanos = NO_TIMESTAMP
    private var totalFrameCount = 0L
    private var totalJankCount = 0L

    override fun onFrame(frameTimeNanos: Long, config: FramePulseConfig): FrameMetrics? {
        val previous = lastFrameTimeNanos
        if (previous != NO_TIMESTAMP && frameTimeNanos <= previous) return null
        lastFrameTimeNanos = frameTimeNanos
        if (previous == NO_TIMESTAMP) return null

        val interval = frameTimeNanos - previous
        val isJank = interval > config.jankThresholdMs.toDouble() * NANOS_PER_MILLI
        append(frameTimeNanos, interval, isJank)
        totalFrameCount++
        if (isJank) totalJankCount++

        evictEndingBefore(frameTimeNanos - config.windowDurationMs * NANOS_PER_MILLI)

        if (!isReportDue(frameTimeNanos, config)) return null
        lastReportTimeNanos = frameTimeNanos
        return snapshot(frameTimeNanos)
    }

    private fun isReportDue(frameTimeNanos: Long, config: FramePulseConfig): Boolean {
        if (lastReportTimeNanos == NO_TIMESTAMP) return true
        return frameTimeNanos - lastReportTimeNanos >= config.reportIntervalMs * NANOS_PER_MILLI
    }

    private fun append(endTime: Long, duration: Long, isJank: Boolean) {
        if (size == endTimes.size) grow()
        val index = (head + size) % endTimes.size
        endTimes[index] = endTime
        durations[index] = duration
        janks[index] = isJank
        size++
        windowDurationSumNanos += duration
        if (isJank) windowJankCount++
    }

    private fun evictEndingBefore(cutoffNanos: Long) {
        while (size > 0 && endTimes[head] < cutoffNanos) {
            windowDurationSumNanos -= durations[head]
            if (janks[head]) windowJankCount--
            head = (head + 1) % endTimes.size
            size--
        }
    }

    private fun grow() {
        val newCapacity = endTimes.size * 2
        val newEndTimes = LongArray(newCapacity)
        val newDurations = LongArray(newCapacity)
        val newJanks = BooleanArray(newCapacity)
        for (i in 0 until size) {
            val from = (head + i) % endTimes.size
            newEndTimes[i] = endTimes[from]
            newDurations[i] = durations[from]
            newJanks[i] = janks[from]
        }
        endTimes = newEndTimes
        durations = newDurations
        janks = newJanks
        head = 0
    }

    private fun snapshot(frameTimeNanos: Long): FrameMetrics {
        var maxDuration = 0L
        for (i in 0 until size) {
            val duration = durations[(head + i) % durations.size]
            if (duration > maxDuration) maxDuration = duration
        }
        val sum = windowDurationSumNanos.toDouble()
        return FrameMetrics(
            lastFrameTimeNanos = frameTimeNanos,
            fps = size * NANOS_PER_SECOND / sum,
            frameCount = size,
            averageFrameTimeMs = sum / size / NANOS_PER_MILLI,
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

package com.mayank.framepulse.internal.metrics

/**
 * Frame counts and frame-time percentiles for a whole session, in constant memory.
 *
 * Every frame is counted in a fixed histogram instead of being stored, so a session can run for
 * hours. The price is resolution: a percentile is the upper edge of the bucket the frame falls
 * in (rounded up), never more than the real maximum:
 * - up to 50 ms, buckets are 0.1 ms wide;
 * - from 50 ms to 1 s, buckets are 1 ms wide;
 * - above 1 s, all frames share one bucket and its percentiles report the exact maximum.
 *
 * Threading: not thread-safe. Main thread only.
 */
internal class SessionFrameStats {

    private val buckets = LongArray(BUCKET_COUNT)

    var frameCount: Long = 0L
        private set
    var jankCount: Long = 0L
        private set
    var frozenFrameCount: Long = 0L
        private set
    var maxDurationNanos: Long = 0L
        private set

    fun record(durationNanos: Long, isJank: Boolean) {
        if (durationNanos <= 0L) return
        buckets[bucketOf(durationNanos)]++
        frameCount++
        if (isJank) jankCount++
        if (isFrozenFrame(durationNanos)) frozenFrameCount++
        if (durationNanos > maxDurationNanos) maxDurationNanos = durationNanos
    }

    /**
     * Nearest-rank percentile in milliseconds, at bucket resolution (see the class comment).
     * 0 when no frame was recorded.
     */
    fun percentileMs(percent: Int): Double {
        if (frameCount == 0L) return 0.0
        // ceil(percent * frameCount / 100) in integer arithmetic.
        val rank = (percent * frameCount + 99) / 100
        var seen = 0L
        for (i in 0 until BUCKET_COUNT) {
            seen += buckets[i]
            if (seen >= rank) {
                val edge = upperEdgeNanos(i)
                return nanosToMillis(if (edge < maxDurationNanos) edge else maxDurationNanos)
            }
        }
        return nanosToMillis(maxDurationNanos)
    }

    /**
     * Buckets include their upper edge, so a duration exactly on an edge (5.0 ms, 50 ms) is
     * reported unchanged. Durations are positive.
     */
    private fun bucketOf(durationNanos: Long): Int = when {
        durationNanos <= FINE_LIMIT_NANOS -> ((durationNanos - 1) / FINE_WIDTH_NANOS).toInt()
        durationNanos <= COARSE_LIMIT_NANOS ->
            FINE_BUCKETS + ((durationNanos - FINE_LIMIT_NANOS - 1) / COARSE_WIDTH_NANOS).toInt()
        else -> OVERFLOW_BUCKET
    }

    /** The largest duration a bucket can hold; [Long.MAX_VALUE] for the overflow bucket. */
    private fun upperEdgeNanos(bucket: Int): Long = when {
        bucket < FINE_BUCKETS -> (bucket + 1) * FINE_WIDTH_NANOS
        bucket < OVERFLOW_BUCKET ->
            FINE_LIMIT_NANOS + (bucket - FINE_BUCKETS + 1) * COARSE_WIDTH_NANOS
        else -> Long.MAX_VALUE
    }

    private companion object {
        const val FINE_WIDTH_NANOS = 100_000L
        const val FINE_LIMIT_NANOS = 50 * NANOS_PER_MILLI
        const val FINE_BUCKETS = (FINE_LIMIT_NANOS / FINE_WIDTH_NANOS).toInt()

        const val COARSE_WIDTH_NANOS = NANOS_PER_MILLI
        const val COARSE_LIMIT_NANOS = 1_000 * NANOS_PER_MILLI
        const val COARSE_BUCKETS =
            ((COARSE_LIMIT_NANOS - FINE_LIMIT_NANOS) / COARSE_WIDTH_NANOS).toInt()

        const val OVERFLOW_BUCKET = FINE_BUCKETS + COARSE_BUCKETS
        const val BUCKET_COUNT = OVERFLOW_BUCKET + 1
    }
}

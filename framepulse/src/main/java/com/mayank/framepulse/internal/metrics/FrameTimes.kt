package com.mayank.framepulse.internal.metrics

import com.mayank.framepulse.FrameMetrics

internal const val NANOS_PER_MILLI: Long = 1_000_000L

/** Frames strictly longer than this are frozen; see [FrameMetrics.FROZEN_FRAME_THRESHOLD_MS]. */
internal const val FROZEN_FRAME_NANOS: Long =
    FrameMetrics.FROZEN_FRAME_THRESHOLD_MS * NANOS_PER_MILLI

internal fun isFrozenFrame(durationNanos: Long): Boolean = durationNanos > FROZEN_FRAME_NANOS

internal fun nanosToMillis(nanos: Long): Double = nanos.toDouble() / NANOS_PER_MILLI

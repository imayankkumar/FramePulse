package com.mayank.framepulse.internal.source

import android.annotation.TargetApi
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window

/**
 * [FrameSource] backed by [Window.OnFrameMetricsAvailableListener] (API 24+).
 *
 * The platform reports every frame the window draws, with the time it took. Unlike
 * [ChoreographerFrameSource], this source:
 * - sees only frames that were drawn, so an idle window delivers nothing;
 * - gets durations measured by the platform, including work off the main thread;
 * - does not keep vsync requested.
 *
 * Each report becomes one [FrameConsumer.onFrameMeasured] call; see [deliverMeasuredFrame] for
 * the reports that are dropped.
 *
 * Requirements:
 * - Only load this class on API 24 or higher.
 * - The window must be hardware accelerated. On a software-rendered window the platform logs a
 *   warning and never calls the listener.
 *
 * Threading: [start] and [stop] must be called on the [handler]'s thread (the main thread).
 * Reports are delivered on the same thread. The platform reuses the [FrameMetrics] object, so
 * its values are read inside the callback and the object is not kept.
 */
@TargetApi(Build.VERSION_CODES.N)
internal class FrameMetricsFrameSource(
    private val window: Window,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : FrameSource {

    private var consumer: FrameConsumer? = null

    private val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
        val consumer = consumer ?: return@OnFrameMetricsAvailableListener
        deliverMeasuredFrame(
            consumer = consumer,
            isFirstDrawFrame = frameMetrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L,
            totalDurationNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION),
            intendedVsyncNanos = if (sdkInt >= Build.VERSION_CODES.O) {
                frameMetrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            } else {
                NOT_REPORTED
            },
            deadlineNanos = if (sdkInt >= Build.VERSION_CODES.S) {
                frameMetrics.getMetric(FrameMetrics.DEADLINE)
            } else {
                NOT_REPORTED
            },
            nowNanos = System.nanoTime(),
        )
    }

    override val isRunning: Boolean
        get() = consumer != null

    override fun start(frameConsumer: FrameConsumer) {
        assertHandlerThread()
        if (consumer != null) {
            throw IllegalStateException("FrameSource is already running")
        }
        consumer = frameConsumer
        try {
            window.addOnFrameMetricsAvailableListener(listener, handler)
        } catch (exception: Exception) {
            consumer = null
            throw exception
        }
    }

    override fun stop() {
        assertHandlerThread()
        if (consumer == null) return
        consumer = null
        try {
            window.removeOnFrameMetricsAvailableListener(listener)
        } catch (ignored: IllegalArgumentException) {
            // The platform throws this when it no longer has the listener. Nothing is left to
            // remove, which is what stop() is for.
        }
    }

    private fun assertHandlerThread() {
        if (Looper.myLooper() !== handler.looper) {
            throw IllegalStateException(
                "FrameMetricsFrameSource must be used on the looper thread it was created for",
            )
        }
    }

    private companion object {
        const val NOT_REPORTED = -1L
    }
}

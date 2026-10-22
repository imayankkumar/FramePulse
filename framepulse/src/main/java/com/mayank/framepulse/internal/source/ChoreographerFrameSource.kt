package com.mayank.framepulse.internal.source

import android.os.Looper
import android.view.Choreographer

/**
 * [FrameSource] backed by [Choreographer] on the current thread's [Looper].
 *
 * Responsibilities:
 * - Register a [Choreographer.FrameCallback] via [Choreographer.postFrameCallback].
 * - Re-post the same callback after each [Choreographer.FrameCallback.doFrame] while running.
 * - Remove the callback via [Choreographer.removeFrameCallback] on [stop].
 *
 * Threading:
 * - [start] and [stop] must be called on the thread whose [Looper] is prepared (main thread
 *   for UI monitoring). [Choreographer.getInstance] is thread-local and requires a prepared
 *   looper on the calling thread.
 *
 * What this measures:
 * - Because the callback re-posts itself every frame, Choreographer requests vsync on every
 *   frame while this source runs. Callbacks therefore arrive at roughly the display refresh rate
 *   even when the app draws nothing. The gap between two callbacks shows how promptly the main
 *   thread serviced vsync, not whether a frame was rendered or presented.
 * - Keeping vsync requested has a small power cost; stop the source when monitoring is not
 *   needed.
 *
 * Implementation details: `docs/FRAME_MONITOR.md`.
 */
internal class ChoreographerFrameSource(
    private val looper: Looper = Looper.getMainLooper(),
) : FrameSource {

    private var choreographer: Choreographer? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var frameConsumer: ((Long) -> Unit)? = null
    private var running: Boolean = false

    override val isRunning: Boolean
        get() = running

    override fun start(frameConsumer: (frameTimeNanos: Long) -> Unit) {
        assertLooperThread()
        if (running) {
            throw IllegalStateException("FrameSource is already running")
        }

        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return

                val consumer = frameConsumer ?: return

                try {
                    consumer(frameTimeNanos)
                } finally {
                    if (running) {
                        choreographer.postFrameCallback(this)
                    }
                }
            }
        }

        this.choreographer = choreographer
        this.frameConsumer = frameConsumer
        this.frameCallback = callback
        running = true

        try {
            choreographer.postFrameCallback(callback)
        } catch (exception: Exception) {
            stop()
            throw exception
        }
    }

    override fun stop() {
        assertLooperThread()
        if (!running) return

        running = false

        val choreographer = choreographer
        val callback = frameCallback
        if (choreographer != null && callback != null) {
            choreographer.removeFrameCallback(callback)
        }

        frameCallback = null
        frameConsumer = null
        this.choreographer = null
    }

    private fun assertLooperThread() {
        if (Looper.myLooper() !== looper) {
            throw IllegalStateException(
                "ChoreographerFrameSource must be used on the looper thread it was created for",
            )
        }
    }
}

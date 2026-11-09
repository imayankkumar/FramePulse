package com.mayank.framepulse.internal.source

/**
 * Test [RefreshRateSource] whose rate the test sets with [changeTo].
 */
internal class FakeRefreshRateSource(private var refreshRateHz: Float) : RefreshRateSource {

    var startCount = 0
        private set
    var stopCount = 0
        private set

    /** When set, [start] throws this after recording the call. */
    var startFailure: Exception? = null

    private var callback: ((Float) -> Unit)? = null

    val isListening: Boolean
        get() = callback != null

    override fun start(onRefreshRateChanged: (refreshRateHz: Float) -> Unit): Float {
        startCount++
        startFailure?.let { throw it }
        callback = onRefreshRateChanged
        return refreshRateHz
    }

    override fun stop() {
        stopCount++
        callback = null
    }

    /** Simulates a display change event. Delivered only while listening, like the real source. */
    fun changeTo(newRateHz: Float) {
        refreshRateHz = newRateHz
        callback?.invoke(newRateHz)
    }
}

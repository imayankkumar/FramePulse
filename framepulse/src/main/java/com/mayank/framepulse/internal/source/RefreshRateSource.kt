package com.mayank.framepulse.internal.source

/**
 * Reports the display refresh rate and notifies when it changes.
 *
 * Threading: main-thread-confined. [start], [stop] and the change callback all run on the main
 * thread.
 */
internal interface RefreshRateSource {

    /**
     * Starts listening for refresh-rate changes and returns the current rate in Hz.
     * [onRefreshRateChanged] receives the new rate in Hz; it may repeat the current rate.
     */
    fun start(onRefreshRateChanged: (refreshRateHz: Float) -> Unit): Float

    /** Stops listening. Idempotent. */
    fun stop()
}

package com.mayank.framepulse.internal.source

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

/**
 * [RefreshRateSource] backed by [Display.getRefreshRate] and a [DisplayManager.DisplayListener]
 * (API 17+). Change events for other displays are ignored.
 *
 * Threading: the listener is registered on a main-thread [Handler], so change callbacks arrive on
 * the main thread, between frame callbacks.
 */
internal class DisplayRefreshRateSource(
    private val displayManager: DisplayManager,
    private val display: Display,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : RefreshRateSource {

    private var callback: ((Float) -> Unit)? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != display.displayId) return
            callback?.invoke(display.refreshRate)
        }
    }

    override fun start(onRefreshRateChanged: (refreshRateHz: Float) -> Unit): Float {
        check(callback == null) { "DisplayRefreshRateSource already started" }
        callback = onRefreshRateChanged
        displayManager.registerDisplayListener(displayListener, handler)
        return display.refreshRate
    }

    override fun stop() {
        if (callback == null) return
        callback = null
        displayManager.unregisterDisplayListener(displayListener)
    }
}

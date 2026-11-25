package com.mayank.framepulse.internal.source

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View

/** A hardware-accelerated window that tests make draw a new frame with [redraw]. */
class FrameTestActivity : Activity() {

    lateinit var content: View
        private set

    private var dark = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = View(this)
        setContentView(content)
    }

    /**
     * Changes the content's color. A plain invalidate() of a view that draws nothing is not
     * enough: the renderer skips frames with nothing new to draw, and skipped frames are not
     * reported.
     */
    fun redraw() {
        dark = !dark
        content.setBackgroundColor(if (dark) Color.DKGRAY else Color.LTGRAY)
    }
}

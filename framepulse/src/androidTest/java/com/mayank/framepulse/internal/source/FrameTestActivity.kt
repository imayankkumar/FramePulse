package com.mayank.framepulse.internal.source

import android.app.Activity
import android.os.Bundle
import android.view.View

/** An empty, hardware-accelerated window that tests redraw by invalidating [content]. */
class FrameTestActivity : Activity() {

    lateinit var content: View
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = View(this)
        setContentView(content)
    }
}

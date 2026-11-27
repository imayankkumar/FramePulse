package com.mayank.framepulse.gradle

import java.io.File
import java.util.concurrent.CountDownLatch

/**
 * Runs [ReportServer] in its own process, started by `framepulseReport`. Gradle stops processes
 * it started when the build is cancelled, so Ctrl+C stops the server; a server inside the
 * Gradle daemon would keep running.
 *
 * Arguments: the folder to serve and the first port to try.
 */
internal object ReportServerMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val server = ReportServer.start(File(args[0]), args[1].toInt())
        println("")
        println("FramePulse report: http://localhost:${server.port}/")
        println("Press Ctrl+C to stop.")
        CountDownLatch(1).await()
    }
}

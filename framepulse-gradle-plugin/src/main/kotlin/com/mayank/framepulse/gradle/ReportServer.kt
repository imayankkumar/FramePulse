package com.mayank.framepulse.gradle

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Serves the files of [root] on this computer only (127.0.0.1), read-only. A folder is served as
 * its `index.html`; anything outside [root] is not found.
 */
internal class ReportServer private constructor(
    private val server: HttpServer,
    private val root: File,
) {

    val port: Int
        get() = server.address.port

    fun stop() {
        server.stop(0)
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val file = resolve(exchange.requestURI.path)
            if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
                exchange.sendResponseHeaders(METHOD_NOT_ALLOWED, -1)
            } else if (file == null) {
                val body = "Not found".toByteArray()
                exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                exchange.sendResponseHeaders(NOT_FOUND, body.size.toLong())
                exchange.responseBody.write(body)
            } else {
                val body = file.readBytes()
                exchange.responseHeaders.set("Content-Type", contentType(file))
                exchange.responseHeaders.set("Cache-Control", "no-store")
                if (exchange.requestMethod == "HEAD") {
                    exchange.sendResponseHeaders(OK, -1)
                } else {
                    exchange.sendResponseHeaders(OK, body.size.toLong())
                    exchange.responseBody.write(body)
                }
            }
        }
    }

    /** The file for a request path, or null when it does not exist or is outside [root]. */
    private fun resolve(path: String): File? {
        val rootPath = root.canonicalFile
        var file = File(rootPath, path.trimStart('/')).canonicalFile
        if (file.isDirectory) file = File(file, "index.html")
        val inside = file.toPath().startsWith(rootPath.toPath())
        return file.takeIf { inside && it.isFile }
    }

    private fun contentType(file: File): String = when (file.extension.lowercase()) {
        "html" -> "text/html; charset=utf-8"
        "css" -> "text/css; charset=utf-8"
        "js" -> "text/javascript; charset=utf-8"
        "json" -> "application/json"
        "png" -> "image/png"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    companion object {
        private const val OK = 200
        private const val NOT_FOUND = 404
        private const val METHOD_NOT_ALLOWED = 405
        private const val PORTS_TO_TRY = 10

        /**
         * Starts serving [root] on the first free port from [firstPort] on, trying ten ports.
         * Pass 0 for any free port.
         */
        fun start(root: File, firstPort: Int): ReportServer {
            val loopback = InetAddress.getLoopbackAddress()
            val ports = if (firstPort == 0) listOf(0) else (firstPort until firstPort + PORTS_TO_TRY).toList()
            var lastError: IOException? = null
            for (port in ports) {
                try {
                    val http = HttpServer.create(InetSocketAddress(loopback, port), 0)
                    val reportServer = ReportServer(http, root)
                    http.createContext("/") { reportServer.handle(it) }
                    http.start()
                    return reportServer
                } catch (e: BindException) {
                    lastError = e
                }
            }
            throw IOException("No free port from $firstPort to ${ports.last()}", lastError)
        }
    }
}

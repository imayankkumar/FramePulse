package com.mayank.framepulse.gradle

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

class ReportServerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var server: ReportServer

    @Before
    fun setUp() {
        root = folder.newFolder("reports")
        File(root, "index.html").writeText("<!DOCTYPE html><p>index</p>")
        File(root, "Pixel_6_Pro").mkdirs()
        File(root, "Pixel_6_Pro/index.html").writeText("<!DOCTYPE html><p>pixel</p>")
        folder.newFile("secret.txt").writeText("secret")
        server = ReportServer.start(root, 0)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `serves a folder as its index page`() {
        val (status, body, type) = get("/")
        assertEquals(200, status)
        assertEquals("<!DOCTYPE html><p>index</p>", body)
        assertEquals("text/html; charset=utf-8", type)

        assertEquals("<!DOCTYPE html><p>pixel</p>", get("/Pixel_6_Pro/").second)
        assertEquals("<!DOCTYPE html><p>pixel</p>", get("/Pixel_6_Pro/index.html").second)
    }

    @Test
    fun `does not serve files outside the folder`() {
        assertEquals(404, get("/../secret.txt").first)
        assertEquals(404, get("/%2e%2e/secret.txt").first)
        assertEquals(404, get("/missing.html").first)
    }

    @Test
    fun `listens on this computer only`() {
        assertTrue(server.port > 0)
        val connection = URI("http://127.0.0.1:${server.port}/").toURL().openConnection() as HttpURLConnection
        assertEquals(200, connection.responseCode)
    }

    private fun get(path: String): Triple<Int, String, String?> {
        val connection = URI("http://127.0.0.1:${server.port}$path").toURL().openConnection() as HttpURLConnection
        val status = connection.responseCode
        val stream = if (status < 400) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        return Triple(status, body, connection.getHeaderField("Content-Type"))
    }
}

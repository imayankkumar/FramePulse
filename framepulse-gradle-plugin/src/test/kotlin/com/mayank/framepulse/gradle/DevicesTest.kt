package com.mayank.framepulse.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesTest {

    @Test
    fun `parses adb devices and keeps the state of each`() {
        val output = """
            * daemon not running; starting now at tcp:5037
            * daemon started successfully
            List of devices attached
            emulator-5554	device
            adb-24021FDEE00015-fKV7To._adb-tls-connect._tcp	device
            R5CT1234	unauthorized

        """.trimIndent()

        assertEquals(
            listOf(
                AdbDevice("emulator-5554", "device"),
                AdbDevice("adb-24021FDEE00015-fKV7To._adb-tls-connect._tcp", "device"),
                AdbDevice("R5CT1234", "unauthorized"),
            ),
            parseAdbDevices(output),
        )
    }

    @Test
    fun `finds no device in an empty list`() {
        assertEquals(emptyList<AdbDevice>(), parseAdbDevices("List of devices attached\n\n"))
    }

    @Test
    fun `names a folder after the model and adds the serial for a second one`() {
        assertEquals("Pixel_6_Pro", deviceFolderName("Pixel 6 Pro", "abc", emptySet()))
        assertEquals("Pixel_6_Pro_abc_1", deviceFolderName("Pixel 6 Pro", "abc:1", setOf("Pixel_6_Pro")))
        assertEquals("emulator-5554", deviceFolderName("", "emulator-5554", emptySet()))
    }

    @Test
    fun `forwards to the only report`() {
        val page = indexPage("com.example", listOf(DeviceReport("Pixel 6 Pro", "Pixel_6_Pro", "copied")))

        assertTrue(page.contains("url=Pixel_6_Pro/index.html"))
    }

    @Test
    fun `lists every device when there is more than one`() {
        val page = indexPage(
            "com.example",
            listOf(
                DeviceReport("Pixel 6 Pro", "Pixel_6_Pro", "copied"),
                DeviceReport("Pixel 8", "Pixel_8", "copied"),
                DeviceReport("R5CT1234", null, "skipped, the device is unauthorized"),
            ),
        )

        assertFalse(page.contains("http-equiv"))
        assertTrue(page.contains("<a href=\"Pixel_8/index.html\">Pixel 8</a>"))
        assertTrue(page.contains("<li>R5CT1234: skipped, the device is unauthorized</li>"))
    }

    @Test
    fun `reads the report from the app's external files folder`() {
        assertEquals(
            "/sdcard/Android/data/com.example.debug/files/framepulse-report.html",
            deviceReportPath("com.example.debug"),
        )
    }
}

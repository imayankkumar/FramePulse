package com.mayank.framepulse.gradle

/** One line of `adb devices`: the serial and its state, such as "device" or "unauthorized". */
internal data class AdbDevice(val serial: String, val state: String)

/** Parses the output of `adb devices`, skipping the header and daemon messages. */
internal fun parseAdbDevices(output: String): List<AdbDevice> = output.lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.startsWith("List of devices") && !it.startsWith("*") }
    .mapNotNull { line ->
        val parts = line.split(Regex("\\s+"))
        if (parts.size >= 2) AdbDevice(parts[0], parts[1]) else null
    }
    .toList()

/**
 * A folder name for a device's report: its model, such as "Pixel_6_Pro", or the model and its
 * serial when another device of the same model already took the name.
 */
internal fun deviceFolderName(model: String, serial: String, taken: Set<String>): String {
    val base = safeName(model.ifBlank { serial })
    return if (base !in taken) base else "${base}_${safeName(serial)}"
}

private fun safeName(text: String): String =
    text.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifEmpty { "device" }

/** Where `framepulse-report` writes the file on a device, for the first user. */
internal fun deviceReportPath(applicationId: String): String =
    "/sdcard/Android/data/$applicationId/files/framepulse-report.html"

/** The outcome for one device, listed on the index page. */
internal data class DeviceReport(
    val name: String,
    /** The report's folder under the output folder, or null when there was none to copy. */
    val folder: String?,
    val message: String,
)

/**
 * The page at the top of the output folder. With exactly one report it forwards straight to it;
 * otherwise it lists every device and what happened.
 */
internal fun indexPage(applicationId: String, devices: List<DeviceReport>): String {
    val copied = devices.filter { it.folder != null }
    val redirect = copied.singleOrNull()?.let {
        "<meta http-equiv=\"refresh\" content=\"0; url=${escape(it.folder!!)}/index.html\">"
    }.orEmpty()
    val rows = devices.joinToString("\n") { device ->
        val name = if (device.folder != null) {
            "<a href=\"${escape(device.folder)}/index.html\">${escape(device.name)}</a>"
        } else {
            escape(device.name)
        }
        "<li>$name: ${escape(device.message)}</li>"
    }
    return """
        |<!DOCTYPE html>
        |<html lang="en">
        |<head>
        |<meta charset="utf-8">
        |<meta name="viewport" content="width=device-width, initial-scale=1">
        |$redirect
        |<title>FramePulse frame reports</title>
        |<style>
        |body{font:15px/1.6 system-ui,sans-serif;margin:0 auto;max-width:720px;padding:24px 16px;
        |color:#1d1d1b;background:#f6f6f4}
        |@media (prefers-color-scheme: dark){body{color:#ececea;background:#1c1c1e}a{color:#8db4ec}}
        |</style>
        |</head>
        |<body>
        |<h1>FramePulse frame reports</h1>
        |<p>${escape(applicationId)}</p>
        |<ul>
        |$rows
        |</ul>
        |</body>
        |</html>
        |
    """.trimMargin()
}

private fun escape(text: String): String = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

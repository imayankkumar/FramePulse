package com.mayank.framepulse.report.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPageTest {

    @Test
    fun `adds visits of the same screen together`() {
        val report = ReportPage()
        report.addVisit("Feed", frameCount = 600, jankCount = 6, frozenFrameCount = 0, maxFrameTimeMs = 40.0)
        report.addVisit("Feed", frameCount = 400, jankCount = 4, frozenFrameCount = 1, maxFrameTimeMs = 900.0)

        val html = report.toHtml(app = "Sample", device = "Pixel", generatedAt = "now")

        assertTrue(html.contains("1,000 frames across 1 screen<"))
        assertTrue(html.contains("<span class=\"pill warn\">10 jank</span>"))
        assertTrue(html.contains("<span class=\"pill bad\">1 frozen</span>"))
        assertTrue(html.contains("max 900 ms"))
        assertTrue(html.contains("2 visits"))
        assertTrue(html.contains(">99.0%<"))
    }

    @Test
    fun `shares of the longest stage count only jank frames with stage times`() {
        val report = ReportPage()
        repeat(3) { report.addJankFrame("Feed", "draw", emptyMap()) }
        report.addJankFrame("Feed", "layout", emptyMap())
        report.addJankFrame("Feed", null, emptyMap())
        report.addVisit("Feed", frameCount = 100, jankCount = 5, frozenFrameCount = 0, maxFrameTimeMs = 30.0)

        val html = report.toHtml(app = "Sample", device = "Pixel", generatedAt = "now")

        assertTrue(html.contains("<li>draw: 3 of 4 (75.0%)</li>"))
        assertTrue(html.contains("<li>layout: 1 of 4 (25.0%)</li>"))
    }

    @Test
    fun `lists tags on jank frames except the excluded one`() {
        val report = ReportPage()
        val tags = mapOf("activity" to "Feed", "scrolling" to "true")
        repeat(2) { report.addJankFrame("Feed", "draw", tags) }
        report.addVisit("Feed", frameCount = 100, jankCount = 2, frozenFrameCount = 0, maxFrameTimeMs = 30.0)

        val html = report.toHtml(app = "Sample", device = "Pixel", generatedAt = "now", excludedTag = "activity")

        assertTrue(html.contains("<code>scrolling=true</code>: 2 frames"))
        assertFalse(html.contains("activity=Feed"))
    }

    @Test
    fun `escapes names and tags`() {
        val report = ReportPage()
        report.addJankFrame("<Feed>", "draw", mapOf("query" to "a&b"))
        report.addVisit("<Feed>", frameCount = 10, jankCount = 1, frozenFrameCount = 0, maxFrameTimeMs = 30.0)

        val html = report.toHtml(app = "Shop & Go", device = "Pixel \"6\"", generatedAt = "now")

        assertTrue(html.contains("<h1>Shop &amp; Go</h1>"))
        assertTrue(html.contains("&lt;Feed&gt;"))
        assertTrue(html.contains("query=a&amp;b"))
        assertTrue(html.contains("Pixel &quot;6&quot;"))
        assertFalse(html.contains("<Feed>"))
    }

    @Test
    fun `leaves out screens with jank frames but no finished visit`() {
        val report = ReportPage()
        report.addJankFrame("Running", "draw", emptyMap())

        assertFalse(report.toHtml(app = "Sample", device = "Pixel", generatedAt = "now").contains("Running"))
    }

    @Test
    fun `shows a dash instead of a share when no frames were drawn`() {
        val html = ReportPage().toHtml(app = "Sample", device = "Pixel", generatedAt = "now")

        assertTrue(html.contains("0 frames across 0 screens"))
        assertTrue(html.contains(">&ndash;<"))
    }

    @Test
    fun `gives no colour to a screen that drew no frames`() {
        val report = ReportPage()
        report.addVisit("Idle", frameCount = 0, jankCount = 0, frozenFrameCount = 0, maxFrameTimeMs = 0.0)

        val html = report.toHtml(app = "Sample", device = "Pixel", generatedAt = "now")

        assertTrue(html.contains("<span class=\"pct empty\">&ndash;</span>"))
    }
}

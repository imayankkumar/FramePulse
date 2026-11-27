package com.mayank.framepulse.report.internal

import java.util.Locale

/**
 * Collects every screen's frames during one run of the app and renders them as a single HTML
 * page: an overall share of smooth frames, the totals, and one card per screen with the frame
 * stage that was longest in its jank frames and the tags that were set when they happened.
 *
 * It only adds counts, so it never keeps frames and its size does not grow with the run. Every
 * number on the page is a count or a ratio of counts that FramePulse measured; there is no
 * weighted score. Percentiles are left out because they cannot be added up across visits.
 *
 * Main thread only.
 */
internal class ReportPage {

    private val screens = LinkedHashMap<String, ScreenStats>()

    /** Counts one jank frame of [screen]: the stage that took longest, and the tags it had. */
    fun addJankFrame(screen: String, longestStage: String?, tags: Map<String, String>) {
        val stats = statsFor(screen)
        if (longestStage != null) {
            stats.stagedJankCount++
            stats.jankByStage.increment(longestStage)
        }
        tags.forEach { (name, value) -> stats.jankByTag.increment("$name=$value") }
    }

    /** Adds one visit of [screen], from its session summary. */
    fun addVisit(
        screen: String,
        frameCount: Long,
        jankCount: Long,
        frozenFrameCount: Long,
        maxFrameTimeMs: Double,
    ) {
        val stats = statsFor(screen)
        stats.visits++
        stats.frameCount += frameCount
        stats.jankCount += jankCount
        stats.frozenFrameCount += frozenFrameCount
        stats.maxFrameTimeMs = maxOf(stats.maxFrameTimeMs, maxFrameTimeMs)
    }

    /**
     * The whole report as one HTML page with no external files.
     *
     * @param app The app's name, for the page heading.
     * @param device For example "Google Pixel 6 Pro, API 36".
     * @param generatedAt For example "27 Nov 2026, 15:50".
     * @param excludedTag Tag left out of the per-screen tag list, because the card already shows
     *   it (the activity name).
     */
    fun toHtml(app: String, device: String, generatedAt: String, excludedTag: String? = null): String {
        val visited = screens.values.filter { it.visits > 0 }
        val frames = visited.sumOf { it.frameCount }
        val jank = visited.sumOf { it.jankCount }
        val frozen = visited.sumOf { it.frozenFrameCount }
        val worst = visited.maxOfOrNull { it.maxFrameTimeMs } ?: 0.0
        val smooth = smoothShare(frames, jank)

        return buildString {
            append(PAGE_START)
            append("<header><span class=\"brand\">FramePulse</span><span class=\"chips\">")
            append(chip(device)).append(chip(generatedAt))
            append("</span></header>\n<main>\n<section class=\"hero\"><div>")
            append("<h1>").append(escape(app)).append("</h1>")
            append("<p class=\"sub\">Frame report: ").append(formatCount(frames)).append(" frames across ")
            append(visited.size).append(if (visited.size == 1) " screen" else " screens")
            append("</p><div class=\"totals\">")
            append(total("frozen", formatCount(frozen), "bad"))
            append(total("jank", formatCount(jank), "warn"))
            append(total("worst frame", "%.0f ms".format(Locale.US, worst), "info"))
            append("</div></div>")
            append(ring(smooth))
            append("</section>\n")
            append("<p class=\"legend\">Smooth frames are frames that were not jank. ")
            append("Green: under 1% jank, amber: 1&ndash;5%, red: over 5%.</p>\n")
            append("<h2>By screen</h2>\n<section class=\"grid\">\n")
            visited.sortedByDescending { jankShare(it.frameCount, it.jankCount) }
                .forEach { append(card(it, excludedTag)) }
            append("</section>\n</main>\n</body>\n</html>\n")
        }
    }

    private fun statsFor(screen: String): ScreenStats = screens.getOrPut(screen) { ScreenStats(screen) }

    private fun card(stats: ScreenStats, excludedTag: String?): String = buildString {
        val smooth = smoothShare(stats.frameCount, stats.jankCount)
        val tone = tone(stats.frameCount, stats.jankCount)
        append("<article class=\"card\"><div class=\"card-head\"><h3>").append(escape(stats.name))
        append("</h3><span class=\"pct ").append(tone).append("\">").append(percent(smooth))
        append("</span></div>")
        append("<div class=\"bar\"><div class=\"").append(tone).append("\" style=\"width:")
        append("%.1f".format(Locale.US, (smooth ?: 0.0) * 100)).append("%\"></div></div>")
        append("<div class=\"pills\">")
        append(pill("${formatCount(stats.frameCount)} frames", ""))
        append(pill("${formatCount(stats.jankCount)} jank", if (stats.jankCount > 0) "warn" else ""))
        append(pill("${formatCount(stats.frozenFrameCount)} frozen", if (stats.frozenFrameCount > 0) "bad" else ""))
        append(pill("max %.0f ms".format(Locale.US, stats.maxFrameTimeMs), ""))
        append(pill(if (stats.visits == 1) "1 visit" else "${stats.visits} visits", ""))
        append("</div>")

        append("<p class=\"k\">Longest stage in jank frames</p><ul>")
        if (stats.stagedJankCount == 0) {
            append("<li class=\"none\">No jank frames with stage times</li>")
        } else {
            stats.jankByStage.entries.sortedByDescending { it.value }.take(TOP_ITEMS).forEach {
                append("<li>").append(escape(it.key)).append(": ")
                append(it.value).append(" of ").append(stats.stagedJankCount).append(" (")
                append(percent(it.value.toDouble() / stats.stagedJankCount)).append(")</li>")
            }
        }
        append("</ul>")

        val tags = stats.jankByTag.entries
            .filter { excludedTag == null || !it.key.startsWith("$excludedTag=") }
            .sortedByDescending { it.value }
            .take(TOP_ITEMS)
        if (tags.isNotEmpty()) {
            append("<p class=\"k\">Tags on jank frames</p><ul>")
            tags.forEach {
                append("<li><code>").append(escape(it.key)).append("</code>: ")
                append(it.value).append(if (it.value == 1) " frame" else " frames").append("</li>")
            }
            append("</ul>")
        }
        append("</article>\n")
    }

    private class ScreenStats(val name: String) {
        var visits = 0
        var frameCount = 0L
        var jankCount = 0L
        var frozenFrameCount = 0L
        var maxFrameTimeMs = 0.0

        /** Jank frames that had stage times; the denominator of [jankByStage]. */
        var stagedJankCount = 0
        val jankByStage = LinkedHashMap<String, Int>()
        val jankByTag = LinkedHashMap<String, Int>()
    }

    private companion object {
        const val TOP_ITEMS = 3
        const val RING_RADIUS = 50.0

        fun MutableMap<String, Int>.increment(key: String) {
            this[key] = (this[key] ?: 0) + 1
        }

        fun smoothShare(frames: Long, jank: Long): Double? =
            if (frames == 0L) null else 1.0 - jank.toDouble() / frames

        fun jankShare(frames: Long, jank: Long): Double =
            if (frames == 0L) 0.0 else jank.toDouble() / frames

        /** No colour when nothing was drawn, as there is no share to judge. */
        fun tone(frames: Long, jank: Long): String =
            if (frames == 0L) "empty" else toneOf(jankShare(frames, jank))

        /** Matches the legend: under 1% jank is green, up to 5% amber, more red. */
        fun toneOf(jankShare: Double): String = when {
            jankShare < 0.01 -> "good"
            jankShare <= 0.05 -> "warn"
            else -> "bad"
        }

        fun percent(share: Double?): String =
            if (share == null) "&ndash;" else "%.1f%%".format(Locale.US, share * 100)

        fun formatCount(count: Long): String = "%,d".format(Locale.US, count)

        fun ring(smooth: Double?): String {
            val circumference = 2 * Math.PI * RING_RADIUS
            val filled = circumference * (smooth ?: 0.0)
            val toneClass = if (smooth == null) "info" else toneOf(1.0 - smooth)
            return "<svg class=\"ring\" viewBox=\"0 0 120 120\" role=\"img\" aria-label=\"" +
                "${percent(smooth)} smooth frames\">" +
                "<circle cx=\"60\" cy=\"60\" r=\"50\" class=\"track\"/>" +
                "<circle cx=\"60\" cy=\"60\" r=\"50\" class=\"arc $toneClass\" " +
                "stroke-dasharray=\"${"%.1f".format(Locale.US, filled)} " +
                "${"%.1f".format(Locale.US, circumference)}\" transform=\"rotate(-90 60 60)\"/>" +
                "<text x=\"60\" y=\"62\" class=\"big\">${percent(smooth)}</text>" +
                "<text x=\"60\" y=\"80\" class=\"small\">smooth frames</text></svg>"
        }

        fun chip(text: String): String = "<span class=\"chip\">${escape(text)}</span>"

        fun total(label: String, value: String, tone: String): String =
            "<div class=\"total $tone\"><b>$value</b><span>$label</span></div>"

        fun pill(text: String, tone: String): String = "<span class=\"pill $tone\">$text</span>"

        fun escape(text: String): String = buildString(text.length) {
            text.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }

        val PAGE_START = """
            |<!DOCTYPE html>
            |<html lang="en">
            |<head>
            |<meta charset="utf-8">
            |<meta name="viewport" content="width=device-width, initial-scale=1">
            |<title>FramePulse frame report</title>
            |<style>
            |:root{--bg:#f6f6f4;--card:#fff;--text:#1d1d1b;--muted:#6b6b66;--line:#e3e2dc;
            |--good:#2f7d4f;--good-bg:#e4f2e9;--warn:#9a6200;--warn-bg:#fbf0d9;--bad:#b3261e;
            |--bad-bg:#fbe4e2;--info:#2a5fa8;--info-bg:#e3edf9;--track:#e6e5df}
            |@media (prefers-color-scheme: dark){:root{--bg:#1c1c1e;--card:#2a2a2d;--text:#ececea;
            |--muted:#a3a39e;--line:#3a3a3e;--good:#7fc79a;--good-bg:#1f3a2a;--warn:#e6b65c;
            |--warn-bg:#3d3220;--bad:#f08b82;--bad-bg:#45231f;--info:#8db4ec;--info-bg:#1f2d44;
            |--track:#3a3a3e}}
            |*{box-sizing:border-box}
            |body{margin:0;background:var(--bg);color:var(--text);
            |font:15px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
            |header{display:flex;justify-content:space-between;align-items:center;gap:12px;
            |flex-wrap:wrap;padding:14px 16px;border-bottom:1px solid var(--line)}
            |.brand{font-weight:600}.chips{display:flex;gap:6px;flex-wrap:wrap}
            |.chip{font-size:12px;color:var(--muted);border:1px solid var(--line);border-radius:999px;
            |padding:2px 10px}
            |main{max-width:1080px;margin:0 auto;padding:24px 16px 48px}
            |h1{font-size:30px;margin:0}h2{font-size:20px;margin:28px 0 12px}
            |h3{font-size:16px;margin:0;overflow-wrap:anywhere}
            |.sub{color:var(--muted);margin:4px 0 16px}
            |.hero{display:flex;justify-content:space-between;align-items:center;gap:24px;flex-wrap:wrap}
            |.totals{display:flex;gap:10px;flex-wrap:wrap}
            |.total{border-radius:10px;padding:10px 16px;text-align:center;min-width:96px}
            |.total b{display:block;font-size:22px}.total span{font-size:12px}
            |.good{color:var(--good)}.warn{color:var(--warn)}.bad{color:var(--bad)}.info{color:var(--info)}
            |.total.warn,.pill.warn{background:var(--warn-bg)}.total.bad,.pill.bad{background:var(--bad-bg)}
            |.total.info{background:var(--info-bg)}
            |.ring{width:140px;height:140px}.ring circle{fill:none;stroke-width:9}
            |.ring .track{stroke:var(--track)}.ring .arc{stroke-linecap:round}
            |.arc.good{stroke:var(--good)}.arc.warn{stroke:var(--warn)}.arc.bad{stroke:var(--bad)}
            |.arc.info{stroke:var(--info)}
            |.ring text{text-anchor:middle;fill:var(--text)}.ring .big{font-size:22px;font-weight:600}
            |.ring .small{font-size:10px;fill:var(--muted)}
            |.legend{font-size:13px;color:var(--muted);margin:16px 0 0}
            |.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,300px),1fr));gap:14px}
            |.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px 18px}
            |.card-head{display:flex;justify-content:space-between;align-items:baseline;gap:8px}
            |.pct{font-weight:600;font-size:18px}
            |.bar{height:5px;border-radius:3px;background:var(--track);margin:10px 0 12px}
            |.bar div{height:5px;border-radius:3px}.bar .good{background:var(--good)}
            |.bar .warn{background:var(--warn)}.bar .bad{background:var(--bad)}
            |.pills{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:12px}
            |.pill{font-size:12px;border-radius:6px;padding:1px 8px;background:var(--track)}
            |.k{font-size:12px;color:var(--muted);margin:10px 0 4px}
            |ul{margin:0;padding-left:18px}li{font-size:14px}.none{color:var(--muted)}
            |code{font-size:13px}
            |</style>
            |</head>
            |<body>
            |
        """.trimMargin()
    }
}

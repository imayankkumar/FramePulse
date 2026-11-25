package com.mayank.framepulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FramePulseConfigTest {

    @Test
    fun defaultsAssumeSixtyHertzAndOnePointFiveMultiplier() {
        val config = FramePulseConfig.DEFAULT

        assertEquals(1_000L, config.windowDurationMs)
        assertEquals(16.67f, config.expectedFramePeriodMs, 0f)
        assertEquals(1.5f, config.jankThresholdMultiplier, 0f)
        assertEquals(250L, config.reportIntervalMs)
        assertEquals(25.005f, config.jankThresholdMs, 0.0001f)
    }

    @Test
    fun aNewBuilderStartsWithTheDefaults() {
        assertEquals(FramePulseConfig.DEFAULT, FramePulseConfig.Builder().build())
    }

    @Test
    fun theBuilderSetsEveryValue() {
        val config = FramePulseConfig.Builder()
            .windowDurationMs(2_000)
            .expectedFramePeriodMs(20f)
            .jankThresholdMultiplier(2f)
            .reportIntervalMs(0)
            .build()

        assertEquals(2_000L, config.windowDurationMs)
        assertEquals(20f, config.expectedFramePeriodMs, 0f)
        assertEquals(2f, config.jankThresholdMultiplier, 0f)
        assertEquals(0L, config.reportIntervalMs)
        assertEquals(40f, config.jankThresholdMs, 0f)
    }

    @Test
    fun refreshRateDerivesTheFramePeriod() {
        val config = FramePulseConfig.Builder().refreshRate(120f).build()

        assertEquals(8.3333f, config.expectedFramePeriodMs, 0.0001f)
        assertEquals(12.5f, config.jankThresholdMs, 0.0001f)
    }

    @Test
    fun newBuilderKeepsTheOtherValues() {
        val config = FramePulseConfig.Builder().windowDurationMs(3_000).reportIntervalMs(0).build()

        val changed = config.newBuilder().refreshRate(120f).build()

        assertEquals(3_000L, changed.windowDurationMs)
        assertEquals(0L, changed.reportIntervalMs)
        assertEquals(8.3333f, changed.expectedFramePeriodMs, 0.0001f)
        assertEquals(16.67f, config.expectedFramePeriodMs, 0f)
    }

    @Test
    fun configsCompareByValue() {
        val config = FramePulseConfig.Builder().reportIntervalMs(0).build()

        assertEquals(config, FramePulseConfig.Builder().reportIntervalMs(0).build())
        assertEquals(config.hashCode(), config.newBuilder().build().hashCode())
        assertNotEquals(config, FramePulseConfig.DEFAULT)
        assertEquals(
            "FramePulseConfig(windowDurationMs=1000, expectedFramePeriodMs=16.67, " +
                "jankThresholdMultiplier=1.5, reportIntervalMs=0)",
            config.toString(),
        )
    }

    @Test
    fun invalidValuesAreRejected() {
        val builder = FramePulseConfig.Builder()

        assertThrows(IllegalArgumentException::class.java) { builder.windowDurationMs(0) }
        assertThrows(IllegalArgumentException::class.java) { builder.expectedFramePeriodMs(0f) }
        assertThrows(IllegalArgumentException::class.java) {
            builder.expectedFramePeriodMs(Float.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) { builder.jankThresholdMultiplier(0.9f) }
        assertThrows(IllegalArgumentException::class.java) { builder.reportIntervalMs(-1) }
        assertThrows(IllegalArgumentException::class.java) { builder.refreshRate(0f) }
        assertThrows(IllegalArgumentException::class.java) { builder.refreshRate(Float.NaN) }
    }

    @Test
    fun aRejectedValueLeavesTheBuilderUnchanged() {
        val builder = FramePulseConfig.Builder().windowDurationMs(2_000)

        assertThrows(IllegalArgumentException::class.java) { builder.windowDurationMs(-5) }

        assertEquals(2_000L, builder.build().windowDurationMs)
    }
}

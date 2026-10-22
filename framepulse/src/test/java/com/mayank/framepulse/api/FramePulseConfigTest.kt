package com.mayank.framepulse.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FramePulseConfigTest {

    @Test
    fun defaultsAssumeSixtyHertzAndOnePointFiveMultiplier() {
        val config = FramePulseConfig.DEFAULT

        assertEquals(1_000L, config.windowDurationMs)
        assertEquals(16.67f, config.expectedFramePeriodMs, 0f)
        assertEquals(1.5f, config.jankThresholdMultiplier, 0f)
        assertEquals(25.005f, config.jankThresholdMs, 0.0001f)
    }

    @Test
    fun withRefreshRateDerivesTheFramePeriod() {
        val config = FramePulseConfig().withRefreshRate(120f)

        assertEquals(8.3333f, config.expectedFramePeriodMs, 0.0001f)
        assertEquals(12.5f, config.jankThresholdMs, 0.0001f)
    }

    @Test
    fun invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { FramePulseConfig(windowDurationMs = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            FramePulseConfig(expectedFramePeriodMs = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FramePulseConfig(jankThresholdMultiplier = 0.9f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FramePulseConfig(reportIntervalMs = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FramePulseConfig().withRefreshRate(0f)
        }
    }
}

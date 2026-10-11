package dev.livepipe

import org.junit.Assert.assertEquals
import org.junit.Test

class ChargingTest {
    @Test fun nothingEasesOffTheCharger() {
        for (m in 0..2) for (w in listOf(false, true)) assertEquals(1f, Charging.bitrateFactor(m, plugged = false, warm = w))
    }

    @Test fun modesEaseInOrder() {
        assertEquals(1f, Charging.bitrateFactor(Charging.NORMAL, true, true))
        assertEquals(1f, Charging.bitrateFactor(Charging.MEDIUM, true, false))
        assertEquals(0.6f, Charging.bitrateFactor(Charging.MEDIUM, true, true))
        assertEquals(0.6f, Charging.bitrateFactor(Charging.FAST, true, false))
        assertEquals(0.35f, Charging.bitrateFactor(Charging.FAST, true, true))
    }

    @Test fun currentUnitsAreNormalisedToMilliamps() {
        assertEquals(2450, Charging.milliamps(2_450_000))   // spec: µA
        assertEquals(247, Charging.milliamps(247))          // OnePlus/OPLUS: mA
        assertEquals(-900, Charging.milliamps(-900_000))
    }
}

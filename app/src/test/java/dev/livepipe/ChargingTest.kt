package dev.livepipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargingTest {
    @Test fun targetsFollowCRate() {
        assertEquals(2250, Charging.targetMa(Charging.NORMAL, 4500))   // 0.5C
        assertEquals(4500, Charging.targetMa(Charging.MEDIUM, 4500))   // 1C
        assertNull(Charging.targetMa(Charging.FAST, 4500))             // stock maximum
        assertEquals(500, Charging.targetMa(Charging.NORMAL, 800))     // floor
    }

    @Test fun capacityFromChargeCounterInEitherUnit() {
        assertEquals(4500, Charging.capacityMah(3_600_000, 80))        // spec: µAh
        assertEquals(4500, Charging.capacityMah(3600, 80))             // firmware reporting mAh
        assertEquals(4000, Charging.capacityMah(0, 80))                // unknown: typical phone
    }

    @Test fun currentUnitsAreNormalisedToMilliamps() {
        assertEquals(2450, Charging.milliamps(2_450_000))   // spec: µA
        assertEquals(247, Charging.milliamps(247))          // OnePlus/OPLUS: mA
        assertEquals(-900, Charging.milliamps(-900_000))
    }
}

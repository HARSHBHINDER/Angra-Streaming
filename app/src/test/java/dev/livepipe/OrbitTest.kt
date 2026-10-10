package dev.livepipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class OrbitTest {
    @Test fun orbsSitOnTheArcAndOpenTowardTheMiddle() {
        // Bubble parked on the left edge, vertically centred: the middle orb must point straight right.
        val c = BubbleService.orbitCentres(28f, 1200f, 1080f, 2400f, 5, 300f, 150.0)
        c.forEach { (x, y) -> assertEquals(300f, hypot(x - 28f, y - 1200f), 0.5f) }
        assertEquals(328f, c[2].first, 0.5f); assertEquals(1200f, c[2].second, 0.5f)
        assertTrue(c.all { it.first > 28f })        // nothing fans off the left edge
    }

    @Test fun slotsFillUpAndAssignSwaps() {
        // Bad/missing ids fall back to defaults, never duplicates, always 5.
        val s = BubbleAction.slots("shot,nope,shot,mark")
        assertEquals(listOf(BubbleAction.SCREENSHOT, BubbleAction.HIGHLIGHT, BubbleAction.MIC, BubbleAction.BREAK, BubbleAction.RECORD), s)
        // Putting an already-used control in another slot swaps the two.
        val t = BubbleAction.assign(s, 4, BubbleAction.SCREENSHOT)
        assertEquals(BubbleAction.RECORD, t[0]); assertEquals(BubbleAction.SCREENSHOT, t[4])
        assertEquals(BubbleAction.SLOTS, t.distinct().size)
    }

    @Test fun rightEdgeBubbleFansLeft() {
        val c = BubbleService.orbitCentres(1052f, 1200f, 1080f, 2400f, 5, 300f, 150.0)
        assertTrue(c.all { it.first < 1052f })
    }
}

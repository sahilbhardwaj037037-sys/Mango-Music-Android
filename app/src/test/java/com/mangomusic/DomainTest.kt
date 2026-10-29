package com.mangomusic

import com.mangomusic.domain.*
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    @Test fun searchHandlesWhitespaceCaseAndDuplicates() {
        assertEquals(listOf(Catalog.samples.first()), Catalog.search(Catalog.samples + Catalog.samples.first(), "  MANGO DAWN  "))
        assertEquals(3, Catalog.search(Catalog.samples, "studio").size)
        assertTrue(Catalog.search(Catalog.samples, "missing").isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun searchRejectsControlCharacters() { Catalog.search(Catalog.samples, "bad\nquery") }
    @Test(expected = IllegalArgumentException::class) fun searchBoundsQuery() { Catalog.search(Catalog.samples, "x".repeat(201)) }
    @Test fun pauseFreezesAndResumeKeepsDiscAngle() {
        val moving = DiscMotion.advance(123f, 16f, true)
        assertTrue(moving > 123f)
        val paused = DiscMotion.advance(moving, 5000f, false)
        assertEquals(moving, paused)
        assertEquals(moving + .288f, DiscMotion.advance(paused, 16f, true), .001f)
    }
    @Test fun discHandlesWrapAndLongFrameWithoutJump() {
        assertTrue(DiscMotion.advance(359.9f, 16f, true) < 1f)
        assertEquals(11.152f, DiscMotion.advance(10f, 5000f, true), .001f)
    }
    @Test fun timeFormattingHandlesUnknownAndNegative() {
        assertEquals("0:00", timeLabel(-1)); assertEquals("3:27", timeLabel(207000))
    }
}

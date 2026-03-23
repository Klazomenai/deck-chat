package dev.klazomenai.deckchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewRegistryTest {

    @Test
    fun `lookup maren returns correct voice dir and display name`() {
        val crew = CrewRegistry.lookup("maren")
        assertEquals("maren", crew.name)
        assertEquals("Maren", crew.displayName)
        assertEquals("vits-piper-en_GB-cori-high", crew.voiceDir)
        assertEquals("dispatch", crew.defaultVerbosity)
    }

    @Test
    fun `lookup crest returns correct voice dir and display name`() {
        val crew = CrewRegistry.lookup("crest")
        assertEquals("crest", crew.name)
        assertEquals("Crest", crew.displayName)
        assertEquals("vits-piper-en_US-lessac-high", crew.voiceDir)
    }

    @Test
    fun `lookup unknown falls back to maren`() {
        val crew = CrewRegistry.lookup("unknown")
        assertEquals("maren", crew.name)
        assertEquals("Maren", crew.displayName)
    }

    @Test
    fun `lookup is case-insensitive`() {
        assertEquals("maren", CrewRegistry.lookup("MAREN").name)
        assertEquals("crest", CrewRegistry.lookup("Crest").name)
        assertEquals("maren", CrewRegistry.lookup("MaReN").name)
    }

    @Test
    fun `isKnown returns true for registered crew`() {
        assertTrue(CrewRegistry.isKnown("maren"))
        assertTrue(CrewRegistry.isKnown("crest"))
    }

    @Test
    fun `isKnown returns false for unknown crew`() {
        assertFalse(CrewRegistry.isKnown("unknown"))
        assertFalse(CrewRegistry.isKnown(""))
    }

    @Test
    fun `allNames contains maren and crest`() {
        val names = CrewRegistry.allNames()
        assertTrue(names.contains("maren"))
        assertTrue(names.contains("crest"))
        assertEquals(2, names.size)
    }
}

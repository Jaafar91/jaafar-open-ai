package com.jaafar.remoteconfig.fontcreator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontNameUniquenessTest {
    @Test
    fun `normalized key collapses equivalent names`() {
        val first = normalizedFontStorageKey("My Font")
        val second = normalizedFontStorageKey("my---font")
        val third = normalizedFontStorageKey("my_font")

        assertEquals("my-font", first)
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `normalized key is blank for symbols only`() {
        assertTrue(normalizedFontStorageKey("---___***").isBlank())
    }
}

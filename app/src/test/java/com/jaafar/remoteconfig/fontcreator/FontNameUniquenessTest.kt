package com.jaafar.remoteconfig.fontcreator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `normalized key accepts non-Latin script names`() {
        // A name written entirely in a non-Latin script (e.g. Arabic, one of this app's
        // supported LanguageScripts) must not normalize to blank -- that silently blocks
        // createProject()/renameActiveProject() from ever accepting it.
        assertFalse(normalizedFontStorageKey("خط يدي").isBlank())
        assertEquals(normalizedFontStorageKey("خط يدي"), normalizedFontStorageKey("خط   يدي"))
    }

    @Test
    fun `duplicate project name matches case and normalized variants`() {
        val existingNames = listOf("My Font")

        assertTrue(isDuplicateFontProjectName(existingNames, "my font"))
        assertTrue(isDuplicateFontProjectName(existingNames, "my---font"))
        assertTrue(isDuplicateFontProjectName(existingNames, "my_font"))
    }

    @Test
    fun `duplicate project name ignores blank and symbols-only input`() {
        val existingNames = listOf("My Font")

        assertFalse(isDuplicateFontProjectName(existingNames, ""))
        assertFalse(isDuplicateFontProjectName(existingNames, "   "))
        assertFalse(isDuplicateFontProjectName(existingNames, "---___***"))
    }

    @Test
    fun `created and imported names conflict across normalized variants`() {
        assertTrue(fontNamesConflict("Test Font", "test-font"))
        assertTrue(fontNamesConflict("test_font", "TEST FONT"))
        assertFalse(fontNamesConflict("Test Font", "Another Font"))
    }
}

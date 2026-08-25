package com.jaafar.remoteconfig.fontcreator

import org.junit.Assert.assertEquals
import org.junit.Test

class PhraseModeTest {
    @Test
    fun phraseCharactersAreUniqueSupportedAndOrdered() {
        val supported = setOf('H'.code, 'e'.code, 'l'.code, 'o'.code, '!'.code)

        val result = applicablePhraseCodePoints("Hello, Hello!", supported)

        assertEquals(listOf('H'.code, 'e'.code, 'l'.code, 'o'.code, '!'.code), result)
    }

    @Test
    fun phraseCharactersIgnoreSpacesAndUnsupportedCharacters() {
        val supported = setOf('A'.code, '1'.code)

        val result = applicablePhraseCodePoints(" A🙂1 ", supported)

        assertEquals(listOf('A'.code, '1'.code), result)
    }
}

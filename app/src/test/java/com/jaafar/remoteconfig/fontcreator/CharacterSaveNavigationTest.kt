package com.jaafar.remoteconfig.fontcreator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterSaveNavigationTest {
    private val order = listOf('A'.code, 'B'.code, 'C'.code)

    @Test
    fun `completed font advances to the next character`() {
        assertEquals('B'.code, nextCharacterAfterSave(order, 'A'.code, order.toSet()))
    }

    @Test
    fun `completed font wraps after the final character`() {
        assertEquals('A'.code, nextCharacterAfterSave(order, 'C'.code, order.toSet()))
    }

    @Test
    fun `incomplete font continues to the next missing character`() {
        assertEquals('C'.code, nextCharacterAfterSave(order, 'A'.code, setOf('A'.code, 'B'.code)))
    }

    @Test
    fun `empty character order closes the editor`() {
        assertNull(nextCharacterAfterSave(emptyList(), 'A'.code, emptySet()))
    }
}

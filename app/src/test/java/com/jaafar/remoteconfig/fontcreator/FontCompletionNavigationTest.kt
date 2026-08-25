package com.jaafar.remoteconfig.fontcreator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontCompletionNavigationTest {
    private val order = listOf('A'.code, 'B'.code, 'C'.code)

    @Test
    fun `saving the final missing character finishes creation`() {
        assertNull(characterAfterSave(order, 'C'.code, order.toSet(), wasExisting = false))
    }

    @Test
    fun `editing a completed font advances normally`() {
        assertEquals('B'.code, characterAfterSave(order, 'A'.code, order.toSet(), wasExisting = true))
    }

    @Test
    fun `editing the final character wraps to the first`() {
        assertEquals('A'.code, characterAfterSave(order, 'C'.code, order.toSet(), wasExisting = true))
    }

    @Test
    fun `creation continues to another missing character`() {
        assertEquals('C'.code, characterAfterSave(order, 'A'.code, setOf('A'.code, 'B'.code), wasExisting = false))
    }
}

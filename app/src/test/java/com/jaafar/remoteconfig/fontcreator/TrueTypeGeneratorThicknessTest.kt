package com.jaafar.remoteconfig.fontcreator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrueTypeGeneratorThicknessTest {
    @Test
    fun defaultThicknessPreservesExistingOutlineRadius() {
        assertEquals(55f, glyphStrokeRadius(8f), 0f)
    }

    @Test
    fun outlineRadiusScalesWithSavedThickness() {
        assertTrue(glyphStrokeRadius(4f) < glyphStrokeRadius(8f))
        assertTrue(glyphStrokeRadius(16f) > glyphStrokeRadius(8f))
    }

    @Test
    fun outlineRadiusIsClampedToSafeFontBounds() {
        assertEquals(14f, glyphStrokeRadius(0f), 0f)
        assertEquals(165f, glyphStrokeRadius(100f), 0f)
    }
}

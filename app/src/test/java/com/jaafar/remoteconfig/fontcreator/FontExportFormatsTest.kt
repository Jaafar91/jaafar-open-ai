package com.jaafar.remoteconfig.fontcreator

import java.nio.ByteBuffer
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * There's no Android/JVM toolchain in the sandbox this was authored in to run this directly, so
 * the WOFF1 algorithm was first prototyped in Python and round-tripped through fontTools against
 * a synthetic sfnt. This test exercises the real Kotlin implementation instead, against a real
 * sfnt from TrueTypeGenerator (a pure-Kotlin class with no Android dependency, so it runs fine
 * under plain JUnit) -- decoding the WOFF back by hand and checking every table survives exactly.
 */
class FontExportFormatsTest {
    private fun realSfnt(): ByteArray {
        val drawings = (65..70).map { codePoint ->
            GlyphDrawing(
                codePoint = codePoint,
                strokes = listOf(GlyphStroke(listOf(GlyphPoint(10f, 10f), GlyphPoint(50f, 50f), GlyphPoint(90f, 10f)))),
                canvasWidth = 120f,
                canvasHeight = 120f,
                strokeWidth = 8f,
            )
        }
        return TrueTypeGenerator().generate(drawings, fontName = "Woff Test")
    }

    @Test
    fun woffEncodesEveryTableAndRoundTripsExactly() {
        val sfnt = realSfnt()
        val woff = encodeAsWoff(sfnt)

        assertEquals("wOFF", String(woff, 0, 4, Charsets.US_ASCII))

        val sourceBuffer = ByteBuffer.wrap(sfnt)
        val numTables = sourceBuffer.getShort(4).toInt() and 0xFFFF
        val sourceTables = (0 until numTables).associate { i ->
            val recordOffset = 12 + i * 16
            val tag = String(sfnt, recordOffset, 4, Charsets.US_ASCII)
            val offset = sourceBuffer.getInt(recordOffset + 8)
            val length = sourceBuffer.getInt(recordOffset + 12)
            tag to sfnt.copyOfRange(offset, offset + length)
        }

        val woffBuffer = ByteBuffer.wrap(woff)
        assertEquals("WOFF header numTables should match the source", numTables, woffBuffer.getShort(12).toInt() and 0xFFFF)
        assertEquals("WOFF header length field must equal the actual file size", woff.size, woffBuffer.getInt(8))

        val decodedTables = (0 until numTables).associate { i ->
            val recordOffset = 44 + i * 20
            val tag = String(woff, recordOffset, 4, Charsets.US_ASCII)
            val tableOffset = woffBuffer.getInt(recordOffset + 4)
            val compLength = woffBuffer.getInt(recordOffset + 8)
            val origLength = woffBuffer.getInt(recordOffset + 12)
            val compData = woff.copyOfRange(tableOffset, tableOffset + compLength)
            val raw = if (compLength == origLength) {
                compData
            } else {
                val inflater = Inflater()
                inflater.setInput(compData)
                val out = ByteArray(origLength)
                var total = 0
                while (total < origLength && !inflater.finished()) {
                    total += inflater.inflate(out, total, origLength - total)
                }
                inflater.end()
                out
            }
            tag to raw
        }

        assertEquals(sourceTables.keys, decodedTables.keys)
        sourceTables.forEach { (tag, bytes) ->
            assertTrue("table \"$tag\" should round-trip exactly", bytes.contentEquals(decodedTables.getValue(tag)))
        }
    }

    @Test
    fun exportFormatsHaveDistinctExtensionsAndMimeTypes() {
        val formats = FontExportFormat.entries
        assertEquals(formats.map { it.extension }.toSet().size, formats.size)
        assertEquals(formats.map { it.mimeType }.toSet().size, formats.size)
    }
}

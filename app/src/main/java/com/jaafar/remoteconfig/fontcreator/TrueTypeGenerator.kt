package com.jaafar.remoteconfig.fontcreator

import java.util.SortedMap
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Small, dependency-free TrueType writer for the editor's Basic Latin outlines. */
class TrueTypeGenerator {
    fun generate(drawings: Collection<GlyphDrawing>, spaceWidthMm: Float = 3f, letterSpacingMm: Float = 0f, fontName: String = "My Hand Font"): ByteArray {
        val space = GlyphDrawing(32, emptyList(), 1f, 1f)
        val ordered = (drawings.filter { it.codePoint in 33..126 } + space).sortedBy { it.codePoint }
        val glyphs = listOf(notdefGlyph()) + ordered.map(::drawingGlyph)
        val glyf = Bytes()
        val offsets = mutableListOf(0)
        glyphs.forEach {
            glyf.bytes(it)
            glyf.pad4()
            offsets += glyf.size
        }

        val tables = sortedMapOf<String, ByteArray>()
        tables["OS/2"] = os2(ordered)
        tables["cmap"] = cmap(ordered)
        tables["glyf"] = glyf.toByteArray()
        tables["head"] = head()
        tables["hhea"] = hhea(glyphs.size)
        tables["hmtx"] = hmtx(ordered, spaceWidthMm, letterSpacingMm)
        tables["loca"] = Bytes().apply { offsets.forEach(::u32) }.toByteArray()
        tables["maxp"] = maxp(glyphs.size, glyphs.maxOfOrNull(::contourCount) ?: 0)
        tables["name"] = name(fontName)
        tables["post"] = post()
        return sfnt(tables)
    }

    private data class P(val x: Int, val y: Int)

    private fun drawingGlyph(drawing: GlyphDrawing): ByteArray {
        val width = drawing.canvasWidth.coerceAtLeast(1f)
        val height = drawing.canvasHeight.coerceAtLeast(1f)
        val baseline = height * .78f
        val scale = 1500f / (height * .68f)
        fun convert(p: GlyphPoint) = P(
            (p.x / width * 1600 + 180).roundToInt().coerceIn(-32768, 32767),
            ((baseline - p.y) * scale).roundToInt().coerceIn(-450, 1900),
        )
        val contours = drawing.strokes.mapNotNull { stroke ->
            val source = stroke.points.map(::convert).distinct()
            if (source.size < 2) return@mapNotNull null
            val radius = 55.0
            val left = ArrayList<P>()
            val right = ArrayList<P>()
            source.forEachIndexed { index, point ->
                val before = source[(index - 1).coerceAtLeast(0)]
                val after = source[(index + 1).coerceAtMost(source.lastIndex)]
                val dx = (after.x - before.x).toDouble()
                val dy = (after.y - before.y).toDouble()
                val length = hypot(dx, dy).coerceAtLeast(1.0)
                val nx = -dy / length * radius
                val ny = dx / length * radius
                left += P((point.x + nx).roundToInt(), (point.y + ny).roundToInt())
                right += P((point.x - nx).roundToInt(), (point.y - ny).roundToInt())
            }
            ensureClockwise(left + right.asReversed())
        }
        return simpleGlyph(contours)
    }

    private fun notdefGlyph(): ByteArray = simpleGlyph(listOf(ensureClockwise(listOf(
        P(150, -100), P(150, 1500), P(1350, 1500), P(1350, -100),
    ))))

    private fun ensureClockwise(points: List<P>): List<P> {
        val twiceArea = points.indices.sumOf { i ->
            val a = points[i]
            val b = points[(i + 1) % points.size]
            a.x.toLong() * b.y - b.x.toLong() * a.y
        }
        return if (twiceArea > 0) points.asReversed() else points
    }

    private fun simpleGlyph(contours: List<List<P>>): ByteArray {
        if (contours.isEmpty()) return Bytes().apply { s16(0); repeat(4) { s16(0) } }.toByteArray()
        val all = contours.flatten()
        return Bytes().apply {
            s16(contours.size)
            s16(all.minOf { it.x }); s16(all.minOf { it.y })
            s16(all.maxOf { it.x }); s16(all.maxOf { it.y })
            var end = -1
            contours.forEach { end += it.size; u16(end) }
            u16(0) // no hinting instructions
            repeat(all.size) { u8(0x01) } // on-curve, signed 16-bit deltas
            var last = 0
            all.forEach { s16(it.x - last); last = it.x }
            last = 0
            all.forEach { s16(it.y - last); last = it.y }
        }.toByteArray()
    }

    private fun contourCount(glyph: ByteArray) = ((glyph[0].toInt() and 255) shl 8) or (glyph[1].toInt() and 255)

    private fun head() = Bytes().apply {
        u32(0x00010000); u32(0x00010000); u32(0); u32(0x5F0F3CF5)
        u16(0x000B); u16(UNITS_PER_EM); repeat(16) { u8(0) }
        s16(0); s16(-450); s16(2048); s16(1900)
        u16(0); u16(8); s16(2); s16(1); s16(0)
    }.toByteArray()

    private fun hhea(numberOfMetrics: Int) = Bytes().apply {
        u32(0x00010000); s16(1550); s16(-450); s16(0); u16(2048)
        s16(0); s16(0); s16(2048); s16(1); s16(0); s16(0)
        repeat(4) { s16(0) }; s16(0); u16(numberOfMetrics)
    }.toByteArray()

    private fun maxp(glyphs: Int, maxContours: Int) = Bytes().apply {
        u32(0x00010000); u16(glyphs); u16(256); u16(maxContours)
        u16(0); u16(0); u16(2); u16(0); u16(0); u16(0); u16(0)
        u16(0); u16(0); u16(0); u16(0)
    }.toByteArray()

    private fun hmtx(drawings: List<GlyphDrawing>, spaceWidthMm: Float, letterSpacingMm: Float) = Bytes().apply {
        u16(2048); s16(0) // .notdef
        drawings.forEach {
            // At the font's nominal 12 pt size, 1 mm is approximately 484 font units.
            val advance = if (it.codePoint == 32) (spaceWidthMm * 484f).roundToInt().coerceIn(100, 4096)
                else (2048 + letterSpacingMm * 484f).roundToInt().coerceIn(500, 4096)
            u16(advance); s16(0)
        }
    }.toByteArray()

    private fun cmap(drawings: List<GlyphDrawing>): ByteArray {
        val mappings = drawings.mapIndexed { index, glyph -> glyph.codePoint to index + 1 }
        val segCount = mappings.size + 1
        val power = Integer.highestOneBit(segCount)
        val sub = Bytes().apply {
            u16(4); u16(16 + segCount * 8); u16(0); u16(segCount * 2)
            u16(power * 2); u16(Integer.numberOfTrailingZeros(power)); u16(segCount * 2 - power * 2)
            mappings.forEach { u16(it.first) }; u16(0xFFFF); u16(0)
            mappings.forEach { u16(it.first) }; u16(0xFFFF)
            mappings.forEach { (code, glyph) -> u16((glyph - code) and 0xFFFF) }; u16(1)
            repeat(segCount) { u16(0) }
        }.toByteArray()
        return Bytes().apply { u16(0); u16(1); u16(3); u16(1); u32(12); bytes(sub) }.toByteArray()
    }

    private fun os2(drawings: List<GlyphDrawing>) = Bytes().apply {
        u16(0); s16(1024); u16(400); u16(5); u16(0)
        repeat(11) { s16(0) }; repeat(10) { u8(0) }
        repeat(4) { u32(0) }; bytes("JFNT".toByteArray())
        u16(0x40); u16(drawings.minOfOrNull { it.codePoint } ?: 32); u16(drawings.maxOfOrNull { it.codePoint } ?: 126)
        s16(1550); s16(-450); s16(0); u16(1550); u16(450)
    }.toByteArray()

    private fun name(fontName: String): ByteArray {
        val safeName = fontName.filter { it.isLetterOrDigit() }.ifBlank { "MyHandFont" }.take(31)
        val displayName = fontName.trim().ifBlank { "My Hand Font" }.take(63)
        val values = listOf(1 to displayName, 2 to "Regular", 4 to "$displayName Regular", 6 to "$safeName-Regular")
        val strings = Bytes()
        val records = Bytes()
        values.forEach { (id, value) ->
            val encoded = value.flatMap { listOf(0.toByte(), it.code.toByte()) }.toByteArray()
            records.u16(3); records.u16(1); records.u16(0x0409); records.u16(id)
            records.u16(encoded.size); records.u16(strings.size); strings.bytes(encoded)
        }
        return Bytes().apply { u16(0); u16(values.size); u16(6 + records.size); bytes(records.toByteArray()); bytes(strings.toByteArray()) }.toByteArray()
    }

    private fun post() = Bytes().apply {
        u32(0x00030000); u32(0); s16(0); s16(0); u32(0); repeat(4) { u32(0) }
    }.toByteArray()

    private fun sfnt(tables: SortedMap<String, ByteArray>): ByteArray {
        val count = tables.size
        val power = Integer.highestOneBit(count)
        val headerSize = 12 + count * 16
        var offset = headerSize
        val records = tables.map { (tag, data) ->
            val record = Table(tag, checksum(data), offset, data)
            offset += (data.size + 3) and -4
            record
        }
        val out = Bytes().apply {
            u32(0x00010000); u16(count); u16(power * 16)
            u16(Integer.numberOfTrailingZeros(power)); u16(count * 16 - power * 16)
            records.forEach { record ->
                bytes(record.tag.toByteArray()); u32(record.checksum); u32(record.offset); u32(record.data.size)
            }
            records.forEach { bytes(it.data); pad4() }
        }.toByteArray()
        val adjustment = (0xB1B0AFBAL - checksum(out)) and 0xFFFFFFFFL
        val headOffset = records.first { it.tag == "head" }.offset + 8
        for (i in 0..3) out[headOffset + i] = (adjustment shr (24 - i * 8)).toByte()
        return out
    }

    private data class Table(val tag: String, val checksum: Long, val offset: Int, val data: ByteArray)

    private fun checksum(data: ByteArray): Long {
        var sum = 0L
        for (i in data.indices step 4) {
            var word = 0L
            repeat(4) { j -> word = word shl 8 or (if (i + j < data.size) (data[i + j].toInt() and 255).toLong() else 0) }
            sum = (sum + word) and 0xFFFFFFFFL
        }
        return sum
    }

    private class Bytes {
        private val data = ArrayList<Byte>()
        val size get() = data.size
        fun u8(value: Int) { data += value.toByte() }
        fun u16(value: Int) { u8(value ushr 8); u8(value) }
        fun s16(value: Int) = u16(value and 0xFFFF)
        fun u32(value: Int) = u32(value.toLong())
        fun u32(value: Long) { u8((value ushr 24).toInt()); u8((value ushr 16).toInt()); u8((value ushr 8).toInt()); u8(value.toInt()) }
        fun bytes(value: ByteArray) { value.forEach { data += it } }
        fun pad4() { while (size % 4 != 0) u8(0) }
        fun toByteArray() = data.toByteArray()
    }
}

package com.jaafar.remoteconfig.fontcreator

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.Deflater

/**
 * The formats a completed font can be exported as. OTF here is the exact same TrueType ('glyf')
 * outline data [TrueTypeGenerator] already produces, repackaged with an .otf extension/MIME type
 * rather than re-authored as a PostScript/CFF font -- a TrueType-flavored OTF is valid per the
 * OpenType spec (the sfnt table directory is what matters, not the extension) and renders
 * identically everywhere a real OTF does. Only WOFF is an actual re-encode; see [encodeAsWoff].
 */
internal enum class FontExportFormat(val extension: String, val mimeType: String, val label: String) {
    TTF("ttf", "font/ttf", "TTF"),
    OTF("otf", "font/otf", "OTF"),
    WOFF("woff", "font/woff", "WOFF"),
}

/** Writes [sourceFile] (TrueTypeGenerator's sfnt output) out as [format] into a cache file named
 *  after the font, ready to hand to a share intent or copy into Downloads. Re-exporting the same
 *  font/format overwrites the same file rather than accumulating one per tap. */
internal fun exportFontFile(context: Context, sourceFile: File, name: String, format: FontExportFormat): File {
    val displayKey = normalizedFontStorageKey(name).ifBlank { "font" }
    val outFile = File(context.cacheDir, "$displayKey.${format.extension}")
    val bytes = if (format == FontExportFormat.WOFF) encodeAsWoff(sourceFile.readBytes()) else sourceFile.readBytes()
    outFile.writeBytes(bytes)
    return outFile
}

/**
 * Wraps an sfnt font (TrueType or OpenType, as produced by TrueTypeGenerator's private `sfnt()`)
 * into WOFF1: a 44-byte header, a 20-byte-per-table directory recording each table's WOFF offset/
 * compressed length/original length/original checksum, followed by each table's data -- zlib-
 * deflated (RFC 1950, WOFF1's required compression) if that's actually smaller than the raw
 * table, stored as-is otherwise -- each padded to a 4-byte boundary. There's no Android/JVM
 * toolchain in this environment to run this directly, so the exact algorithm below (down to the
 * per-field byte layout) was prototyped in Python and round-tripped through fontTools'
 * TTFont(...) parser against a synthetic multi-table sfnt built the same way
 * TrueTypeGenerator.sfnt() builds one (sorted table map, checksum-adjusted head table); every
 * table came back byte-identical after WOFF-decompression.
 */
internal fun encodeAsWoff(sfntBytes: ByteArray): ByteArray {
    val buffer = ByteBuffer.wrap(sfntBytes) // big-endian is ByteBuffer's default order
    val flavor = buffer.getInt(0)
    val numTables = buffer.getShort(4).toInt() and 0xFFFF

    data class SourceTable(val tag: String, val checksum: Long, val offset: Int, val length: Int)
    val sourceTables = (0 until numTables).map { i ->
        val recordOffset = 12 + i * 16
        val tag = String(sfntBytes, recordOffset, 4, Charsets.US_ASCII)
        val checksum = buffer.getInt(recordOffset + 4).toLong() and 0xFFFFFFFFL
        val tableOffset = buffer.getInt(recordOffset + 8)
        val length = buffer.getInt(recordOffset + 12)
        SourceTable(tag, checksum, tableOffset, length)
    }

    data class WoffTable(val tag: String, val checksum: Long, val origLength: Int, val data: ByteArray)
    val woffTables = sourceTables.map { table ->
        val raw = sfntBytes.copyOfRange(table.offset, table.offset + table.length)
        val compressed = deflate(raw)
        val data = if (compressed.size < raw.size) compressed else raw
        WoffTable(table.tag, table.checksum, table.length, data)
    }

    val headerSize = 44
    val tableDirSize = numTables * 20
    var runningOffset = headerSize + tableDirSize
    data class PlacedTable(val table: WoffTable, val offset: Int)
    val placedTables = woffTables.map { table ->
        val placed = PlacedTable(table, runningOffset)
        runningOffset += table.data.size
        runningOffset += (4 - runningOffset % 4) % 4
        placed
    }

    var totalSfntSize = 12 + numTables * 16
    woffTables.forEach { totalSfntSize += (it.origLength + 3) and 3.inv() }

    val out = ByteArrayOutputStream(runningOffset)
    out.writeU32(0x774F4646L) // 'wOFF' signature
    out.writeU32(flavor.toLong() and 0xFFFFFFFFL)
    out.writeU32(0L) // total WOFF length, patched in below once known
    out.writeU16(numTables)
    out.writeU16(0) // reserved
    out.writeU32(totalSfntSize.toLong() and 0xFFFFFFFFL)
    out.writeU16(0) // majorVersion
    out.writeU16(0) // minorVersion
    repeat(5) { out.writeU32(0L) } // metaOffset, metaLength, metaOrigLength, privOffset, privLength

    placedTables.forEach { (table, offset) ->
        out.write(table.tag.toByteArray(Charsets.US_ASCII))
        out.writeU32(offset.toLong() and 0xFFFFFFFFL)
        out.writeU32(table.data.size.toLong() and 0xFFFFFFFFL)
        out.writeU32(table.origLength.toLong() and 0xFFFFFFFFL)
        out.writeU32(table.checksum)
    }

    placedTables.forEach { (table, offset) ->
        while (out.size() < offset) out.write(0)
        out.write(table.data)
    }
    while (out.size() % 4 != 0) out.write(0)

    val result = out.toByteArray()
    val totalLength = result.size
    result[8] = (totalLength ushr 24).toByte()
    result[9] = (totalLength ushr 16).toByte()
    result[10] = (totalLength ushr 8).toByte()
    result[11] = totalLength.toByte()
    return result
}

private fun deflate(data: ByteArray): ByteArray {
    // No nowrap: WOFF1 requires the zlib format (RFC 1950), not raw deflate.
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    deflater.setInput(data)
    deflater.finish()
    val output = ByteArrayOutputStream(data.size)
    val chunk = ByteArray(4096)
    while (!deflater.finished()) {
        val count = deflater.deflate(chunk)
        output.write(chunk, 0, count)
    }
    deflater.end()
    return output.toByteArray()
}

private fun ByteArrayOutputStream.writeU16(value: Int) {
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

private fun ByteArrayOutputStream.writeU32(value: Long) {
    write(((value ushr 24) and 0xFF).toInt())
    write(((value ushr 16) and 0xFF).toInt())
    write(((value ushr 8) and 0xFF).toInt())
    write((value and 0xFF).toInt())
}

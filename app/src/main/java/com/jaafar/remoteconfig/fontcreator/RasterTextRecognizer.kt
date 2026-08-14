package com.jaafar.remoteconfig.fontcreator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max

internal data class RecognizedDocument(val pages: List<RecognizedPage>) {
    val extractedText: String
        get() = pages.joinToString("\n\n") { page ->
            page.lines.joinToString("\n") { it.text }.trim()
        }.trim()
}

internal data class RecognizedPage(
    val width: Int,
    val height: Int,
    val lines: List<RecognizedLine>,
)

internal data class RecognizedLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

internal class RasterTextRecognizer(typeface: Typeface) {
    private val templates = FontCreatorViewModel.CHARACTER_ORDER
        .filter { it != 32 }
        .map(Int::toChar)
        .map { char -> GlyphTemplate(char, renderTemplate(char, typeface)) }

    fun recognize(bitmap: Bitmap): RecognizedPage {
        val textBitmap = bitmap.prepareForRecognition()
        val mask = BinaryMask.fromBitmap(textBitmap)
        val width = textBitmap.width
        val height = textBitmap.height
        val lines = findLineBounds(mask).mapNotNull { bounds ->
            recognizeLine(mask, bounds)
        }
        if (textBitmap !== bitmap) textBitmap.recycle()
        return RecognizedPage(width, height, lines)
    }

    private fun recognizeLine(mask: BinaryMask, bounds: Rect): RecognizedLine? {
        val characterBounds = findCharacterBounds(mask, bounds)
        if (characterBounds.isEmpty()) return null
        val gaps = characterBounds.zipWithNext { first, second -> second.left - first.right }
            .filter { it > 0 }
        val baseGap = gaps.median().coerceAtLeast(1)
        val spaceThreshold = max(bounds.height() / 3, baseGap * 2)
        val text = buildString {
            characterBounds.forEachIndexed { index, rect ->
                if (index > 0) {
                    val gap = rect.left - characterBounds[index - 1].right
                    if (gap >= spaceThreshold) append(' ')
                }
                append(classify(mask, rect))
            }
        }.trim()
        if (text.isBlank()) return null
        return RecognizedLine(text, bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    private fun classify(mask: BinaryMask, bounds: Rect): Char {
        val sample = normalize(mask.crop(bounds), bounds.width(), bounds.height())
        val sampleAspect = bounds.width().toFloat() / bounds.height().coerceAtLeast(1)
        val sampleDensity = sample.count { it }.toFloat() / sample.size.coerceAtLeast(1)
        return templates.minByOrNull { template ->
            diff(sample, template.mask) +
                abs(sampleAspect - template.aspect) * 140f +
                abs(sampleDensity - template.density) * 240f
        }?.char ?: '?'
    }

    private data class GlyphTemplate(val char: Char, val mask: BooleanArray) {
        val aspect: Float = contentAspect(mask, TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
        val density: Float = mask.count { it }.toFloat() / mask.size.coerceAtLeast(1)
    }

    private companion object {
        private const val TEMPLATE_WIDTH = 28
        private const val TEMPLATE_HEIGHT = 36
        private const val TEMPLATE_TEXT_SIZE = 72f

        private fun renderTemplate(char: Char, typeface: Typeface): BooleanArray {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = TEMPLATE_TEXT_SIZE
                color = Color.BLACK
            }
            val text = char.toString()
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val bitmapWidth = max(bounds.width() + 32, 48)
            val bitmapHeight = max(bounds.height() + 32, 48)
            val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawText(text, 16f - bounds.left, 16f - bounds.top, paint)
            val mask = BinaryMask.fromBitmap(bitmap)
            val content = mask.findContentBounds() ?: Rect(0, 0, bitmap.width, bitmap.height)
            val normalized = normalize(mask.crop(content), TEMPLATE_WIDTH, TEMPLATE_HEIGHT)
            bitmap.recycle()
            return normalized
        }

        private fun findLineBounds(mask: BinaryMask): List<Rect> {
            val threshold = max(2, mask.width / 80)
            val bounds = mutableListOf<Rect>()
            var start = -1
            for (y in 0 until mask.height) {
                val count = mask.countRow(y)
                if (count >= threshold) {
                    if (start == -1) start = y
                } else if (start != -1) {
                    addLine(mask, start, y - 1, bounds)
                    start = -1
                }
            }
            if (start != -1) addLine(mask, start, mask.height - 1, bounds)
            return bounds
        }

        private fun addLine(mask: BinaryMask, start: Int, end: Int, target: MutableList<Rect>) {
            if (end - start < 4) return
            val left = (0 until mask.width).firstOrNull { x -> mask.countColumn(x, start, end + 1) > 0 } ?: return
            val rightExclusive = (mask.width - 1 downTo 0).firstOrNull { x -> mask.countColumn(x, start, end + 1) > 0 }?.plus(1) ?: return
            target += Rect(left, start, rightExclusive, end + 1)
        }

        private fun findCharacterBounds(mask: BinaryMask, line: Rect): List<Rect> {
            val parts = mutableListOf<Rect>()
            val mergeGap = max(1, line.height() / 14)
            var start = -1
            var lastDark = -1
            for (x in line.left until line.right) {
                if (mask.countColumn(x, line.top, line.bottom) > 0) {
                    if (start == -1) start = x
                    lastDark = x
                } else if (start != -1 && x - lastDark > mergeGap) {
                    addCharacter(mask, line, start, lastDark + 1, parts)
                    start = -1
                }
            }
            if (start != -1) addCharacter(mask, line, start, lastDark + 1, parts)
            return parts
        }

        private fun addCharacter(mask: BinaryMask, line: Rect, left: Int, right: Int, target: MutableList<Rect>) {
            val top = (line.top until line.bottom).firstOrNull { y -> mask.countRow(y, left, right) > 0 } ?: return
            val bottomExclusive = (line.bottom - 1 downTo line.top).firstOrNull { y -> mask.countRow(y, left, right) > 0 }?.plus(1) ?: return
            if (right - left < 2 || bottomExclusive - top < 2) return
            target += Rect(left, top, right, bottomExclusive)
        }

        private fun normalize(source: BooleanArray, sourceWidth: Int, sourceHeight: Int): BooleanArray {
            if (sourceWidth <= 0 || sourceHeight <= 0 || source.isEmpty()) {
                return BooleanArray(TEMPLATE_WIDTH * TEMPLATE_HEIGHT)
            }
            val normalized = BooleanArray(TEMPLATE_WIDTH * TEMPLATE_HEIGHT)
            for (y in 0 until TEMPLATE_HEIGHT) {
                val sourceY = (y * sourceHeight / TEMPLATE_HEIGHT).coerceIn(0, sourceHeight - 1)
                for (x in 0 until TEMPLATE_WIDTH) {
                    val sourceX = (x * sourceWidth / TEMPLATE_WIDTH).coerceIn(0, sourceWidth - 1)
                    normalized[y * TEMPLATE_WIDTH + x] = source[sourceY * sourceWidth + sourceX]
                }
            }
            return normalized
        }

        private fun diff(first: BooleanArray, second: BooleanArray): Float {
            var difference = 0f
            for (index in first.indices) if (first[index] != second[index]) difference += 1f
            return difference
        }

        private fun contentAspect(mask: BooleanArray, width: Int, height: Int): Float {
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (!mask[y * width + x]) continue
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (right < left || bottom < top) return 1f
            return (right - left + 1).toFloat() / (bottom - top + 1).coerceAtLeast(1)
        }

        private fun List<Int>.median(): Int {
            if (isEmpty()) return 0
            val sorted = sorted()
            return sorted[sorted.size / 2]
        }
    }
}

private data class BinaryMask(
    val width: Int,
    val height: Int,
    val pixels: BooleanArray,
) {
    fun countRow(y: Int, startX: Int = 0, endX: Int = width): Int {
        var count = 0
        for (x in startX until endX) if (pixels[y * width + x]) count++
        return count
    }

    fun countColumn(x: Int, startY: Int = 0, endY: Int = height): Int {
        var count = 0
        for (y in startY until endY) if (pixels[y * width + x]) count++
        return count
    }

    fun crop(bounds: Rect): BooleanArray {
        val result = BooleanArray(bounds.width() * bounds.height())
        for (y in 0 until bounds.height()) {
            for (x in 0 until bounds.width()) {
                result[y * bounds.width() + x] = pixels[(bounds.top + y) * width + bounds.left + x]
            }
        }
        return result
    }

    fun findContentBounds(): Rect? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!pixels[y * width + x]) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < left || bottom < top) null else Rect(left, top, right + 1, bottom + 1)
    }

    companion object {
        fun fromBitmap(bitmap: Bitmap): BinaryMask {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val luminance = IntArray(pixels.size)
            var total = 0L
            pixels.forEachIndexed { index, pixel ->
                val value = if (Color.alpha(pixel) < 16) 255
                else (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
                luminance[index] = value
                total += value
            }
            val threshold = (total / luminance.size).toInt().coerceIn(30, 225)
            val mask = BooleanArray(pixels.size) { index -> luminance[index] < threshold }
            val darkPixels = mask.count { it }
            val shouldInvert = darkPixels > mask.size / 2
            if (shouldInvert) {
                for (index in mask.indices) mask[index] = !mask[index]
            }
            return BinaryMask(width, height, mask)
        }
    }
}

private fun Bitmap.prepareForRecognition(maxDimension: Int = 1400): Bitmap {
    val largestDimension = max(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / largestDimension
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

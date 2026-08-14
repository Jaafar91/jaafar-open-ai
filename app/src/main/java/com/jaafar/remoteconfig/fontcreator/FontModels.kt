package com.jaafar.remoteconfig.fontcreator

import org.json.JSONArray
import org.json.JSONObject

const val UNITS_PER_EM = 2048
const val MAX_EDITABLE_GLYPHS = 95

/** A language/script block that a font project can include. */
enum class LanguageScript(
    val displayName: String,
    /** Unicode code-point ranges covered by this script (inclusive). */
    val ranges: List<IntRange>,
) {
    BASIC_LATIN("Basic Latin", listOf(0x0020..0x007E)),
    LATIN_EXTENDED("Latin Extended", listOf(0x00C0..0x024F)),
    ARABIC("Arabic", listOf(0x0600..0x06FF)),
    HEBREW("Hebrew", listOf(0x0590..0x05FF)),
    GREEK("Greek", listOf(0x0370..0x03FF)),
    CYRILLIC("Cyrillic", listOf(0x0400..0x04FF)),
    DEVANAGARI("Devanagari", listOf(0x0900..0x097F)),
    CJK("CJK Unified Ideographs", listOf(0x4E00..0x9FFF)),
    HANGUL("Hangul Syllables", listOf(0xAC00..0xD7A3)),
    HIRAGANA("Hiragana", listOf(0x3040..0x309F)),
    KATAKANA("Katakana", listOf(0x30A0..0x30FF)),
    THAI("Thai", listOf(0x0E00..0x0E7F)),
    ;

    /** All code points covered by this script. */
    val codePoints: List<Int> by lazy { ranges.flatMap { it.toList() } }
}

data class FontProject(
    val name: String,
    val drawings: List<GlyphDrawing> = emptyList(),
    val letterSpacingMm: Float = 0f,
    val wordSpacingMm: Float = 3f,
    val selectedLanguages: Set<LanguageScript> = setOf(LanguageScript.BASIC_LATIN),
)

data class GlyphPoint(val x: Float, val y: Float, val onCurve: Boolean = true)

data class GlyphStroke(val points: List<GlyphPoint>)

data class GlyphDrawing(
    val codePoint: Int,
    val strokes: List<GlyphStroke>,
    val canvasWidth: Float,
    val canvasHeight: Float,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("codePoint", codePoint)
        put("canvasWidth", canvasWidth.toDouble())
        put("canvasHeight", canvasHeight.toDouble())
        put("bounds", boundsJson())
        put("strokes", JSONArray().apply {
            strokes.forEach { stroke ->
                put(JSONArray().apply {
                    stroke.points.forEach { point ->
                        put(JSONObject().apply {
                            put("x", point.x.toDouble())
                            put("y", point.y.toDouble())
                            put("onCurve", point.onCurve)
                        })
                    }
                })
            }
        })
    }

    private fun boundsJson(): JSONObject {
        val points = strokes.flatMap { it.points }
        return JSONObject().apply {
            put("left", points.minOfOrNull { it.x }?.toDouble() ?: 0.0)
            put("top", points.minOfOrNull { it.y }?.toDouble() ?: 0.0)
            put("right", points.maxOfOrNull { it.x }?.toDouble() ?: 0.0)
            put("bottom", points.maxOfOrNull { it.y }?.toDouble() ?: 0.0)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): GlyphDrawing {
            val strokesJson = json.getJSONArray("strokes")
            val strokes = buildList {
                for (i in 0 until strokesJson.length()) {
                    val pointsJson = strokesJson.getJSONArray(i)
                    add(GlyphStroke(buildList {
                        for (j in 0 until pointsJson.length()) {
                            val point = pointsJson.getJSONObject(j)
                            add(GlyphPoint(
                                point.getDouble("x").toFloat(),
                                point.getDouble("y").toFloat(),
                                point.optBoolean("onCurve", true),
                            ))
                        }
                    }))
                }
            }
            return GlyphDrawing(
                json.getInt("codePoint"), strokes,
                json.getDouble("canvasWidth").toFloat(),
                json.getDouble("canvasHeight").toFloat(),
            )
        }
    }
}

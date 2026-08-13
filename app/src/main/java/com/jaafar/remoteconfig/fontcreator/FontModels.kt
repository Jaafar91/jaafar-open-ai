package com.jaafar.remoteconfig.fontcreator

import org.json.JSONArray
import org.json.JSONObject

const val UNITS_PER_EM = 2048
const val MAX_EDITABLE_GLYPHS = 95

data class FontProject(
    val name: String,
    val drawings: List<GlyphDrawing> = emptyList(),
    val letterSpacingMm: Float = 0f,
    val wordSpacingMm: Float = 3f,
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

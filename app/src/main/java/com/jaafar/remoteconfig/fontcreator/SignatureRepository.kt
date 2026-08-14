package com.jaafar.remoteconfig.fontcreator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SignatureRepository(context: Context) {
    private val signatureFile = File(context.filesDir, "signatures.json")

    fun load(): List<SavedSignature> = runCatching {
        val array = JSONArray(signatureFile.readText())
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val strokesJson = item.getJSONArray("strokes")
                add(
                    SavedSignature(
                        name = item.getString("name"),
                        strokes = buildList {
                            for (j in 0 until strokesJson.length()) {
                                val pointsJson = strokesJson.getJSONArray(j)
                                add(
                                    GlyphStroke(
                                        buildList {
                                            for (k in 0 until pointsJson.length()) {
                                                val point = pointsJson.getJSONObject(k)
                                                add(
                                                    GlyphPoint(
                                                        x = point.getDouble("x").toFloat(),
                                                        y = point.getDouble("y").toFloat(),
                                                        onCurve = point.optBoolean("onCurve", true),
                                                    )
                                                )
                                            }
                                        }
                                    )
                                )
                            }
                        },
                        canvasWidth = item.optDouble("canvasWidth", 1.0).toFloat(),
                        canvasHeight = item.optDouble("canvasHeight", 1.0).toFloat(),
                        savedAt = item.optLong("savedAt", System.currentTimeMillis()),
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    fun save(signatures: Collection<SavedSignature>) {
        val payload = JSONArray().apply {
            signatures.forEach { signature ->
                put(
                    JSONObject().apply {
                        put("name", signature.name)
                        put("canvasWidth", signature.canvasWidth.toDouble())
                        put("canvasHeight", signature.canvasHeight.toDouble())
                        put("savedAt", signature.savedAt)
                        put(
                            "strokes",
                            JSONArray().apply {
                                signature.strokes.forEach { stroke ->
                                    put(
                                        JSONArray().apply {
                                            stroke.points.forEach { point ->
                                                put(
                                                    JSONObject().apply {
                                                        put("x", point.x.toDouble())
                                                        put("y", point.y.toDouble())
                                                        put("onCurve", point.onCurve)
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }
        val temporary = File(signatureFile.parentFile, "${signatureFile.name}.tmp")
        temporary.writeText(payload.toString())
        if (temporary.renameTo(signatureFile)) return
        try {
            signatureFile.writeText(payload.toString())
        } finally {
            temporary.delete()
        }
    }
}

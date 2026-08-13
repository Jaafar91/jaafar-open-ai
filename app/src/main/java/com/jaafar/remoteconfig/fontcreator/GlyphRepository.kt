package com.jaafar.remoteconfig.fontcreator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class GlyphRepository(context: Context) {
    private val projectFile = File(context.filesDir, "font-projects.json")
    private val legacyFile = File(context.filesDir, "font-project.json")

    fun load(): List<FontProject> = runCatching {
        val array = JSONArray(projectFile.readText())
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val glyphs = item.getJSONArray("drawings")
                add(FontProject(
                    name = item.getString("name"),
                    drawings = buildList {
                        for (j in 0 until glyphs.length()) add(GlyphDrawing.fromJson(glyphs.getJSONObject(j)))
                    },
                    letterSpacingMm = item.optDouble("letterSpacingMm", 0.0).toFloat(),
                    wordSpacingMm = item.optDouble("wordSpacingMm", 3.0).toFloat(),
                ))
            }
        }
    }.getOrElse {
        val legacy = runCatching { JSONArray(legacyFile.readText()) }.getOrNull() ?: return emptyList()
        listOf(FontProject("My first font", buildList {
            for (i in 0 until legacy.length()) add(GlyphDrawing.fromJson(legacy.getJSONObject(i)))
        }))
    }

    fun save(projects: Collection<FontProject>) {
        val payload = JSONArray().apply { projects.forEach { project ->
            put(JSONObject().apply {
                put("name", project.name)
                put("letterSpacingMm", project.letterSpacingMm.toDouble())
                put("wordSpacingMm", project.wordSpacingMm.toDouble())
                put("drawings", JSONArray().apply {
                    project.drawings.sortedBy { it.codePoint }.forEach { put(it.toJson()) }
                })
            })
        } }
        val temporary = File(projectFile.parentFile, "${projectFile.name}.tmp")
        temporary.writeText(payload.toString())
        if (!temporary.renameTo(projectFile)) {
            projectFile.writeText(payload.toString())
            temporary.delete()
        }
    }
}

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
                val langArray = item.optJSONArray("selectedLanguages")
                val selectedLanguages: Set<LanguageScript> = if (langArray != null) {
                    buildSet {
                        for (k in 0 until langArray.length()) {
                            runCatching { add(LanguageScript.valueOf(langArray.getString(k))) }
                        }
                    }.ifEmpty { setOf(LanguageScript.BASIC_LATIN) }
                } else setOf(LanguageScript.BASIC_LATIN)
                // Projects saved before this field existed have no recorded goal -- EXPORT
                // matches their actual behavior (the full character set was always required).
                val goal = runCatching { FontGoal.valueOf(item.getString("goal")) }.getOrDefault(FontGoal.EXPORT)
                // Projects saved before this field existed have no recorded preview phrase --
                // the shared default text is the same thing every project effectively used.
                val previewPhrase = item.optString("previewPhrase", DEFAULT_PREVIEW_TEXT)
                // Projects saved before this field existed never had anything skipped -- an
                // empty set matches their actual behavior (skipping didn't exist yet).
                val skippedArray = item.optJSONArray("skippedCodePoints")
                val skippedCodePoints: Set<Int> = if (skippedArray != null) {
                    buildSet { for (k in 0 until skippedArray.length()) add(skippedArray.getInt(k)) }
                } else emptySet()
                add(FontProject(
                    name = item.getString("name"),
                    drawings = buildList {
                        for (j in 0 until glyphs.length()) add(GlyphDrawing.fromJson(glyphs.getJSONObject(j)))
                    },
                    letterSpacingMm = item.optDouble("letterSpacingMm", 0.0).toFloat(),
                    wordSpacingMm = item.optDouble("wordSpacingMm", 3.0).toFloat(),
                    selectedLanguages = selectedLanguages,
                    goal = goal,
                    skippedCodePoints = skippedCodePoints,
                    previewPhrase = previewPhrase,
                    // Projects saved before this field existed have no recorded edit time --
                    // 0 sorts them before anything with a real timestamp, same as iOS's
                    // decodeIfPresent-with-createdAt-fallback does for its equivalent field.
                    lastModifiedAt = item.optLong("lastModifiedAt", 0L),
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
                put("selectedLanguages", JSONArray().apply { project.selectedLanguages.forEach { put(it.name) } })
                put("goal", project.goal.name)
                put("skippedCodePoints", JSONArray().apply { project.skippedCodePoints.forEach { put(it) } })
                put("previewPhrase", project.previewPhrase)
                put("lastModifiedAt", project.lastModifiedAt)
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

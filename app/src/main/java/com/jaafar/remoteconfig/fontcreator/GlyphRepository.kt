package com.jaafar.remoteconfig.fontcreator

import android.content.Context
import org.json.JSONArray
import java.io.File

class GlyphRepository(context: Context) {
    private val projectFile = File(context.filesDir, "font-project.json")

    fun load(): Map<Int, GlyphDrawing> = runCatching {
        val array = JSONArray(projectFile.readText())
        buildMap {
            for (i in 0 until array.length()) {
                GlyphDrawing.fromJson(array.getJSONObject(i)).also { put(it.codePoint, it) }
            }
        }
    }.getOrDefault(emptyMap())

    fun save(drawings: Collection<GlyphDrawing>) {
        val payload = JSONArray().apply { drawings.sortedBy { it.codePoint }.forEach { put(it.toJson()) } }
        val temporary = File(projectFile.parentFile, "${projectFile.name}.tmp")
        temporary.writeText(payload.toString())
        if (!temporary.renameTo(projectFile)) {
            projectFile.writeText(payload.toString())
            temporary.delete()
        }
    }
}

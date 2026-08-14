package com.jaafar.remoteconfig.fontcreator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ImportedFontRepository(context: Context) {
    private val file = File(context.filesDir, "imported-fonts.json")

    fun load(): List<ImportedFont> = runCatching {
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(ImportedFont(
                    displayName = obj.getString("displayName"),
                    fileName = obj.getString("fileName"),
                    importedAt = obj.optLong("importedAt", System.currentTimeMillis()),
                ))
            }
        }
    }.getOrElse { emptyList() }

    fun save(fonts: List<ImportedFont>) {
        val payload = JSONArray().apply {
            fonts.forEach { font ->
                put(JSONObject().apply {
                    put("displayName", font.displayName)
                    put("fileName", font.fileName)
                    put("importedAt", font.importedAt)
                })
            }
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(payload.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(payload.toString())
            tmp.delete()
        }
    }
}

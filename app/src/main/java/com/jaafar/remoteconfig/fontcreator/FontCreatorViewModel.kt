package com.jaafar.remoteconfig.fontcreator

import android.app.Application
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.util.concurrent.Executors

class FontCreatorViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val FIRST_CODE_POINT = 32
        const val LAST_CODE_POINT = 126
    }

    private val repository = GlyphRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    val drawings = mutableStateMapOf<Int, GlyphDrawing>().apply { putAll(repository.load()) }
    var selectedCodePoint by mutableStateOf<Int?>(null)
    var isPagingMode by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var generatedFont by mutableStateOf<File?>(null)
        private set
    var previewTypeface by mutableStateOf<Typeface?>(null)
        private set

    fun edit(codePoint: Int) {
        isPagingMode = false
        selectedCodePoint = codePoint
    }

    fun startPaging() {
        isPagingMode = true
        selectedCodePoint = (FIRST_CODE_POINT..LAST_CODE_POINT).firstOrNull { it !in drawings }
            ?: FIRST_CODE_POINT
    }

    fun closeEditor() {
        selectedCodePoint = null
        isPagingMode = false
    }

    fun saveDrawing(drawing: GlyphDrawing) {
        drawings[drawing.codePoint] = drawing
        selectedCodePoint = if (isPagingMode && drawing.codePoint < LAST_CODE_POINT) {
            drawing.codePoint + 1
        } else {
            isPagingMode = false
            null
        }
        status = "Glyph saved. Generate the font to update the preview."
        val snapshot = drawings.values.toList()
        executor.execute { repository.save(snapshot) }
    }

    fun generate() {
        if (drawings.isEmpty()) { status = "Draw at least one character first."; return }
        status = "Generating font…"
        val snapshot = drawings.values.toList()
        executor.execute {
            runCatching {
                repository.save(snapshot)
                val file = File(getApplication<Application>().cacheDir, "my-hand-font.ttf")
                file.writeBytes(TrueTypeGenerator().generate(snapshot))
                val typeface = if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(file).build() else Typeface.createFromFile(file)
                file to typeface
            }.onSuccess { (file, typeface) -> main.post {
                generatedFont = file; previewTypeface = typeface
                status = "Font generated with ${snapshot.size} character${if (snapshot.size == 1) "" else "s"}."
            } }.onFailure { error -> main.post { status = "Could not generate font: ${error.message ?: "unknown error"}" } }
        }
    }

    override fun onCleared() { executor.shutdown(); super.onCleared() }
}

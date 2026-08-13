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
        val CHARACTER_ORDER: List<Int> = buildList {
            addAll('A'.code..'Z'.code)
            addAll('a'.code..'z'.code)
            addAll('0'.code..'9'.code)
            " .,!?\'\"-:;()".forEach { add(it.code) }
            addAll((33..126).filter { it !in this })
        }.distinct()
    }

    private val repository = GlyphRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val preferences = application.getSharedPreferences("font_settings", 0)
    private var pagingQueue = emptyList<Int>()
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
    var spaceWidthMm by mutableStateOf(preferences.getFloat("space_width_mm", 3f))
        private set

    fun edit(codePoint: Int) {
        isPagingMode = false
        selectedCodePoint = codePoint
    }

    fun startPaging() {
        pagingQueue = CHARACTER_ORDER.filter { it != ' '.code && it !in drawings }
        if (pagingQueue.isEmpty()) {
            status = "All supported characters have already been drawn."
            return
        }
        isPagingMode = true
        selectedCodePoint = pagingQueue.first()
    }

    fun drawMissingCharacters(text: String) {
        pagingQueue = text.asSequence().map { it.code }
            .filter { it in 32..126 && it != ' '.code && it !in drawings }
            .distinct().toList()
        if (pagingQueue.isEmpty()) {
            status = "This text has no missing supported characters."
            return
        }
        isPagingMode = true
        selectedCodePoint = pagingQueue.first()
        status = "${pagingQueue.size} missing character${if (pagingQueue.size == 1) "" else "s"} to draw."
    }

    fun setSpaceWidthMm(value: String) {
        val width = value.toFloatOrNull()
        if (width == null || width <= 0f || width > 50f) return
        spaceWidthMm = width
        preferences.edit().putFloat("space_width_mm", width).apply()
    }

    fun closeEditor() {
        selectedCodePoint = null
        isPagingMode = false
        pagingQueue = emptyList()
    }

    fun saveDrawing(drawing: GlyphDrawing) {
        drawings[drawing.codePoint] = drawing
        if (isPagingMode) pagingQueue = pagingQueue.filterNot { it == drawing.codePoint }
        selectedCodePoint = if (isPagingMode && pagingQueue.isNotEmpty()) pagingQueue.first() else {
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
                file.writeBytes(TrueTypeGenerator().generate(snapshot, spaceWidthMm))
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

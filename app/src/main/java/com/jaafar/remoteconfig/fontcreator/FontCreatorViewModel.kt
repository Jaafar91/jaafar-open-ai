package com.jaafar.remoteconfig.fontcreator

import android.app.Application
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.util.concurrent.Executors

class FontCreatorViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        val CHARACTER_ORDER: List<Int> = buildList {
            addAll('A'.code..'Z'.code); addAll('a'.code..'z'.code); addAll('0'.code..'9'.code)
            " .,!?\'\"-:;()".forEach { add(it.code) }; addAll((33..126).filter { it !in this })
        }.distinct()
    }

    private val repository = GlyphRepository(application)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pagingQueue = emptyList<Int>()
    val projects = mutableStateListOf<FontProject>().apply { addAll(repository.load()) }
    val drawings = mutableStateMapOf<Int, GlyphDrawing>()
    var activeProjectIndex by mutableStateOf<Int?>(null); private set
    val activeProject: FontProject? get() = activeProjectIndex?.let { projects.getOrNull(it) }
    var selectedCodePoint by mutableStateOf<Int?>(null)
    var isPagingMode by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var generatedFont by mutableStateOf<File?>(null); private set
    var previewTypeface by mutableStateOf<Typeface?>(null); private set

    fun createProject(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) { status = "Enter a name for the font."; return false }
        if (projects.any { it.name.equals(clean, true) }) { status = "A font with that name already exists."; return false }
        projects.add(FontProject(clean)); openProject(projects.lastIndex); persist(); return true
    }

    fun openProject(index: Int) {
        val project = projects.getOrNull(index) ?: return
        activeProjectIndex = index; drawings.clear(); drawings.putAll(project.drawings.associateBy { it.codePoint })
        selectedCodePoint = null; generatedFont = generatedFile(project.name).takeIf { it.exists() }
        previewTypeface = generatedFont?.let { runCatching { loadTypeface(it) }.getOrNull() }
        status = ""
    }

    fun closeProject() { syncActive(); activeProjectIndex = null; drawings.clear(); generatedFont = null; previewTypeface = null }
    fun edit(codePoint: Int) { isPagingMode = false; selectedCodePoint = codePoint }
    fun startPaging() = startQueue(CHARACTER_ORDER.filter { it != 32 && it !in drawings }, "All supported characters have already been drawn.")
    fun drawMissingCharacters(text: String) = startQueue(text.asSequence().map { it.code }.filter { it in 33..126 && it !in drawings }.distinct().toList(), "This text has no missing supported characters.")
    private fun startQueue(queue: List<Int>, emptyMessage: String) {
        if (queue.isEmpty()) { status = emptyMessage; return }
        pagingQueue = queue; isPagingMode = true; selectedCodePoint = queue.first()
    }

    fun setSpacing(letter: String, word: String): Boolean {
        val letterValue = letter.toFloatOrNull(); val wordValue = word.toFloatOrNull()
        if (letterValue == null || letterValue !in -3f..10f || wordValue == null || wordValue !in 0.2f..50f) {
            status = "Use -3 to 10 mm for letter spacing and 0.2 to 50 mm for word spacing."; return false
        }
        updateActive { it.copy(letterSpacingMm = letterValue, wordSpacingMm = wordValue) }
        status = "Spacing saved. Generate again to apply it."
        return true
    }

    fun closeEditor() { selectedCodePoint = null; isPagingMode = false; pagingQueue = emptyList() }
    fun saveDrawing(drawing: GlyphDrawing) {
        drawings[drawing.codePoint] = drawing
        if (isPagingMode) pagingQueue = pagingQueue.filterNot { it == drawing.codePoint }
        selectedCodePoint = if (isPagingMode && pagingQueue.isNotEmpty()) pagingQueue.first() else null
        if (selectedCodePoint == null) isPagingMode = false
        syncActive(); persist(); status = "Glyph saved."
    }

    fun generate() {
        val project = activeProject ?: return
        if (drawings.isEmpty()) { status = "Draw at least one character first."; return }
        status = "Generating font…"; syncActive(); val snapshot = activeProject ?: return
        executor.execute { runCatching {
            val file = generatedFile(snapshot.name)
            file.writeBytes(TrueTypeGenerator().generate(snapshot.drawings, snapshot.wordSpacingMm, snapshot.letterSpacingMm, snapshot.name))
            file to loadTypeface(file)
        }.onSuccess { (file, typeface) -> main.post { generatedFont = file; previewTypeface = typeface; status = "${snapshot.name} generated and saved." } }
            .onFailure { error -> main.post { status = "Could not generate font: ${error.message ?: "unknown error"}" } } }
    }

    private fun loadTypeface(file: File) = if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(file).build() else Typeface.createFromFile(file)
    private fun generatedFile(name: String) = File(getApplication<Application>().filesDir, "font-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}.ttf")
    private fun updateActive(transform: (FontProject) -> FontProject) { val index = activeProjectIndex ?: return; projects[index] = transform(projects[index]); persist() }
    private fun syncActive() { val index = activeProjectIndex ?: return; projects[index] = projects[index].copy(drawings = drawings.values.toList()) }
    private fun persist() { val snapshot = projects.toList(); executor.execute { repository.save(snapshot) } }
    override fun onCleared() { syncActive(); executor.shutdown(); super.onCleared() }
}

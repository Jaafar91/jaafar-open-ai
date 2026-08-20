package com.jaafar.remoteconfig.fontcreator

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.net.Uri
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
        private const val PREFS_DEFAULT_SIGNATURE = "default_signature_name"
        private const val PREFS_DEFAULT_STAMP = "default_stamp_name"
        val CHARACTER_ORDER: List<Int> = buildList {
            addAll('A'.code..'Z'.code); addAll('a'.code..'z'.code); addAll('0'.code..'9'.code)
            " .,!?\'\"-:;()".forEach { add(it.code) }; addAll((33..126).filter { it !in this })
        }.distinct()
    }

    /** Returns the ordered code points for the active project's selected languages. */
    val activeCharacterOrder: List<Int> get() {
        val project = activeProject ?: return CHARACTER_ORDER
        val codePoints = project.selectedLanguages
            .flatMap { it.codePoints }
            .distinct()
            .filter { it != 0x20 } // exclude plain space (handled separately in spacing)
        val letters = codePoints.filter { it.toChar().isLetter() }.sorted()
        val digits = codePoints.filter { it.toChar().isDigit() }.sorted()
        val symbols = codePoints.filter { !it.toChar().isLetter() && !it.toChar().isDigit() }.sorted()
        return letters + digits + symbols
    }

    private val repository = GlyphRepository(application)
    private val signatureRepository = SignatureRepository(application)
    private val importedFontRepository = ImportedFontRepository(application)
    private val prefs = application.getSharedPreferences("appearance", 0)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pagingQueue by mutableStateOf(emptyList<Int>())
    private var pagingHistory by mutableStateOf(emptyList<Int>())
    private var pagingTotal by mutableStateOf(0)
    val projects = mutableStateListOf<FontProject>().apply { addAll(repository.load()) }
    val signatures = mutableStateListOf<SavedSignature>().apply { addAll(signatureRepository.load().sortedByDescending { it.savedAt }) }
    val importedFonts = mutableStateListOf<ImportedFont>().apply { addAll(importedFontRepository.load()) }
    val drawings = mutableStateMapOf<Int, GlyphDrawing>()
    var activeProjectIndex by mutableStateOf<Int?>(null); private set
    val activeProject: FontProject? get() = activeProjectIndex?.let { projects.getOrNull(it) }
    var selectedCodePoint by mutableStateOf<Int?>(null)
    var isPagingMode by mutableStateOf(false); private set
    val canGoToPreviousLetter: Boolean get() = isPagingMode && pagingHistory.isNotEmpty()
    val pagingProgress: Pair<Int, Int>? get() = if (isPagingMode && pagingTotal > 0) (pagingTotal - pagingQueue.size + 1).coerceAtMost(pagingTotal) to pagingTotal else null
    var status by mutableStateOf(""); private set
    var generatedFont by mutableStateOf<File?>(null); private set
    var previewTypeface by mutableStateOf<Typeface?>(null); private set
    var referenceFontKey by mutableStateOf(prefs.getString("reference_font", "Default") ?: "Default"); private set
    var importStatus by mutableStateOf(""); private set
    var defaultSignatureName by mutableStateOf(prefs.getString(PREFS_DEFAULT_SIGNATURE, null)); private set
    var defaultStampName by mutableStateOf(prefs.getString(PREFS_DEFAULT_STAMP, null)); private set

    /** Returns all available typefaces (generated + imported) with their display labels. */
    fun hasGeneratedFont(name: String): Boolean = generatedFile(name).exists()

    fun allFontOptions(): List<Pair<String, Typeface>> {
        val app = getApplication<Application>()
        val generated = projects.mapNotNull { project ->
            val file = generatedFile(project.name)
            if (file.exists()) runCatching { project.name to loadTypeface(file) }.getOrNull() else null
        }
        val imported = importedFonts.mapNotNull { font ->
            val file = File(app.filesDir, font.fileName)
            if (file.exists()) runCatching { font.displayName to loadTypeface(file) }.getOrNull() else null
        }
        return generated + imported
    }

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
    fun setLanguages(languages: Set<LanguageScript>): Boolean {
        if (languages.isEmpty()) { status = "Select at least one language."; return false }
        updateActive { it.copy(selectedLanguages = languages) }
        status = "Languages saved."
        return true
    }

    fun startPaging() = startQueue(activeCharacterOrder.filter { it !in drawings }, "All supported characters have already been drawn.")
    fun drawMissingCharacters(text: String) = startQueue(text.asSequence().map { it.code }.filter { it != 0x20 && it in activeCharacterOrder && it !in drawings }.distinct().toList(), "This text has no missing supported characters.")
    private fun startQueue(queue: List<Int>, emptyMessage: String) {
        if (queue.isEmpty()) { status = emptyMessage; return }
        pagingQueue = queue
        pagingHistory = emptyList()
        pagingTotal = queue.size
        isPagingMode = true
        selectedCodePoint = queue.first()
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

    fun closeEditor() {
        selectedCodePoint = null
        isPagingMode = false
        pagingQueue = emptyList()
        pagingHistory = emptyList()
    }
    fun skipLetter() {
        if (!isPagingMode) return
        selectedCodePoint?.let { pagingHistory = pagingHistory + it }
        pagingQueue = pagingQueue.filterNot { it == selectedCodePoint }
        selectedCodePoint = if (pagingQueue.isNotEmpty()) pagingQueue.first() else null
        if (selectedCodePoint == null) isPagingMode = false
    }

    fun previousLetter() {
        if (!isPagingMode || pagingHistory.isEmpty()) return
        val previous = pagingHistory.last()
        pagingHistory = pagingHistory.dropLast(1)
        pagingQueue = listOf(previous) + pagingQueue.filterNot { it == previous }
        selectedCodePoint = previous
    }

    fun saveDrawing(drawing: GlyphDrawing) {
        drawings[drawing.codePoint] = drawing
        if (isPagingMode) {
            pagingHistory = pagingHistory + drawing.codePoint
            pagingQueue = pagingQueue.filterNot { it == drawing.codePoint }
        }
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

    fun importFont(contentResolver: ContentResolver, uri: Uri, displayName: String) {
        val cleanName = displayName.trim().ifEmpty { "Imported Font" }
        importStatus = "Importing…"
        executor.execute {
            runCatching {
                val ext = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)?.substringAfterLast('.', "ttf") else "ttf"
                } ?: "ttf"
                val safeExt = if (ext.lowercase() in listOf("ttf", "otf")) ext.lowercase() else "ttf"
                val fileName = "imported-${System.currentTimeMillis()}.$safeExt"
                val destFile = File(getApplication<Application>().filesDir, fileName)
                contentResolver.openInputStream(uri)?.use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
                    ?: error("Cannot read source file")
                // Validate as Typeface
                loadTypeface(destFile)
                ImportedFont(displayName = cleanName, fileName = fileName)
            }.onSuccess { font ->
                main.post {
                    val existing = importedFonts.indexOfFirst { it.displayName.equals(font.displayName, ignoreCase = true) }
                    if (existing >= 0) importedFonts[existing] = font else importedFonts.add(0, font)
                    persistImportedFonts()
                    importStatus = "\"${font.displayName}\" imported."
                }
            }.onFailure { error ->
                main.post { importStatus = "Import failed: ${error.message ?: "unknown error"}" }
            }
        }
    }

    fun deleteProject(name: String) {
        val index = projects.indexOfFirst { it.name == name }
        if (index < 0) return
        val project = projects[index]
        if (activeProjectIndex == index) {
            activeProjectIndex = null
            drawings.clear()
            generatedFont = null
            previewTypeface = null
        } else if (activeProjectIndex != null && activeProjectIndex!! > index) {
            activeProjectIndex = activeProjectIndex!! - 1
        }
        projects.removeAt(index)
        generatedFile(project.name).delete()
        persist()
        status = "Font deleted."
    }

    fun deleteImportedFont(displayName: String) {
        val idx = importedFonts.indexOfFirst { it.displayName == displayName }
        if (idx >= 0) {
            val font = importedFonts[idx]
            importedFonts.removeAt(idx)
            persistImportedFonts()
            executor.execute { File(getApplication<Application>().filesDir, font.fileName).delete() }
        }
    }

    fun setReferenceFont(key: String) {
        referenceFontKey = key
        prefs.edit().putString("reference_font", key).apply()
    }

    fun referenceTypeface(): Typeface? {
        val app = getApplication<Application>()
        return when (referenceFontKey) {
            "Default" -> null
            "Sans-serif" -> Typeface.SANS_SERIF
            "Serif" -> Typeface.SERIF
            "Monospace" -> Typeface.MONOSPACE
            else -> {
                val imported = importedFonts.firstOrNull { it.displayName == referenceFontKey }
                if (imported != null) {
                    runCatching { loadTypeface(File(app.filesDir, imported.fileName)) }.getOrNull()
                } else {
                    val project = projects.firstOrNull { it.name == referenceFontKey }
                    if (project != null) {
                        val file = generatedFile(project.name)
                        runCatching { if (file.exists()) loadTypeface(file) else null }.getOrNull()
                    } else null
                }
            }
        }
    }

    fun suggestedSignatureName(baseName: String): String {
        val cleanBaseName = baseName.trim()
        if (signatures.none { it.name.equals(cleanBaseName, ignoreCase = true) }) return cleanBaseName
        var suffix = 1
        var candidate = "$cleanBaseName ($suffix)"
        while (signatures.any { it.name.equals(candidate, ignoreCase = true) }) {
            suffix += 1
            candidate = "$cleanBaseName ($suffix)"
        }
        return candidate
    }

    fun hasSavedSignatureName(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        return signatures.any { it.name.equals(cleanName, ignoreCase = true) }
    }

    fun saveSignature(name: String, strokes: List<GlyphStroke>, canvasWidth: Float, canvasHeight: Float): String? {
        val cleanInputName = name.trim()
        if (cleanInputName.isNotBlank() && hasSavedSignatureName(cleanInputName)) return null
        val cleanName = cleanInputName.ifEmpty { suggestedSignatureName("My signature") }
        val signature = SavedSignature(
            name = cleanName,
            strokes = strokes,
            canvasWidth = canvasWidth.coerceAtLeast(1f),
            canvasHeight = canvasHeight.coerceAtLeast(1f),
            savedAt = System.currentTimeMillis(),
            imageFileName = null,
        )
        upsertSignature(signature)
        setDefaultSignature(cleanName)
        return cleanName
    }

    fun saveSignatureFromImage(contentResolver: ContentResolver, uri: Uri, name: String, removeWhiteBackground: Boolean = true): String? {
        val cleanInputName = name.trim()
        if (cleanInputName.isNotBlank() && hasSavedSignatureName(cleanInputName)) return null
        val cleanName = cleanInputName.ifEmpty { suggestedSignatureName("My stamp") }
        val fileName = "stamp-${System.currentTimeMillis()}.png"
        val outputFile = File(getApplication<Application>().filesDir, fileName)
        try {
            val sourceBitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } ?: error("Cannot read source image")
            val outputBitmap = if (removeWhiteBackground) {
                val result = removeNearWhitePixels(sourceBitmap)
                sourceBitmap.recycle()
                result
            } else {
                sourceBitmap
            }
            try {
                java.io.FileOutputStream(outputFile).use { outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                outputBitmap.recycle()
            }
        } catch (error: Exception) {
            outputFile.delete()
            throw error
        }
        upsertSignature(
            SavedSignature(
                name = cleanName,
                strokes = emptyList(),
                canvasWidth = 1f,
                canvasHeight = 1f,
                savedAt = System.currentTimeMillis(),
                imageFileName = fileName,
            )
        )
        setDefaultStamp(cleanName)
        return cleanName
    }

    fun setDefaultSignature(name: String?) {
        defaultSignatureName = name
        prefs.edit().putString(PREFS_DEFAULT_SIGNATURE, name).apply()
    }

    fun setDefaultStamp(name: String?) {
        defaultStampName = name
        prefs.edit().putString(PREFS_DEFAULT_STAMP, name).apply()
    }

    fun renameSignature(currentName: String, newName: String): Boolean {
        val index = signatures.indexOfFirst { it.name == currentName }
        if (index < 0) return false
        val clean = newName.trim()
        if (clean.isBlank()) return false
        val duplicate = signatures.withIndex().any { (idx, signature) ->
            idx != index && signature.name.equals(clean, ignoreCase = true)
        }
        if (duplicate) return false
        val existing = signatures[index]
        signatures[index] = existing.copy(name = clean)
        if (defaultSignatureName == currentName && existing.imageFileName == null) setDefaultSignature(clean)
        if (defaultStampName == currentName && existing.imageFileName != null) setDefaultStamp(clean)
        persistSignatures()
        return true
    }

    fun deleteSignature(name: String) {
        val index = signatures.indexOfFirst { it.name == name }
        if (index >= 0) {
            val removed = signatures.removeAt(index)
            if (removed.imageFileName == null && defaultSignatureName == removed.name) {
                setDefaultSignature(signatures.firstOrNull { it.imageFileName == null }?.name)
            }
            if (removed.imageFileName != null && defaultStampName == removed.name) {
                setDefaultStamp(signatures.firstOrNull { it.imageFileName != null }?.name)
            }
            removed.imageFileName?.let { fileName ->
                executor.execute { File(getApplication<Application>().filesDir, fileName).delete() }
            }
            persistSignatures()
        }
    }

    fun signatureImageFile(signature: SavedSignature): File? =
        signature.imageFileName?.let { File(getApplication<Application>().filesDir, it).takeIf(File::exists) }

    private fun upsertSignature(signature: SavedSignature) {
        val existingIndex = signatures.indexOfFirst { it.name.equals(signature.name, ignoreCase = true) }
        if (existingIndex >= 0) {
            val replaced = signatures.removeAt(existingIndex)
            if (replaced.imageFileName != null && replaced.imageFileName != signature.imageFileName) {
                executor.execute { File(getApplication<Application>().filesDir, replaced.imageFileName).delete() }
            }
        }
        signatures.add(0, signature)
        persistSignatures()
    }

    private fun loadTypeface(file: File) = if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(file).build() else Typeface.createFromFile(file)
    private fun generatedFile(name: String) = File(getApplication<Application>().filesDir, "font-${name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}.ttf")
    private fun updateActive(transform: (FontProject) -> FontProject) { val index = activeProjectIndex ?: return; projects[index] = transform(projects[index]); persist() }
    private fun syncActive() { val index = activeProjectIndex ?: return; projects[index] = projects[index].copy(drawings = drawings.values.toList()) }
    private fun persist() { val snapshot = projects.toList(); executor.execute { repository.save(snapshot) } }
    private fun persistSignatures() { val snapshot = signatures.toList(); executor.execute { signatureRepository.save(snapshot) } }
    private fun persistImportedFonts() { val snapshot = importedFonts.toList(); executor.execute { importedFontRepository.save(snapshot) } }
    override fun onCleared() { syncActive(); executor.shutdown(); super.onCleared() }
}

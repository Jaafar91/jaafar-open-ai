package com.jaafar.remoteconfig.fontcreator

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class FontCreatorViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val PREFS_DEFAULT_SIGNATURE = "default_signature_name"
        private const val PREFS_DEFAULT_STAMP = "default_stamp_name"
        private const val PREFS_PHRASE_MODE = "phrase_mode_enabled"
        private const val PREFS_LAST_PHRASE = "last_phrase"
        val CHARACTER_ORDER: List<Int> = buildList {
            addAll('A'.code..'Z'.code); addAll('a'.code..'z'.code); addAll('0'.code..'9'.code)
            " .,!?\'\"-:;()".forEach { add(it.code) }; addAll((33..126).filter { it !in this })
        }.distinct()

        // Free plan: 1 saved item of each kind. Pro removes these caps.
        const val FREE_FONT_LIMIT = 1
        const val FREE_SIGNATURE_LIMIT = 1
        const val FREE_STAMP_LIMIT = 1

        // Use font on image / Fill & Mark stay open on the free plan up to this many completed
        // exports each, per calendar month -- not a hard Pro-only lock. Pro removes the cap.
        const val FREE_MONTHLY_FEATURE_EXPORTS = 5
        private const val PREFS_IMAGE_EXPORT_MONTH = "image_export_month"
        private const val PREFS_IMAGE_EXPORT_COUNT = "image_export_count"
        private const val PREFS_FILLMARK_EXPORT_MONTH = "fillmark_export_month"
        private const val PREFS_FILLMARK_EXPORT_COUNT = "fillmark_export_count"

        // Ask for a rating once, after a few successful shares from either Use font on image or
        // Fill & Mark -- never again after that, whatever the customer chooses, so it never nags.
        private const val SUCCESSFUL_SHARES_BEFORE_RATING_PROMPT = 3
        private const val PREFS_RATING_PROMPT_SHOWN = "rating_prompt_shown"
        private const val PREFS_SUCCESSFUL_SHARE_COUNT = "successful_share_count_for_rating"
    }

    /** The code points [project] actually needs drawn to be "complete" -- every character in its
     *  selected languages, except a [FontGoal.USE_ON_IMAGE] project, which only needs letters and
     *  digits (no punctuation/symbols): that's what typically shows up captioning a photo, so
     *  such a project can unlock Fine-tune/Share/Download/the celebration screen sooner. Ordered
     *  letters-then-digits(-then-symbols), the order [activeCharacterOrder] draws them in. */
    private fun requiredCodePoints(project: FontProject): List<Int> {
        val codePoints = project.selectedLanguages
            .flatMap { it.codePoints }
            .distinct()
            .filter { it != 0x20 } // exclude plain space (handled separately in spacing)
        val letters = codePoints.filter { it.toChar().isLetter() }.sorted()
        val digits = codePoints.filter { it.toChar().isDigit() }.sorted()
        if (project.goal == FontGoal.USE_ON_IMAGE) return letters + digits
        val symbols = codePoints.filter { !it.toChar().isLetter() && !it.toChar().isDigit() }.sorted()
        return letters + digits + symbols
    }

    /** Returns the ordered code points for the active project's selected languages. */
    val activeCharacterOrder: List<Int> get() = activeProject?.let(::requiredCodePoints) ?: CHARACTER_ORDER

    val phraseCharacterOrder: List<Int>
        get() = applicablePhraseCodePoints(lastPhrase, activeCharacterOrder.toSet())

    val editorCharacterOrder: List<Int>
        get() = if (phraseModeEnabled) phraseCharacterOrder else activeCharacterOrder

    fun characterCount(project: FontProject): Int = requiredCodePoints(project).size

    fun isProjectComplete(project: FontProject): Boolean {
        val required = requiredCodePoints(project)
        return required.isNotEmpty() && project.drawings.map { it.codePoint }.toSet().containsAll(required)
    }

    val isPro: Boolean get() = billing.isPro

    /** "2026-9" style key for the current calendar month -- a monthly export count is only
     *  valid while it was stored under this same key; any other stored key (or none) means
     *  the count is from a previous month and reads as zero, no explicit reset step needed. */
    private fun currentYearMonth(): String {
        val calendar = java.util.Calendar.getInstance()
        return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.MONTH)}"
    }

    private fun monthlyExportCount(monthKey: String, countKey: String): Int =
        if (prefs.getString(monthKey, null) == currentYearMonth()) prefs.getInt(countKey, 0) else 0

    private fun recordMonthlyExport(monthKey: String, countKey: String) {
        if (isPro) return // Pro is unlimited -- nothing to track.
        val nextCount = monthlyExportCount(monthKey, countKey) + 1
        prefs.edit().putString(monthKey, currentYearMonth()).putInt(countKey, nextCount).apply()
    }

    val hasReachedFreeUseOnImageLimit: Boolean
        get() = !isPro && monthlyExportCount(PREFS_IMAGE_EXPORT_MONTH, PREFS_IMAGE_EXPORT_COUNT) >= FREE_MONTHLY_FEATURE_EXPORTS
    val hasReachedFreeFillMarkLimit: Boolean
        get() = !isPro && monthlyExportCount(PREFS_FILLMARK_EXPORT_MONTH, PREFS_FILLMARK_EXPORT_COUNT) >= FREE_MONTHLY_FEATURE_EXPORTS

    /** Call once a "Use font on image" export actually completes -- entering the screen or
     *  placing text layers is free; only a completed export counts against the monthly cap. */
    fun recordUseOnImageExport() = recordMonthlyExport(PREFS_IMAGE_EXPORT_MONTH, PREFS_IMAGE_EXPORT_COUNT)

    /** Call once a Fill & Mark export actually completes -- same "only a completed export
     *  counts" rule as [recordUseOnImageExport]. */
    fun recordFillMarkExport() = recordMonthlyExport(PREFS_FILLMARK_EXPORT_MONTH, PREFS_FILLMARK_EXPORT_COUNT)

    var showRatingPrompt by mutableStateOf(false)
        private set

    /** Call alongside [recordUseOnImageExport]/[recordFillMarkExport] on every successful share
     *  from either feature -- unlike those, this counts Pro customers too (free-tier quota
     *  tracking skips them, but they're just as worth asking for a rating). Surfaces
     *  [showRatingPrompt] exactly once, after [SUCCESSFUL_SHARES_BEFORE_RATING_PROMPT] of these. */
    fun recordSuccessfulShareForRating() {
        if (prefs.getBoolean(PREFS_RATING_PROMPT_SHOWN, false)) return
        val count = prefs.getInt(PREFS_SUCCESSFUL_SHARE_COUNT, 0) + 1
        prefs.edit().putInt(PREFS_SUCCESSFUL_SHARE_COUNT, count).apply()
        if (count >= SUCCESSFUL_SHARES_BEFORE_RATING_PROMPT) showRatingPrompt = true
    }

    /** Dismisses the prompt and marks it shown for good, whether the customer rated or declined --
     *  it never asks again. */
    fun dismissRatingPrompt() {
        showRatingPrompt = false
        prefs.edit().putBoolean(PREFS_RATING_PROMPT_SHOWN, true).apply()
    }

    // Hand-drawn (projects) and imported fonts are two separate lists/models, but count
    // together against the free plan's single shared font cap.
    val hasReachedFreeFontLimit: Boolean get() = !isPro && (projects.size + importedFonts.size) >= FREE_FONT_LIMIT

    // Signatures and stamps share one SavedSignature list, told apart by imageFileName --
    // null for a drawn signature, set for a stamp rasterized from a photo -- so each needs its
    // own count against its own cap rather than one combined check.
    val hasReachedFreeSignatureLimit: Boolean
        get() = !isPro && signatures.count { it.imageFileName == null } >= FREE_SIGNATURE_LIMIT
    val hasReachedFreeStampLimit: Boolean
        get() = !isPro && signatures.count { it.imageFileName != null } >= FREE_STAMP_LIMIT

    private val repository = GlyphRepository(application)
    private val signatureRepository = SignatureRepository(application)
    private val importedFontRepository = ImportedFontRepository(application)
    internal val billing = BillingManager(application)
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
    var lastEditedCodePoint by mutableStateOf<Int?>(null); private set
    var lastStrokeWidth by mutableFloatStateOf(8f); private set
    var phraseModeEnabled by mutableStateOf(prefs.getBoolean(PREFS_PHRASE_MODE, false)); private set
    var lastPhrase by mutableStateOf(prefs.getString(PREFS_LAST_PHRASE, "") ?: ""); private set

    /** Returns all available typefaces (generated + imported) with their display labels. */
    fun hasGeneratedFont(name: String): Boolean = generatedFile(name).exists()

    /**
     * All usable typefaces (generated + imported), most recently modified/imported first --
     * so a caller that just wants "one sensible default font" (Fill & Mark, or "Use font on
     * image"'s own fallback) can simply take the first entry instead of getting creation order.
     *
     * [completeOnly] excludes a created font missing glyphs for some of its own selected
     * characters. Fill & Mark passes true, since arbitrary typed text there could hit an
     * undrawn glyph; "Use font on image" leaves it false because an incomplete (phrase-mode)
     * font can still render the specific phrase it was drawn for. Either way, the fonts library
     * screen is unaffected and still lists an incomplete font with its own "x of y" progress.
     */
    fun allFontOptions(completeOnly: Boolean = false): List<Pair<String, Typeface>> {
        val app = getApplication<Application>()
        data class Entry(val name: String, val typeface: Typeface, val modifiedAt: Long)
        val generated = projects.mapNotNull { project ->
            if (project.drawings.isEmpty()) return@mapNotNull null
            if (completeOnly && !isProjectComplete(project)) return@mapNotNull null
            val file = generatedFile(project.name)
            runCatching {
                // Keep Library fonts usable and current even if the user has not opened Preview.
                writeFontFileAtomically(
                    file,
                    TrueTypeGenerator().generate(
                        project.drawings,
                        project.wordSpacingMm,
                        project.letterSpacingMm,
                        project.name,
                    ),
                )
                Entry(project.name, loadTypeface(file), project.lastModifiedAt)
            }.getOrNull()
        }
        val imported = importedFonts.mapNotNull { font ->
            val file = File(app.filesDir, font.fileName)
            if (file.exists()) runCatching { Entry(font.displayName, loadTypeface(file), font.importedAt) }.getOrNull() else null
        }
        return (generated + imported).sortedByDescending { it.modifiedAt }.map { it.name to it.typeface }
    }

    fun createProject(name: String, goal: FontGoal = FontGoal.EXPORT): Boolean {
        if (hasReachedFreeFontLimit) {
            status = "Free plan allows $FREE_FONT_LIMIT font. Upgrade to Pro for unlimited fonts."
            return false
        }
        val clean = name.trim()
        if (clean.isBlank()) { status = "Enter a name for the font."; return false }
        val requestedStorageKey = normalizedFontStorageKey(clean)
        if (requestedStorageKey.isBlank()) { status = "Use letters or numbers in the font name."; return false }
        if (hasFontName(clean)) {
            status = "A font with that name already exists."
            return false
        }
        projects.add(FontProject(clean, goal = goal)); openProject(projects.lastIndex); persist(); return true
    }

    fun hasFontName(name: String, excludingProjectIndex: Int? = null): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val requestedStorageKey = normalizedFontStorageKey(clean)
        if (requestedStorageKey.isBlank()) return false
        return projects.withIndex().any { (index, project) ->
            index != excludingProjectIndex && fontNamesConflict(project.name, clean)
        } || importedFonts.any { font -> fontNamesConflict(font.displayName, clean) }
    }

    fun hasProjectName(name: String): Boolean = hasFontName(name)

    fun renameActiveProject(name: String): Boolean {
        val index = activeProjectIndex ?: return false
        val current = projects.getOrNull(index) ?: return false
        val clean = name.trim()
        if (clean.isBlank()) { status = "Enter a name for the font."; return false }
        val requestedStorageKey = normalizedFontStorageKey(clean)
        if (requestedStorageKey.isBlank()) { status = "Use letters or numbers in the font name."; return false }
        if (hasFontName(clean, excludingProjectIndex = index)) {
            status = "A font with that name already exists."
            return false
        }
        if (current.name == clean) return true

        syncActive()
        val renamed = projects[index].copy(name = clean, lastModifiedAt = System.currentTimeMillis())
        projects[index] = renamed
        persist()
        if (referenceFontKey == current.name) setReferenceFont(clean)

        val oldFile = generatedFile(current.name)
        if (renamed.drawings.isEmpty()) {
            oldFile.delete()
            generatedFont = null
            previewTypeface = null
            status = "Font renamed."
            return true
        }

        status = "Renaming font…"
        executor.execute {
            runCatching {
                val newFile = generatedFile(renamed.name)
                writeFontFileAtomically(newFile, TrueTypeGenerator().generate(renamed.drawings, renamed.wordSpacingMm, renamed.letterSpacingMm, renamed.name))
                newFile to loadTypeface(newFile)
            }.onSuccess { (newFile, typeface) ->
                if (oldFile != newFile) oldFile.delete()
                main.post {
                    generatedFont = newFile
                    previewTypeface = typeface
                    status = "Font renamed."
                }
            }.onFailure { error ->
                main.post { status = "Font renamed, but its file could not be regenerated: ${error.message ?: "unknown error"}" }
            }
        }
        return true
    }

    fun openProject(index: Int) {
        val project = projects.getOrNull(index) ?: return
        activeProjectIndex = index; drawings.clear(); drawings.putAll(project.drawings.associateBy { it.codePoint })
        selectedCodePoint = null; generatedFont = generatedFile(project.name).takeIf { it.exists() }
        previewTypeface = generatedFont?.let { runCatching { loadTypeface(it) }.getOrNull() }
        lastStrokeWidth = 8f
        status = ""
    }

    fun closeProject() { syncActive(); activeProjectIndex = null; drawings.clear(); generatedFont = null; previewTypeface = null }
    /** Captured once per editing session (here and in [startQueue], its paging-mode equivalent)
     *  so a save that finishes the session can tell a touch-up of an already-complete font (this
     *  was already true when editing started) apart from a genuine first-time completion (it
     *  wasn't) -- the former should return to wherever the customer was, not show the
     *  celebration screen again. */
    var wasCompleteBeforeCurrentEdit: Boolean = false
        private set
    fun edit(codePoint: Int) {
        wasCompleteBeforeCurrentEdit = activeProject?.let(::isProjectComplete) == true
        lastEditedCodePoint = codePoint; isPagingMode = false; selectedCodePoint = codePoint
    }
    fun editLetters() {
        val order = activeCharacterOrder
        if (order.isEmpty()) { status = "No characters available."; return }
        val start = lastEditedCodePoint?.takeIf { it in order } ?: order.first()
        edit(start)
    }
    fun editPrevious() {
        val order = activeCharacterOrder
        val current = selectedCodePoint ?: return
        val idx = order.indexOf(current)
        if (idx > 0) edit(order[idx - 1])
    }
    fun editNext() {
        val order = activeCharacterOrder
        val current = selectedCodePoint ?: return
        val idx = order.indexOf(current)
        if (idx >= 0 && idx < order.size - 1) edit(order[idx + 1])
    }
    fun setLanguages(languages: Set<LanguageScript>): Boolean {
        if (languages.isEmpty()) { status = "Select at least one language."; return false }
        updateActive { it.copy(selectedLanguages = languages) }
        status = "Languages saved."
        return true
    }

    fun startPaging() = startQueue(activeCharacterOrder.filter { it !in drawings }, "All supported characters have already been drawn.")

    fun startPhrase(phrase: String): Boolean {
        val cleanPhrase = phrase.trim()
        if (cleanPhrase.isBlank()) {
            status = "Enter a phrase first."
            return false
        }
        val phraseCharacters = applicablePhraseCodePoints(cleanPhrase, activeCharacterOrder.toSet())
        if (phraseCharacters.isEmpty()) {
            status = "The phrase has no characters supported by the selected languages."
            return false
        }
        lastPhrase = cleanPhrase
        phraseModeEnabled = true
        prefs.edit()
            .putString(PREFS_LAST_PHRASE, cleanPhrase)
            .putBoolean(PREFS_PHRASE_MODE, true)
            .apply()
        // Queues every character of the phrase, not just the ones still missing -- so this also
        // works as a "review/edit this phrase" flow for an already-complete font (e.g. from
        // Fine-tune, touching up a letter the customer doesn't like), not only a "draw what's
        // left" one. An already-drawn character just opens pre-loaded with its existing strokes
        // (GlyphEditorScreen already does this via `initial = drawings[codePoint]`), so this is a
        // genuine edit, not a blank redraw.
        startQueue(phraseCharacters, "Phrase ready — nothing to draw or review.")
        return true
    }

    fun disablePhraseMode() {
        phraseModeEnabled = false
        prefs.edit().putBoolean(PREFS_PHRASE_MODE, false).apply()
        if (isPagingMode) {
            isPagingMode = false
            pagingQueue = emptyList()
            pagingHistory = emptyList()
        }
    }

    private fun clearPhraseModeState() {
        phraseModeEnabled = false
        lastPhrase = ""
        pagingQueue = emptyList()
        pagingHistory = emptyList()
        pagingTotal = 0
        prefs.edit()
            .remove(PREFS_PHRASE_MODE)
            .remove(PREFS_LAST_PHRASE)
            .apply()
    }

    private fun startQueue(queue: List<Int>, emptyMessage: String) {
        if (queue.isEmpty()) { status = emptyMessage; return }
        wasCompleteBeforeCurrentEdit = activeProject?.let(::isProjectComplete) == true
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
        status = "Spacing updated."
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
        lastStrokeWidth = drawing.strokeWidth
        drawings[drawing.codePoint] = drawing
        if (isPagingMode) {
            pagingHistory = pagingHistory + drawing.codePoint
            pagingQueue = pagingQueue.filterNot { it == drawing.codePoint }
        }
        selectedCodePoint = if (isPagingMode && pagingQueue.isNotEmpty()) pagingQueue.first() else null
        val phraseFinished = selectedCodePoint == null && phraseModeEnabled
        if (selectedCodePoint == null) isPagingMode = false
        if (phraseFinished) {
            clearPhraseModeState()
        }
        syncActive(); persist(); status = if (phraseFinished) "Phrase ready." else "Glyph saved."
    }

    fun saveDrawingAndContinue(drawing: GlyphDrawing) {
        val wasExisting = drawing.codePoint in drawings
        lastStrokeWidth = drawing.strokeWidth
        drawings[drawing.codePoint] = drawing
        syncActive(); persist(); status = "Letter saved."
        val order = activeCharacterOrder
        selectedCodePoint = characterAfterSave(order, drawing.codePoint, drawings.keys, wasExisting)
        if (selectedCodePoint == null && !wasExisting) status = "Your font is ready."
        isPagingMode = false
    }

    fun saveDrawingAndStay(drawing: GlyphDrawing) {
        lastStrokeWidth = drawing.strokeWidth
        drawings[drawing.codePoint] = drawing
        syncActive(); persist(); status = "Letter saved."
        selectedCodePoint = drawing.codePoint
        isPagingMode = false
    }

    fun generate() {
        val project = activeProject ?: return
        if (drawings.isEmpty()) { status = "Draw at least one character first."; return }
        status = "Generating font…"; syncActive(); val snapshot = activeProject ?: return
        executor.execute { runCatching {
            val file = generatedFile(snapshot.name)
            writeFontFileAtomically(file, TrueTypeGenerator().generate(snapshot.drawings, snapshot.wordSpacingMm, snapshot.letterSpacingMm, snapshot.name))
            file to loadTypeface(file)
        }.onSuccess { (file, typeface) -> main.post { generatedFont = file; previewTypeface = typeface; status = "${snapshot.name} generated and saved." } }
            .onFailure { error -> main.post { status = "Could not generate font: ${error.message ?: "unknown error"}" } } }
    }

    /** Loads [project]'s generated Typeface for lightweight previews (e.g. the fonts list),
     *  off the main thread -- the *real* font, not an approximation, so it matches Fine-tune's
     *  rendering exactly. Reuses the same generated file [generate] would produce if one
     *  already exists on disk, otherwise builds and persists it there now, so a later visit to
     *  Fine-tune or Use-on-image finds it ready instead of regenerating it again. */
    suspend fun typefaceForPreview(project: FontProject): Typeface? = withContext(Dispatchers.IO) {
        runCatching {
            val file = generatedFile(project.name)
            if (!file.exists()) {
                if (project.drawings.isEmpty()) return@withContext null
                writeFontFileAtomically(file, TrueTypeGenerator().generate(project.drawings, project.wordSpacingMm, project.letterSpacingMm, project.name))
            }
            loadTypeface(file)
        }.getOrNull()
    }

    fun importFont(contentResolver: ContentResolver, uri: Uri, displayName: String) {
        if (hasReachedFreeFontLimit) {
            importStatus = "Free plan allows $FREE_FONT_LIMIT font. Upgrade to Pro for unlimited fonts."
            return
        }
        val cleanName = displayName.trim().ifEmpty { "Imported Font" }
        if (hasFontName(cleanName)) {
            importStatus = "A font with that name already exists."
            return
        }
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

    fun deleteImportedFont(fileName: String) {
        val idx = importedFonts.indexOfFirst { it.fileName == fileName }
        if (idx >= 0) {
            val font = importedFonts[idx]
            importedFonts.removeAt(idx)
            persistImportedFonts()
            executor.execute { File(getApplication<Application>().filesDir, font.fileName).delete() }
            // Otherwise a leftover "<name> imported." from whenever this font was first added
            // stays on screen after deleting it, reading like the deletion itself said so.
            importStatus = "\"${font.displayName}\" deleted."
        }
    }

    fun setReferenceFont(key: String) {
        referenceFontKey = key
        prefs.edit().putString("reference_font", key).apply()
    }

    fun referenceTypeface(): Typeface {
        val app = getApplication<Application>()
        return when (referenceFontKey) {
            "Default" -> Typeface.DEFAULT
            "Sans-serif" -> Typeface.SANS_SERIF
            "Serif" -> Typeface.SERIF
            "Monospace" -> Typeface.MONOSPACE
            else -> {
                val imported = importedFonts.firstOrNull { it.displayName == referenceFontKey }
                if (imported != null) {
                    runCatching { loadTypeface(File(app.filesDir, imported.fileName)) }.getOrDefault(Typeface.DEFAULT)
                } else {
                    val project = projects.firstOrNull { it.name == referenceFontKey }
                    if (project != null) {
                        val file = generatedFile(project.name)
                        runCatching { if (file.exists()) loadTypeface(file) else Typeface.DEFAULT }.getOrDefault(Typeface.DEFAULT)
                    } else Typeface.DEFAULT
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
        if (hasReachedFreeSignatureLimit) return null
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
        if (hasReachedFreeStampLimit) return null
        val cleanInputName = name.trim()
        if (cleanInputName.isNotBlank() && hasSavedSignatureName(cleanInputName)) return null
        val cleanName = cleanInputName.ifEmpty { suggestedSignatureName("My stamp") }
        val fileName = "stamp-${System.currentTimeMillis()}.png"
        val outputFile = File(getApplication<Application>().filesDir, fileName)
        try {
            // loadBitmap() downsamples to a bounded dimension instead of decoding the source
            // image at full resolution -- a gallery photo picked as a stamp/signature can easily
            // be 50MP+, which would otherwise blow up memory for no visual benefit here.
            val sourceBitmap: Bitmap = loadBitmap(contentResolver, uri) ?: error("Cannot read source image")
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

    /** Updates an existing signature's name and redrawn strokes in place -- unlike
     *  [saveSignature], which is create-only and rejects any name already in use, including
     *  the signature's own current name. Used by the signature editor's edit flow. */
    fun updateSignature(originalName: String, newName: String, strokes: List<GlyphStroke>, canvasWidth: Float, canvasHeight: Float): Boolean {
        val index = signatures.indexOfFirst { it.name == originalName }
        if (index < 0) return false
        val clean = newName.trim().ifEmpty { originalName }
        val duplicate = signatures.withIndex().any { (idx, signature) ->
            idx != index && signature.name.equals(clean, ignoreCase = true)
        }
        if (duplicate) return false
        val existing = signatures[index]
        signatures[index] = existing.copy(name = clean, strokes = strokes, canvasWidth = canvasWidth.coerceAtLeast(1f), canvasHeight = canvasHeight.coerceAtLeast(1f))
        if (defaultSignatureName == originalName) setDefaultSignature(clean)
        persistSignatures()
        return true
    }

    /** Updates an existing stamp's name and/or replaces its image in place -- unlike
     *  [saveSignatureFromImage], which is create-only and rejects any name already in use,
     *  including the stamp's own current name. Used by the stamp editor's edit flow. */
    fun updateSignatureImage(
        originalName: String,
        newName: String,
        contentResolver: ContentResolver,
        uri: Uri,
        removeWhiteBackground: Boolean = true,
    ): Boolean {
        val index = signatures.indexOfFirst { it.name == originalName }
        if (index < 0) return false
        val clean = newName.trim().ifEmpty { originalName }
        val duplicate = signatures.withIndex().any { (idx, signature) ->
            idx != index && signature.name.equals(clean, ignoreCase = true)
        }
        if (duplicate) return false
        val fileName = "stamp-${System.currentTimeMillis()}.png"
        val outputFile = File(getApplication<Application>().filesDir, fileName)
        try {
            // loadBitmap() downsamples to a bounded dimension instead of decoding the source
            // image at full resolution -- a gallery photo picked as a stamp/signature can easily
            // be 50MP+, which would otherwise blow up memory for no visual benefit here.
            val sourceBitmap: Bitmap = loadBitmap(contentResolver, uri) ?: error("Cannot read source image")
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
            return false
        }
        val oldImageFileName = signatures[index].imageFileName
        signatures[index] = signatures[index].copy(name = clean, imageFileName = fileName, savedAt = System.currentTimeMillis())
        if (oldImageFileName != null && oldImageFileName != fileName) {
            executor.execute { File(getApplication<Application>().filesDir, oldImageFileName).delete() }
        }
        if (defaultStampName == originalName) setDefaultStamp(clean)
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

    /** Writes [bytes] to [file] atomically (write to a sibling temp file, then rename it over
     *  [file]) instead of File.writeBytes()'s truncate-then-write. [generatedFile]s the same
     *  path can be (re)written from up to three unsynchronized places at once -- allFontOptions()
     *  on the caller's thread, generate() on [executor], typefaceForPreview() on Dispatchers.IO
     *  -- and a plain truncate-then-write can momentarily leave the file empty/partial while
     *  another thread's loadTypeface() has it mmap'd, crashing the process with SIGBUS.
     *  File.renameTo() maps to the POSIX rename() syscall for two paths on the same filesystem
     *  (always true here -- both live in filesDir), which is atomic, so any concurrent reader
     *  always sees either the complete old file or the complete new one, never a torn write.
     *  (java.nio.file.Files.move() would be clearer but requires API 26+; minSdk here is 24.) */
    private fun writeFontFileAtomically(file: File, bytes: ByteArray) {
        val tempFile = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
        tempFile.writeBytes(bytes)
        if (!tempFile.renameTo(file)) {
            tempFile.delete()
            throw IOException("Could not replace ${file.name}")
        }
    }

    private fun generatedFile(name: String) = File(getApplication<Application>().filesDir, "font-${normalizedFontStorageKey(name)}.ttf")
    // Both helpers stamp lastModifiedAt centrally (matching the iOS app's updateFont()) so
    // every edit path -- rename, spacing, language selection, saving a drawn letter -- keeps
    // it current without each call site having to remember to.
    private fun updateActive(transform: (FontProject) -> FontProject) {
        val index = activeProjectIndex ?: return
        projects[index] = transform(projects[index]).copy(lastModifiedAt = System.currentTimeMillis())
        persist()
    }
    private fun syncActive() {
        val index = activeProjectIndex ?: return
        projects[index] = projects[index].copy(drawings = drawings.values.toList(), lastModifiedAt = System.currentTimeMillis())
    }
    private fun persist() { val snapshot = projects.toList(); executor.execute { repository.save(snapshot) } }
    private fun persistSignatures() { val snapshot = signatures.toList(); executor.execute { signatureRepository.save(snapshot) } }
    private fun persistImportedFonts() { val snapshot = importedFonts.toList(); executor.execute { importedFontRepository.save(snapshot) } }
    override fun onCleared() { syncActive(); executor.shutdown(); super.onCleared() }
}

internal fun applicablePhraseCodePoints(phrase: String, supported: Set<Int>): List<Int> =
    phrase.codePoints().toArray().toList().distinct().filter { it != 0x20 && it in supported }

internal fun fontNamesConflict(first: String, second: String): Boolean {
    val firstKey = normalizedFontStorageKey(first)
    val secondKey = normalizedFontStorageKey(second)
    return first.equals(second, ignoreCase = true) ||
        (firstKey.isNotBlank() && firstKey == secondKey)
}

internal fun characterAfterSave(order: List<Int>, current: Int, drawn: Set<Int>, wasExisting: Boolean): Int? {
    if (order.isEmpty()) return null
    if (!wasExisting && order.all { it in drawn }) return null
    val currentIndex = order.indexOf(current).takeIf { it >= 0 } ?: 0
    val charactersAfterCurrent = order.drop(currentIndex + 1) + order.take(currentIndex + 1)
    return charactersAfterCurrent.firstOrNull { it !in drawn }
        ?: order[(currentIndex + 1) % order.size]
}

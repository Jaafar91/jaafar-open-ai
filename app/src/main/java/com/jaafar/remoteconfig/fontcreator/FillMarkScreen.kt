package com.jaafar.remoteconfig.fontcreator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/** Visual mark type — note: this is visual document marking only, not certified signing. */
internal enum class MarkType(val label: String) {
    Text("Text"),
    Date("Date"),
    Check("Check"),
    Signature("Signature"),
    Stamp("Stamp"),
}

internal enum class CheckStyle(val symbol: String) { Check("\u2713"), Cross("\u2717") }

internal data class DocumentMark(
    val id: Long = System.nanoTime(),
    val type: MarkType,
    val offsetX: Float,        // top-left x as fraction 0..1
    val offsetY: Float,        // top-left y as fraction 0..1
    val sizeFraction: Float = 0.20f,  // width as fraction of page width
    val text: String = "",
    val colorArgb: Int = Color.BLACK,
    val fontKey: String? = null,
    val checkStyle: CheckStyle = CheckStyle.Check,
    val signatureName: String? = null,
    val applyToAllPages: Boolean = false,
    val targetPage: Int = 0,
)

// ---------------------------------------------------------------------------
// Recent documents (local-only, stored in SharedPreferences as JSON)
// ---------------------------------------------------------------------------

private const val PREFS_RECENT_DOCS = "fill_mark_recent_docs"
private const val PREFS_KEY_LIST = "list"
private const val MAX_RECENT = 5

private data class RecentDoc(val uriString: String, val displayName: String, val lastUsed: Long)

private fun loadRecentDocs(context: android.content.Context): List<RecentDoc> {
    val prefs = context.getSharedPreferences(PREFS_RECENT_DOCS, 0)
    val json = prefs.getString(PREFS_KEY_LIST, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(RecentDoc(obj.getString("uri"), obj.getString("name"), obj.getLong("lastUsed")))
            }
        }.sortedByDescending { it.lastUsed }
    }.getOrDefault(emptyList())
}

private fun saveRecentDoc(context: android.content.Context, uriString: String, displayName: String) {
    val existing = loadRecentDocs(context).filter { it.uriString != uriString }.take(MAX_RECENT - 1)
    val updated = listOf(RecentDoc(uriString, displayName, System.currentTimeMillis())) + existing
    val arr = JSONArray()
    updated.forEach { doc ->
        arr.put(JSONObject().apply {
            put("uri", doc.uriString)
            put("name", doc.displayName)
            put("lastUsed", doc.lastUsed)
        })
    }
    context.getSharedPreferences(PREFS_RECENT_DOCS, 0)
        .edit().putString(PREFS_KEY_LIST, arr.toString()).apply()
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

/**
 * Fill & Mark Document feature.
 *
 * Allows the user to open a PDF or image and place visual text, date, check,
 * signature, and stamp marks on it, then export/share the result.
 *
 * NOTE: This is visual document marking only. It does not produce legally
 * certified digital signatures or preserve editable PDF form fields.
 *
 * @param initialUri  URI passed in via the Android Share sheet (ACTION_SEND).
 *                    When non-null the editor opens immediately with this document.
 * @param initialText Optional preset text to pre-populate when the editor opens
 *                    (used by Quick marks from the Signatures & Stamps screen).
 */
@Composable
internal fun FillMarkScreen(
    vm: FontCreatorViewModel,
    initialUri: Uri? = null,
    initialText: String? = null,
    back: () -> Unit,
) {
    var documentUri by remember { mutableStateOf(initialUri) }

    if (documentUri == null) {
        FillMarkLandingScreen(
            onDocumentChosen = { uri -> documentUri = uri },
            back = back,
        )
    } else {
        FillMarkEditorScreen(
            vm = vm,
            documentUri = documentUri!!,
            initialText = initialText,
            back = { documentUri = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Landing screen
// ---------------------------------------------------------------------------

@Composable
private fun FillMarkLandingScreen(
    onDocumentChosen: (Uri) -> Unit,
    back: () -> Unit,
) {
    val context = LocalContext.current
    var recentDocs by remember { mutableStateOf(loadRecentDocs(context)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = displayNameForUri(context, uri) ?: uri.lastPathSegment ?: "Document"
        saveRecentDoc(context, uri.toString(), name)
        recentDocs = loadRecentDocs(context)
        onDocumentChosen(uri)
    }

    Page("Fill & Mark Document", back) {
        Text("Prepare a document", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Open a PDF or image, then add text, dates, checks, signatures, or stamps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Choose a PDF or image")
        }

        Text(
            "Visual marks are added to the exported copy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (recentDocs.isNotEmpty()) {
            HorizontalDivider()
            Text("Recent documents", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(recentDocs, key = { it.uriString }) { doc ->
                    OutlinedButton(
                        onClick = {
                            val uri = Uri.parse(doc.uriString)
                            saveRecentDoc(context, doc.uriString, doc.displayName)
                            recentDocs = loadRecentDocs(context)
                            onDocumentChosen(uri)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(doc.displayName, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Editor
// ---------------------------------------------------------------------------

@Composable
private fun FillMarkEditorScreen(
    vm: FontCreatorViewModel,
    documentUri: Uri,
    initialText: String? = null,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontOptions = remember { vm.allFontOptions() }

    // Document state
    var isPdf by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    DisposableEffect(previewBitmap) {
        val b = previewBitmap
        onDispose { b?.recycle() }
    }

    // Marks state
    val marks = remember { mutableStateListOf<DocumentMark>() }
    var selectedMarkId by remember { mutableStateOf<Long?>(null) }
    var activeTool by remember { mutableStateOf<MarkType?>(null) }

    // Per-tool config state (shared across marks for ergonomics)
    var configText by remember { mutableStateOf(initialText ?: "") }
    var configColorIdx by remember { mutableIntStateOf(0) }
    var configFontIdx by remember { mutableIntStateOf(-1) }
    var configSizeFraction by remember { mutableFloatStateOf(0.20f) }
    var configCheckStyle by remember { mutableStateOf(CheckStyle.Check) }
    var configSignatureName by remember { mutableStateOf<String?>(null) }
    var configApplyToAll by remember { mutableStateOf(false) }

    val textColors = remember {
        listOf(
            "Black" to Color.BLACK,
            "Blue" to Color.BLUE,
            "Red" to Color.RED,
            "Green" to 0xFF006400.toInt(),
        )
    }

    val selectedMark = marks.firstOrNull { it.id == selectedMarkId }

    // Canvas display size (tracked so pointer handlers can use it)
    var canvasDisplaySize by remember { mutableStateOf(IntSize.Zero) }

    // Bitmap cache for signature/stamp marks – loaded/rendered off the UI thread.
    var sigBitmapCache by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val sigNamesInMarks = marks.mapNotNull { it.signatureName }.distinct()
    LaunchedEffect(sigNamesInMarks) {
        val oldCache = sigBitmapCache
        // Capture ViewModel state on the calling (main) coroutine thread before switching to IO.
        val signaturesSnapshot = vm.signatures.toList()
        val newCache = withContext(Dispatchers.IO) {
            sigNamesInMarks.mapNotNull { name ->
                val sig = signaturesSnapshot.firstOrNull { it.name == name } ?: return@mapNotNull null
                val bmp = oldCache[name]
                    ?: (if (sig.imageFileName != null) loadSignatureBitmap(context, sig)
                        else renderStrokeSignatureToBitmap(sig))
                    ?: return@mapNotNull null
                name to bmp
            }.toMap()
        }
        oldCache.forEach { (key, bmp) -> if (key !in newCache) bmp.recycle() }
        sigBitmapCache = newCache
    }
    DisposableEffect(Unit) {
        onDispose { sigBitmapCache.values.forEach { it.recycle() } }
    }

    // One effect owns document loading and PDF-page changes, avoiding a first-load race.
    LaunchedEffect(documentUri, currentPage) {
        val mimeType = withContext(Dispatchers.IO) { context.contentResolver.getType(documentUri) }
        val looksLikePdf = withContext(Dispatchers.IO) {
            displayNameForUri(context, documentUri)?.endsWith(".pdf", true) == true
        }
        if (mimeType == "application/pdf" || looksLikePdf) {
            val count = withContext(Dispatchers.IO) { getPdfPageCount(context, documentUri) }
            isPdf = true
            pageCount = count
            val safePage = currentPage.coerceIn(0, (count - 1).coerceAtLeast(0))
            val bmp = withContext(Dispatchers.IO) { renderPdfPage(context, documentUri, safePage) }
            previewBitmap = bmp
        } else {
            val bmp = withContext(Dispatchers.IO) { loadBitmap(context.contentResolver, documentUri) }
            isPdf = false
            pageCount = 0
            previewBitmap = bmp
        }
    }

    // Sync selected-mark config into edit fields whenever selection changes
    LaunchedEffect(selectedMarkId) {
        val m = marks.firstOrNull { it.id == selectedMarkId } ?: return@LaunchedEffect
        activeTool = m.type
        configText = m.text
        configColorIdx = textColors.indexOfFirst { it.second == m.colorArgb }.takeIf { it >= 0 } ?: 0
        configFontIdx = fontOptions.indexOfFirst { it.first == m.fontKey }.takeIf { it >= 0 } ?: -1
        configSizeFraction = m.sizeFraction
        configCheckStyle = m.checkStyle
        configSignatureName = m.signatureName
        configApplyToAll = m.applyToAllPages
    }

    fun addOrUpdateMark(offsetX: Float, offsetY: Float) {
        val tool = activeTool ?: return
        val todayText = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val newMark = DocumentMark(
            type = tool,
            offsetX = offsetX,
            offsetY = offsetY,
            sizeFraction = configSizeFraction,
            text = when (tool) {
                MarkType.Text -> configText.ifBlank { "Text" }
                MarkType.Date -> todayText
                MarkType.Check -> ""
                MarkType.Signature, MarkType.Stamp -> ""
            },
            colorArgb = textColors.getOrNull(configColorIdx)?.second ?: Color.BLACK,
            fontKey = fontOptions.getOrNull(configFontIdx)?.first,
            checkStyle = configCheckStyle,
            signatureName = configSignatureName,
            applyToAllPages = configApplyToAll,
            targetPage = currentPage,
        )
        marks.add(newMark)
        selectedMarkId = newMark.id
    }

    fun updateSelectedMark() {
        val idx = marks.indexOfFirst { it.id == selectedMarkId }
        if (idx < 0) return
        val m = marks[idx]
        marks[idx] = m.copy(
            text = when (m.type) {
                MarkType.Text -> configText.ifBlank { "Text" }
                MarkType.Date -> m.text
                MarkType.Check -> ""
                MarkType.Signature, MarkType.Stamp -> ""
            },
            colorArgb = textColors.getOrNull(configColorIdx)?.second ?: Color.BLACK,
            fontKey = fontOptions.getOrNull(configFontIdx)?.first,
            sizeFraction = configSizeFraction,
            checkStyle = configCheckStyle,
            signatureName = configSignatureName,
            applyToAllPages = configApplyToAll,
        )
    }

    // Export action – captured in a lambda so the icon button in the top bar can trigger it.
    fun triggerExport() {
        val fontOptionsSnapshot = vm.allFontOptions()
        val signaturesSnapshot = vm.signatures.toList()
        scope.launch {
            isProcessing = true
            status = "Exporting\u2026"
            val result = withContext(Dispatchers.IO) {
                exportDocument(
                    context, documentUri, marks.toList(), isPdf,
                    fontOptionsSnapshot, signaturesSnapshot,
                )
            }
            isProcessing = false
            if (result != null) {
                saveRecentDoc(context, documentUri.toString(), displayNameForUri(context, documentUri) ?: "Document")
                shareDocument(context, result.file, result.mimeType)
                status = "Export ready to share."
            } else {
                status = "Export failed."
            }
        }
    }

    Page(
        title = "Fill & Mark Document",
        back = back,
        actions = {
            // Share / Export icon button fixed in the top app bar.
            IconButton(
                onClick = ::triggerExport,
                enabled = !isProcessing && marks.isNotEmpty() && previewBitmap != null,
            ) {
                FillMarkShareIcon()
            }
        },
    ) {
        // The outer Column does NOT scroll — only the document area scrolls.
        Column(Modifier.fillMaxSize()) {

            // ── FIXED CONTROLS (top) ────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // PDF page navigation
                if (isPdf && pageCount > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { if (currentPage > 0) currentPage-- },
                            enabled = currentPage > 0,
                        ) { Text("\u2190") }
                        Text(
                            "Page ${currentPage + 1} of $pageCount",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = { if (currentPage < pageCount - 1) currentPage++ },
                            enabled = currentPage < pageCount - 1,
                        ) { Text("\u2192") }
                    }
                }

                // Tool selector toolbar
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MarkType.entries.forEach { tool ->
                        val isActive = activeTool == tool
                        if (isActive) {
                            Button(onClick = { activeTool = null; selectedMarkId = null }) { Text(tool.label) }
                        } else {
                            OutlinedButton(onClick = {
                                activeTool = tool
                                selectedMarkId = null
                            }) { Text(tool.label) }
                        }
                    }
                }

                // Per-tool configuration panel
                if (activeTool != null) {
                    Text(
                        "Tap on the document to place a ${activeTool!!.label.lowercase()}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MarkConfigPanel(
                        tool = activeTool!!,
                        configText = configText,
                        onConfigTextChange = { configText = it },
                        configColorIdx = configColorIdx,
                        onColorChange = { configColorIdx = it; updateSelectedMark() },
                        textColors = textColors,
                        configFontIdx = configFontIdx,
                        onFontChange = { configFontIdx = it; updateSelectedMark() },
                        fontOptions = fontOptions,
                        configSizeFraction = configSizeFraction,
                        onSizeChange = { configSizeFraction = it; updateSelectedMark() },
                        configCheckStyle = configCheckStyle,
                        onCheckStyleChange = { configCheckStyle = it; updateSelectedMark() },
                        configSignatureName = configSignatureName,
                        onSignatureChange = { configSignatureName = it; updateSelectedMark() },
                        vm = vm,
                        isPdf = isPdf,
                        configApplyToAll = configApplyToAll,
                        onApplyToAllChange = { configApplyToAll = it; updateSelectedMark() },
                        selectedMark = selectedMark,
                        onTextCommit = { updateSelectedMark() },
                    )
                }

                // Selected-mark actions
                if (selectedMark != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                marks.removeIf { it.id == selectedMarkId }
                                selectedMarkId = null
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Delete mark") }
                        OutlinedButton(
                            onClick = { selectedMarkId = null; activeTool = null },
                            modifier = Modifier.weight(1f),
                        ) { Text("Deselect") }
                    }
                }

                HorizontalDivider()
            }

            // ── SCROLLABLE DOCUMENT AREA ─────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 200.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val bitmap = previewBitmap
                if (bitmap != null) {
                    val previewAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val visibleMarks = marks.filter { it.applyToAllPages || it.targetPage == currentPage }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(previewAspect)
                            .border(1.dp, ComposeColor.Gray)
                            .onSizeChanged { canvasDisplaySize = it },
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(activeTool, currentPage) {
                                    detectTapGestures { offset ->
                                        val w = canvasDisplaySize.width.toFloat().coerceAtLeast(1f)
                                        val h = canvasDisplaySize.height.toFloat().coerceAtLeast(1f)
                                        val hit = visibleMarks.lastOrNull { mark ->
                                            markContainsPoint(mark, offset, w, h)
                                        }
                                        when {
                                            hit != null -> selectedMarkId = hit.id
                                            activeTool != null -> {
                                                val xFrac = (offset.x / w).coerceIn(0f, 0.85f)
                                                val yFrac = (offset.y / h).coerceIn(0f, 0.85f)
                                                addOrUpdateMark(xFrac, yFrac)
                                            }
                                            else -> selectedMarkId = null
                                        }
                                    }
                                }
                                .pointerInput(selectedMarkId) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val w = canvasDisplaySize.width.toFloat().coerceAtLeast(1f)
                                        val h = canvasDisplaySize.height.toFloat().coerceAtLeast(1f)
                                        val idx = marks.indexOfFirst { it.id == selectedMarkId }
                                        if (idx >= 0) {
                                            val m = marks[idx]
                                            marks[idx] = m.copy(
                                                offsetX = (m.offsetX + dragAmount.x / w).coerceIn(0f, 1f),
                                                offsetY = (m.offsetY + dragAmount.y / h).coerceIn(0f, 1f),
                                            )
                                        }
                                    }
                                },
                        ) {
                            val w = size.width
                            val h = size.height
                            visibleMarks.forEach { mark ->
                                val isSelected = mark.id == selectedMarkId
                                drawMarkOnCanvas(mark, w, h, isSelected, fontOptions, sigBitmapCache)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Loading document\u2026", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── FIXED BOTTOM (export status) ─────────────────────────────────────
            if (marks.isNotEmpty() && !isProcessing) {
                Button(
                    onClick = ::triggerExport,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Export & Share")
                }
            }
            if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Draws a three-node share/network graph icon (three dots connected by two lines)
 * without requiring the material-icons-extended library.
 */
@Composable
private fun FillMarkShareIcon() {
    val color = LocalContentColor.current
    Canvas(
        Modifier
            .size(24.dp)
            .semantics { contentDescription = "Export & Share" },
    ) {
        val stroke = 2.dp.toPx()
        val radius = size.minDimension * 0.11f
        val left = Offset(size.width * 0.25f, size.height * 0.5f)
        val top = Offset(size.width * 0.72f, size.height * 0.25f)
        val bottom = Offset(size.width * 0.72f, size.height * 0.75f)
        drawLine(color, left, top, stroke)
        drawLine(color, left, bottom, stroke)
        drawCircle(color, radius, left)
        drawCircle(color, radius, top)
        drawCircle(color, radius, bottom)
    }
}

// ---------------------------------------------------------------------------
// Mark configuration panel
// ---------------------------------------------------------------------------

@Composable
private fun MarkConfigPanel(
    tool: MarkType,
    configText: String,
    onConfigTextChange: (String) -> Unit,
    configColorIdx: Int,
    onColorChange: (Int) -> Unit,
    textColors: List<Pair<String, Int>>,
    configFontIdx: Int,
    onFontChange: (Int) -> Unit,
    fontOptions: List<Pair<String, Typeface>>,
    configSizeFraction: Float,
    onSizeChange: (Float) -> Unit,
    configCheckStyle: CheckStyle,
    onCheckStyleChange: (CheckStyle) -> Unit,
    configSignatureName: String?,
    onSignatureChange: (String?) -> Unit,
    vm: FontCreatorViewModel,
    isPdf: Boolean,
    configApplyToAll: Boolean,
    onApplyToAllChange: (Boolean) -> Unit,
    selectedMark: DocumentMark?,
    onTextCommit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (tool) {
            MarkType.Text -> {
                OutlinedTextField(
                    value = configText,
                    onValueChange = onConfigTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Text") },
                    singleLine = true,
                    trailingIcon = {
                        if (selectedMark != null) {
                            OutlinedButton(onClick = onTextCommit) { Text("Apply") }
                        }
                    },
                )
                // Quick marks presets
                Text("Quick marks", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    QUICK_MARK_PRESETS.forEach { preset ->
                        OutlinedButton(onClick = {
                            onConfigTextChange(preset)
                            onTextCommit()
                        }) { Text(preset, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                TextMarkStyleControls(
                    configColorIdx, onColorChange, textColors,
                    configFontIdx, onFontChange, fontOptions,
                    configSizeFraction, onSizeChange,
                )
            }
            MarkType.Date -> {
                val today = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
                Text("Date: $today (tap on the document to place)", style = MaterialTheme.typography.bodySmall)
                TextMarkStyleControls(
                    configColorIdx, onColorChange, textColors,
                    configFontIdx, onFontChange, fontOptions,
                    configSizeFraction, onSizeChange,
                )
            }
            MarkType.Check -> {
                Text("Check style", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CheckStyle.entries.forEach { style ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = configCheckStyle == style,
                                onClick = { onCheckStyleChange(style) },
                            )
                            Text("${style.symbol} ${style.name}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                TextMarkStyleControls(
                    configColorIdx, onColorChange, textColors,
                    configFontIdx, onFontChange, fontOptions,
                    configSizeFraction, onSizeChange,
                )
            }
            MarkType.Signature -> {
                val sigs = vm.signatures.filter { it.imageFileName == null }
                SignatureStampSelector(
                    label = "Select a signature",
                    items = sigs,
                    selectedName = configSignatureName,
                    onSelect = onSignatureChange,
                )
                SizeControl(configSizeFraction, onSizeChange)
                if (isPdf) ApplyToAllToggle(configApplyToAll, onApplyToAllChange)
            }
            MarkType.Stamp -> {
                val stamps = vm.signatures.filter { it.imageFileName != null }
                SignatureStampSelector(
                    label = "Select a stamp",
                    items = stamps,
                    selectedName = configSignatureName,
                    onSelect = onSignatureChange,
                )
                SizeControl(configSizeFraction, onSizeChange)
                if (isPdf) ApplyToAllToggle(configApplyToAll, onApplyToAllChange)
            }
        }
    }
}

@Composable
private fun TextMarkStyleControls(
    colorIdx: Int, onColorChange: (Int) -> Unit, colors: List<Pair<String, Int>>,
    fontIdx: Int, onFontChange: (Int) -> Unit, fontOptions: List<Pair<String, Typeface>>,
    sizeFraction: Float, onSizeChange: (Float) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        colors.forEachIndexed { idx, (name, _) ->
            if (idx == colorIdx) {
                Button(onClick = {}) { Text(name) }
            } else {
                OutlinedButton(onClick = { onColorChange(idx) }) { Text(name) }
            }
        }
    }
    if (fontOptions.isNotEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val defaultLabel = "Default"
            if (fontIdx < 0) {
                Button(onClick = {}) { Text(defaultLabel) }
            } else {
                OutlinedButton(onClick = { onFontChange(-1) }) { Text(defaultLabel) }
            }
            fontOptions.forEachIndexed { idx, (name, _) ->
                if (idx == fontIdx) {
                    Button(onClick = {}) { Text(name, maxLines = 1) }
                } else {
                    OutlinedButton(onClick = { onFontChange(idx) }) { Text(name, maxLines = 1) }
                }
            }
        }
    }
    SizeControl(sizeFraction, onSizeChange)
}

@Composable
private fun SizeControl(sizeFraction: Float, onSizeChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 4.dp))
        Slider(
            value = sizeFraction,
            onValueChange = onSizeChange,
            valueRange = 0.05f..0.40f,
            modifier = Modifier.weight(1f),
        )
        Text("${(sizeFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ApplyToAllToggle(applyToAll: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!applyToAll) {
            Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("This page") }
            OutlinedButton(onClick = { onToggle(true) }, modifier = Modifier.weight(1f)) { Text("All pages") }
        } else {
            OutlinedButton(onClick = { onToggle(false) }, modifier = Modifier.weight(1f)) { Text("This page") }
            Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("All pages") }
        }
    }
}

@Composable
private fun SignatureStampSelector(
    label: String,
    items: List<SavedSignature>,
    selectedName: String?,
    onSelect: (String?) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    if (items.isEmpty()) {
        Text(
            "No items saved yet. Create one in Signatures & Stamps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        // Use Column (not LazyColumn) to avoid nesting unbounded scrollable in verticalScroll parent.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { sig ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(
                        selected = sig.name == selectedName,
                        onClick = { onSelect(sig.name) },
                    )
                    SignaturePreview(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, ComposeColor.Gray),
                        signature = sig,
                    )
                    Text(sig.name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Canvas rendering helpers
// ---------------------------------------------------------------------------

/**
 * Estimates the bounding box of [mark] in canvas-pixel coordinates and tests
 * whether [point] falls inside it.
 */
private fun markContainsPoint(mark: DocumentMark, point: androidx.compose.ui.geometry.Offset, w: Float, h: Float): Boolean {
    val left = mark.offsetX * w
    val top = mark.offsetY * h
    val width = mark.sizeFraction * w
    val height = when (mark.type) {
        MarkType.Text, MarkType.Date, MarkType.Check -> width * 0.5f
        MarkType.Signature, MarkType.Stamp -> width * 0.7f
    }
    return point.x in left..(left + width) && point.y in top..(top + height)
}

/**
 * Draws a single [mark] on a Compose [DrawScope] canvas for the preview.
 * [sigBitmapCache] is a pre-loaded map of signatureName → Bitmap (loaded off UI thread).
 * [fontOptions] is the resolved list of available typefaces.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarkOnCanvas(
    mark: DocumentMark,
    w: Float,
    h: Float,
    isSelected: Boolean,
    fontOptions: List<Pair<String, Typeface>>,
    sigBitmapCache: Map<String, Bitmap>,
) {
    val left = mark.offsetX * w
    val top = mark.offsetY * h
    val markWidth = mark.sizeFraction * w

    when (mark.type) {
        MarkType.Text, MarkType.Date -> {
            val displayText = mark.text
            val textSizePx = markWidth * 0.25f
            drawIntoCanvas { c ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = mark.colorArgb
                    textSize = textSizePx.coerceAtLeast(8f)
                    if (mark.fontKey != null) {
                        fontOptions.firstOrNull { it.first == mark.fontKey }?.second?.let { typeface = it }
                    }
                }
                c.nativeCanvas.drawText(displayText, left, top + textSizePx, paint)
            }
            if (isSelected) {
                val textW = displayText.length * markWidth * 0.15f
                drawSelectionRect(left, top, textW.coerceAtLeast(markWidth * 0.3f), markWidth * 0.3f)
            }
        }
        MarkType.Check -> {
            val textSizePx = markWidth * 0.4f
            drawIntoCanvas { c ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = mark.colorArgb
                    textSize = textSizePx.coerceAtLeast(12f)
                }
                c.nativeCanvas.drawText(mark.checkStyle.symbol, left, top + textSizePx, paint)
            }
            if (isSelected) drawSelectionRect(left, top, markWidth * 0.35f, markWidth * 0.4f)
        }
        MarkType.Signature, MarkType.Stamp -> {
            val sigName = mark.signatureName
            if (sigName != null) {
                val sigImgBitmap = sigBitmapCache[sigName]
                if (sigImgBitmap != null) {
                    val sigNatW = sigImgBitmap.width.toFloat().coerceAtLeast(1f)
                    val sigNatH = sigImgBitmap.height.toFloat().coerceAtLeast(1f)
                    val scale = markWidth / sigNatW
                    val drawH = sigNatH * scale
                    drawImage(
                        image = sigImgBitmap.asImageBitmap(),
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(markWidth.toInt(), drawH.toInt()),
                    )
                    if (isSelected) drawSelectionRect(left, top, markWidth, drawH)
                } else {
                    // Stroke-based signature – look up by name in sigBitmapCache keys absence means strokes
                    // We need the strokes here; they'll be rendered via a separate stroke lookup
                    drawIntoCanvas { c ->
                        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.DKGRAY
                            textSize = (markWidth * 0.15f).coerceAtLeast(10f)
                        }
                        c.nativeCanvas.drawText("[$sigName]", left, top + p.textSize, p)
                    }
                    if (isSelected) drawSelectionRect(left, top, markWidth, markWidth * 0.3f)
                }
            } else {
                // Placeholder when no signature selected
                drawIntoCanvas { c ->
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.GRAY
                        textSize = (markWidth * 0.15f).coerceAtLeast(10f)
                    }
                    c.nativeCanvas.drawText("[${mark.type.label}]", left, top + p.textSize, p)
                }
                if (isSelected) drawSelectionRect(left, top, markWidth, markWidth * 0.3f)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionRect(
    left: Float, top: Float, width: Float, height: Float,
) {
    drawRect(
        color = ComposeColor(0x44_1565C0),
        topLeft = androidx.compose.ui.geometry.Offset(left, top),
        size = androidx.compose.ui.geometry.Size(width, height),
    )
    drawRect(
        color = ComposeColor(0xFF_1565C0.toInt()),
        topLeft = androidx.compose.ui.geometry.Offset(left, top),
        size = androidx.compose.ui.geometry.Size(width, height),
        style = Stroke(width = 2f),
    )
}

// ---------------------------------------------------------------------------
// Export / rendering to file
// ---------------------------------------------------------------------------

private fun exportDocument(
    context: android.content.Context,
    sourceUri: Uri,
    marks: List<DocumentMark>,
    isPdf: Boolean,
    fontOptions: List<Pair<String, Typeface>>,
    signatures: List<SavedSignature>,
): SignedDocument? = runCatching {
    if (isPdf) {
        val outFile = exportMarkedPdf(context, sourceUri, marks, fontOptions, signatures) ?: return@runCatching null
        SignedDocument(outFile, "application/pdf")
    } else {
        val outFile = exportMarkedImage(context, sourceUri, marks, fontOptions, signatures) ?: return@runCatching null
        SignedDocument(outFile, "image/png")
    }
}.getOrNull()

private fun exportMarkedImage(
    context: android.content.Context,
    sourceUri: Uri,
    marks: List<DocumentMark>,
    fontOptions: List<Pair<String, Typeface>>,
    signatures: List<SavedSignature>,
): File? {
    val source = loadBitmap(context.contentResolver, sourceUri) ?: return null
    try {
        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        return try {
            val canvas = Canvas(output)
            marks.forEach { mark ->
                drawMarkOnBitmap(canvas, mark, output.width, output.height, 0, context, fontOptions, signatures)
            }
            deleteOldSignedFiles(context.cacheDir, "filled-image-", ".png")
            File(context.cacheDir, "filled-image-${System.currentTimeMillis()}.png").also { file ->
                FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.PNG, 100, out) }
            }
        } finally {
            output.recycle()
        }
    } finally {
        source.recycle()
    }
}

private fun exportMarkedPdf(
    context: android.content.Context,
    sourceUri: Uri,
    marks: List<DocumentMark>,
    fontOptions: List<Pair<String, Typeface>>,
    signatures: List<SavedSignature>,
): File? {
    val descriptor = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return null
    return descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val outputDocument = PdfDocument()
            try {
                for (pageIdx in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIdx)
                    val bmp = Bitmap.createBitmap(
                        page.width.coerceAtLeast(1),
                        page.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    try {
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val canvas = Canvas(bmp)
                        marks.forEach { mark ->
                            val shouldDraw = mark.applyToAllPages || mark.targetPage == pageIdx
                            if (shouldDraw) {
                                drawMarkOnBitmap(canvas, mark, bmp.width, bmp.height, pageIdx, context, fontOptions, signatures)
                            }
                        }
                        val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, pageIdx + 1).create()
                        val outPage = outputDocument.startPage(pageInfo)
                        outPage.canvas.drawColor(Color.WHITE)
                        outPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                        outputDocument.finishPage(outPage)
                    } finally {
                        bmp.recycle()
                        page.close()
                    }
                }
                deleteOldSignedFiles(context.cacheDir, "filled-pdf-", ".pdf")
                File(context.cacheDir, "filled-pdf-${System.currentTimeMillis()}.pdf").also { file ->
                    FileOutputStream(file).use { out -> outputDocument.writeTo(out) }
                }
            } finally {
                outputDocument.close()
            }
        }
    }
}

/**
 * Renders a [mark] onto an Android [Canvas] at its target position and scale.
 * This is used for the file export path (not the live Compose preview).
 */
private fun drawMarkOnBitmap(
    canvas: Canvas,
    mark: DocumentMark,
    pageWidth: Int,
    pageHeight: Int,
    @Suppress("UNUSED_PARAMETER") pageIdx: Int,
    context: android.content.Context,
    fontOptions: List<Pair<String, Typeface>>,
    signatures: List<SavedSignature>,
) {
    val left = mark.offsetX * pageWidth
    val top = mark.offsetY * pageHeight
    val markWidthPx = mark.sizeFraction * pageWidth

    when (mark.type) {
        MarkType.Text, MarkType.Date -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mark.colorArgb
                textSize = (markWidthPx * 0.25f).coerceAtLeast(8f)
                if (mark.fontKey != null) {
                    fontOptions.firstOrNull { it.first == mark.fontKey }?.second?.let { typeface = it }
                }
            }
            canvas.drawText(mark.text, left, top + paint.textSize, paint)
        }
        MarkType.Check -> {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mark.colorArgb
                textSize = (markWidthPx * 0.4f).coerceAtLeast(12f)
            }
            canvas.drawText(mark.checkStyle.symbol, left, top + paint.textSize, paint)
        }
        MarkType.Signature, MarkType.Stamp -> {
            val sig = signatures.firstOrNull { it.name == mark.signatureName } ?: return
            val sigBitmap = loadSignatureBitmap(context, sig)
            drawSignature(
                canvas, sig, pageWidth, pageHeight,
                mark.sizeFraction, mark.offsetX, mark.offsetY,
                sigBitmap,
            )
            sigBitmap?.recycle()
        }
    }
}

/**
 * Renders a stroke-based [SavedSignature] (no imageFileName) to a [Bitmap] so it can
 * be cached and drawn on the preview canvas without file I/O on the UI thread.
 */
private fun renderStrokeSignatureToBitmap(sig: SavedSignature): Bitmap? {
    val pts = sig.strokes.flatMap { it.points }
    if (pts.isEmpty()) return null
    val minX = pts.minOf { it.x }
    val minY = pts.minOf { it.y }
    val maxX = pts.maxOf { it.x }
    val maxY = pts.maxOf { it.y }
    val w = (maxX - minX).coerceAtLeast(1f).toInt()
    val h = (maxY - minY).coerceAtLeast(1f).toInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = maxOf(2f, w * 0.03f)
    }
    sig.strokes.forEach { stroke ->
        val first = stroke.points.firstOrNull() ?: return@forEach
        val path = android.graphics.Path().apply {
            moveTo(first.x - minX, first.y - minY)
            stroke.points.drop(1).forEach { pt -> lineTo(pt.x - minX, pt.y - minY) }
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

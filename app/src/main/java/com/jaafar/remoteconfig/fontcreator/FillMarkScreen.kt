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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.draw.clip
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
    Check("Checkmark"),
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
private val FLOATING_TOOLBAR_BOTTOM_PADDING = 84.dp
private val PRIMARY_MARK_TOOLS = listOf(MarkType.Text, MarkType.Date, MarkType.Check, MarkType.Signature)

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
        Text(
            "Open a PDF or image and add visual marks: text, date, check, signature, or stamp. " +
                "This is visual document marking — it does not create a certified digital signature " +
                "or preserve editable PDF form fields.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open document\u2026")
        }

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
@OptIn(ExperimentalMaterial3Api::class)
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
    var configSizeFraction by remember { mutableFloatStateOf(0.15f) }
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
    var showToolSheet by remember { mutableStateOf(initialText != null) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showToolMenu by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var nextMarkId by remember { mutableLongStateOf(0L) }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val currentZoomScale by rememberUpdatedState(zoomScale)

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

    // Load document
    LaunchedEffect(documentUri) {
        val mimeType = withContext(Dispatchers.IO) { context.contentResolver.getType(documentUri) }
        val looksLikePdf = withContext(Dispatchers.IO) {
            displayNameForUri(context, documentUri)?.endsWith(".pdf", true) == true
        }
        if (mimeType == "application/pdf" || looksLikePdf) {
            val count = withContext(Dispatchers.IO) { getPdfPageCount(context, documentUri) }
            val bmp = withContext(Dispatchers.IO) { renderPdfPage(context, documentUri, 0) }
            isPdf = true
            pageCount = count
            currentPage = 0
            previewBitmap = bmp
        } else {
            val bmp = withContext(Dispatchers.IO) { loadBitmap(context.contentResolver, documentUri) }
            isPdf = false
            pageCount = 0
            previewBitmap = bmp
        }
    }

    // Reload PDF page when page changes
    LaunchedEffect(currentPage) {
        if (!isPdf) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) { renderPdfPage(context, documentUri, currentPage) }
        previewBitmap = bmp
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
            id = ++nextMarkId,
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

    fun updateSelectedMark(transform: (DocumentMark) -> DocumentMark) {
        val idx = marks.indexOfFirst { it.id == selectedMarkId }
        if (idx < 0) return
        marks[idx] = transform(marks[idx])
    }

    fun updateSelectedMark() {
        updateSelectedMark { m ->
            m.copy(
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
    }

    fun duplicateSelectedMark() {
        val mark = selectedMark ?: return
        val duplicate = mark.copy(
            id = ++nextMarkId,
            offsetX = (mark.offsetX + 0.04f).coerceIn(0f, 0.92f),
            offsetY = (mark.offsetY + 0.04f).coerceIn(0f, 0.92f),
        )
        marks.add(duplicate)
        selectedMarkId = duplicate.id
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

    LaunchedEffect(currentPage, previewBitmap) {
        zoomScale = 1f
        horizontalScrollState.scrollTo(0)
        verticalScrollState.scrollTo(0)
    }

    val pageLabel = if (isPdf && pageCount > 0) "Page ${currentPage + 1} of $pageCount" else "Page 1 of 1"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageLabel, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = back) { FillMarkBackIcon() } },
                actions = {
                    IconButton(
                        onClick = ::triggerExport,
                        enabled = !isProcessing && marks.isNotEmpty() && previewBitmap != null,
                    ) {
                        FillMarkShareIcon()
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) { FillMarkMoreIcon() }
                        DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Open another document") },
                                onClick = {
                                    showTopMenu = false
                                    back()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Reset zoom") },
                                onClick = {
                                    showTopMenu = false
                                    zoomScale = 1f
                                },
                            )
                            if (marks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Clear marks") },
                                    onClick = {
                                        showTopMenu = false
                                        marks.clear()
                                        selectedMarkId = null
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PRIMARY_MARK_TOOLS.forEach { tool ->
                        CompactToolButton(
                            label = tool.label,
                            selected = activeTool == tool,
                            modifier = Modifier.weight(1f),
                        ) {
                            activeTool = tool
                            selectedMarkId = null
                            showToolSheet = true
                        }
                    }
                    Box {
                        CompactToolButton(
                            label = "More",
                            selected = activeTool != null && activeTool !in PRIMARY_MARK_TOOLS,
                            modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                        ) { showToolMenu = true }
                        DropdownMenu(expanded = showToolMenu, onDismissRequest = { showToolMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(MarkType.Stamp.label) },
                                onClick = {
                                    showToolMenu = false
                                    activeTool = MarkType.Stamp
                                    selectedMarkId = null
                                    showToolSheet = true
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val bitmap = previewBitmap
            if (bitmap != null) {
                val previewAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                        .horizontalScroll(horizontalScrollState)
                        .verticalScroll(verticalScrollState)
                        .then(
                            if (isPdf && pageCount > 1 && zoomScale <= 1.05f && selectedMarkId == null) {
                                Modifier.pointerInput(isPdf, pageCount, currentPage, zoomScale, selectedMarkId) {
                                    var accumulatedDrag = 0f
                                    detectHorizontalDragGestures(
                                        onHorizontalDrag = { change, dragAmount ->
                                            accumulatedDrag += dragAmount
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            val threshold = canvasDisplaySize.width * 0.12f
                                            when {
                                                accumulatedDrag < -threshold && currentPage < pageCount - 1 -> currentPage++
                                                accumulatedDrag > threshold && currentPage > 0 -> currentPage--
                                            }
                                            accumulatedDrag = 0f
                                        },
                                        onDragCancel = { accumulatedDrag = 0f },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                zoomScale = (currentZoomScale * zoom).coerceIn(1f, 4f)
                            }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val documentHeight = maxWidth / previewAspect
                    Box(
                        Modifier
                            .padding(12.dp)
                            .requiredWidth(maxWidth * zoomScale)
                            .requiredHeight(documentHeight * zoomScale)
                            .clip(MaterialTheme.shapes.medium)
                            .background(ComposeColor.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
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
                                .pointerInput(activeTool) {
                                    detectTapGestures { offset ->
                                        val w = canvasDisplaySize.width.toFloat().coerceAtLeast(1f)
                                        val h = canvasDisplaySize.height.toFloat().coerceAtLeast(1f)
                                        val hit = marks.lastOrNull { mark ->
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
                                        updateSelectedMark { mark ->
                                            mark.copy(
                                                offsetX = (mark.offsetX + dragAmount.x / w).coerceIn(0f, 1f),
                                                offsetY = (mark.offsetY + dragAmount.y / h).coerceIn(0f, 1f),
                                            )
                                        }
                                    }
                                },
                        ) {
                            val w = size.width
                            val h = size.height
                            marks.forEach { mark ->
                                val isSelected = mark.id == selectedMarkId
                                drawMarkOnCanvas(mark, w, h, isSelected, fontOptions, sigBitmapCache)
                            }
                        }
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading document\u2026", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (selectedMark != null) {
                FloatingMarkToolbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = FLOATING_TOOLBAR_BOTTOM_PADDING),
                    textColors = textColors,
                    selectedColor = selectedMark.colorArgb,
                    onDecreaseSize = {
                        configSizeFraction = (selectedMark.sizeFraction - 0.03f).coerceAtLeast(0.05f)
                        updateSelectedMark { it.copy(sizeFraction = configSizeFraction) }
                    },
                    onIncreaseSize = {
                        configSizeFraction = (selectedMark.sizeFraction + 0.03f).coerceAtMost(0.40f)
                        updateSelectedMark { it.copy(sizeFraction = configSizeFraction) }
                    },
                    onColorSelected = { color ->
                        configColorIdx = textColors.indexOfFirst { it.second == color }.takeIf { it >= 0 } ?: configColorIdx
                        updateSelectedMark { it.copy(colorArgb = color) }
                    },
                    onDuplicate = ::duplicateSelectedMark,
                    onDelete = {
                        marks.removeIf { it.id == selectedMarkId }
                        selectedMarkId = null
                    },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (status.isNotBlank()) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (showToolSheet && activeTool != null) {
        ModalBottomSheet(
            onDismissRequest = { showToolSheet = false },
        ) {
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
    }
}

/**
 * Draws a three-node share/network graph icon (three dots connected by two lines)
 * without requiring the material-icons-extended library.
 */
@Composable
private fun FillMarkBackIcon() {
    val color = LocalContentColor.current
    Canvas(
        Modifier
            .size(24.dp)
            .semantics { contentDescription = "Back" },
    ) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * 0.72f, size.height * 0.18f), Offset(size.width * 0.28f, size.height * 0.5f), stroke)
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.5f), Offset(size.width * 0.72f, size.height * 0.82f), stroke)
    }
}

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

@Composable
private fun FillMarkMoreIcon() {
    val color = LocalContentColor.current
    Canvas(
        Modifier
            .size(24.dp)
            .semantics { contentDescription = "More options" },
    ) {
        val radius = size.minDimension * 0.10f
        val centerX = size.width / 2f
        drawCircle(color, radius, Offset(centerX, size.height * 0.28f))
        drawCircle(color, radius, Offset(centerX, size.height * 0.5f))
        drawCircle(color, radius, Offset(centerX, size.height * 0.72f))
    }
}

@Composable
private fun CompactToolButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accessibilityLabel: String = label,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = accessibilityLabel },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun FloatingMarkToolbar(
    modifier: Modifier = Modifier,
    textColors: List<Pair<String, Int>>,
    selectedColor: Int,
    onDecreaseSize: () -> Unit,
    onIncreaseSize: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactToolButton(
                label = "−",
                selected = false,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                accessibilityLabel = "Decrease size",
                onClick = onDecreaseSize,
            )
            CompactToolButton(
                label = "+",
                selected = false,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                accessibilityLabel = "Increase size",
                onClick = onIncreaseSize,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                textColors.forEach { (name, color) ->
                    ColorCircleButton(
                        label = name,
                        color = ComposeColor(color),
                        selected = selectedColor == color,
                    ) { onColorSelected(color) }
                }
            }
            CompactToolButton(label = "Duplicate", selected = false, onClick = onDuplicate)
            CompactToolButton(label = "Delete", selected = false, onClick = onDelete)
        }
    }
}

@Composable
private fun ColorCircleButton(
    label: String,
    color: ComposeColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (selected) 32.dp else 28.dp)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            when (tool) {
                MarkType.Text -> "Text options"
                MarkType.Date -> "Date options"
                MarkType.Check -> "Checkmark options"
                MarkType.Signature -> "Signature options"
                MarkType.Stamp -> "Stamp options"
            },
            style = MaterialTheme.typography.titleMedium,
        )
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
                ControlGroup("Quick marks") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QUICK_MARK_PRESETS.forEach { preset ->
                            OutlinedButton(onClick = {
                                onConfigTextChange(preset)
                                onTextCommit()
                            }) { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        }
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
                ControlGroup("Preview") {
                    Text("Date: $today (tap on the document to place)", style = MaterialTheme.typography.bodySmall)
                }
                TextMarkStyleControls(
                    configColorIdx, onColorChange, textColors,
                    configFontIdx, onFontChange, fontOptions,
                    configSizeFraction, onSizeChange,
                )
            }
            MarkType.Check -> {
                ControlGroup("Style") {
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
    ControlGroup("Color") {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            colors.forEachIndexed { idx, (name, color) ->
                ColorCircleButton(
                    label = name,
                    color = ComposeColor(color),
                    selected = idx == colorIdx,
                ) { onColorChange(idx) }
            }
        }
    }
    if (fontOptions.isNotEmpty()) {
        ControlGroup("Font") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
    }
    SizeControl(sizeFraction, onSizeChange)
}

@Composable
private fun SizeControl(sizeFraction: Float, onSizeChange: (Float) -> Unit) {
    ControlGroup("Size") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(
                value = sizeFraction,
                onValueChange = onSizeChange,
                valueRange = 0.05f..0.40f,
                modifier = Modifier.weight(1f),
            )
            Text("${(sizeFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ApplyToAllToggle(applyToAll: Boolean, onToggle: (Boolean) -> Unit) {
    ControlGroup("Placement") {
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
}

@Composable
private fun ControlGroup(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
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
        MarkType.Text, MarkType.Date, MarkType.Check -> width * 0.3f
        MarkType.Signature, MarkType.Stamp -> width * 0.5f
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

package com.jaafar.remoteconfig.fontcreator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class SignaturePage { Library, Editor, ImportStamp, Apply }

/** Preset text labels available as Quick marks in the library. */
internal val QUICK_MARK_PRESETS = listOf("Approved", "Paid", "Received", "Confidential")

@Composable
internal fun SignatureScreen(
    vm: FontCreatorViewModel,
    onQuickMark: (String) -> Unit = {},
    back: () -> Unit,
) {
    var page by remember { mutableStateOf(SignaturePage.Apply) }
    var selectedSignatureName by remember { mutableStateOf<String?>(null) }

    when (page) {
        SignaturePage.Library -> SignatureLibraryScreen(
            vm = vm,
            selectedSignatureName = selectedSignatureName,
            onSelect = { name -> selectedSignatureName = name },
            onCreateNew = { page = SignaturePage.Editor },
            onImportStamp = { page = SignaturePage.ImportStamp },
            onUseSelected = { page = SignaturePage.Apply },
            onQuickMark = onQuickMark,
            back = { page = SignaturePage.Apply },
        )
        SignaturePage.Editor -> SignatureEditorScreen(
            vm = vm,
            onSaved = { name ->
                selectedSignatureName = name
                page = SignaturePage.Apply
            },
            back = { page = SignaturePage.Library },
        )
        SignaturePage.ImportStamp -> ImportStampFromImageScreen(
            vm = vm,
            onSaved = { name ->
                selectedSignatureName = name
                page = SignaturePage.Apply
            },
            back = { page = SignaturePage.Library },
        )
        SignaturePage.Apply -> {
            val sig = vm.signatures.firstOrNull { it.name == selectedSignatureName }
            ApplySignatureScreen(
                vm = vm,
                signature = sig,
                onAddMark = { page = SignaturePage.Library },
                back = back,
            )
        }
    }
}

@Composable
private fun SignatureLibraryScreen(
    vm: FontCreatorViewModel,
    selectedSignatureName: String?,
    onSelect: (String) -> Unit,
    onCreateNew: () -> Unit,
    onImportStamp: () -> Unit,
    onUseSelected: () -> Unit,
    onQuickMark: (String) -> Unit,
    back: () -> Unit,
) {
    val signatures = vm.signatures.filter { it.imageFileName == null }
    val stamps = vm.signatures.filter { it.imageFileName != null }

    Page("Signatures & Stamps", back) {
        if (vm.signatures.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("No saved signatures or stamps yet.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onCreateNew) { Text("Draw a signature") }
                    OutlinedButton(onClick = onImportStamp) { Text("Import a stamp") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (signatures.isNotEmpty()) {
                    item { Text("Signatures", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
                    items(signatures, key = { it.name }) { signature ->
                        MarkLibraryItem(
                            mark = signature,
                            isSelected = signature.name == selectedSignatureName,
                            onSelect = { onSelect(signature.name) },
                            onDelete = {
                                vm.deleteSignature(signature.name)
                                if (selectedSignatureName == signature.name) {
                                    onSelect(vm.signatures.firstOrNull()?.name ?: "")
                                }
                            },
                        )
                    }
                }
                if (stamps.isNotEmpty()) {
                    item { Text("Stamps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                    items(stamps, key = { it.name }) { stamp ->
                        MarkLibraryItem(
                            mark = stamp,
                            isSelected = stamp.name == selectedSignatureName,
                            onSelect = { onSelect(stamp.name) },
                            onDelete = {
                                vm.deleteSignature(stamp.name)
                                if (selectedSignatureName == stamp.name) {
                                    onSelect(vm.signatures.firstOrNull()?.name ?: "")
                                }
                            },
                        )
                    }
                }
                item {
                    QuickMarksSection(onQuickMark = onQuickMark)
                }
            }
            HorizontalDivider()
            OutlinedButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) { Text("Draw a new signature") }
            OutlinedButton(onClick = onImportStamp, modifier = Modifier.fillMaxWidth()) { Text("Import a new stamp") }
            Button(
                onClick = onUseSelected,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedSignatureName != null && vm.signatures.any { it.name == selectedSignatureName },
            ) {
                Text("Use selected mark")
            }
        }
    }
}

@Composable
private fun QuickMarksSection(onQuickMark: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Quick marks", style = MaterialTheme.typography.titleSmall)
        Text(
            "Tap a preset to add it as a text mark in Fill & Mark Document.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QUICK_MARK_PRESETS.forEach { label ->
                OutlinedButton(
                    onClick = { onQuickMark(label) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MarkLibraryItem(
    mark: SavedSignature,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        Modifier.fillMaxWidth().border(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shape = MaterialTheme.shapes.medium,
        ).clickable { onSelect() }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)
            SignaturePreview(Modifier.size(88.dp, 56.dp), mark)
            Column(Modifier.weight(1f)) {
                Text(mark.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (mark.imageFileName != null) "Stamp" else "Signature",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isSelected) Text("Selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun ImportStampFromImageScreen(
    vm: FontCreatorViewModel,
    onSaved: (String) -> Unit,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("My stamp") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var removeWhiteBackground by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(rawBitmap) {
        val bitmapToRecycle = rawBitmap
        onDispose { bitmapToRecycle?.recycle() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        status = ""
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(context.contentResolver, uri) }
            rawBitmap = bitmap
            if (bitmap == null) status = "Could not load image."
        }
    }

    var processedPreview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(rawBitmap, removeWhiteBackground) {
        val old = processedPreview
        processedPreview = null
        old?.recycle()
        val src = rawBitmap ?: return@LaunchedEffect
        if (!removeWhiteBackground) return@LaunchedEffect
        var processed: Bitmap? = null
        try {
            processed = withContext(Dispatchers.Default) { removeNearWhitePixels(src) }
            processedPreview = processed
            processed = null
        } finally {
            processed?.recycle()
        }
    }
    DisposableEffect(Unit) {
        onDispose { processedPreview?.recycle() }
    }

    Page("Import Stamp", back) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Stamp name") },
            singleLine = true,
        )
        OutlinedButton(
            onClick = { picker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        ) { Text(if (selectedUri == null) "Choose image" else "Choose a different image") }
        if (rawBitmap != null) {
            val bitmap = rawBitmap!!
            val displayBitmap = processedPreview ?: bitmap
            Box(
                Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()).border(1.dp, ComposeColor.Gray)
            ) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = removeWhiteBackground,
                    onCheckedChange = { removeWhiteBackground = it },
                )
                Text("Remove white background", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text("Select an image to preview and import it as a reusable stamp.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val uri = selectedUri ?: return@Button
                scope.launch {
                    saving = true
                    status = "Saving stamp\u2026"
                    val savedName = withContext(Dispatchers.IO) {
                        runCatching { vm.saveSignatureFromImage(context.contentResolver, uri, name, removeWhiteBackground) }.getOrNull()
                    }
                    saving = false
                    if (savedName != null) {
                        status = "Stamp saved."
                        onSaved(savedName)
                    } else {
                        status = "Could not save this stamp image."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && selectedUri != null,
        ) { Text("Save stamp") }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SignatureEditorScreen(
    vm: FontCreatorViewModel,
    onSaved: (String) -> Unit,
    back: () -> Unit,
) {
    var name by remember { mutableStateOf("My signature") }
    var strokes by remember { mutableStateOf<List<GlyphStroke>>(emptyList()) }
    var active by remember { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(1f to 1f) }
    var status by remember { mutableStateOf("") }

    Page("New Signature", back) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Signature name") },
            singleLine = true,
        )
        Canvas(
            Modifier.fillMaxWidth().height(220.dp)
                .background(ComposeColor.White)
                .border(1.dp, ComposeColor.Gray)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { active = listOf(GlyphPoint(it.x, it.y)) },
                        onDrag = { change, _ ->
                            change.consume()
                            val next = GlyphPoint(change.position.x, change.position.y)
                            if (active.lastOrNull()?.let { hypotSquared(it, next) > 9f } != false) {
                                active = active + next
                            }
                        },
                        onDragEnd = {
                            if (active.size > 1) strokes = strokes + GlyphStroke(active)
                            active = emptyList()
                        },
                        onDragCancel = { active = emptyList() },
                    )
                }
        ) {
            canvasSize = size.width to size.height
            (strokes.map { it.points } + listOf(active)).forEach { points ->
                if (points.size > 1) {
                    drawPath(
                        ComposePath().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        },
                        ComposeColor.Black,
                        style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { strokes = emptyList(); active = emptyList() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
            OutlinedButton(onClick = { strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Undo") }
            Button(
                onClick = {
                    val savedName = vm.saveSignature(name, strokes, canvasSize.first, canvasSize.second)
                    status = "Saved."
                    onSaved(savedName)
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("Save signature") }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ApplySignatureScreen(
    vm: FontCreatorViewModel,
    signature: SavedSignature?,
    onAddMark: () -> Unit,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signatureImageFile = signature?.let { vm.signatureImageFile(it) }
    val signatureImageBitmap = rememberImageBitmap(signatureImageFile)
    var signatureScale by remember { mutableFloatStateOf(22f) }
    var sigOffsetX by remember { mutableFloatStateOf(0.5f) }
    var sigOffsetY by remember { mutableFloatStateOf(0.82f) }
    var isProcessing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var isPdf by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var applyToAll by remember { mutableStateOf(false) }
    DisposableEffect(previewBitmap) {
        val bitmapToRecycle = previewBitmap
        onDispose { bitmapToRecycle?.recycle() }
    }

    LaunchedEffect(selectedFileUri, currentPage) {
        val uri = selectedFileUri ?: return@LaunchedEffect
        if (!isPdf) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) { renderPdfPage(context, uri, currentPage) }
        if (bitmap != null) {
            previewBitmap = bitmap
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedFileUri = uri
            status = ""
            sigOffsetX = 0.5f
            sigOffsetY = 0.82f
            scope.launch {
                val mimeType = context.contentResolver.getType(uri)
                val looksLikePdf = displayNameForUri(context, uri)?.endsWith(".pdf", true) == true
                if (mimeType == "application/pdf" || looksLikePdf) {
                    isPdf = true
                    currentPage = 0
                    applyToAll = false
                    val count = withContext(Dispatchers.IO) { getPdfPageCount(context, uri) }
                    pageCount = count
                    val bitmap = withContext(Dispatchers.IO) { renderPdfPage(context, uri, 0) }
                    previewBitmap = bitmap
                } else {
                    isPdf = false
                    pageCount = 0
                    val bitmap = withContext(Dispatchers.IO) { loadBitmap(context.contentResolver, uri) }
                    previewBitmap = bitmap
                }
            }
        }
    }

    Page("Sign or Stamp a Document", back) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (signature != null) {
                        SignaturePreview(Modifier.size(88.dp, 56.dp), signature)
                        Text(signature.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = onAddMark) { Text("Change mark") }
                    } else {
                        Text(
                            "No mark selected.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = onAddMark) { Text("Add mark") }
                    }
                }
            }

            if (signature != null) {
                Text("Mark size", style = MaterialTheme.typography.titleMedium)
                Slider(value = signatureScale, onValueChange = { signatureScale = it }, valueRange = 12f..35f)
                Text("${signatureScale.toInt()}% of page width", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()
            }

            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
            ) {
                Text(if (selectedFileUri == null) "Choose image or PDF" else "Choose a different file")
            }

            if (selectedFileUri == null) {
                Text(
                    "Choose a document above to see the mark placement preview.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (selectedFileUri != null) {
                val bitmap = previewBitmap
                if (bitmap != null) {
                    Text("Mark placement", style = MaterialTheme.typography.titleMedium)
                    Text("Drag the mark to position it.", style = MaterialTheme.typography.bodySmall)

                    if (isPdf && pageCount > 0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = { if (currentPage > 0) currentPage-- }, enabled = currentPage > 0) { Text("\u2190 Previous") }
                            Text("Page ${currentPage + 1} of $pageCount", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { if (currentPage < pageCount - 1) currentPage++ }, enabled = currentPage < pageCount - 1) { Text("Next \u2192") }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!applyToAll) {
                                Button(onClick = { applyToAll = false }, modifier = Modifier.weight(1f)) { Text("Apply to this page") }
                                OutlinedButton(onClick = { applyToAll = true }, modifier = Modifier.weight(1f)) { Text("Apply to all pages") }
                            } else {
                                OutlinedButton(onClick = { applyToAll = false }, modifier = Modifier.weight(1f)) { Text("Apply to this page") }
                                Button(onClick = { applyToAll = true }, modifier = Modifier.weight(1f)) { Text("Apply to all pages") }
                            }
                        }
                    }

                    val previewAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(previewAspect).border(1.dp, ComposeColor.Gray),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures { offset ->
                                        sigOffsetX = (offset.x / size.width).coerceIn(0f, 1f)
                                        sigOffsetY = (offset.y / size.height).coerceIn(0f, 1f)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            sigOffsetX = (offset.x / size.width).coerceIn(0f, 1f)
                                            sigOffsetY = (offset.y / size.height).coerceIn(0f, 1f)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            sigOffsetX = (change.position.x / size.width).coerceIn(0f, 1f)
                                            sigOffsetY = (change.position.y / size.height).coerceIn(0f, 1f)
                                        },
                                    )
                                },
                        ) {
                            val sigPoints = if (signatureImageBitmap == null && signature != null) signature.strokes.flatMap { it.points } else emptyList()
                            if (signatureImageBitmap != null || sigPoints.isNotEmpty()) {
                                val sigNatWidth = signatureImageBitmap?.width?.toFloat()?.coerceAtLeast(1f)
                                    ?: (sigPoints.maxOf { it.x } - sigPoints.minOf { it.x }).coerceAtLeast(1f)
                                val sigNatHeight = signatureImageBitmap?.height?.toFloat()?.coerceAtLeast(1f)
                                    ?: (sigPoints.maxOf { it.y } - sigPoints.minOf { it.y }).coerceAtLeast(1f)
                                val targetSigWidth = size.width * (signatureScale / 100f).coerceIn(0.12f, 0.35f)
                                val scale = targetSigWidth / sigNatWidth
                                val left = (sigOffsetX * size.width).coerceIn(0f, (size.width - sigNatWidth * scale).coerceAtLeast(0f))
                                val top = (sigOffsetY * size.height).coerceIn(0f, (size.height - sigNatHeight * scale).coerceAtLeast(0f))
                                if (signatureImageBitmap != null) {
                                    drawImage(
                                        image = signatureImageBitmap.asImageBitmap(),
                                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                                        dstSize = IntSize((sigNatWidth * scale).toInt(), (sigNatHeight * scale).toInt()),
                                    )
                                } else if (signature != null) {
                                    val minX = sigPoints.minOf { it.x }
                                    val minY = sigPoints.minOf { it.y }
                                    signature.strokes.forEach { stroke ->
                                        if (stroke.points.size > 1) {
                                            drawPath(
                                                ComposePath().apply {
                                                    moveTo(left + (stroke.points.first().x - minX) * scale, top + (stroke.points.first().y - minY) * scale)
                                                    stroke.points.drop(1).forEach { pt ->
                                                        lineTo(left + (pt.x - minX) * scale, top + (pt.y - minY) * scale)
                                                    }
                                                },
                                                ComposeColor.Black,
                                                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                            )
                                        }
                                    }
                                }
                                drawRect(
                                    color = ComposeColor(0x44_1565C0),
                                    topLeft = Offset(left, top),
                                    size = androidx.compose.ui.geometry.Size(sigNatWidth * scale, sigNatHeight * scale),
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val uri = selectedFileUri ?: return@Button
                            val sig = signature ?: return@Button
                            scope.launch {
                                isProcessing = true
                                status = "Applying mark\u2026"
                                val result = withContext(Dispatchers.IO) {
                                    signDocumentWithPage(
                                        context, uri, sig,
                                        signatureScale / 100f, sigOffsetX, sigOffsetY,
                                        if (isPdf) currentPage else 0,
                                        if (isPdf) applyToAll else false,
                                    )
                                }
                                isProcessing = false
                                if (result != null) {
                                    shareDocument(context, result.file, result.mimeType)
                                    status = "Signed ${if (result.mimeType == "application/pdf") "PDF" else "image"} ready to share."
                                } else {
                                    status = "Unable to sign the selected file."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing && signature != null,
                    ) {
                        Text("Apply mark and share")
                    }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading preview\u2026", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun SignaturePreview(modifier: Modifier, signature: SavedSignature) {
    val context = LocalContext.current
    val imageBitmap = rememberImageBitmap(
        signature.imageFileName?.let { fileName -> File(context.filesDir, fileName).takeIf { it.exists() } }
    )
    if (imageBitmap != null) {
        Box(modifier.background(ComposeColor.White).border(1.dp, ComposeColor.LightGray)) {
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Canvas(modifier.background(ComposeColor.White).border(1.dp, ComposeColor.LightGray)) {
            val points = signature.strokes.flatMap { it.points }
            val minX = points.minOfOrNull { it.x } ?: 0f
            val minY = points.minOfOrNull { it.y } ?: 0f
            val maxX = points.maxOfOrNull { it.x } ?: 1f
            val maxY = points.maxOfOrNull { it.y } ?: 1f
            val width = (maxX - minX).coerceAtLeast(1f)
            val height = (maxY - minY).coerceAtLeast(1f)
            val scale = minOf(size.width * .82f / width, size.height * .62f / height)
            val left = (size.width - width * scale) / 2f
            val top = (size.height - height * scale) / 2f
            signature.strokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    drawPath(
                        ComposePath().apply {
                            moveTo(left + (stroke.points.first().x - minX) * scale, top + (stroke.points.first().y - minY) * scale)
                            stroke.points.drop(1).forEach { point ->
                                lineTo(left + (point.x - minX) * scale, top + (point.y - minY) * scale)
                            }
                        },
                        ComposeColor.Black,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberImageBitmap(file: File?): Bitmap? {
    val path = file?.absolutePath
    val bitmapState = produceState<Bitmap?>(initialValue = null, path) {
        value = null
        val decoded = if (path == null) null else withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
        value = decoded
        try {
            awaitCancellation()
        } finally {
            decoded?.recycle()
        }
    }
    return bitmapState.value
}

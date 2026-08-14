package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

private enum class SignaturePage { Library, Editor, ImportStamp, Apply }

@Composable
internal fun SignatureScreen(vm: FontCreatorViewModel, back: () -> Unit) {
    var page by remember { mutableStateOf(SignaturePage.Library) }
    var selectedSignatureName by remember { mutableStateOf<String?>(null) }

    when (page) {
        SignaturePage.Library -> SignatureLibraryScreen(
            vm = vm,
            selectedSignatureName = selectedSignatureName,
            onSelect = { name -> selectedSignatureName = name },
            onCreateNew = { page = SignaturePage.Editor },
            onImportStamp = { page = SignaturePage.ImportStamp },
            onUseSelected = { page = SignaturePage.Apply },
            back = back,
        )
        SignaturePage.Editor -> SignatureEditorScreen(
            vm = vm,
            onSaved = { name ->
                selectedSignatureName = name
                page = SignaturePage.Library
            },
            back = { page = SignaturePage.Library },
        )
        SignaturePage.ImportStamp -> ImportStampFromImageScreen(
            vm = vm,
            onSaved = { name ->
                selectedSignatureName = name
                page = SignaturePage.Library
            },
            back = { page = SignaturePage.Library },
        )
        SignaturePage.Apply -> {
            val sig = vm.signatures.firstOrNull { it.name == selectedSignatureName }
                ?: vm.signatures.firstOrNull()
            if (sig == null) {
                page = SignaturePage.Library
            } else {
                ApplySignatureScreen(
                    vm = vm,
                    signature = sig,
                    back = { page = SignaturePage.Library },
                )
            }
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
    back: () -> Unit,
) {
    Page("Signatures", back) {
        if (vm.signatures.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("No saved signatures yet.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onCreateNew) { Text("Create your first signature") }
                    OutlinedButton(onClick = onImportStamp) { Text("Add stamp from image") }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.signatures, key = { it.name }) { signature ->
                    val isSelected = signature.name == selectedSignatureName
                    OutlinedCard(
                        Modifier.fillMaxWidth().border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.medium,
                        ).clickable { onSelect(signature.name) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = { onSelect(signature.name) })
                            SignaturePreview(Modifier.size(88.dp, 56.dp), signature)
                            Column(Modifier.weight(1f)) {
                                Text(signature.name, style = MaterialTheme.typography.titleSmall)
                                if (isSelected) Text("Selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            OutlinedButton(onClick = {
                                vm.deleteSignature(signature.name)
                                if (selectedSignatureName == signature.name) {
                                    onSelect(vm.signatures.firstOrNull()?.name ?: "")
                                }
                            }) { Text("Delete") }
                        }
                    }
                }
            }
            HorizontalDivider()
            OutlinedButton(onClick = onCreateNew, modifier = Modifier.fillMaxWidth()) { Text("Create new signature") }
            OutlinedButton(onClick = onImportStamp, modifier = Modifier.fillMaxWidth()) { Text("Add stamp from image") }
            Button(
                onClick = onUseSelected,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedSignatureName != null && vm.signatures.any { it.name == selectedSignatureName },
            ) {
                Text("Use selected signature")
            }
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
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    DisposableEffect(previewBitmap) {
        val bitmapToRecycle = previewBitmap
        onDispose { bitmapToRecycle?.recycle() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        status = ""
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(context.contentResolver, uri) }
            previewBitmap = bitmap
            if (bitmap == null) status = "Could not load image."
        }
    }

    Page("Add Stamp from Image", back) {
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
        if (previewBitmap != null) {
            val bitmap = previewBitmap!!
            Box(
                Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()).border(1.dp, ComposeColor.Gray)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            Text("Select an image to import it as a reusable stamp.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val uri = selectedUri ?: return@Button
                scope.launch {
                    saving = true
                    status = "Saving stamp…"
                    val savedName = withContext(Dispatchers.IO) {
                        runCatching { vm.saveSignatureFromImage(context.contentResolver, uri, name) }.getOrNull()
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
    signature: SavedSignature,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signatureImageBitmap = rememberImageBitmap(vm.signatureImageFile(signature))
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

    // Reload PDF preview when the page changes
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

    Page("Apply Signature", back) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Show active signature info
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SignaturePreview(Modifier.size(88.dp, 56.dp), signature)
                    Text(signature.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                }
            }

            Text("Signed output size", style = MaterialTheme.typography.titleMedium)
            Slider(value = signatureScale, onValueChange = { signatureScale = it }, valueRange = 12f..35f)
            Text("${signatureScale.toInt()}% of page width", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()

            // File picker button
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
            ) {
                Text(if (selectedFileUri == null) "Choose image or PDF" else "Choose a different file")
            }

            if (selectedFileUri == null) {
                Text(
                    "Choose a file above to see the signature placement preview.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Preview + signature placement
            if (selectedFileUri != null) {
                val bitmap = previewBitmap
                if (bitmap != null) {
                    Text("Signature placement", style = MaterialTheme.typography.titleMedium)
                    Text("Drag the signature to position it.", style = MaterialTheme.typography.bodySmall)

                    // PDF page navigation
                    if (isPdf && pageCount > 0) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = { if (currentPage > 0) currentPage-- }, enabled = currentPage > 0) { Text("← Previous") }
                            Text("Page ${currentPage + 1} of $pageCount", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { if (currentPage < pageCount - 1) currentPage++ }, enabled = currentPage < pageCount - 1) { Text("Next →") }
                        }

                        // Apply scope toggle
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

                    // Preview canvas: real image/PDF page behind, signature draggable on top
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
                            val sigPoints = if (signatureImageBitmap == null) signature.strokes.flatMap { it.points } else emptyList()
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
                                    drawIntoCanvas { canvas ->
                                        val dst = android.graphics.RectF(
                                            left,
                                            top,
                                            left + sigNatWidth * scale,
                                            top + sigNatHeight * scale,
                                        )
                                        canvas.nativeCanvas.drawBitmap(signatureImageBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG))
                                    }
                                } else {
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

                    // Sign and share button
                    Button(
                        onClick = {
                            val uri = selectedFileUri ?: return@Button
                            scope.launch {
                                isProcessing = true
                                status = "Signing document…"
                                val result = withContext(Dispatchers.IO) {
                                    signDocumentWithPage(
                                        context, uri, signature,
                                        signatureScale / 100f, sigOffsetX, sigOffsetY,
                                        if (isPdf) currentPage else 0,
                                        if (isPdf) applyToAll else false,
                                    )
                                }
                                isProcessing = false
                                if (result != null) {
                                    shareSignedDocument(context, result)
                                    status = "Signed ${if (result.mimeType == "application/pdf") "PDF" else "image"} ready to share."
                                } else {
                                    status = "Unable to sign the selected file."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing,
                    ) {
                        Text("Sign and share")
                    }
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading preview…", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SignaturePreview(modifier: Modifier, signature: SavedSignature) {
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
private fun rememberImageBitmap(file: File?): Bitmap? {
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

private data class SignedDocument(val file: File, val mimeType: String)

private suspend fun signDocumentWithPage(
    context: android.content.Context,
    sourceUri: Uri,
    signature: SavedSignature,
    widthFraction: Float,
    offsetXFraction: Float,
    offsetYFraction: Float,
    targetPage: Int,
    applyToAll: Boolean,
): SignedDocument? = withContext(Dispatchers.IO) {
    runCatching {
        val mimeType = context.contentResolver.getType(sourceUri)
        val looksLikePdf = displayNameForUri(context, sourceUri)?.endsWith(".pdf", true) == true
        if (mimeType == "application/pdf" || looksLikePdf) {
            SignedDocument(signPdf(context, sourceUri, signature, widthFraction, offsetXFraction, offsetYFraction, targetPage, applyToAll) ?: return@runCatching null, "application/pdf")
        } else {
            SignedDocument(signImage(context, sourceUri, signature, widthFraction, offsetXFraction, offsetYFraction) ?: return@runCatching null, "image/png")
        }
    }.getOrNull()
}

private fun signImage(
    context: android.content.Context,
    sourceUri: Uri,
    signature: SavedSignature,
    widthFraction: Float,
    offsetXFraction: Float,
    offsetYFraction: Float,
): File? {
    val source = loadBitmap(context.contentResolver, sourceUri) ?: return null
    val signatureBitmap = loadSignatureBitmap(context, signature)
    try {
        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        return try {
            drawSignature(Canvas(output), signature, output.width, output.height, widthFraction, offsetXFraction, offsetYFraction, signatureBitmap)
            deleteOldSignedFiles(context.cacheDir, "signed-image-", ".png")
            File(context.cacheDir, "signed-image-${System.currentTimeMillis()}.png").also { file ->
                FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            output.recycle()
        }
    } finally {
        signatureBitmap?.recycle()
        source.recycle()
    }
}

private fun signPdf(
    context: android.content.Context,
    sourceUri: Uri,
    signature: SavedSignature,
    widthFraction: Float,
    offsetXFraction: Float,
    offsetYFraction: Float,
    targetPage: Int,
    applyToAll: Boolean,
): File? {
    val descriptor = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return null
    val signatureBitmap = loadSignatureBitmap(context, signature)
    descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val outputDocument = PdfDocument()
            try {
                for (index in 0 until renderer.pageCount) {
                    val page = renderer.openPage(index)
                    val bitmap = Bitmap.createBitmap(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val shouldSign = applyToAll || index == targetPage
                        if (shouldSign) {
                            drawSignature(Canvas(bitmap), signature, bitmap.width, bitmap.height, widthFraction, offsetXFraction, offsetYFraction, signatureBitmap)
                        }
                        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                        val outputPage = outputDocument.startPage(pageInfo)
                        outputPage.canvas.drawColor(Color.WHITE)
                        outputPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        outputDocument.finishPage(outputPage)
                    } finally {
                        bitmap.recycle()
                        page.close()
                    }
                }
                deleteOldSignedFiles(context.cacheDir, "signed-pdf-", ".pdf")
                return File(context.cacheDir, "signed-pdf-${System.currentTimeMillis()}.pdf").also { file ->
                    FileOutputStream(file).use { outputDocument.writeTo(it) }
                }
            } finally {
                outputDocument.close()
                signatureBitmap?.recycle()
            }
        }
    }
}

private fun getPdfPageCount(context: android.content.Context, uri: Uri): Int {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
    return descriptor.use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
}

private fun renderPdfPage(context: android.content.Context, uri: Uri, pageIndex: Int): Bitmap? {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
    return descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            val page = renderer.openPage(safeIndex)
            try {
                val bitmap = Bitmap.createBitmap(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }
}

private fun deleteOldSignedFiles(directory: File, prefix: String, suffix: String) {
    directory.listFiles()?.filter { it.name.startsWith(prefix) && it.name.endsWith(suffix) }?.forEach { it.delete() }
}

private fun drawSignature(
    canvas: Canvas,
    signature: SavedSignature,
    pageWidth: Int,
    pageHeight: Int,
    widthFraction: Float,
    offsetXFraction: Float,
    offsetYFraction: Float,
    signatureBitmap: Bitmap? = null,
) {
    if (signatureBitmap != null) {
        val signatureWidth = signatureBitmap.width.coerceAtLeast(1).toFloat()
        val signatureHeight = signatureBitmap.height.coerceAtLeast(1).toFloat()
        val targetWidth = pageWidth * widthFraction.coerceIn(.12f, .35f)
        val scale = targetWidth / signatureWidth
        val left = (offsetXFraction * pageWidth).coerceIn(0f, (pageWidth - signatureWidth * scale).coerceAtLeast(0f))
        val top = (offsetYFraction * pageHeight).coerceIn(0f, (pageHeight - signatureHeight * scale).coerceAtLeast(0f))
        val destination = android.graphics.RectF(left, top, left + signatureWidth * scale, top + signatureHeight * scale)
        canvas.drawBitmap(signatureBitmap, null, destination, Paint(Paint.ANTI_ALIAS_FLAG))
        return
    }
    val points = signature.strokes.flatMap { it.points }
    if (points.isEmpty()) return
    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }
    val signatureWidth = (maxX - minX).coerceAtLeast(1f)
    val signatureHeight = (maxY - minY).coerceAtLeast(1f)
    val targetWidth = pageWidth * widthFraction.coerceIn(.12f, .35f)
    val scale = targetWidth / signatureWidth
    val left = (offsetXFraction * pageWidth).coerceIn(0f, (pageWidth - signatureWidth * scale).coerceAtLeast(0f))
    val top = (offsetYFraction * pageHeight).coerceIn(0f, (pageHeight - signatureHeight * scale).coerceAtLeast(0f))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = max(2f, targetWidth * .03f)
    }
    signature.strokes.forEach { stroke ->
        val first = stroke.points.firstOrNull() ?: return@forEach
        val path = Path().apply {
            moveTo(left + (first.x - minX) * scale, top + (first.y - minY) * scale)
            stroke.points.drop(1).forEach { point ->
                lineTo(left + (point.x - minX) * scale, top + (point.y - minY) * scale)
            }
        }
        canvas.drawPath(path, paint)
    }
}

private fun loadSignatureBitmap(context: android.content.Context, signature: SavedSignature): Bitmap? {
    val fileName = signature.imageFileName ?: return null
    val file = File(context.filesDir, fileName)
    if (!file.exists()) return null
    return BitmapFactory.decodeFile(file.absolutePath)
}

private fun shareSignedDocument(context: android.content.Context, document: SignedDocument) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", document.file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = document.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share signed file").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun loadBitmap(resolver: android.content.ContentResolver, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        resolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }
    }
}.getOrNull()

private fun displayNameForUri(context: android.content.Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
}

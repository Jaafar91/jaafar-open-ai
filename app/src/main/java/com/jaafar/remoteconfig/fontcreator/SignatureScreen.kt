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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

@Composable
internal fun SignatureScreen(vm: FontCreatorViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(vm.signatures.firstOrNull()?.name ?: "My signature") }
    var strokes by remember { mutableStateOf(vm.signatures.firstOrNull()?.strokes ?: emptyList()) }
    var active by remember { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember {
        mutableStateOf(
            vm.signatures.firstOrNull()?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)
        )
    }
    var selectedSignatureName by remember { mutableStateOf(vm.signatures.firstOrNull()?.name) }
    var signatureScale by remember { mutableFloatStateOf(22f) }
    // Position of the signature as fractions of page size (top-left anchor of the signature bounding box)
    var sigOffsetX by remember { mutableFloatStateOf(0.75f) }
    var sigOffsetY by remember { mutableFloatStateOf(0.82f) }
    var isProcessing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val selectedSignature = vm.signatures.firstOrNull { it.name == selectedSignatureName } ?: vm.signatures.firstOrNull()

    LaunchedEffect(vm.signatures.firstOrNull()?.name, selectedSignatureName) {
        if (selectedSignatureName == null) selectedSignatureName = vm.signatures.firstOrNull()?.name
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val signature = selectedSignature
        if (uri != null && signature != null) {
            scope.launch {
                isProcessing = true
                status = "Signing document..."
                val result = signDocument(context, uri, signature, signatureScale / 100f, sigOffsetX, sigOffsetY)
                isProcessing = false
                if (result != null) {
                    shareSignedDocument(context, result)
                    status = "Signed ${if (result.mimeType == "application/pdf") "PDF" else "image"} ready to share."
                } else {
                    status = "Unable to sign the selected file."
                }
            }
        }
    }

    Page("Signature", back) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Draw your signature, save it on this device, then apply it to an image or PDF.")
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
                        name = savedName
                        selectedSignatureName = savedName
                        status = "Signature saved."
                    },
                    enabled = strokes.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save signature") }
            }
            HorizontalDivider()
            Text("Signed output size", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = signatureScale,
                onValueChange = { signatureScale = it },
                valueRange = 12f..35f,
            )
            Text("${signatureScale.toInt()}% of page width", style = MaterialTheme.typography.bodySmall)
            Text("PDF files are signed on the last page.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Signature placement", style = MaterialTheme.typography.titleMedium)
            Text(
                "Drag the signature preview to set its position on the document.",
                style = MaterialTheme.typography.bodySmall,
            )
            // Interactive placement canvas: tap or drag to move the signature anchor point
            val placementBgColor = ComposeColor(0xFFF5F5F5)
            val placementBorderColor = ComposeColor.Gray
            Canvas(
                modifier = Modifier.fillMaxWidth().height(200.dp)
                    .background(placementBgColor)
                    .border(1.dp, placementBorderColor)
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
                    }
            ) {
                // Draw page outline
                drawRect(
                    color = ComposeColor.White,
                    topLeft = Offset.Zero,
                    size = this.size,
                )
                // Draw dashed grid lines for guidance
                val dashColor = ComposeColor(0xFFCCCCCC)
                for (i in 1..2) {
                    drawLine(dashColor, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), strokeWidth = 1f)
                    drawLine(dashColor, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), strokeWidth = 1f)
                }
                // Draw the signature preview at the chosen position
                val sig = selectedSignature
                if (sig != null) {
                    val sigPoints = sig.strokes.flatMap { it.points }
                    if (sigPoints.isNotEmpty()) {
                        val minX = sigPoints.minOf { it.x }
                        val minY = sigPoints.minOf { it.y }
                        val maxX = sigPoints.maxOf { it.x }
                        val maxY = sigPoints.maxOf { it.y }
                        val sigNatWidth = (maxX - minX).coerceAtLeast(1f)
                        val sigNatHeight = (maxY - minY).coerceAtLeast(1f)
                        val targetSigWidth = size.width * (signatureScale / 100f).coerceIn(0.12f, 0.35f)
                        val scale = targetSigWidth / sigNatWidth
                        // Clamp so the signature stays within the canvas
                        val left = (sigOffsetX * size.width).coerceIn(0f, (size.width - sigNatWidth * scale).coerceAtLeast(0f))
                        val top = (sigOffsetY * size.height).coerceIn(0f, (size.height - sigNatHeight * scale).coerceAtLeast(0f))
                        sig.strokes.forEach { stroke ->
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
                        // Bounding box highlight
                        drawRect(
                            color = ComposeColor(0x44_1565C0),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(sigNatWidth * scale, sigNatHeight * scale),
                        )
                        // Crosshair at the actual (clamped) anchor point
                        val cx = left
                        val cy = top
                        drawLine(ComposeColor(0xFF_1565C0), Offset(cx - 12f, cy), Offset(cx + 12f, cy), strokeWidth = 2f)
                        drawLine(ComposeColor(0xFF_1565C0), Offset(cx, cy - 12f), Offset(cx, cy + 12f), strokeWidth = 2f)
                    }
                } else {
                    val cx = sigOffsetX * size.width
                    val cy = sigOffsetY * size.height
                    drawCircle(ComposeColor(0xFF_BBBBBB), radius = 12f, center = Offset(cx, cy))
                    drawLine(ComposeColor(0xFF_1565C0), Offset(cx - 12f, cy), Offset(cx + 12f, cy), strokeWidth = 2f)
                    drawLine(ComposeColor(0xFF_1565C0), Offset(cx, cy - 12f), Offset(cx, cy + 12f), strokeWidth = 2f)
                }
            }
            Button(
                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedSignature != null && !isProcessing,
            ) {
                Text(if (selectedSignature == null) "Save a signature first" else "Choose image or PDF to sign")
            }
            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            if (vm.signatures.isNotEmpty()) {
                Text("Saved signatures", style = MaterialTheme.typography.titleMedium)
                vm.signatures.forEach { signature ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedSignatureName = signature.name
                            name = signature.name
                            strokes = signature.strokes
                            active = emptyList()
                            canvasSize = signature.canvasWidth to signature.canvasHeight
                            status = "Loaded ${signature.name}."
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SignaturePreview(
                                modifier = Modifier.size(88.dp, 56.dp),
                                signature = signature,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(signature.name, style = MaterialTheme.typography.titleSmall)
                                if (selectedSignatureName == signature.name) {
                                    Text("Used for signing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            OutlinedButton(onClick = {
                                vm.deleteSignature(signature.name)
                                if (selectedSignatureName == signature.name) {
                                    selectedSignatureName = vm.signatures.firstOrNull()?.name
                                }
                                val replacement = vm.signatures.firstOrNull { it.name == selectedSignatureName }
                                if (name == signature.name) name = replacement?.name ?: "My signature"
                                if (replacement == null) {
                                    strokes = emptyList()
                                    active = emptyList()
                                } else {
                                    strokes = replacement.strokes
                                    active = emptyList()
                                    canvasSize = replacement.canvasWidth to replacement.canvasHeight
                                }
                                status = "Signature deleted."
                            }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignaturePreview(modifier: Modifier, signature: SavedSignature) {
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

private data class SignedDocument(val file: File, val mimeType: String)

private suspend fun signDocument(
    context: android.content.Context,
    sourceUri: Uri,
    signature: SavedSignature,
    widthFraction: Float,
    offsetXFraction: Float,
    offsetYFraction: Float,
): SignedDocument? = withContext(Dispatchers.IO) {
    runCatching {
        val mimeType = context.contentResolver.getType(sourceUri)
        val looksLikePdf = displayNameForUri(context, sourceUri)?.endsWith(".pdf", true) == true
        if (mimeType == "application/pdf" || looksLikePdf) {
            SignedDocument(signPdf(context, sourceUri, signature, widthFraction, offsetXFraction, offsetYFraction) ?: return@runCatching null, "application/pdf")
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
    try {
        val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        return try {
            drawSignature(Canvas(output), signature, output.width, output.height, widthFraction, offsetXFraction, offsetYFraction)
            deleteOldSignedFiles(context.cacheDir, "signed-image-", ".png")
            File(context.cacheDir, "signed-image-${System.currentTimeMillis()}.png").also { file ->
                FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            output.recycle()
        }
    } finally {
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
): File? {
    val descriptor = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return null
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
                        if (index == renderer.pageCount - 1) {
                            drawSignature(Canvas(bitmap), signature, bitmap.width, bitmap.height, widthFraction, offsetXFraction, offsetYFraction)
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
) {
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
    // Place the top-left corner of the signature at the chosen fractional position,
    // clamped so the entire signature remains within the page.
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

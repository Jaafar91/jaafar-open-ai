package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val SYSTEM_FONTS = listOf(
    "Default" to Typeface.DEFAULT,
    "Serif" to Typeface.SERIF,
    "Sans-Serif" to Typeface.SANS_SERIF,
    "Monospace" to Typeface.MONOSPACE,
)

@Composable
internal fun PdfFontScreen(vm: FontCreatorViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var selectedFontLabel by remember { mutableStateOf(SYSTEM_FONTS.first().first) }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var extractionNotice by remember { mutableStateOf(DEFAULT_NOTICE) }
    var isError by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recognizedDocument by remember { mutableStateOf<RecognizedDocument?>(null) }

    val availableFonts: List<Pair<String, Typeface>> = remember(vm.projects, vm.importedFonts, vm.previewTypeface) {
        SYSTEM_FONTS + vm.allFontOptions()
    }

    val selectedTypeface = availableFonts.firstOrNull { it.first == selectedFontLabel }?.second
        ?: Typeface.DEFAULT

    val sourcePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            sourceUri = uri
            sourceName = displayNameForUri(context, uri) ?: "document"
            outputFile = null
            recognizedDocument = null
            status = ""
            extractionNotice = DEFAULT_NOTICE
            isError = false
        }
    }

    Page("Restyle scanned text", back) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Create a new visual PDF from a clear scan using a different font. Logos and images are kept, but clean white backgrounds work best.",
                style = MaterialTheme.typography.bodyMedium
            )

            SectionLabel("Choose a scan or PDF")
            OutlinedButton(
                onClick = { sourcePickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (sourceName.isNotEmpty()) "📄 $sourceName" else "Choose scan or PDF")
            }

            SectionLabel("Choose the new font")
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Font: $selectedFontLabel")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    availableFonts.forEach { (label, _) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                selectedFontLabel = label
                                recognizedDocument = null
                                outputFile = null
                                status = ""
                                extractionNotice = DEFAULT_NOTICE
                                isError = false
                                expanded = false
                            }
                        )
                    }
                }
            }

            SectionLabel("Read the text")
            Button(
                onClick = {
                    val uri = sourceUri ?: return@Button
                    isProcessing = true
                    status = "Recognizing text…"
                    extractionNotice = DEFAULT_NOTICE
                    outputFile = null
                    scope.launch {
                        val document = recognizeDocument(context, uri)
                        isProcessing = false
                        if (document != null && document.extractedText.isNotBlank()) {
                            recognizedDocument = document
                            status = "Recognized text from ${document.pages.size} page${if (document.pages.size == 1) "" else "s"}."
                            extractionNotice = OCR_NOTICE
                            isError = false
                        } else {
                            recognizedDocument = null
                            status = "No text could be recognized. Try a sharper scan with clear Latin-script text."
                            extractionNotice = OCR_NOTICE
                            isError = true
                        }
                    }
                },
                enabled = sourceUri != null && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isProcessing) "Recognizing…" else "Recognize text")
            }

            recognizedDocument?.let { document ->
                OutlinedTextField(
                    value = document.extractedText,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Detected text") },
                    minLines = 5,
                    readOnly = true,
                )
            }

            SectionLabel("Create the restyled copy")
            Button(
                onClick = {
                    val uri = sourceUri ?: return@Button
                    val document = recognizedDocument ?: return@Button
                    isProcessing = true
                    status = "Generating PDF…"
                    outputFile = null
                    scope.launch {
                        val result = rebuildPdf(context, uri, document, selectedTypeface)
                        isProcessing = false
                        val file = result.getOrElse { error ->
                            status = restyleFailureMessage(error)
                            isError = true
                            return@launch
                        }
                        outputFile = file
                        status = "Restyled PDF created. Your original is unchanged."
                        isError = false
                    }
                },
                enabled = recognizedDocument != null && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isProcessing) "Creating…" else "Create restyled PDF")
            }

            if (isProcessing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (status.isNotEmpty()) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            if (extractionNotice.isNotEmpty()) {
                Text(
                    extractionNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            outputFile?.let { file ->
                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "Open PDF")) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open restyled PDF")
                }
                OutlinedButton(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share PDF"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share restyled copy")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

private suspend fun recognizeDocument(
    context: android.content.Context,
    sourceUri: Uri,
): RecognizedDocument? = withContext(Dispatchers.IO) {
    runCatching {
        val mimeType = context.contentResolver.getType(sourceUri)
        val looksLikePdf = displayNameForUri(context, sourceUri)?.endsWith(".pdf", true) == true
        when {
            mimeType == "application/pdf" || looksLikePdf -> {
                context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val pages = buildList {
                            for (index in 0 until renderer.pageCount) {
                                val page = renderer.openPage(index)
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                add(recognizeBitmap(bitmap))
                                page.close()
                                bitmap.recycle()
                            }
                        }
                        RecognizedDocument(pages)
                    }
                }
            }
            else -> {
                val bitmap = loadBitmap(context.contentResolver, sourceUri) ?: return@runCatching null
                val page = recognizeBitmap(bitmap)
                bitmap.recycle()
                RecognizedDocument(listOf(page))
            }
        }
    }.getOrNull()
}

private suspend fun recognizeBitmap(bitmap: Bitmap): RecognizedPage {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult()
        val lines = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                line.boundingBox?.let { bounds ->
                    RecognizedLine(
                        text = line.text,
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    )
                }
            }
        }
        RecognizedPage(bitmap.width, bitmap.height, lines)
    } finally {
        recognizer.close()
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) continuation.resume(result)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}

private suspend fun rebuildPdf(
    context: android.content.Context,
    sourceUri: Uri,
    document: RecognizedDocument,
    typeface: Typeface
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val outputDoc = PdfDocument()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = Color.BLACK
        }
        document.pages.forEachIndexed { index, page ->
            val pageInfo = PdfDocument.PageInfo.Builder(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1), index + 1).create()
            val outputPage = outputDoc.startPage(pageInfo)
            val canvas = outputPage.canvas
            val background = loadSourcePageBitmap(context, sourceUri, index)
            if (background != null) {
                canvas.drawBitmap(background, 0f, 0f, null)
            } else {
                canvas.drawColor(Color.WHITE)
            }
            val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            page.lines.forEach { line ->
                if (line.text.isBlank()) return@forEach
                // Mask the original recognised line before drawing the replacement.
                // This preserves surrounding images, logos, and page layout on clean backgrounds.
                canvas.drawRect(
                    (line.left - TEXT_MASK_PADDING).coerceAtLeast(0).toFloat(),
                    (line.top - TEXT_MASK_PADDING).coerceAtLeast(0).toFloat(),
                    (line.right + TEXT_MASK_PADDING).coerceAtMost(page.width).toFloat(),
                    (line.bottom + TEXT_MASK_PADDING).coerceAtMost(page.height).toFloat(),
                    maskPaint,
                )
                val baseTextSize = line.height * BASE_TEXT_SIZE_RATIO
                textPaint.textSize = baseTextSize
                val measuredWidth = textPaint.measureText(line.text)
                if (measuredWidth > 0f) {
                    val scaledTextSize = textPaint.textSize * (line.width * TEXT_WIDTH_PADDING_RATIO / measuredWidth)
                    textPaint.textSize = scaledTextSize.coerceIn(10f, baseTextSize * MAX_TEXT_UPSCALE_RATIO)
                }
                val baseline = (line.bottom - textPaint.descent()).coerceAtLeast(textPaint.textSize)
                canvas.drawText(line.text, line.left.toFloat(), baseline, textPaint)
            }
            outputDoc.finishPage(outputPage)
            background?.recycle()
        }
        val outputFile = File(context.filesDir, "rebuild-${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { outputDoc.writeTo(it) }
        outputDoc.close()
        outputFile
    }
}

private fun restyleFailureMessage(error: Throwable): String = when (error) {
    is SecurityException -> "The document can no longer be accessed. Choose it again and retry."
    is OutOfMemoryError -> "This document is too large to process on this device. Try a smaller file or fewer pages."
    else -> "Could not create the restyled PDF: ${error.message?.take(120) ?: "an unexpected error occurred"}"
}

private fun loadSourcePageBitmap(context: android.content.Context, sourceUri: Uri, pageIndex: Int): Bitmap? {
    val mimeType = context.contentResolver.getType(sourceUri)
    val looksLikePdf = displayNameForUri(context, sourceUri)?.endsWith(".pdf", true) == true
    return if (mimeType == "application/pdf" || looksLikePdf) {
        context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return@use null
                renderer.openPage(pageIndex).use { sourcePage ->
                    Bitmap.createBitmap(sourcePage.width, sourcePage.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        sourcePage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    } else if (pageIndex == 0) {
        loadBitmap(context.contentResolver, sourceUri)
    } else null
}

private const val DEFAULT_NOTICE =
    "Recognition runs privately on this device. The restyled copy keeps the original page image and replaces recognised text visually."

private const val OCR_NOTICE =
    "Beta: Latin-script OCR only. Clear black text on a plain, light background gives the best result; complex backgrounds may show white masks."

private const val TEXT_MASK_PADDING = 3
private const val BASE_TEXT_SIZE_RATIO = 0.82f
private const val TEXT_WIDTH_PADDING_RATIO = 1.04f
private const val MAX_TEXT_UPSCALE_RATIO = 1.15f

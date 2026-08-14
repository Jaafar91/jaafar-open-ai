package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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

    val availableFonts: List<Pair<String, Typeface>> = remember(vm.projects, vm.previewTypeface) {
        val userFonts = vm.projects.mapNotNull { project ->
            val file = File(context.filesDir, "font-${project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}.ttf")
            if (file.exists()) {
                val typeface = runCatching {
                    if (Build.VERSION.SDK_INT >= 26) Typeface.Builder(file).build()
                    else Typeface.createFromFile(file)
                }.getOrNull()
                if (typeface != null) project.name to typeface else null
            } else null
        }
        SYSTEM_FONTS + userFonts
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

    Page("PDF Font Converter", back) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Select an image or rasterized PDF, recognize its text on-device, then rebuild a fresh PDF using your chosen output font.",
                style = MaterialTheme.typography.bodyMedium
            )

            SectionLabel("Step 1 – Select an image or PDF")
            OutlinedButton(
                onClick = { sourcePickerLauncher.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (sourceName.isNotEmpty()) "📄 $sourceName" else "Choose image or PDF")
            }

            SectionLabel("Step 2 – Select the output font")
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

            SectionLabel("Step 3 – Recognize text")
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
                            status = "No text could be recognized. Try a cleaner scan or choose a font closer to the source."
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
                    label = { Text("Recognized text preview") },
                    minLines = 5,
                    readOnly = true,
                )
            }

            SectionLabel("Step 4 – Generate rebuilt PDF")
            Button(
                onClick = {
                    val document = recognizedDocument ?: return@Button
                    isProcessing = true
                    status = "Generating PDF…"
                    outputFile = null
                    scope.launch {
                        val result = rebuildPdf(context, document, selectedTypeface)
                        isProcessing = false
                        if (result != null) {
                            outputFile = result
                            status = "PDF generated successfully."
                            isError = false
                        } else {
                            status = "Failed to generate the PDF."
                            isError = true
                        }
                    }
                },
                enabled = recognizedDocument != null && !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isProcessing) "Generating…" else "Generate PDF")
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
                    Text("Open Generated PDF")
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
                    Text("Share Generated PDF")
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

private fun loadBitmap(resolver: android.content.ContentResolver, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        resolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }
    }
}.getOrNull()

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
    document: RecognizedDocument,
    typeface: Typeface
): File? = withContext(Dispatchers.IO) {
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
            canvas.drawColor(Color.WHITE)
            page.lines.forEach { line ->
                if (line.text.isBlank()) return@forEach
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
        }
        val outputFile = File(context.filesDir, "rebuild-${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { outputDoc.writeTo(it) }
        outputDoc.close()
        outputFile
    }.getOrNull()
}

private fun displayNameForUri(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
}

private const val DEFAULT_NOTICE =
    "Recognition runs privately on this device. Review the extracted text before generating the new PDF."

private const val OCR_NOTICE =
    "ML Kit OCR recognizes Latin-script text and returns line positions. The app then rebuilds a fresh PDF using the selected output font."

private const val BASE_TEXT_SIZE_RATIO = 0.82f
private const val TEXT_WIDTH_PADDING_RATIO = 1.04f
private const val MAX_TEXT_UPSCALE_RATIO = 1.15f

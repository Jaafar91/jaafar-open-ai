package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
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

    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var selectedFontLabel by remember { mutableStateOf(SYSTEM_FONTS.first().first) }
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    val availableFonts: List<Pair<String, Typeface>> = remember(vm.projects, vm.previewTypeface) {
        val userFonts = vm.projects.mapNotNull { project ->
            val file = File(context.filesDir, "font-${project.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')}.ttf")
            if (file.exists()) {
                val typeface = runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 26) Typeface.Builder(file).build()
                    else Typeface.createFromFile(file)
                }.getOrNull()
                if (typeface != null) project.name to typeface else null
            } else null
        }
        SYSTEM_FONTS + userFonts
    }

    val selectedTypeface = availableFonts.firstOrNull { it.first == selectedFontLabel }?.second
        ?: Typeface.DEFAULT

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pdfUri = uri
            pdfName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "document.pdf"
            outputFile = null
            status = ""
            isError = false
        }
    }

    Page("PDF Font Converter", back) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Upload a PDF and convert it using your selected font. Each page is rendered into the new PDF document.",
                style = MaterialTheme.typography.bodyMedium
            )

            // Step 1: Pick PDF
            SectionLabel("Step 1 – Select a PDF file")
            OutlinedButton(
                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (pdfName.isNotEmpty()) "📄 $pdfName" else "Choose PDF file")
            }

            // Step 2: Select font
            SectionLabel("Step 2 – Select a font")
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
                            onClick = { selectedFontLabel = label; expanded = false }
                        )
                    }
                }
            }

            // Step 3: Generate
            SectionLabel("Step 3 – Generate PDF")
            Button(
                onClick = {
                    val uri = pdfUri ?: return@Button
                    isProcessing = true
                    status = "Processing…"
                    outputFile = null
                    scope.launch {
                        val result = convertPdf(context, uri, selectedTypeface)
                        isProcessing = false
                        if (result != null) {
                            outputFile = result
                            status = "PDF generated successfully."
                            isError = false
                        } else {
                            status = "Failed to process the PDF. Make sure the file is a valid PDF."
                            isError = true
                        }
                    }
                },
                enabled = pdfUri != null && !isProcessing,
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

private suspend fun convertPdf(
    context: android.content.Context,
    sourceUri: Uri,
    typeface: Typeface
): File? = withContext(Dispatchers.IO) {
    runCatching {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: return@runCatching null

        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        val outputDoc = PdfDocument()

        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = 24f
            color = Color.DKGRAY
        }

        for (i in 0 until pageCount) {
            val page = renderer.openPage(i)
            val width = page.width
            val height = page.height

            // Render the PDF page to a bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Create a new PDF page with the same dimensions
            val pageInfo = PdfDocument.PageInfo.Builder(width, height, i + 1).create()
            val outputPage = outputDoc.startPage(pageInfo)
            val pageCanvas = outputPage.canvas

            // Draw the original page content
            pageCanvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
            bitmap.recycle()

            // Draw a footer with the selected font (making the font choice visible)
            val footerText = "${i + 1} / $pageCount"
            val textWidth = footerPaint.measureText(footerText)
            pageCanvas.drawText(
                footerText,
                (width - textWidth) / 2f,
                height - 12f,
                footerPaint
            )

            outputDoc.finishPage(outputPage)
        }

        renderer.close()
        pfd.close()

        val outputFile = File(context.filesDir, "converted-${System.currentTimeMillis()}.pdf")
        FileOutputStream(outputFile).use { outputDoc.writeTo(it) }
        outputDoc.close()
        outputFile
    }.getOrNull()
}

package com.jaafar.remoteconfig.fontcreator

import android.content.ContentResolver
import android.content.Context
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Shared document-level utilities used by both SignatureScreen and
// FillMarkScreen. All functions are package-internal.
// ---------------------------------------------------------------------------

internal data class SignedDocument(val file: File, val mimeType: String)

internal fun renderPdfPage(context: Context, uri: Uri, pageIndex: Int): Bitmap? {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
    return descriptor.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            val page = renderer.openPage(safeIndex)
            try {
                val bitmap = Bitmap.createBitmap(
                    page.width.coerceAtLeast(1),
                    page.height.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        }
    }
}

internal fun getPdfPageCount(context: Context, uri: Uri): Int {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0
    return descriptor.use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
}

internal fun loadBitmap(resolver: ContentResolver, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        resolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }
    }
}.getOrNull()

internal fun displayNameForUri(context: Context, uri: Uri): String? {
    return context.contentResolver.query(
        uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
        null, null, null,
    )?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
}

internal fun deleteOldSignedFiles(directory: File, prefix: String, suffix: String) {
    directory.listFiles()
        ?.filter { it.name.startsWith(prefix) && it.name.endsWith(suffix) }
        ?.forEach { it.delete() }
}

internal fun shareDocument(context: Context, file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share file").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

internal fun loadSignatureBitmap(context: Context, signature: SavedSignature): Bitmap? {
    val fileName = signature.imageFileName ?: return null
    val file = File(context.filesDir, fileName)
    if (!file.exists()) return null
    return BitmapFactory.decodeFile(file.absolutePath)
}

/**
 * Draws a single signature/stamp mark onto [canvas] at the position and scale
 * described by the fraction parameters. [signatureBitmap] is pre-loaded by the
 * caller and may be null for stroke-based signatures.
 */
internal fun drawSignature(
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
        val sigW = signatureBitmap.width.coerceAtLeast(1).toFloat()
        val sigH = signatureBitmap.height.coerceAtLeast(1).toFloat()
        val targetWidth = pageWidth * widthFraction.coerceIn(.12f, .35f)
        val scale = targetWidth / sigW
        val left = (offsetXFraction * pageWidth)
            .coerceIn(0f, (pageWidth - sigW * scale).coerceAtLeast(0f))
        val top = (offsetYFraction * pageHeight)
            .coerceIn(0f, (pageHeight - sigH * scale).coerceAtLeast(0f))
        val dst = android.graphics.RectF(left, top, left + sigW * scale, top + sigH * scale)
        canvas.drawBitmap(signatureBitmap, null, dst, Paint(Paint.ANTI_ALIAS_FLAG))
        return
    }
    val points = signature.strokes.flatMap { it.points }
    if (points.isEmpty()) return
    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val maxX = points.maxOf { it.x }
    val maxY = points.maxOf { it.y }
    val sigW = (maxX - minX).coerceAtLeast(1f)
    val sigH = (maxY - minY).coerceAtLeast(1f)
    val targetWidth = pageWidth * widthFraction.coerceIn(.12f, .35f)
    val scale = targetWidth / sigW
    val left = (offsetXFraction * pageWidth)
        .coerceIn(0f, (pageWidth - sigW * scale).coerceAtLeast(0f))
    val top = (offsetYFraction * pageHeight)
        .coerceIn(0f, (pageHeight - sigH * scale).coerceAtLeast(0f))
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
            stroke.points.drop(1).forEach { pt ->
                lineTo(left + (pt.x - minX) * scale, top + (pt.y - minY) * scale)
            }
        }
        canvas.drawPath(path, paint)
    }
}

internal suspend fun signDocumentWithPage(
    context: Context,
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
            SignedDocument(
                signPdf(
                    context, sourceUri, signature,
                    widthFraction, offsetXFraction, offsetYFraction, targetPage, applyToAll,
                ) ?: return@runCatching null,
                "application/pdf",
            )
        } else {
            SignedDocument(
                signImage(
                    context, sourceUri, signature,
                    widthFraction, offsetXFraction, offsetYFraction,
                ) ?: return@runCatching null,
                "image/png",
            )
        }
    }.getOrNull()
}

private fun signImage(
    context: Context,
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
            drawSignature(
                Canvas(output), signature, output.width, output.height,
                widthFraction, offsetXFraction, offsetYFraction, signatureBitmap,
            )
            deleteOldSignedFiles(context.cacheDir, "signed-image-", ".png")
            File(context.cacheDir, "signed-image-${System.currentTimeMillis()}.png").also { file ->
                FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.PNG, 100, out) }
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
    context: Context,
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
                    val bitmap = Bitmap.createBitmap(
                        page.width.coerceAtLeast(1),
                        page.height.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        if (applyToAll || index == targetPage) {
                            drawSignature(
                                Canvas(bitmap), signature, bitmap.width, bitmap.height,
                                widthFraction, offsetXFraction, offsetYFraction, signatureBitmap,
                            )
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
                    FileOutputStream(file).use { out -> outputDocument.writeTo(out) }
                }
            } finally {
                outputDocument.close()
                signatureBitmap?.recycle()
            }
        }
    }
}

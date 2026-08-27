package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private data class TextColorOption(val name: String, val value: Int, val composeColor: Color)
private enum class EditorPanel { Text, Font, Size, Color }
private const val PREF_KEY_LAST_IMAGE_FONT = "last_font"

private val TEXT_COLORS = listOf(
    TextColorOption("White", android.graphics.Color.WHITE, Color.White),
    TextColorOption("Black", android.graphics.Color.BLACK, Color.Black),
    TextColorOption("Red", android.graphics.Color.RED, Color.Red),
    TextColorOption("Yellow", android.graphics.Color.YELLOW, Color.Yellow),
    TextColorOption("Blue", android.graphics.Color.BLUE, Color.Blue),
    TextColorOption("Green", android.graphics.Color.rgb(0, 160, 70), Color(0xFF00A046)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTextEditorScreen(
    imageUri: Uri,
    fontOptions: List<Pair<String, Typeface>>,
    initiallySelectedFont: String? = null,
    initialText: String = "",
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("image_editor", 0) }
    val bitmap = remember(imageUri) { loadBitmap(context.contentResolver, imageUri) }
    val lastSavedFont = remember { preferences.getString(PREF_KEY_LAST_IMAGE_FONT, null) }
    var selectedFontLabel by remember(fontOptions, initiallySelectedFont) {
        val resolved = when {
            initiallySelectedFont != null && fontOptions.any { it.first == initiallySelectedFont } -> initiallySelectedFont
            lastSavedFont != null && fontOptions.any { it.first == lastSavedFont } -> lastSavedFont
            else -> fontOptions.firstOrNull()?.first ?: "Default"
        }
        mutableStateOf(resolved)
    }
    val typeface = fontOptions.firstOrNull { it.first == selectedFontLabel }?.second ?: Typeface.DEFAULT
    var text by remember(imageUri, initialText) { mutableStateOf(initialText) }
    var sizePercent by remember { mutableFloatStateOf(10f) }
    var textColor by remember { mutableStateOf(TEXT_COLORS.first()) }
    var textPosition by remember { mutableStateOf(Offset(.5f, .85f)) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activePanel by remember { mutableStateOf<EditorPanel?>(null) }
    var fontQuery by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            AppTopBar("Write on image", onBack) {
                TextButton(
                    enabled = bitmap != null && text.isNotBlank(),
                    onClick = {
                        bitmap?.let { source ->
                            shareImage(context, renderImage(source, text, typeface, sizePercent, textColor.value, textPosition))
                        }
                    },
                ) { Text("Share") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bitmap == null) {
                Text("This image could not be opened.", color = MaterialTheme.colorScheme.error)
            } else {
                ComposeCanvas(
                    Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                        .onSizeChanged { canvasSize = it }
                        // Position changes on every pointer event. Keeping it out of the keys prevents
                        // Compose from cancelling and recreating this detector mid-gesture.
                        .pointerInput(bitmap, canvasSize, text, typeface, sizePercent) {
                            var canDragText = false
                            detectDragGestures(
                                onDragStart = { pointer ->
                                    canDragText = isPointNearOverlayText(
                                        pointer = pointer,
                                        canvasSize = canvasSize,
                                        imageWidth = bitmap.width,
                                        imageHeight = bitmap.height,
                                        text = text,
                                        typeface = typeface,
                                        sizePercent = sizePercent,
                                        textPosition = textPosition,
                                        touchPadding = 24.dp.toPx(),
                                    )
                                },
                                onDragEnd = { canDragText = false },
                                onDragCancel = { canDragText = false },
                            ) { change, dragAmount ->
                                if (canDragText) {
                                    change.consume()
                                    val imageSize = displayedImageSize(canvasSize, bitmap.width, bitmap.height)
                                    if (imageSize.width > 0 && imageSize.height > 0) {
                                        textPosition = Offset(
                                            (textPosition.x + dragAmount.x / imageSize.width).coerceIn(0f, 1f),
                                            (textPosition.y + dragAmount.y / imageSize.height).coerceIn(0f, 1f),
                                        )
                                    }
                                }
                            }
                        },
                ) {
                    val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
                    val width = (bitmap.width * scale).roundToInt()
                    val height = (bitmap.height * scale).roundToInt()
                    val left = ((size.width - width) / 2f).roundToInt()
                    val top = ((size.height - height) / 2f).roundToInt()
                    drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset(left, top), dstSize = IntSize(width, height))
                    drawIntoCanvas { canvas ->
                        val paint = overlayPaint(typeface, height * sizePercent / 100f, textColor.value)
                        drawOverlayText(
                            canvas.nativeCanvas, text,
                            left + width * textPosition.x, top + height * textPosition.y,
                            width * .9f, paint,
                        )
                    }
                }
                Text("Drag the text to reposition it", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorToolButton("Text", EditorPanel.Text) { activePanel = it }
                    EditorToolButton("Font", EditorPanel.Font) { activePanel = it }
                    EditorToolButton("Size", EditorPanel.Size) { activePanel = it }
                    EditorToolButton("Color", EditorPanel.Color) { activePanel = it }
                }
            }
        }
    }
    activePanel?.let { panel ->
        ModalBottomSheet(onDismissRequest = { activePanel = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (panel) {
                    EditorPanel.Text -> {
                        Text("Edit text", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Text") },
                            supportingText = { Text("Use Enter to start a new line") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily(typeface),
                            ),
                        )
                    }
                    EditorPanel.Font -> {
                        Text("Choose font", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = fontQuery,
                            onValueChange = { fontQuery = it },
                            label = { Text("Search fonts") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val visibleFonts = fontOptions.filter { (label, _) -> label.contains(fontQuery, ignoreCase = true) }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                            items(visibleFonts) { (label, optionTypeface) ->
                                OutlinedButton(
                                    onClick = {
                                        selectedFontLabel = label
                                        preferences.edit().putString(PREF_KEY_LAST_IMAGE_FONT, label).apply()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (label == selectedFontLabel) "✓  $label" else label,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily(optionTypeface),
                                    )
                                }
                            }
                        }
                    }
                    EditorPanel.Size -> {
                        Text("Text size · ${sizePercent.roundToInt()}%", style = MaterialTheme.typography.titleLarge)
                        Slider(value = sizePercent, onValueChange = { sizePercent = it }, valueRange = 4f..24f)
                    }
                    EditorPanel.Color -> {
                        Text("Text color", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TEXT_COLORS.forEach { option ->
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(48.dp).background(option.composeColor, androidx.compose.foundation.shape.CircleShape)
                                        .border(if (textColor == option) 4.dp else 1.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                        .semantics { contentDescription = "${option.name} text" }
                                        .clickable { textColor = option },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (textColor == option) {
                                        Text("✓", color = if (option.value == android.graphics.Color.WHITE || option.value == android.graphics.Color.YELLOW) Color.Black else Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.EditorToolButton(
    label: String,
    panel: EditorPanel,
    onClick: (EditorPanel) -> Unit,
) {
    OutlinedButton(onClick = { onClick(panel) }, modifier = Modifier.weight(1f)) { Text(label) }
}

private fun overlayPaint(typeface: Typeface, textSize: Float, textColor: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    this.textSize = textSize
    color = textColor
    textAlign = Paint.Align.CENTER
    val shadow = if (android.graphics.Color.luminance(textColor) > .45f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    setShadowLayer(textSize / 18f, 0f, textSize / 30f, shadow)
}

private fun renderImage(source: Bitmap, text: String, typeface: Typeface, sizePercent: Float, textColor: Int, textPosition: Offset): Bitmap {
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
    val paint = overlayPaint(typeface, output.height * sizePercent / 100f, textColor)
    drawOverlayText(Canvas(output), text, output.width * textPosition.x, output.height * textPosition.y, output.width * .9f, paint)
    return output
}

private fun displayedImageSize(canvasSize: IntSize, imageWidth: Int, imageHeight: Int): IntSize {
    if (canvasSize.width == 0 || canvasSize.height == 0) return IntSize.Zero
    val scale = minOf(canvasSize.width.toFloat() / imageWidth, canvasSize.height.toFloat() / imageHeight)
    return IntSize((imageWidth * scale).roundToInt(), (imageHeight * scale).roundToInt())
}

private fun isPointNearOverlayText(
    pointer: Offset,
    canvasSize: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    text: String,
    typeface: Typeface,
    sizePercent: Float,
    textPosition: Offset,
    touchPadding: Float,
): Boolean {
    if (text.isBlank()) return false
    val imageSize = displayedImageSize(canvasSize, imageWidth, imageHeight)
    if (imageSize.width <= 0 || imageSize.height <= 0) return false
    val left = (canvasSize.width - imageSize.width) / 2f
    val top = (canvasSize.height - imageSize.height) / 2f
    val centerX = left + imageSize.width * textPosition.x
    val bottomBaseline = top + imageSize.height * textPosition.y
    val paint = overlayPaint(typeface, imageSize.height * sizePercent / 100f, android.graphics.Color.WHITE)
    val lines = wrapTextLines(text, imageSize.width * .9f, paint)
    val textWidth = lines.maxOfOrNull { line -> paint.measureText(line) }?.coerceAtLeast(paint.textSize) ?: paint.textSize
    val firstBaseline = bottomBaseline - paint.fontSpacing * (lines.size - 1)
    val boundsTop = firstBaseline + paint.fontMetrics.top - touchPadding
    val boundsBottom = bottomBaseline + paint.fontMetrics.bottom + touchPadding
    return pointer.x in (centerX - textWidth / 2f - touchPadding)..(centerX + textWidth / 2f + touchPadding) &&
        pointer.y in boundsTop..boundsBottom
}

private fun drawOverlayText(canvas: Canvas, text: String, centerX: Float, bottomBaseline: Float, maxWidth: Float, paint: Paint) {
    val lines = wrapTextLines(text, maxWidth, paint)
    val lineHeight = paint.fontSpacing
    val firstBaseline = bottomBaseline - lineHeight * (lines.size - 1)
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, centerX, firstBaseline + index * lineHeight, paint)
    }
}

internal fun wrapTextLines(text: String, maxWidth: Float, paint: Paint): List<String> {
    if (text.isEmpty()) return listOf("")
    return text.replace("\r\n", "\n").split("\n").flatMap { explicitLine ->
        wrapSingleLine(explicitLine, maxWidth, paint)
    }
}

private fun wrapSingleLine(line: String, maxWidth: Float, paint: Paint): List<String> {
    if (line.isBlank()) return listOf("")
    if (paint.measureText(line) <= maxWidth) return listOf(line)

    val result = mutableListOf<String>()
    var currentLine = ""
    line.trim().split(Regex("\\s+")).forEach { word ->
        val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (currentLine.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
            result += currentLine
            currentLine = word
        } else {
            currentLine = candidate
        }
    }
    if (currentLine.isNotEmpty()) result += currentLine
    return result.ifEmpty { listOf("") }
}

private fun shareImage(context: android.content.Context, bitmap: Bitmap) {
    runCatching {
        val file = File(context.cacheDir, "font-image-${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share image").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        Toast.makeText(context, "Unable to share image.", Toast.LENGTH_SHORT).show()
    }
}

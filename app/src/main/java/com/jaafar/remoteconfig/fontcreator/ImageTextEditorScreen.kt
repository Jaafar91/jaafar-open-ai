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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private data class TextColorOption(val name: String, val value: Int, val composeColor: Color)
private enum class EditorPanel { Text, Font, Size, Color }
private enum class FontFilter { All, MyFonts, System }
private const val PREF_KEY_LAST_IMAGE_FONT = "last_font"
private const val PREF_KEY_RECENT_IMAGE_FONTS = "recent_fonts"
private const val RECENT_FONT_SEPARATOR = "\u001F"

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
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val textFocusRequester = remember { FocusRequester() }
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
    var isEditingText by remember { mutableStateOf(false) }
    var fontQuery by remember { mutableStateOf("") }
    var showAllFonts by remember { mutableStateOf(false) }
    var fontFilter by remember { mutableStateOf(FontFilter.All) }
    var recentFontLabels by remember(fontOptions) {
        val stored = preferences.getString(PREF_KEY_RECENT_IMAGE_FONTS, "").orEmpty()
            .split(RECENT_FONT_SEPARATOR).filter { saved -> fontOptions.any { it.first == saved } }
        mutableStateOf((listOf(selectedFontLabel) + stored).distinct().take(6))
    }

    fun selectImageFont(label: String) {
        selectedFontLabel = label
        recentFontLabels = (listOf(label) + recentFontLabels).distinct().take(6)
        preferences.edit()
            .putString(PREF_KEY_LAST_IMAGE_FONT, label)
            .putString(PREF_KEY_RECENT_IMAGE_FONTS, recentFontLabels.joinToString(RECENT_FONT_SEPARATOR))
            .apply()
    }

    LaunchedEffect(isEditingText, canvasSize) {
        if (isEditingText && canvasSize.width > 0 && canvasSize.height > 0) {
            runCatching {
                textFocusRequester.requestFocus()
                keyboard?.show()
            }
        }
    }

    BackHandler {
        when {
            activePanel != null -> activePanel = null
            isEditingText -> { isEditingText = false; keyboard?.hide() }
            else -> onBack()
        }
    }
    Scaffold(
        topBar = {
            AppTopBar("Write on image", onBack) {
                if (isEditingText) {
                    TextButton(onClick = { isEditingText = false; keyboard?.hide() }) { Text("Done") }
                } else {
                    TextButton(
                        enabled = bitmap != null && text.isNotBlank(),
                        onClick = {
                            bitmap?.let { source ->
                                shareImage(context, renderImage(source, text, typeface, sizePercent, textColor.value, textPosition))
                            }
                        },
                    ) { Text("Share") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (bitmap == null) {
                Text("This image could not be opened.", color = MaterialTheme.colorScheme.error)
            } else {
                Box(
                    Modifier.fillMaxWidth().weight(1f).background(Color.Black)
                        .onSizeChanged { canvasSize = it },
                ) {
                    ComposeCanvas(
                        Modifier.fillMaxSize()
                            .pointerInput(bitmap, canvasSize, text, typeface) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val imageSize = displayedImageSize(canvasSize, bitmap.width, bitmap.height)
                                    val touchesText = isPointNearOverlayText(
                                        pointer = centroid,
                                        canvasSize = canvasSize,
                                        imageWidth = bitmap.width,
                                        imageHeight = bitmap.height,
                                        text = text,
                                        typeface = typeface,
                                        sizePercent = sizePercent,
                                        textPosition = textPosition,
                                        touchPadding = 24.dp.toPx(),
                                    )
                                    if (!isEditingText && touchesText && imageSize.width > 0 && imageSize.height > 0) {
                                        textPosition = Offset(
                                            (textPosition.x + pan.x / imageSize.width).coerceIn(0f, 1f),
                                            (textPosition.y + pan.y / imageSize.height).coerceIn(0f, 1f),
                                        )
                                        sizePercent = (sizePercent * zoom).coerceIn(4f, 24f)
                                    }
                                }
                            }
                            .pointerInput(bitmap, canvasSize, text, typeface) {
                                detectTapGestures { pointer ->
                                    val touchesText = isPointNearOverlayText(
                                            pointer, canvasSize, bitmap.width, bitmap.height, text,
                                            typeface, sizePercent, textPosition, 24.dp.toPx(),
                                        )
                                    if (touchesText) {
                                        isEditingText = true
                                    } else if (isEditingText) {
                                        isEditingText = false
                                        keyboard?.hide()
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
                        if (!isEditingText) {
                            drawIntoCanvas { canvas ->
                                val paint = overlayPaint(typeface, height * sizePercent / 100f, textColor.value)
                                drawOverlayText(
                                    canvas.nativeCanvas, text,
                                    left + width * textPosition.x, top + height * textPosition.y,
                                    width * .9f, paint,
                                )
                            }
                        }
                    }
                    if (isEditingText && canvasSize.width > 0 && canvasSize.height > 0) {
                        val imageSize = displayedImageSize(canvasSize, bitmap.width, bitmap.height)
                        val imageLeft = (canvasSize.width - imageSize.width) / 2f
                        val imageTop = (canvasSize.height - imageSize.height) / 2f
                        val editorWidthPx = imageSize.width * .9f
                        val textSizePx = imageSize.height * sizePercent / 100f
                        val centerX = imageLeft + imageSize.width * textPosition.x
                        val baselineY = imageTop + imageSize.height * textPosition.y
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier
                                .offset { IntOffset((centerX - editorWidthPx / 2f).roundToInt(), (baselineY - textSizePx).roundToInt()) }
                                .width(with(density) { editorWidthPx.toDp() })
                                .border(1.dp, MaterialTheme.colorScheme.primary)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = .35f))
                                .padding(6.dp)
                                .focusRequester(textFocusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = textColor.composeColor,
                                fontFamily = androidx.compose.ui.text.font.FontFamily(typeface),
                                fontSize = with(density) { textSizePx.toSp() },
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
                Text("Drag the text to reposition it", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorToolButton("Text", EditorPanel.Text) { activePanel = null; isEditingText = true }
                    EditorToolButton("Font", EditorPanel.Font) { isEditingText = false; keyboard?.hide(); activePanel = it }
                    EditorToolButton("Size", EditorPanel.Size) { isEditingText = false; keyboard?.hide(); activePanel = it }
                    EditorToolButton("Color", EditorPanel.Color) { isEditingText = false; keyboard?.hide(); activePanel = it }
                }
            }
        }
    }
    activePanel?.let { panel ->
        ModalBottomSheet(onDismissRequest = { activePanel = null; showAllFonts = false; fontQuery = "" }) {
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
                        if (!showAllFonts) {
                            Text("Recent fonts", style = MaterialTheme.typography.titleLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recentFontLabels.mapNotNull { recent -> fontOptions.firstOrNull { it.first == recent } }) { (label, optionTypeface) ->
                                    FilterChip(
                                        selected = label == selectedFontLabel,
                                        onClick = { selectImageFont(label) },
                                        modifier = Modifier.width(112.dp).heightIn(min = 72.dp),
                                        label = {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Aa", style = MaterialTheme.typography.titleLarge, fontFamily = androidx.compose.ui.text.font.FontFamily(optionTypeface))
                                                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                            }
                                        },
                                    )
                                }
                                item {
                                    OutlinedButton(
                                        onClick = { showAllFonts = true },
                                        modifier = Modifier.width(112.dp).heightIn(min = 72.dp),
                                    ) { Text("All fonts") }
                                }
                            }
                            Text("Swipe to preview. Changes appear on the image immediately.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = fontFilter == FontFilter.All, onClick = { fontFilter = FontFilter.All }, label = { Text("All") })
                                FilterChip(selected = fontFilter == FontFilter.MyFonts, onClick = { fontFilter = FontFilter.MyFonts }, label = { Text("My fonts") })
                                FilterChip(selected = fontFilter == FontFilter.System, onClick = { fontFilter = FontFilter.System }, label = { Text("System") })
                            }
                            OutlinedTextField(
                                value = fontQuery,
                                onValueChange = { fontQuery = it },
                                label = { Text("Search fonts") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val filteredBySource = when (fontFilter) {
                                FontFilter.All -> fontOptions
                                FontFilter.System -> fontOptions.take(4)
                                FontFilter.MyFonts -> fontOptions.drop(4)
                            }
                            val visibleFonts = filteredBySource.filter { (label, _) -> label.contains(fontQuery, ignoreCase = true) }
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                                items(visibleFonts) { (label, optionTypeface) ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { selectImageFont(label) }.padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(label, style = MaterialTheme.typography.labelMedium)
                                            Text(
                                                text.ifBlank { "Aa" },
                                                style = MaterialTheme.typography.titleLarge,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily(optionTypeface),
                                                maxLines = 1,
                                            )
                                        }
                                        if (label == selectedFontLabel) Text("✓", style = MaterialTheme.typography.titleLarge)
                                    }
                                    HorizontalDivider()
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

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
private enum class EditorPanel { Font, Size, Color }
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

/** One independent piece of text on the image -- its own font, size, color, and position, so
 *  the screen can hold more than one at a time. Mirrors the iOS app's `TextLayer`. */
private data class TextLayer(
    val id: Long,
    val text: String = "",
    val fontLabel: String,
    val sizePercent: Float = 10f,
    val color: TextColorOption = TEXT_COLORS.first(),
    val position: Offset = Offset(.5f, .85f),
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
    val resolvedInitialFont = remember(fontOptions, initiallySelectedFont) {
        when {
            initiallySelectedFont != null && fontOptions.any { it.first == initiallySelectedFont } -> initiallySelectedFont
            lastSavedFont != null && fontOptions.any { it.first == lastSavedFont } -> lastSavedFont
            else -> fontOptions.firstOrNull()?.first ?: "Default"
        }
    }
    // The font a freshly-added text layer starts with -- whatever was most recently picked,
    // persisted across screen visits the same way it was before layers existed.
    var lastUsedFontLabel by remember(fontOptions, initiallySelectedFont) { mutableStateOf(resolvedInitialFont) }
    fun typefaceFor(label: String): Typeface = fontOptions.firstOrNull { it.first == label }?.second ?: Typeface.DEFAULT

    // Multiple independent text layers, not just one -- tapping "Add" always creates another
    // one instead of only ever being able to re-edit whatever's already there.
    val layers = remember(imageUri, initialText) { mutableStateListOf<TextLayer>() }
    var nextLayerId by remember { mutableLongStateOf(0L) }
    var selectedLayerId by remember(imageUri, initialText) { mutableStateOf<Long?>(null) }
    var editingLayerId by remember(imageUri, initialText) { mutableStateOf<Long?>(null) }
    LaunchedEffect(imageUri, initialText) {
        if (layers.isEmpty()) {
            val id = nextLayerId++
            layers.add(TextLayer(id = id, text = initialText, fontLabel = resolvedInitialFont))
            selectedLayerId = id
            if (initialText.isBlank()) editingLayerId = id
        }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activePanel by remember { mutableStateOf<EditorPanel?>(null) }
    var fontQuery by remember { mutableStateOf("") }
    var showAllFonts by remember { mutableStateOf(false) }
    var fontFilter by remember { mutableStateOf(FontFilter.All) }
    var recentFontLabels by remember(fontOptions) {
        val stored = preferences.getString(PREF_KEY_RECENT_IMAGE_FONTS, "").orEmpty()
            .split(RECENT_FONT_SEPARATOR).filter { saved -> fontOptions.any { it.first == saved } }
        mutableStateOf((listOf(lastUsedFontLabel) + stored).distinct().take(6))
    }

    fun addLayer() {
        val id = nextLayerId++
        layers.add(TextLayer(id = id, fontLabel = lastUsedFontLabel))
        selectedLayerId = id
        editingLayerId = id
        activePanel = null
    }

    fun deleteSelectedLayer() {
        val id = selectedLayerId ?: return
        layers.removeAll { it.id == id }
        selectedLayerId = null
        editingLayerId = null
        activePanel = null
    }

    fun selectLayerFont(index: Int, label: String) {
        if (index !in layers.indices) return
        layers[index] = layers[index].copy(fontLabel = label)
        lastUsedFontLabel = label
        recentFontLabels = (listOf(label) + recentFontLabels).distinct().take(6)
        preferences.edit()
            .putString(PREF_KEY_LAST_IMAGE_FONT, label)
            .putString(PREF_KEY_RECENT_IMAGE_FONTS, recentFontLabels.joinToString(RECENT_FONT_SEPARATOR))
            .apply()
    }

    LaunchedEffect(editingLayerId, canvasSize) {
        if (editingLayerId != null && canvasSize.width > 0 && canvasSize.height > 0) {
            runCatching {
                textFocusRequester.requestFocus()
                keyboard?.show()
            }
        }
    }

    BackHandler {
        when {
            activePanel != null -> activePanel = null
            editingLayerId != null -> { editingLayerId = null; keyboard?.hide() }
            else -> onBack()
        }
    }
    Scaffold(
        topBar = {
            AppTopBar("Write on image", onBack) {
                if (editingLayerId != null) {
                    TextButton(onClick = { editingLayerId = null; keyboard?.hide() }) { Text("Done") }
                } else {
                    // Icon-only, matching ShareButton's use elsewhere in the app (the font
                    // workspace's own share action) instead of a text label here.
                    IconButton(
                        enabled = bitmap != null && layers.any { it.text.isNotBlank() },
                        onClick = {
                            bitmap?.let { source ->
                                shareImage(context, renderImage(source, layers, ::typefaceFor))
                            }
                        },
                    ) { ActionIcon(ActionIconType.Share, "Share image") }
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
                            .pointerInput(bitmap, canvasSize, layers.size, fontOptions) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val imageSize = displayedImageSize(canvasSize, bitmap.width, bitmap.height)
                                    if (imageSize.width <= 0 || imageSize.height <= 0) return@detectTransformGestures
                                    val hitIndex = layers.indexOfLast { layer ->
                                        layer.id != editingLayerId && isPointNearOverlayText(
                                            pointer = centroid,
                                            canvasSize = canvasSize,
                                            imageWidth = bitmap.width,
                                            imageHeight = bitmap.height,
                                            text = layer.text,
                                            typeface = typefaceFor(layer.fontLabel),
                                            sizePercent = layer.sizePercent,
                                            textPosition = layer.position,
                                            touchPadding = 24.dp.toPx(),
                                        )
                                    }
                                    if (hitIndex >= 0) {
                                        val layer = layers[hitIndex]
                                        selectedLayerId = layer.id
                                        layers[hitIndex] = layer.copy(
                                            position = Offset(
                                                (layer.position.x + pan.x / imageSize.width).coerceIn(0f, 1f),
                                                (layer.position.y + pan.y / imageSize.height).coerceIn(0f, 1f),
                                            ),
                                            sizePercent = (layer.sizePercent * zoom).coerceIn(4f, 24f),
                                        )
                                    }
                                }
                            }
                            .pointerInput(bitmap, canvasSize, layers.size, fontOptions) {
                                detectTapGestures { pointer ->
                                    val hitIndex = layers.indexOfLast { layer ->
                                        isPointNearOverlayText(
                                            pointer, canvasSize, bitmap.width, bitmap.height, layer.text,
                                            typefaceFor(layer.fontLabel), layer.sizePercent, layer.position, 24.dp.toPx(),
                                        )
                                    }
                                    if (hitIndex >= 0) {
                                        selectedLayerId = layers[hitIndex].id
                                        editingLayerId = layers[hitIndex].id
                                    } else if (editingLayerId != null || selectedLayerId != null) {
                                        editingLayerId = null
                                        selectedLayerId = null
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
                        // Always draw the same Paint-based wrapped text that renderImage() uses for
                        // the exported file, during editing too -- this is the single source of
                        // truth for how the text wraps. The BasicTextField below (shown only while
                        // editing) renders its own text invisibly and exists purely to host the
                        // cursor and keyboard input: letting Compose's own soft-wrap draw the
                        // *visible* glyphs there could disagree with this Paint-based wrap for the
                        // app's custom-generated fonts, which is what made lengthy text collapse
                        // onto a single line as soon as editing ended.
                        drawIntoCanvas { canvas ->
                            layers.forEach { layer ->
                                if (layer.id == editingLayerId) return@forEach
                                val paint = overlayPaint(typefaceFor(layer.fontLabel), height * layer.sizePercent / 100f, layer.color.value)
                                drawOverlayText(
                                    canvas.nativeCanvas, layer.text,
                                    left + width * layer.position.x, top + height * layer.position.y,
                                    width * .9f, paint,
                                )
                            }
                        }
                    }
                    val editingLayer = layers.firstOrNull { it.id == editingLayerId }
                    if (editingLayer != null && canvasSize.width > 0 && canvasSize.height > 0) {
                        val editingTypeface = typefaceFor(editingLayer.fontLabel)
                        val imageSize = displayedImageSize(canvasSize, bitmap.width, bitmap.height)
                        val imageLeft = (canvasSize.width - imageSize.width) / 2f
                        val imageTop = (canvasSize.height - imageSize.height) / 2f
                        val editorWidthPx = imageSize.width * .9f
                        val textSizePx = imageSize.height * editingLayer.sizePercent / 100f
                        val editorPaint = overlayPaint(editingTypeface, textSizePx, editingLayer.color.value)
                        val editorLines = wrapTextLines(editingLayer.text, editorWidthPx, editorPaint)
                        val editorTextSize = with(density) { textSizePx.toSp() }
                        val editorLineHeight = with(density) { editorPaint.fontSpacing.toSp() }
                        val centerX = imageLeft + imageSize.width * editingLayer.position.x
                        val baselineY = imageTop + imageSize.height * editingLayer.position.y
                        val editorTop = baselineY - editorPaint.fontSpacing * (editorLines.size - 1) - textSizePx
                        BasicTextField(
                            value = editingLayer.text,
                            onValueChange = { newValue ->
                                val idx = layers.indexOfFirst { it.id == editingLayer.id }
                                if (idx >= 0) layers[idx] = layers[idx].copy(text = newValue)
                            },
                            modifier = Modifier
                                .offset { IntOffset((centerX - editorWidthPx / 2f).roundToInt(), editorTop.roundToInt()) }
                                .width(with(density) { editorWidthPx.toDp() })
                                .border(1.dp, MaterialTheme.colorScheme.primary)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = .35f))
                                .padding(6.dp)
                                .focusRequester(textFocusRequester),
                            // Text itself is invisible -- the Canvas above already draws the real,
                            // correctly-wrapped text -- so only the cursor is visible here.
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = Color.Transparent,
                                fontFamily = androidx.compose.ui.text.font.FontFamily(editingTypeface),
                                fontSize = editorTextSize,
                                lineHeight = editorLineHeight,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(editingLayer.color.composeColor),
                        )
                    }
                }
                Text("Tap Add for more text · drag to reposition · pinch to resize", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorToolButton("Add", Icons.Filled.Add) { addLayer() }
                    EditorToolButton("Font", Icons.Filled.FontDownload, enabled = selectedLayerId != null) {
                        editingLayerId = null; keyboard?.hide(); activePanel = EditorPanel.Font
                    }
                    EditorToolButton("Size", Icons.Filled.FormatSize, enabled = selectedLayerId != null) {
                        editingLayerId = null; keyboard?.hide(); activePanel = EditorPanel.Size
                    }
                    EditorToolButton("Color", Icons.Filled.Palette, enabled = selectedLayerId != null) {
                        editingLayerId = null; keyboard?.hide(); activePanel = EditorPanel.Color
                    }
                    EditorToolButton("Delete", Icons.Filled.Delete, enabled = selectedLayerId != null) { deleteSelectedLayer() }
                }
            }
        }
    }
    val selectedIndex = layers.indexOfFirst { it.id == selectedLayerId }
    if (activePanel != null && selectedIndex < 0) {
        // The selected layer was deleted (or none was ever selected) while its panel was open.
        activePanel = null
    }
    activePanel?.let { panel ->
        if (selectedIndex < 0) return@let
        val selectedLayer = layers[selectedIndex]
        ModalBottomSheet(onDismissRequest = { activePanel = null; showAllFonts = false; fontQuery = "" }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (panel) {
                    EditorPanel.Font -> {
                        if (!showAllFonts) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Fonts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { showAllFonts = true }) { Text("Browse all") }
                            }
                            val carouselLabels = (
                                recentFontLabels + fontOptions.drop(1).map { it.first } + fontOptions.take(1).map { it.first }
                            ).distinct().take(10)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(carouselLabels.mapNotNull { name -> fontOptions.firstOrNull { it.first == name } }) { (label, optionTypeface) ->
                                    val selected = label == selectedLayer.fontLabel
                                    Surface(
                                        onClick = { selectLayerFont(selectedIndex, label) },
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.width(128.dp).heightIn(min = 86.dp),
                                    ) {
                                        Column(
                                            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                selectedLayer.text.ifBlank { "Aa" }.replace('\n', ' ').take(14),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily(optionTypeface),
                                                maxLines = 1,
                                            )
                                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                            if (selected) {
                                                HorizontalDivider(
                                                    modifier = Modifier.width(28.dp).padding(top = 5.dp),
                                                    thickness = 3.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("All fonts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                TextButton(onClick = { showAllFonts = false; fontQuery = "" }) { Text("Recent") }
                            }
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
                                FontFilter.System -> fontOptions.take(1)
                                FontFilter.MyFonts -> fontOptions.drop(1)
                            }
                            val visibleFonts = filteredBySource.filter { (label, _) -> label.contains(fontQuery, ignoreCase = true) }
                            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                                items(visibleFonts) { (label, optionTypeface) ->
                                    val selected = label == selectedLayer.fontLabel
                                    Surface(
                                        onClick = { selectLayerFont(selectedIndex, label) },
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(label, style = MaterialTheme.typography.labelSmall)
                                                Text(
                                                    selectedLayer.text.ifBlank { "Aa" }.replace('\n', ' '),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily(optionTypeface),
                                                    maxLines = 1,
                                                )
                                            }
                                            if (selected) Text("✓", style = MaterialTheme.typography.titleLarge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    EditorPanel.Size -> {
                        Text("Text size · ${selectedLayer.sizePercent.roundToInt()}%", style = MaterialTheme.typography.titleLarge)
                        Slider(
                            value = selectedLayer.sizePercent,
                            onValueChange = { layers[selectedIndex] = layers[selectedIndex].copy(sizePercent = it) },
                            valueRange = 4f..24f,
                        )
                    }
                    EditorPanel.Color -> {
                        Text("Text color", style = MaterialTheme.typography.titleLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TEXT_COLORS.forEach { option ->
                                androidx.compose.foundation.layout.Box(
                                    Modifier.size(48.dp).background(option.composeColor, androidx.compose.foundation.shape.CircleShape)
                                        .border(if (selectedLayer.color == option) 4.dp else 1.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                                        .semantics { contentDescription = "${option.name} text" }
                                        .clickable { layers[selectedIndex] = layers[selectedIndex].copy(color = option) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selectedLayer.color == option) {
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

// Icon-only, matching the iOS app's write-on-image toolbar (SF Symbols
// plus/textformat/textformat.size/paintpalette there; the closest standard Material
// icons here). label is kept as the accessibility description since there's no
// visible text anymore.
@Composable
private fun androidx.compose.foundation.layout.RowScope.EditorToolButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f), enabled = enabled) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp))
    }
}

private fun overlayPaint(typeface: Typeface, textSize: Float, textColor: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    this.textSize = textSize
    color = textColor
    textAlign = Paint.Align.CENTER
    val shadow = if (android.graphics.Color.luminance(textColor) > .45f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    setShadowLayer(textSize / 18f, 0f, textSize / 30f, shadow)
}

private fun renderImage(source: Bitmap, layers: List<TextLayer>, typefaceFor: (String) -> Typeface): Bitmap {
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(output)
    layers.forEach { layer ->
        val paint = overlayPaint(typefaceFor(layer.fontLabel), output.height * layer.sizePercent / 100f, layer.color.value)
        drawOverlayText(canvas, layer.text, output.width * layer.position.x, output.height * layer.position.y, output.width * .9f, paint)
    }
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

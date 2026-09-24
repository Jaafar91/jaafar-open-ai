package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jaafar.remoteconfig.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Font creation, preview, export, and import screens. */

@Composable internal fun FontReadyScreen(
    vm: FontCreatorViewModel,
    previewText: String,
    changePreviewText: (String) -> Unit,
    back: () -> Unit,
    useOnImage: (String, String) -> Unit,
) {
    val project = vm.activeProject
    if (project == null) {
        Page("Fine-tune your font", back) { Text("Choose a handwriting font first.") }
        return
    }
    // Clamped to the sliders' own ranges below, not just setSpacing's wider validity check --
    // a font saved with a spacing value from before these ranges were narrowed (or otherwise
    // outside them) would hand the Slider a value past its valueRange, which crashed it.
    var letterSpacing by remember(project.name) { mutableFloatStateOf(project.letterSpacingMm.coerceIn(LETTER_SPACING_RANGE)) }
    var wordSpacing by remember(project.name) { mutableFloatStateOf(project.wordSpacingMm.coerceIn(WORD_SPACING_RANGE)) }
    // Regenerating the font is a disk write followed by a native Typeface reload of that same
    // file -- doing that on every intermediate value while a slider is still being dragged (it
    // can fire dozens of times a second) flooded the background executor with overlapping
    // regenerate/reload cycles against the same path, which could crash native font loading.
    // Debouncing means the actual regenerate only runs once the value has held still for a
    // moment, not on every pixel of a fast drag; each new change cancels the pending one.
    // Skips its very first firing on entering the screen -- the caller already generate()d
    // right before navigating here, so an immediate rerun would just redo the same work.
    var isFirstSpacingChange by remember(project.name) { mutableStateOf(true) }
    LaunchedEffect(letterSpacing, wordSpacing) {
        if (isFirstSpacingChange) {
            isFirstSpacingChange = false
        } else {
            delay(150)
            if (vm.setSpacing(letterSpacing.toString(), wordSpacing.toString())) vm.generate()
        }
    }
    // Word spacing has no visible effect with only one word in the preview -- there's nothing
    // to space apart -- so its control is disabled rather than left inertly interactive.
    val previewWordCount = previewText.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    // Matches the iOS app's "Fine-tune your font" screen: the preview *is* the screen --
    // a big live-rendered card with the text field woven directly into it, a single
    // slider-based spacing card, and one primary action -- instead of a status banner,
    // a completion badge, +/- spacing steppers, and two competing buttons.
    Page("Fine-tune your font", back, scrollable = true) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 130.dp), contentAlignment = Alignment.Center) {
                    val typeface = vm.previewTypeface
                    if (typeface == null) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            previewText.ifBlank { " " },
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(typeface)),
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                        )
                    }
                }
                OutlinedTextField(
                    value = previewText,
                    onValueChange = changePreviewText,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type something to preview") },
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                )
            }
        }
        // Drops straight into the drawing canvas for the letters in previewText -- reusing
        // phrase mode, now that it queues already-drawn characters too (see startPhrase) --
        // instead of leaving "I don't like this letter" with no obvious way to fix it.
        OutlinedButton(
            onClick = { vm.startPhrase(previewText) },
            modifier = Modifier.fillMaxWidth(),
            enabled = previewText.isNotBlank(),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Don't like a letter? Edit it")
        }
        if (vm.previewTypeface != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // These ranges match what TrueTypeGenerator actually produces (its hmtx
                    // advance widths are clamped to 500..4096 / 100..4096 font units) --
                    // the previous wider ranges mostly got silently clamped to the same
                    // value, so dragging looked like it did something but had no effect.
                    SpacingSlider("Letter spacing", letterSpacing, LETTER_SPACING_RANGE, 0.25f) { letterSpacing = it }
                    SpacingSlider(
                        "Word spacing", wordSpacing, WORD_SPACING_RANGE, 0.25f,
                        enabled = previewWordCount > 1,
                    ) { wordSpacing = it }
                    Text(
                        "Changes save automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = { useOnImage(project.name, previewText) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use on an image")
            }
        }
        if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
    }
}

private val LETTER_SPACING_RANGE = -3f..4f
private val WORD_SPACING_RANGE = 0.25f..8f

@Composable
private fun SpacingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current.copy(alpha = contentAlpha),
            )
            Text(
                "${String.format(java.util.Locale.US, "%.2f", value)} mm",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            // coerceAtLeast(0): Slider requires steps >= 0 -- defensive against a range/step
            // combination that would otherwise compute negative and crash it.
            steps = (((range.endInclusive - range.start) / step).toInt() - 1).coerceAtLeast(0),
            enabled = enabled,
        )
    }
}

/** Lets the customer pick TTF/OTF/WOFF before [onFormatSelected] runs -- shared by [ShareButton]
 *  and [DownloadButton] so the format choice looks and behaves the same in both places. [trigger]
 *  gets an onClick that opens the menu, so either caller can use whatever tappable it wants
 *  (an icon button, a full card) as the anchor. */
@Composable
private fun FormatMenuAnchor(onFormatSelected: (FontExportFormat) -> Unit, trigger: @Composable (onClick: () -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        trigger { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FontExportFormat.entries.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.label) },
                    onClick = { expanded = false; onFormatSelected(format) },
                )
            }
        }
    }
}

private fun shareExportedFont(context: android.content.Context, exported: java.io.File, format: FontExportFormat, name: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", exported)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = format.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share $name"))
}

/** Renders as an [AssetStyleCard] -- the same tappable "asset tile" style Home uses -- so it
 *  reads as a real action on the screen instead of a small icon a customer might miss. */
@Composable internal fun ShareButton(file: java.io.File, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FormatMenuAnchor(onFormatSelected = { format ->
        scope.launch {
            val exported = withContext(Dispatchers.IO) { exportFontFile(context, file, name, format) }
            shareExportedFont(context, exported, format, name)
        }
    }) { onClick ->
        AssetStyleCard("Share", "Send to another app", modifier, onClick) { ActionIcon(ActionIconType.Share, "Share $name") }
    }
}

/** Saves the generated font into the device's Downloads folder, distinct from [ShareButton]'s
 *  share sheet -- a customer who just wants a copy on their phone shouldn't have to go through
 *  another app to get one. Below Android 10 (no permission-free MediaStore.Downloads path,
 *  see [downloadToPublicDownloads]) this falls back to the same share sheet as [ShareButton].
 *  Renders as an [AssetStyleCard], matching [ShareButton]. */
@Composable internal fun DownloadButton(file: java.io.File, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FormatMenuAnchor(onFormatSelected = { format ->
        scope.launch {
            val exported = withContext(Dispatchers.IO) { exportFontFile(context, file, name, format) }
            val saved = withContext(Dispatchers.IO) { downloadToPublicDownloads(context, exported, exported.name, format.mimeType) }
            if (saved) {
                Toast.makeText(context, "Saved \"${exported.name}\" to Downloads", Toast.LENGTH_SHORT).show()
            } else {
                shareExportedFont(context, exported, format, name)
            }
        }
    }) { onClick ->
        AssetStyleCard("Download", "Save to your device", modifier, onClick) { ActionIcon(ActionIconType.Download, "Download $name") }
    }
}

internal enum class ActionIconType { Add, Edit, Share, Import, Download }

/** Hand-drawn action glyph (this app's own icon set, not Material Icons) -- reused wherever
 *  an add/edit/share/import action needs an icon-only control instead of a text button. */
@Composable internal fun ActionIcon(type: ActionIconType, description: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val stroke = 2.dp.toPx()
        when (type) {
            ActionIconType.Add -> {
                drawLine(color, Offset(size.width / 2, size.height * .2f), Offset(size.width / 2, size.height * .8f), stroke)
                drawLine(color, Offset(size.width * .2f, size.height / 2), Offset(size.width * .8f, size.height / 2), stroke)
            }
            ActionIconType.Edit -> {
                drawLine(color, Offset(size.width * .25f, size.height * .75f), Offset(size.width * .75f, size.height * .25f), stroke * 2)
                drawLine(color, Offset(size.width * .2f, size.height * .8f), Offset(size.width * .35f, size.height * .77f), stroke)
            }
            ActionIconType.Share -> {
                val radius = size.minDimension * .11f
                val left = Offset(size.width * .25f, size.height * .5f)
                val top = Offset(size.width * .72f, size.height * .25f)
                val bottom = Offset(size.width * .72f, size.height * .75f)
                drawLine(color, left, top, stroke)
                drawLine(color, left, bottom, stroke)
                drawCircle(color, radius, left)
                drawCircle(color, radius, top)
                drawCircle(color, radius, bottom)
            }
            ActionIconType.Import, ActionIconType.Download -> {
                // Down-arrow-into-tray icon -- Import brings an external file in, Download saves
                // this app's own generated file out to the device; same "incoming" shape reads
                // right for both, so one glyph covers them.
                drawLine(color, Offset(size.width / 2, size.height * .15f), Offset(size.width / 2, size.height * .7f), stroke)
                drawLine(color, Offset(size.width * .3f, size.height * .5f), Offset(size.width / 2, size.height * .7f), stroke)
                drawLine(color, Offset(size.width * .7f, size.height * .5f), Offset(size.width / 2, size.height * .7f), stroke)
                drawLine(color, Offset(size.width * .2f, size.height * .82f), Offset(size.width * .8f, size.height * .82f), stroke)
            }
        }
    }
}

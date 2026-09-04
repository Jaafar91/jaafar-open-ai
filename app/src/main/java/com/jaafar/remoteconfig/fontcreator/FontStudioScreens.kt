package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
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
    var letterSpacing by remember(project.name) { mutableFloatStateOf(project.letterSpacingMm) }
    var wordSpacing by remember(project.name) { mutableFloatStateOf(project.wordSpacingMm) }
    val updateSpacing: (Float, Float) -> Unit = { nextLetter, nextWord ->
        letterSpacing = nextLetter
        wordSpacing = nextWord
        if (vm.setSpacing(nextLetter.toString(), nextWord.toString())) vm.generate()
    }

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
                    SpacingSlider("Letter spacing", letterSpacing, -3f..4f, 0.25f) { updateSpacing(it, wordSpacing) }
                    SpacingSlider("Word spacing", wordSpacing, 0.25f..8f, 0.25f) { updateSpacing(letterSpacing, it) }
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

@Composable
private fun SpacingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                "${String.format(java.util.Locale.US, "%.2f", value)} mm",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = ((range.endInclusive - range.start) / step).toInt() - 1,
        )
    }
}

@Composable internal fun ShareButton(file: java.io.File, name: String) {
    val context = LocalContext.current
    IconButton(onClick = { val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "font/ttf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share $name")) }) { ActionIcon(ActionIconType.Share, "Share $name") }
}

internal enum class ActionIconType { Add, Edit, Share, Import }

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
            ActionIconType.Import -> {
                // Down-arrow-into-tray icon
                drawLine(color, Offset(size.width / 2, size.height * .15f), Offset(size.width / 2, size.height * .7f), stroke)
                drawLine(color, Offset(size.width * .3f, size.height * .5f), Offset(size.width / 2, size.height * .7f), stroke)
                drawLine(color, Offset(size.width * .7f, size.height * .5f), Offset(size.width / 2, size.height * .7f), stroke)
                drawLine(color, Offset(size.width * .2f, size.height * .82f), Offset(size.width * .8f, size.height * .82f), stroke)
            }
        }
    }
}

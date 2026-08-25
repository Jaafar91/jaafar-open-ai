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
import androidx.compose.ui.text.font.FontWeight
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
    startDrawing: () -> Unit,
    useOnImage: (String, String) -> Unit,
) {
    val project = vm.activeProject
    if (project == null) {
        Page("Fine-tune your font", back) { Text("Choose a handwriting font first.") }
        return
    }
    val context = LocalContext.current
    val requiredCharacters = vm.activeCharacterOrder.toSet()
    val drawnCodePoints = project.drawings.map { it.codePoint }.toSet()
    val drawn = drawnCodePoints.intersect(requiredCharacters).size
    val complete = requiredCharacters.isNotEmpty() && requiredCharacters.all { it in drawnCodePoints }
    var letterSpacing by remember(project.name) { mutableFloatStateOf(project.letterSpacingMm) }
    var wordSpacing by remember(project.name) { mutableFloatStateOf(project.wordSpacingMm) }
    val updateSpacing: (Float, Float) -> Unit = { nextLetter, nextWord ->
        letterSpacing = nextLetter
        wordSpacing = nextWord
        if (vm.setSpacing(nextLetter.toString(), nextWord.toString())) vm.generate()
    }

    Page("Fine-tune your font", back, scrollable = true) {
        Text(project.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (vm.previewTypeface == null) {
            Text("Creating your font…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Turning $drawn drawn letters into a usable font preview.", style = MaterialTheme.typography.bodySmall)
        } else {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    if (complete) "FONT FILE READY" else "$drawn LETTERS INCLUDED",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                if (complete) "Your font is ready to use." else "Preview and adjust the letters you have drawn so far.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = previewText,
                onValueChange = changePreviewText,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Preview text") },
                minLines = 3,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(vm.previewTypeface!!)),
            )
            Text("Spacing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            SpacingControl(label = "Letter spacing", value = letterSpacing, step = 0.25f, min = -3f, max = 10f) {
                updateSpacing(it, wordSpacing)
            }
            SpacingControl(label = "Word spacing", value = wordSpacing, step = 0.5f, min = 0.2f, max = 50f) {
                updateSpacing(letterSpacing, it)
            }
            Text("Spacing changes are saved automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { useOnImage(project.name, previewText) }, modifier = Modifier.fillMaxWidth()) {
                Text("Use on an image")
            }
            OutlinedButton(onClick = startDrawing, modifier = Modifier.fillMaxWidth()) {
                Text("Continue drawing")
            }
            vm.generatedFont?.let { file ->
                OutlinedButton(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "font/ttf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share ${project.name}"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Share font file") }
            }
        }
        if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun ShareButton(file: java.io.File, name: String) {
    val context = LocalContext.current
    IconButton(onClick = { val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "font/ttf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share $name")) }) { ActionIcon(ActionIconType.Share, "Share $name") }
}

private enum class ActionIconType { Add, Edit, Share, Import }

@Composable private fun ActionIcon(type: ActionIconType, description: String) {
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

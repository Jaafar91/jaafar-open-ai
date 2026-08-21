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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jaafar.remoteconfig.R

/** Character-drawing and spacing workflow. */

@Composable internal fun LettersScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    preview: () -> Unit,
    adjustSpacing: () -> Unit,
    setPreviewText: (String) -> Unit,
) = Page("Font workspace", back, actions = {
    val file = vm.generatedFont
    val project = vm.activeProject
    if (file != null && project != null) ShareButton(file, project.name)
}) {
    var showEditDrawnLetters by remember { mutableStateOf(false) }
    var showPhraseDialog by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    val project = vm.activeProject
    val total = vm.activeCharacterOrder.size
    val drawn = vm.drawings.size
    val nextCode = vm.activeCharacterOrder.firstOrNull { it !in vm.drawings }

    Text(project?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Draw one letter at a time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$drawn of $total letters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (total > 0) LinearProgressIndicator(progress = (drawn.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
        }
    }

    if (drawn == 0) {
        Text("Start your font", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = vm::startPaging, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start drawing")
        }
        OutlinedButton(onClick = { phrase = ""; showPhraseDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Use a phrase")
        }
    } else if (nextCode != null) {
        Text("Keep building your font.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { vm.startPaging() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Continue drawing")
        }
        OutlinedButton(onClick = { phrase = ""; showPhraseDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Draw letters from a phrase")
        }
        OutlinedButton(onClick = { vm.generate(); preview() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Visibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try your font")
        }
    } else {
        Text("Your letter set is complete.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Button(onClick = { vm.generate(); preview() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Visibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try your font")
        }
    }

    if (drawn > 0) {
        OutlinedButton(onClick = { showEditDrawnLetters = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Edit letters")
        }
        OutlinedButton(onClick = adjustSpacing, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Tune, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Adjust spacing")
        }
    }
    if (showEditDrawnLetters) AlertDialog(
        onDismissRequest = { showEditDrawnLetters = false },
        title = { Text("Edit drawn letters") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    vm.drawings.keys.sortedWith(
                        compareBy<Int> {
                            when (it) {
                                in 'A'.code..'Z'.code -> 0
                                in 'a'.code..'z'.code -> 1
                                in '0'.code..'9'.code -> 2
                                else -> 3
                            }
                        }.thenBy { it },
                    ),
                ) { codePoint ->
                    OutlinedButton(
                        onClick = {
                            showEditDrawnLetters = false
                            vm.edit(codePoint)
                        },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(codePoint.toChar().toString())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showEditDrawnLetters = false }) { Text("Done") } },
    )

    if (showPhraseDialog) AlertDialog(
        onDismissRequest = { showPhraseDialog = false },
        title = { Text("Use a phrase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type a phrase. You will draw its missing letters first.")
                OutlinedTextField(phrase, { phrase = it }, Modifier.fillMaxWidth(), label = { Text("Phrase") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.drawMissingCharacters(phrase)
                setPreviewText(phrase)
                showPhraseDialog = false
            }, enabled = phrase.isNotBlank()) { Text("Start drawing") }
        },
        dismissButton = { TextButton(onClick = { showPhraseDialog = false }) { Text("Cancel") } },
    )


}

@Composable
internal fun SpacingScreen(
    vm: FontCreatorViewModel,
    previewText: String,
    changePreviewText: (String) -> Unit,
    back: () -> Unit,
) {
    var letter by remember(vm.activeProject) { mutableFloatStateOf(vm.activeProject?.letterSpacingMm ?: 0f) }
    var word by remember(vm.activeProject) { mutableFloatStateOf(vm.activeProject?.wordSpacingMm ?: 3f) }
    var showSampleEditor by remember { mutableStateOf(false) }

    Page("Spacing", back) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sample", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = { showSampleEditor = true },
                        modifier = Modifier.semantics { contentDescription = "Edit sample text" },
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                    }
                }
                Text(
                    spacingPreviewText(
                        previewText.ifBlank { DEFAULT_PREVIEW_TEXT },
                        letter,
                        word,
                    ),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = vm.previewTypeface?.let(::FontFamily) ?: FontFamily.Default,
                    ),
                )
            }
        }

        SpacingControl(label = "Letter spacing", value = letter, step = 0.25f, min = -3f, max = 10f) { letter = it }
        SpacingControl(label = "Word spacing", value = word, step = 0.5f, min = 0.2f, max = 50f) { word = it }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (vm.setSpacing(letter.toString(), word.toString())) {
                    vm.generate()
                    back()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply spacing") }
        if (vm.status.isNotBlank()) {
            Text(vm.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSampleEditor) {
        var editedText by remember(previewText) { mutableStateOf(previewText) }
        AlertDialog(
            onDismissRequest = { showSampleEditor = false },
            title = { Text("Sample text") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("Text") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    changePreviewText(editedText)
                    showSampleEditor = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showSampleEditor = false }) { Text("Cancel") } },
        )
    }
}

private fun spacingPreviewText(
    text: String,
    letterSpacingMm: Float,
    wordSpacingMm: Float,
) = buildAnnotatedString {
    val letterSpacing = (letterSpacingMm * 0.5f).sp
    val wordSpacing = (wordSpacingMm * 0.5f).sp
    text.forEach { character ->
        withStyle(SpanStyle(letterSpacing = if (character.isWhitespace()) wordSpacing else letterSpacing)) {
            append(character)
        }
    }
}

@Composable
private fun SpacingControl(
    label: String,
    value: Float,
    step: Float,
    min: Float,
    max: Float,
    change: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { change((value - step).coerceAtLeast(min)) }, enabled = value > min) { Text("−") }
        Text("${String.format(java.util.Locale.US, "%.2f", value)} mm", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { change((value + step).coerceAtMost(max)) }, enabled = value < max) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GlyphEditorScreen(codePoint: Int, initial: GlyphDrawing?, pagingMode: Boolean, pagingProgress: Pair<Int, Int>?, canGoPrevious: Boolean, referenceTypeface: Typeface?, onCancel: () -> Unit, onPrevious: () -> Unit, onSkip: () -> Unit, onSave: (GlyphDrawing) -> Unit) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember(codePoint) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember(codePoint) { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    var strokeWidth by remember { mutableFloatStateOf(initial?.strokeWidth ?: 8f) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val initialStrokes = remember(codePoint) { initial?.strokes ?: emptyList<GlyphStroke>() }
    val isDirty = strokes != initialStrokes
    val handleBack = { if (isDirty) showDiscardDialog = true else onCancel() }
    BackHandler(onBack = handleBack)
    val char = codePoint.toChar().toString()
    val title = "Draw a letter"
    val isLastInQueue = pagingProgress != null && pagingProgress.first == pagingProgress.second
    val saveLabel = when {
        isLastInQueue -> "Save & Finish"
        pagingMode -> "Save & Next"
        else -> "Save letter"
    }
    Scaffold(
        topBar = {
            AppTopBar(title, handleBack) {
                if (pagingMode && canGoPrevious) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Filled.NavigateBefore, contentDescription = "Previous letter")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pagingProgress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${pagingProgress.first} of ${pagingProgress.second}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = (pagingProgress.first.toFloat() / pagingProgress.second).coerceIn(0f, 1f),
                        modifier = Modifier.weight(1f).height(4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Text("Copy the reference character into the canvas.", style = MaterialTheme.typography.bodySmall)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("REFERENCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        char,
                        style = MaterialTheme.typography.displayLarge,
                        fontFamily = referenceTypeface?.let(::FontFamily),
                    )
                }
            }
            Text("Gray: ascender/descender · Red: baseline", style = MaterialTheme.typography.bodySmall)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .background(Color.White)
                    .border(1.dp, Color.Gray)
                    .pointerInput(codePoint, strokes) {
                        detectDragGestures(
                            onDragStart = { active = listOf(GlyphPoint(it.x, it.y)) },
                            onDrag = { change, _ ->
                                change.consume()
                                val next = GlyphPoint(change.position.x, change.position.y)
                                if (active.lastOrNull()?.let { hypotSquared(it, next) > 9f } != false) active = active + next
                            },
                            onDragEnd = {
                                if (active.size > 1) strokes = strokes + GlyphStroke(active)
                                active = emptyList()
                            },
                            onDragCancel = { active = emptyList() },
                        )
                    },
            ) {
                canvasSize = size.width to size.height
                if (referenceTypeface != null) {
                    drawIntoCanvas { canvas ->
                        val refPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = referenceTypeface
                            textSize = size.height * 0.72f
                            color = android.graphics.Color.argb(34, 0, 0, 0)
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        canvas.nativeCanvas.drawText(char, size.width / 2, size.height * 0.82f, refPaint)
                    }
                }
                drawCoordinateRulers()
                drawLine(Color.LightGray, Offset(0f, size.height * .1f), Offset(size.width, size.height * .1f), 2f)
                drawLine(Color.Red, Offset(0f, size.height * .78f), Offset(size.width, size.height * .78f), 3f)
                drawLine(Color.LightGray, Offset(0f, size.height * .94f), Offset(size.width, size.height * .94f), 2f)
                (strokes.map { it.points } + listOf(active)).forEach { points ->
                    if (points.size > 1) {
                        drawPath(
                            Path().apply {
                                moveTo(points[0].x, points[0].y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            },
                            Color.Black,
                            style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Thickness", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 2f..24f,
                    modifier = Modifier.weight(1f),
                )
                Text("${strokeWidth.toInt()}", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo")
                }
                IconButton({ strokes = emptyList(); active = emptyList() }, enabled = strokes.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
                if (pagingMode) TextButton(onSkip) { Text("Skip") }
                Button({ onSave(GlyphDrawing(codePoint, strokes, canvasSize.first, canvasSize.second, strokeWidth)) }, Modifier.weight(1f), enabled = strokes.isNotEmpty()) {
                    Text(saveLabel, maxLines = 1)
                }
            }
        }
    }
    if (showDiscardDialog) AlertDialog(
        onDismissRequest = { showDiscardDialog = false },
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved strokes for this letter. Discard them?") },
        confirmButton = { TextButton(onClick = { showDiscardDialog = false; onCancel() }) { Text("Discard") } },
        dismissButton = { OutlinedButton(onClick = { showDiscardDialog = false }) { Text("Keep drawing") } },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoordinateRulers() {
    val rulerColor = android.graphics.Color.rgb(110, 110, 110)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = rulerColor
        textSize = 10.sp.toPx()
    }
    listOf(0, 25, 50, 75, 100).forEach { value ->
        val x = size.width * value / 100f
        val y = size.height * value / 100f
        drawLine(Color.LightGray, Offset(x, 0f), Offset(x, 7.dp.toPx()), 1.dp.toPx())
        drawLine(Color.LightGray, Offset(0f, y), Offset(7.dp.toPx(), y), 1.dp.toPx())
        paint.textAlign = when (value) {
            0 -> android.graphics.Paint.Align.LEFT
            100 -> android.graphics.Paint.Align.RIGHT
            else -> android.graphics.Paint.Align.CENTER
        }
        drawContext.canvas.nativeCanvas.drawText(value.toString(), x, 12.dp.toPx(), paint)
        paint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(value.toString(), 3.dp.toPx(), (y + 11.dp.toPx()).coerceAtMost(size.height - 2.dp.toPx()), paint)
    }
    paint.textAlign = android.graphics.Paint.Align.RIGHT
    drawContext.canvas.nativeCanvas.drawText("X", size.width - 3.dp.toPx(), 25.dp.toPx(), paint)
    drawContext.canvas.nativeCanvas.drawText("Y", 20.dp.toPx(), size.height - 3.dp.toPx(), paint)
}

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jaafar.remoteconfig.R

/** Character-drawing and spacing workflow. */

@Composable internal fun LettersScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    editLetters: () -> Unit,
    preview: () -> Unit,
    adjustSpacing: () -> Unit,
    setPreviewText: (String) -> Unit,
) = Page("Font workspace", back, actions = {
    val file = vm.generatedFont
    val project = vm.activeProject
    if (file != null && project != null) ShareButton(file, project.name)
}) {
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
        OutlinedButton(onClick = editLetters, modifier = Modifier.fillMaxWidth()) {
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
    val updateSpacing = { nextLetter: Float, nextWord: Float ->
        letter = nextLetter
        word = nextWord
        if (vm.setSpacing(nextLetter.toString(), nextWord.toString())) {
            vm.generate()
        }
    }

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
                    text = previewText.ifBlank { DEFAULT_PREVIEW_TEXT },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = vm.previewTypeface?.let(::FontFamily) ?: FontFamily.Default,
                    ),
                )
            }
        }

        SpacingControl(label = "Letter spacing", value = letter, step = 0.25f, min = -3f, max = 10f) {
            updateSpacing(it, word)
        }
        SpacingControl(label = "Word spacing", value = word, step = 0.5f, min = 0.2f, max = 50f) {
            updateSpacing(letter, it)
        }

        Spacer(Modifier.weight(1f))
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
@Composable internal fun GlyphEditorScreen(
    codePoint: Int,
    initial: GlyphDrawing?,
    drawings: Map<Int, GlyphDrawing>,
    characterOrder: List<Int>,
    pagingMode: Boolean,
    pagingProgress: Pair<Int, Int>?,
    canGoPrevious: Boolean,
    referenceTypeface: Typeface?,
    onCancel: () -> Unit,
    onPrevious: () -> Unit,
    onNavigatePrevious: () -> Unit,
    onNavigateNext: () -> Unit,
    onSelectCharacter: (Int) -> Unit,
    onSkip: () -> Unit,
    onSave: (GlyphDrawing) -> Unit,
) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember(codePoint) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember(codePoint) { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    var strokeWidth by remember { mutableFloatStateOf(initial?.strokeWidth ?: 8f) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showCharacterPicker by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val initialStrokes = remember(codePoint) { initial?.strokes ?: emptyList<GlyphStroke>() }
    val initialStrokeWidth = remember(codePoint) { initial?.strokeWidth ?: 8f }
    val isDirty = strokes != initialStrokes || strokeWidth != initialStrokeWidth
    val navigateSafely: (() -> Unit) -> Unit = { action ->
        if (isDirty) {
            pendingNavigation = action
            showDiscardDialog = true
        } else action()
    }
    val handleBack = { navigateSafely(onCancel) }
    BackHandler(onBack = handleBack)
    val char = codePoint.toChar().toString()
    val title = "${if (initial == null) "Draw" else "Edit"} letter $char"
    val characterIndex = characterOrder.indexOf(codePoint)
    val canNavigatePrevious = characterIndex > 0
    val canNavigateNext = characterIndex >= 0 && characterIndex < characterOrder.lastIndex
    val isLastInQueue = pagingProgress != null && pagingProgress.first == pagingProgress.second
    val saveLabel = when {
        isLastInQueue -> "Save & Finish"
        pagingMode -> "Save & Next"
        initial != null -> "Save changes"
        else -> "Save letter"
    }
    Scaffold(
        topBar = {
            AppTopBar(title, handleBack) {
                if (pagingMode && canGoPrevious) {
                    OutlinedButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) { Text("Previous") }
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
            if (!pagingMode) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { navigateSafely(onNavigatePrevious) },
                        enabled = canNavigatePrevious,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("← Previous", maxLines = 1) }
                    FilledTonalButton(
                        onClick = { showCharacterPicker = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("Letter $char ▾", maxLines = 1) }
                    OutlinedButton(
                        onClick = { navigateSafely(onNavigateNext) },
                        enabled = canNavigateNext,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) { Text("Next →", maxLines = 1) }
                }
                Spacer(Modifier.height(8.dp))
            }
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
    if (showCharacterPicker) CharacterPickerSheet(
        characterOrder = characterOrder,
        drawings = drawings,
        selectedCodePoint = codePoint,
        onDismiss = { showCharacterPicker = false },
        onSelect = { selected ->
            showCharacterPicker = false
            if (selected != codePoint) navigateSafely { onSelectCharacter(selected) }
        },
    )
    if (showDiscardDialog) AlertDialog(
        onDismissRequest = { showDiscardDialog = false; pendingNavigation = null },
        title = { Text("Discard changes?") },
        text = { Text("You have unsaved strokes for this letter. Discard them?") },
        confirmButton = { TextButton(onClick = {
            val action = pendingNavigation
            showDiscardDialog = false
            pendingNavigation = null
            action?.invoke()
        }) { Text("Discard changes") } },
        dismissButton = { OutlinedButton(onClick = { showDiscardDialog = false; pendingNavigation = null }) { Text("Keep editing") } },
    )
}

private enum class CharacterCategory(val label: String) {
    Uppercase("Uppercase"), Lowercase("Lowercase"), Numbers("Numbers"), Symbols("Symbols")
}

private fun CharacterCategory.contains(codePoint: Int): Boolean = when (this) {
    CharacterCategory.Uppercase -> codePoint.toChar().isUpperCase()
    CharacterCategory.Lowercase -> codePoint.toChar().let { it.isLowerCase() || (it.isLetter() && !it.isUpperCase()) }
    CharacterCategory.Numbers -> codePoint.toChar().isDigit()
    CharacterCategory.Symbols -> !codePoint.toChar().isLetterOrDigit()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharacterPickerSheet(
    characterOrder: List<Int>,
    drawings: Map<Int, GlyphDrawing>,
    selectedCodePoint: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val availableCategories = CharacterCategory.entries.filter { category -> characterOrder.any(category::contains) }
    var selectedCategory by remember(selectedCodePoint, characterOrder) {
        mutableStateOf(availableCategories.firstOrNull { it.contains(selectedCodePoint) } ?: availableCategories.firstOrNull())
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Choose a character", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        if (availableCategories.isNotEmpty()) {
            ScrollableTabRow(selectedTabIndex = availableCategories.indexOf(selectedCategory).coerceAtLeast(0)) {
                availableCategories.forEach { category ->
                    Tab(
                        selected = category == selectedCategory,
                        onClick = { selectedCategory = category },
                        text = { Text(category.label) },
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(64.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(characterOrder.filter { selectedCategory?.contains(it) == true }) { candidate ->
                    val selected = candidate == selectedCodePoint
                    Surface(
                        onClick = { onSelect(candidate) },
                        modifier = Modifier.padding(4.dp).aspectRatio(1f),
                        shape = MaterialTheme.shapes.small,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        drawings[candidate]?.let { drawing ->
                            GlyphPreviewCanvas(drawing, Modifier.fillMaxSize().padding(8.dp))
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(candidate.toChar().toString(), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlyphPreviewCanvas(drawing: GlyphDrawing, modifier: Modifier = Modifier) {
    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val scaleX = size.width / drawing.canvasWidth.coerceAtLeast(1f)
        val scaleY = size.height / drawing.canvasHeight.coerceAtLeast(1f)
        drawing.strokes.forEach { stroke ->
            if (stroke.points.size > 1) drawPath(
                path = Path().apply {
                    moveTo(stroke.points.first().x * scaleX, stroke.points.first().y * scaleY)
                    stroke.points.drop(1).forEach { lineTo(it.x * scaleX, it.y * scaleY) }
                },
                color = strokeColor,
                style = Stroke((drawing.strokeWidth * minOf(scaleX, scaleY)).coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

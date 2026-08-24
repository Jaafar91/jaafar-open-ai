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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showPhraseDialog by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    val total = vm.activeCharacterOrder.size
    val drawn = vm.drawings.size
    val nextCode = vm.activeCharacterOrder.firstOrNull { it !in vm.drawings }
    val categories = CharacterCategory.entries.filter { category -> vm.activeCharacterOrder.any(category::contains) }
    var selectedCategory by remember(vm.activeProject) { mutableStateOf(categories.firstOrNull()) }
    val visibleCharacters = vm.activeCharacterOrder.filter { selectedCategory?.contains(it) == true }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("$drawn of $total letters completed", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (drawn > 0) TextButton(onClick = { vm.generate(); preview() }) { Text("Preview font") }
    }
    if (total > 0) LinearProgressIndicator(progress = (drawn.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())

    if (nextCode != null) {
        Button(onClick = { vm.edit(nextCode) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Draw next letter · ${nextCode.toChar()}")
        }
    } else {
        Button(onClick = { vm.generate(); preview() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Visibility, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Preview font")
        }
    }

    if (categories.isNotEmpty()) {
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
            edgePadding = 0.dp,
        ) {
            categories.forEach { category ->
                Tab(
                    selected = category == selectedCategory,
                    onClick = { selectedCategory = category },
                    text = { Text(category.shortLabel) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(64.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(visibleCharacters) { codePoint ->
                LetterTile(
                    codePoint = codePoint,
                    drawing = vm.drawings[codePoint],
                    onClick = { vm.edit(codePoint) },
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton(onClick = { phrase = ""; showPhraseDialog = true }) { Text("Draw from phrase") }
        if (drawn > 0) TextButton(onClick = adjustSpacing) { Text("Adjust spacing") }
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
    onSaveAndContinue: (GlyphDrawing) -> Unit,
    onSaveAndStay: (GlyphDrawing) -> Unit,
    onSaveAndClose: (GlyphDrawing) -> Unit,
) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember(codePoint) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember(codePoint) { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    var strokeWidth by remember { mutableFloatStateOf(initial?.strokeWidth ?: 8f) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSavedConfirmation by remember(codePoint) { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var savedStrokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var savedStrokeWidth by remember(codePoint) { mutableFloatStateOf(initial?.strokeWidth ?: 8f) }
    val isDirty = strokes != savedStrokes || strokeWidth != savedStrokeWidth
    val navigateSafely: (() -> Unit) -> Unit = { action ->
        if (isDirty) {
            pendingNavigation = action
            showDiscardDialog = true
        } else action()
    }
    val handleBack = { navigateSafely(onCancel) }
    BackHandler(onBack = handleBack)
    val char = codePoint.toChar().toString()
    val title = "Letter $char"
    val characterIndex = characterOrder.indexOf(codePoint)
    val canNavigatePrevious = characterIndex > 0
    val canNavigateNext = characterIndex >= 0 && characterIndex < characterOrder.lastIndex
    val isLastInQueue = pagingProgress != null && pagingProgress.first == pagingProgress.second
    val saveLabel = when {
        isLastInQueue -> "Save & Finish"
        pagingMode -> "Save & Next"
        else -> "Save"
    }
    val drawing = { GlyphDrawing(codePoint, strokes, canvasSize.first, canvasSize.second, strokeWidth) }
    val savePrimary = {
        val saved = drawing()
        savedStrokes = strokes
        savedStrokeWidth = strokeWidth
        when {
            pagingMode -> onSave(saved)
            initial == null -> onSaveAndContinue(saved)
            else -> {
                onSaveAndStay(saved)
                showSavedConfirmation = true
            }
        }
    }
    LaunchedEffect(showSavedConfirmation) {
        if (showSavedConfirmation) {
            kotlinx.coroutines.delay(1600)
            showSavedConfirmation = false
        }
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
                if (!pagingMode) Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Save & close") },
                            enabled = strokes.isNotEmpty(),
                            onClick = {
                                showMoreMenu = false
                                onSaveAndClose(drawing())
                            },
                        )
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
            if (!pagingMode) {
                Text(
                    if (initial == null) "New letter" else if (showSavedConfirmation) "Saved ✓" else "Saved letter",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (showSavedConfirmation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    item {
                        FilledTonalIconButton(
                            onClick = { navigateSafely(onNavigatePrevious) },
                            enabled = canNavigatePrevious,
                        ) { Text("‹") }
                    }
                    items(characterOrder) { candidate ->
                        val selected = candidate == codePoint
                        Surface(
                            onClick = { if (!selected) navigateSafely { onSelectCharacter(candidate) } },
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        ) { Box(contentAlignment = Alignment.Center) { Text(candidate.toChar().toString(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }
                    }
                    item {
                        FilledTonalIconButton(
                            onClick = { navigateSafely(onNavigateNext) },
                            enabled = canNavigateNext,
                        ) { Text("›") }
                    }
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
                Button(savePrimary, Modifier.weight(1f), enabled = strokes.isNotEmpty() && (isDirty || initial == null || pagingMode)) {
                    Text(saveLabel, maxLines = 1)
                }
            }
        }
    }
    if (showDiscardDialog) ModalBottomSheet(onDismissRequest = { showDiscardDialog = false; pendingNavigation = null }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Unsaved changes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Save this letter before continuing?")
            Button(
                onClick = {
                    val action = pendingNavigation
                    onSaveAndStay(drawing())
                    savedStrokes = strokes
                    savedStrokeWidth = strokeWidth
                    showDiscardDialog = false
                    pendingNavigation = null
                    action?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = strokes.isNotEmpty(),
            ) { Text("Save and continue") }
            OutlinedButton(
                onClick = {
                    val action = pendingNavigation
                    showDiscardDialog = false
                    pendingNavigation = null
                    action?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Discard") }
            TextButton(onClick = { showDiscardDialog = false; pendingNavigation = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private enum class CharacterCategory {
    Uppercase, Lowercase, Numbers, Symbols
}

private val CharacterCategory.shortLabel: String get() = when (this) {
    CharacterCategory.Uppercase -> "ABC"
    CharacterCategory.Lowercase -> "abc"
    CharacterCategory.Numbers -> "123"
    CharacterCategory.Symbols -> "Symbols"
}

private fun CharacterCategory.contains(codePoint: Int): Boolean = when (this) {
    CharacterCategory.Uppercase -> codePoint.toChar().isUpperCase()
    CharacterCategory.Lowercase -> codePoint.toChar().let { it.isLowerCase() || (it.isLetter() && !it.isUpperCase()) }
    CharacterCategory.Numbers -> codePoint.toChar().isDigit()
    CharacterCategory.Symbols -> !codePoint.toChar().isLetterOrDigit()
}

@Composable
private fun LetterTile(codePoint: Int, drawing: GlyphDrawing?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(4.dp).aspectRatio(1f),
        shape = MaterialTheme.shapes.small,
        color = if (drawing == null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer,
        border = androidx.compose.foundation.BorderStroke(
            if (drawing == null) 1.dp else 0.dp,
            if (drawing == null) MaterialTheme.colorScheme.outline else Color.Transparent,
        ),
    ) {
        if (drawing != null) {
            GlyphPreviewCanvas(drawing, Modifier.fillMaxSize().padding(8.dp))
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(codePoint.toChar().toString(), style = MaterialTheme.typography.titleLarge)
                    Text("+", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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

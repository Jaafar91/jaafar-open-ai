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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.graphics.toArgb
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
    fineTune: () -> Unit,
    useOnImage: (String) -> Unit,
) = Page("Font workspace", back, actions = {
    val file = vm.generatedFont
    val project = vm.activeProject
    if (file != null && project != null) ShareButton(file, project.name)
}) {
    val project = vm.activeProject
    val total = vm.activeCharacterOrder.size
    val drawn = vm.drawings.size
    val nextCode = vm.activeCharacterOrder.firstOrNull { it !in vm.drawings }
    val useCurrentFont: () -> Unit = {
        project?.let {
            vm.generate()
            useOnImage(it.name)
        }
    }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameName by remember(project?.name) { mutableStateOf(project?.name.orEmpty()) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(project?.name.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        IconButton(onClick = { renameName = project?.name.orEmpty(); showRenameDialog = true }) {
            Icon(Icons.Filled.Edit, contentDescription = "Rename font")
        }
    }
    Text("Draw one letter at a time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$drawn of $total letters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (total > 0) LinearProgressIndicator(progress = (drawn.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
        }
    }

    if (drawn == 0 && nextCode != null) {
        Text("Start your font", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = { vm.edit(nextCode) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start drawing")
        }
    } else if (nextCode != null) {
        Text("Keep building your font.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { vm.edit(nextCode) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Continue drawing")
        }
        OutlinedButton(onClick = useCurrentFont, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Use this font on an image")
        }
    } else {
        Text("Your font is ready!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "You completed all characters. Now put your handwriting to use.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = useCurrentFont, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Use this font on an image")
        }
        OutlinedButton(onClick = vm::editLetters, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Edit letters")
        }
    }

    if (drawn > 0) {
        OutlinedButton(onClick = fineTune, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Tune, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Fine-tune your font")
        }
    }

    if (showRenameDialog && project != null) {
        val cleanName = renameName.trim()
        val invalidName = cleanName.isNotEmpty() && normalizedFontStorageKey(cleanName).isBlank()
        val duplicateName = vm.projects.withIndex().any { (index, candidate) ->
            index != vm.activeProjectIndex && (candidate.name.equals(cleanName, ignoreCase = true) ||
                normalizedFontStorageKey(candidate.name) == normalizedFontStorageKey(cleanName))
        }
        val unchangedName = cleanName == project.name
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename font") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Font name") },
                    singleLine = true,
                    isError = invalidName || duplicateName,
                    supportingText = {
                        when {
                            duplicateName -> Text("A font with that name already exists.")
                            invalidName -> Text("Use letters or numbers in the font name.")
                        }
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = { if (vm.renameActiveProject(renameName)) showRenameDialog = false },
                    enabled = cleanName.isNotEmpty() && !invalidName && !duplicateName && !unchangedName,
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }
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
internal fun SpacingControl(
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { change((value - step).coerceAtLeast(min)) },
            modifier = Modifier.width(52.dp),
            contentPadding = PaddingValues(0.dp),
            enabled = value > min,
        ) { Text("−") }
        Box(Modifier.width(84.dp), contentAlignment = Alignment.Center) {
            Text("${String.format(java.util.Locale.US, "%.2f", value)} mm", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onClick = { change((value + step).coerceAtMost(max)) },
            modifier = Modifier.width(52.dp),
            contentPadding = PaddingValues(0.dp),
            enabled = value < max,
        ) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun GlyphEditorScreen(
    codePoint: Int,
    initial: GlyphDrawing?,
    defaultStrokeWidth: Float,
    drawings: Map<Int, GlyphDrawing>,
    characterOrder: List<Int>,
    pagingMode: Boolean,
    pagingProgress: Pair<Int, Int>?,
    canGoPrevious: Boolean,
    referenceTypeface: Typeface,
    phraseModeEnabled: Boolean,
    phraseText: String,
    onCreatePhrase: (String) -> Boolean,
    onDisablePhrase: () -> Unit,
    onCancel: () -> Unit,
    onPrevious: () -> Unit,
    onSelectCharacter: (Int) -> Unit,
    onSkip: () -> Unit,
    onSave: (GlyphDrawing) -> Unit,
    onSaveAndContinue: (GlyphDrawing) -> Unit,
    onSaveAndStay: (GlyphDrawing) -> Unit,
) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember(codePoint) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember(codePoint) { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    var strokeWidth by remember(codePoint) { mutableFloatStateOf(initial?.strokeWidth ?: defaultStrokeWidth) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPhraseDialog by remember { mutableStateOf(false) }
    var phraseDraft by remember(phraseText) { mutableStateOf(phraseText) }
    var showReference by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var savedStrokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var savedStrokeWidth by remember(codePoint) { mutableFloatStateOf(initial?.strokeWidth ?: defaultStrokeWidth) }
    val strokesChanged = strokes != savedStrokes
    val thicknessChanged = strokes.isNotEmpty() && strokeWidth != savedStrokeWidth
    val isDirty = strokesChanged || thicknessChanged
    val navigateSafely: (() -> Unit) -> Unit = { action ->
        if (isDirty) {
            pendingNavigation = action
            showDiscardDialog = true
        } else action()
    }
    val handleBack = { navigateSafely(onCancel) }
    BackHandler(onBack = handleBack)
    val char = codePoint.toChar().toString()
    val title = "Draw $char"
    val characterIndex = characterOrder.indexOf(codePoint)
    val completedCharacterCount = characterOrder.count { it in drawings }
    val letterBarState = rememberLazyListState()
    LaunchedEffect(codePoint, characterOrder) {
        if (characterIndex >= 0) {
            letterBarState.scrollToItem(characterIndex)
            withFrameNanos { _ -> }
            val layout = letterBarState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == characterIndex }
            if (item != null) {
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                val itemCenter = item.offset + item.size / 2f
                letterBarState.animateScrollBy(itemCenter - viewportCenter)
            }
        }
    }
    val isLastInQueue = pagingProgress != null && pagingProgress.first == pagingProgress.second
    val isFinalMissingCharacter = initial == null && codePoint !in drawings && characterOrder.count { it !in drawings } == 1
    val saveLabel = when {
        isLastInQueue || isFinalMissingCharacter -> "Save & Finish"
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
            else -> onSaveAndContinue(saved)
        }
    }
    Scaffold(
        topBar = {
            AppTopBar(title, handleBack) {
                if (pagingMode && !phraseModeEnabled && canGoPrevious) {
                    OutlinedButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) { Text("Previous") }
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Reference letter") },
                            trailingIcon = { Checkbox(checked = showReference, onCheckedChange = null) },
                            onClick = { showReference = !showReference; showMoreMenu = false },
                        )
                        DropdownMenuItem(
                            text = { Text("Phrase mode") },
                            trailingIcon = { Checkbox(checked = phraseModeEnabled, onCheckedChange = null) },
                            onClick = {
                                showMoreMenu = false
                                if (phraseModeEnabled) {
                                    onDisablePhrase()
                                } else {
                                    phraseDraft = phraseText
                                    showPhraseDialog = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!pagingMode || phraseModeEnabled) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val centerPadding = ((maxWidth - 48.dp) / 2).coerceAtLeast(0.dp)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        state = letterBarState,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = centerPadding),
                    ) {
                        items(characterOrder) { candidate ->
                            val selected = candidate == codePoint
                            val savedDrawing = drawings[candidate]
                            val tileColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            val tileContentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            Surface(
                                onClick = { if (!selected) navigateSafely { onSelectCharacter(candidate) } },
                                shape = MaterialTheme.shapes.small,
                                color = tileColor,
                                contentColor = tileContentColor,
                                modifier = Modifier.size(48.dp),
                            ) {
                                if (savedDrawing != null) {
                                    GlyphBarPreview(savedDrawing, tileContentColor, Modifier.fillMaxSize().padding(6.dp))
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(candidate.toChar().toString(), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        Text(
                                            "*",
                                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$completedCharacterCount / ${characterOrder.size} completed",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = if (characterOrder.isEmpty()) 0f else (completedCharacterCount.toFloat() / characterOrder.size).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text("Use the guides to keep every letter aligned and evenly sized.", style = MaterialTheme.typography.bodySmall)
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
                if (showReference) {
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = referenceTypeface
                            textSize = size.height * .68f
                            color = android.graphics.Color.argb(35, 25, 35, 55)
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        canvas.nativeCanvas.drawText(char, size.width / 2f, size.height * .78f, paint)
                    }
                }
                drawFontGuides()
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
                if (pagingMode && !phraseModeEnabled) TextButton(onSkip) { Text("Skip") }
                Button(savePrimary, Modifier.weight(1f), enabled = strokes.isNotEmpty() && (isDirty || initial == null || pagingMode)) {
                    Text(saveLabel, maxLines = 1)
                }
            }
        }
    }
    if (showPhraseDialog) {
        AlertDialog(
            onDismissRequest = { showPhraseDialog = false },
            title = { Text("Phrase mode") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the text you want to create. Only its supported missing characters will be shown.")
                    OutlinedTextField(
                        value = phraseDraft,
                        onValueChange = { phraseDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phrase") },
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val createPhrase = {
                            if (onCreatePhrase(phraseDraft)) showPhraseDialog = false
                        }
                        navigateSafely(createPhrase)
                    },
                    enabled = phraseDraft.isNotBlank(),
                ) { Text("Create phrase") }
            },
            dismissButton = { TextButton(onClick = { showPhraseDialog = false }) { Text("Cancel") } },
        )
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

@Composable
private fun GlyphBarPreview(drawing: GlyphDrawing, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = minOf(
            size.width / drawing.canvasWidth.coerceAtLeast(1f),
            size.height / drawing.canvasHeight.coerceAtLeast(1f),
        )
        val offsetX = (size.width - drawing.canvasWidth * scale) / 2f
        val offsetY = (size.height - drawing.canvasHeight * scale) / 2f
        drawing.strokes.forEach { stroke ->
            if (stroke.points.size > 1) {
                drawPath(
                    path = Path().apply {
                        moveTo(offsetX + stroke.points.first().x * scale, offsetY + stroke.points.first().y * scale)
                        stroke.points.drop(1).forEach { point ->
                            lineTo(offsetX + point.x * scale, offsetY + point.y * scale)
                        }
                    },
                    color = color,
                    style = Stroke(
                        width = (drawing.strokeWidth * scale).coerceAtLeast(1f),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFontGuides() {
    data class Guide(val fraction: Float, val label: String, val color: Color, val width: Float)
    val guides = listOf(
        Guide(.10f, "Ascender · 10%", Color(0xFF64748B), 1.5f),
        Guide(.20f, "Cap height · 20%", Color(0xFF3B82F6), 1.5f),
        Guide(.42f, "x-height · 42%", Color(0xFF3B82F6), 1.5f),
        Guide(.78f, "Baseline · 78%", Color(0xFFDC2626), 2.5f),
        Guide(.94f, "Descender · 94%", Color(0xFF64748B), 1.5f),
    )
    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.sp.toPx()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    guides.forEach { guide ->
        val y = size.height * guide.fraction
        drawLine(guide.color.copy(alpha = .7f), Offset(0f, y), Offset(size.width, y), guide.width.dp.toPx())
        labelPaint.color = guide.color.toArgb()
        labelPaint.textAlign = android.graphics.Paint.Align.LEFT
        drawContext.canvas.nativeCanvas.drawText(guide.label, 6.dp.toPx(), (y - 4.dp.toPx()).coerceAtLeast(11.dp.toPx()), labelPaint)
    }

    val sideGuideColor = Color(0xFF94A3B8).copy(alpha = .55f)
    val left = size.width * .08f
    val center = size.width * .5f
    val right = size.width * .92f
    drawLine(sideGuideColor, Offset(left, 0f), Offset(left, size.height), 1.dp.toPx())
    drawLine(sideGuideColor, Offset(center, 0f), Offset(center, size.height), 1.dp.toPx())
    drawLine(sideGuideColor, Offset(right, 0f), Offset(right, size.height), 1.dp.toPx())
    labelPaint.color = android.graphics.Color.rgb(100, 116, 139)
    labelPaint.textAlign = android.graphics.Paint.Align.CENTER
    val measureY = size.height - 6.dp.toPx()
    drawContext.canvas.nativeCanvas.drawText("8%", left, measureY, labelPaint)
    drawContext.canvas.nativeCanvas.drawText("center", center, measureY, labelPaint)
    drawContext.canvas.nativeCanvas.drawText("92%", right, measureY, labelPaint)
}

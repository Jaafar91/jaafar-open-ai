package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider

private const val DEFAULT_PREVIEW_TEXT = "The quick brown fox jumps over the lazy dog 123"
private const val USE_SELECTED_FONT_KEY = "use_selected_font_for_app"

private enum class Screen { Dashboard, Fonts, Letters, Spacing, Image, Settings }

@Composable
fun FontCreatorApp(viewModel: FontCreatorViewModel) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", 0) }
    var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
    var useSelectedFont by remember { mutableStateOf(preferences.getBoolean(USE_SELECTED_FONT_KEY, false)) }
    var previewText by remember { mutableStateOf(preferences.getString("preview_text", DEFAULT_PREVIEW_TEXT) ?: DEFAULT_PREVIEW_TEXT) }
    var screen by remember { mutableStateOf(Screen.Dashboard) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = appTypography(viewModel.previewTypeface?.takeIf { useSelectedFont }?.let(::FontFamily)),
    ) {
        when {
            imageUri != null && viewModel.previewTypeface != null -> ImageTextEditorScreen(imageUri!!, viewModel.previewTypeface!!) { imageUri = null }
            viewModel.selectedCodePoint != null -> GlyphEditorScreen(viewModel.selectedCodePoint!!, viewModel.drawings[viewModel.selectedCodePoint], viewModel.isPagingMode, viewModel::closeEditor, viewModel::saveDrawing)
            else -> when (screen) {
                Screen.Dashboard -> Dashboard(viewModel, { screen = it })
                Screen.Fonts -> FontsScreen(viewModel, previewText, { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() }, { screen = Screen.Dashboard }, { screen = Screen.Letters })
                Screen.Letters -> LettersScreen(viewModel, { screen = Screen.Dashboard }, { screen = Screen.Spacing })
                Screen.Spacing -> SpacingScreen(viewModel, previewText, { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() }) { screen = Screen.Letters }
                Screen.Image -> ImageScreen(viewModel, { screen = Screen.Dashboard }) { imageUri = it }
                Screen.Settings -> SettingsScreen(
                    darkTheme,
                    { value -> darkTheme = value; preferences.edit().putBoolean("dark_theme", value).apply() },
                    useSelectedFont,
                    { value -> useSelectedFont = value; preferences.edit().putBoolean(USE_SELECTED_FONT_KEY, value).apply() },
                    previewText,
                    { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() }
                ) { screen = Screen.Dashboard }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Page(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { AppTopBar(title, back, actions) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppTopBar(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title) },
        navigationIcon = { if (back != null) IconButton(onClick = back) { BackIcon() } },
        actions = actions,
    )
}

@Composable private fun BackIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { contentDescription = "Back" }) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * .72f, size.height * .18f), Offset(size.width * .28f, size.height * .5f), stroke)
        drawLine(color, Offset(size.width * .28f, size.height * .5f), Offset(size.width * .72f, size.height * .82f), stroke)
    }
}

private fun appTypography(fontFamily: FontFamily?): Typography {
    val base = Typography()
    fun TextStyle.withSelectedFont() = if (fontFamily == null) this else copy(fontFamily = fontFamily)
    return Typography(
        displayLarge = base.displayLarge.withSelectedFont(), displayMedium = base.displayMedium.withSelectedFont(), displaySmall = base.displaySmall.withSelectedFont(),
        headlineLarge = base.headlineLarge.withSelectedFont(), headlineMedium = base.headlineMedium.withSelectedFont(), headlineSmall = base.headlineSmall.withSelectedFont(),
        titleLarge = base.titleLarge.withSelectedFont(), titleMedium = base.titleMedium.withSelectedFont(), titleSmall = base.titleSmall.withSelectedFont(),
        bodyLarge = base.bodyLarge.withSelectedFont(), bodyMedium = base.bodyMedium.withSelectedFont(), bodySmall = base.bodySmall.withSelectedFont(),
        labelLarge = base.labelLarge.withSelectedFont(), labelMedium = base.labelMedium.withSelectedFont(), labelSmall = base.labelSmall.withSelectedFont(),
    )
}

@Composable private fun Dashboard(vm: FontCreatorViewModel, go: (Screen) -> Unit) = Page("Font Creator") {
    Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("${vm.projects.size} saved font${if (vm.projects.size == 1) "" else "s"}")
    DashboardButton("My fonts", "Create, open, generate, and share named fonts") { go(Screen.Fonts) }
    DashboardButton("Letters", "Draw and manage characters for the open font", vm.activeProject != null) { go(Screen.Letters) }
    DashboardButton("Write on image", "Use the generated font on a photo", vm.previewTypeface != null) { go(Screen.Image) }
    DashboardButton("Settings", "Change appearance and preview text") { go(Screen.Settings) }
    vm.activeProject?.let { Text("Open font: ${it.name}", style = MaterialTheme.typography.titleMedium) }
}

@Composable private fun DashboardButton(title: String, detail: String, enabled: Boolean = true, click: () -> Unit) {
    OutlinedButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(title, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun FontsScreen(vm: FontCreatorViewModel, previewText: String, changePreviewText: (String) -> Unit, back: () -> Unit, edit: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    Page("My fonts", back, actions = {
        IconButton(onClick = { showCreateDialog = true }) {
            ActionIcon(ActionIconType.Add, "Add font")
        }
    }) {
        Text("Each font is saved separately on this device.")
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.projects) { index, project ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openProject(index) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text("${project.drawings.size} drawn characters")
                            if (vm.activeProjectIndex == index) Text("Open", color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { vm.openProject(index); edit() }) {
                            ActionIcon(ActionIconType.Edit, "Edit ${project.name} letters")
                        }
                    }
                }
            }
        }
        vm.activeProject?.let { project ->
            Text("Preview", style = MaterialTheme.typography.titleMedium)
            if (vm.previewTypeface == null) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Text("Generate ${project.name} to see it here.", Modifier.fillMaxWidth().padding(16.dp))
                }
            } else {
                OutlinedTextField(
                    previewText,
                    changePreviewText,
                    Modifier.fillMaxWidth(),
                    label = { Text("Preview text") },
                    minLines = 2,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(vm.previewTypeface!!))
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(vm::generate, Modifier.weight(1f)) { Text("Generate font") }
                vm.generatedFont?.let { file -> ShareButton(file, project.name) }
            }
        }
        if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
    }
    if (showCreateDialog) AlertDialog(
        onDismissRequest = { showCreateDialog = false; name = "" },
        title = { Text("Add font") },
        text = { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Font name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (vm.createProject(name)) { showCreateDialog = false; name = ""; edit() } }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { showCreateDialog = false; name = "" }) { Text("Cancel") } }
    )
}

@Composable private fun ShareButton(file: java.io.File, name: String) {
    val context = LocalContext.current
    IconButton(onClick = { val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "font/ttf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share $name")) }) { ActionIcon(ActionIconType.Share, "Share $name") }
}

private enum class ActionIconType { Add, Edit, Share }

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
        }
    }
}

@Composable private fun LettersScreen(vm: FontCreatorViewModel, back: () -> Unit, spacing: () -> Unit) = Page("Letters · ${vm.activeProject?.name.orEmpty()}", back) {
    var text by remember { mutableStateOf("") }
    var showFonts by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ showFonts = true }, Modifier.fillMaxWidth()) {
            Text("Font: ${vm.activeProject?.name.orEmpty()}")
        }
        DropdownMenu(showFonts, { showFonts = false }) {
            vm.projects.forEachIndexed { index, project ->
                DropdownMenuItem(
                    text = { Text(project.name) },
                    onClick = { vm.openProject(index); showFonts = false },
                    trailingIcon = { if (index == vm.activeProjectIndex) Text("✓") },
                )
            }
        }
    }
    OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Text to support") }, supportingText = { Text("Basic Latin characters only") })
    Button({ vm.drawMissingCharacters(text) }, Modifier.fillMaxWidth(), enabled = text.isNotEmpty()) { Text("Draw missing letters") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(vm::startPaging, Modifier.weight(1f)) { Text("Draw all missing") }
        OutlinedButton(spacing, Modifier.weight(1f)) { Text("Adjust spacing") }
    }
    Text("Tap one character to draw it. Spacing has its own page.", style = MaterialTheme.typography.bodySmall)
    LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(FontCreatorViewModel.CHARACTER_ORDER.filter { it != 32 }, key = { it }) { code ->
            val complete = code in vm.drawings
            Box(Modifier.aspectRatio(1f).background(if (complete) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).border(1.dp, MaterialTheme.colorScheme.outline).clickable { vm.edit(code) }, contentAlignment = Alignment.Center) { Text(code.toChar().toString()) }
        }
    }
    if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun SpacingScreen(vm: FontCreatorViewModel, previewText: String, changePreviewText: (String) -> Unit, back: () -> Unit) = Page("Letter spacing", back) {
    var letter by remember(vm.activeProject) { mutableStateOf(vm.activeProject?.letterSpacingMm?.toString().orEmpty()) }
    var word by remember(vm.activeProject) { mutableStateOf(vm.activeProject?.wordSpacingMm?.toString().orEmpty()) }
    Text("Character spacing", style = MaterialTheme.typography.titleMedium)
    Text("Extra distance between every pair of letters. Use a negative value to bring letters closer.")
    OutlinedTextField(letter, { letter = it }, Modifier.fillMaxWidth(), label = { Text("Extra letter spacing (mm)") }, singleLine = true)
    Text("Word spacing", style = MaterialTheme.typography.titleMedium)
    Text("Width of the blank space character between words.")
    OutlinedTextField(word, { word = it }, Modifier.fillMaxWidth(), label = { Text("Word space width (mm)") }, singleLine = true)
    Text("Preview", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        previewText,
        changePreviewText,
        Modifier.fillMaxWidth(),
        label = { Text("Preview text") },
        supportingText = { Text(if (vm.previewTypeface == null) "Save spacing to generate its preview" else "Save spacing to refresh this preview") },
        minLines = 2,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = vm.previewTypeface?.let { FontFamily(it) } ?: FontFamily.Default
        )
    )
    Button({ if (vm.setSpacing(letter, word)) vm.generate() }, Modifier.fillMaxWidth()) { Text("Save spacing") }
    if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ImageScreen(vm: FontCreatorViewModel, back: () -> Unit, selected: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(selected) }
    Page("Write on image", back) { Text("Choose one image, then add text using ${vm.activeProject?.name.orEmpty()}."); Button({ picker.launch("image/*") }, Modifier.fillMaxWidth(), enabled = vm.previewTypeface != null) { Text("Choose image") } }
}

@Composable private fun SettingsScreen(
    dark: Boolean,
    change: (Boolean) -> Unit,
    useSelectedFont: Boolean,
    changeUseSelectedFont: (Boolean) -> Unit,
    previewText: String,
    changePreviewText: (String) -> Unit,
    back: () -> Unit,
) = Page("Settings", back) {
    Text("Appearance", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!dark, { change(false) }); Text("Light") }
    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(dark, { change(true) }); Text("Dark") }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Use selected font on all screens")
            Text(
                "Applies the open generated font to app controls and labels.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = useSelectedFont, onCheckedChange = changeUseSelectedFont)
    }
    HorizontalDivider()
    Text("Font preview", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        previewText,
        changePreviewText,
        Modifier.fillMaxWidth(),
        label = { Text("Preview characters") },
        supportingText = { Text("Shown in the preview box on My fonts") },
        minLines = 3
    )
    TextButton(onClick = { changePreviewText(DEFAULT_PREVIEW_TEXT) }, enabled = previewText != DEFAULT_PREVIEW_TEXT) { Text("Restore default") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GlyphEditorScreen(codePoint: Int, initial: GlyphDrawing?, pagingMode: Boolean, onCancel: () -> Unit, onSave: (GlyphDrawing) -> Unit) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember(codePoint) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember(codePoint) { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    Scaffold(topBar = { AppTopBar("Draw ${codePoint.toChar()}", onCancel) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gray: ascender/descender · Red: baseline", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(.3f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) { Text(codePoint.toChar().toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold) }
                Canvas(Modifier.weight(.7f).fillMaxHeight().background(Color.White).border(1.dp, Color.Gray).pointerInput(codePoint, strokes) { detectDragGestures(onDragStart = { active = listOf(GlyphPoint(it.x, it.y)) }, onDrag = { change, _ -> change.consume(); val next = GlyphPoint(change.position.x, change.position.y); if (active.lastOrNull()?.let { hypotSquared(it, next) > 9f } != false) active = active + next }, onDragEnd = { if (active.size > 1) strokes = strokes + GlyphStroke(active); active = emptyList() }, onDragCancel = { active = emptyList() }) }) {
                    canvasSize = size.width to size.height
                    drawCoordinateRulers()
                    drawLine(Color.LightGray, Offset(0f, size.height * .1f), Offset(size.width, size.height * .1f), 2f); drawLine(Color.Red, Offset(0f, size.height * .78f), Offset(size.width, size.height * .78f), 3f); drawLine(Color.LightGray, Offset(0f, size.height * .94f), Offset(size.width, size.height * .94f), 2f)
                    (strokes.map { it.points } + listOf(active)).forEach { points -> if (points.size > 1) drawPath(Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }, Color.Black, style = Stroke(8f)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onCancel) { Text("Cancel") }; OutlinedButton({ strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Undo") }; Button({ onSave(GlyphDrawing(codePoint, strokes, canvasSize.first, canvasSize.second)) }, Modifier.weight(1f), enabled = strokes.isNotEmpty()) { Text(if (pagingMode) "Save & next" else "Save letter") } }
        }
    }
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

private fun hypotSquared(a: GlyphPoint, b: GlyphPoint): Float { val dx = a.x - b.x; val dy = a.y - b.y; return dx * dx + dy * dy }

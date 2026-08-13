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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

private enum class Screen { Dashboard, Fonts, Letters, Spacing, Preview, Image, Appearance }

@Composable
fun FontCreatorApp(viewModel: FontCreatorViewModel) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", 0) }
    var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
    var screen by remember { mutableStateOf(Screen.Dashboard) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        when {
            imageUri != null && viewModel.previewTypeface != null -> ImageTextEditorScreen(imageUri!!, viewModel.previewTypeface!!) { imageUri = null }
            viewModel.selectedCodePoint != null -> GlyphEditorScreen(viewModel.selectedCodePoint!!, viewModel.drawings[viewModel.selectedCodePoint], viewModel.isPagingMode, viewModel::closeEditor, viewModel::saveDrawing)
            else -> when (screen) {
                Screen.Dashboard -> Dashboard(viewModel, { screen = it })
                Screen.Fonts -> FontsScreen(viewModel, { screen = Screen.Dashboard }, { screen = Screen.Letters })
                Screen.Letters -> LettersScreen(viewModel, { screen = Screen.Dashboard }, { screen = Screen.Spacing })
                Screen.Spacing -> SpacingScreen(viewModel) { screen = Screen.Letters }
                Screen.Preview -> PreviewScreen(viewModel, { screen = Screen.Dashboard }) { screen = Screen.Fonts }
                Screen.Image -> ImageScreen(viewModel, { screen = Screen.Dashboard }) { imageUri = it }
                Screen.Appearance -> AppearanceScreen(darkTheme, { value -> darkTheme = value; preferences.edit().putBoolean("dark_theme", value).apply() }) { screen = Screen.Dashboard }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Page(title: String, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { if (back != null) TextButton(onClick = back) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable private fun Dashboard(vm: FontCreatorViewModel, go: (Screen) -> Unit) = Page("Font Creator") {
    Text("Dashboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text("${vm.projects.size} saved font${if (vm.projects.size == 1) "" else "s"}")
    DashboardButton("My fonts", "Create, open, generate, and share named fonts") { go(Screen.Fonts) }
    DashboardButton("Letters", "Draw and manage characters for the open font", vm.activeProject != null) { go(Screen.Letters) }
    DashboardButton("Preview", "Test the generated font", vm.activeProject != null) { go(Screen.Preview) }
    DashboardButton("Write on image", "Use the generated font on a photo", vm.previewTypeface != null) { go(Screen.Image) }
    DashboardButton("Appearance", "Choose light or dark theme") { go(Screen.Appearance) }
    vm.activeProject?.let { Text("Open font: ${it.name}", style = MaterialTheme.typography.titleMedium) }
}

@Composable private fun DashboardButton(title: String, detail: String, enabled: Boolean = true, click: () -> Unit) {
    OutlinedButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(title, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun FontsScreen(vm: FontCreatorViewModel, back: () -> Unit, edit: () -> Unit) = Page("My fonts", back) {
    var name by remember { mutableStateOf("") }
    Text("Each font is saved separately on this device.")
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("New font name") }, singleLine = true)
    Button({ if (vm.createProject(name)) { name = ""; edit() } }, Modifier.fillMaxWidth(), enabled = name.isNotBlank()) { Text("Create font") }
    HorizontalDivider()
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(vm.projects) { index, project ->
            OutlinedCard(Modifier.fillMaxWidth().clickable { vm.openProject(index) }) {
                Column(Modifier.padding(16.dp)) { Text(project.name, style = MaterialTheme.typography.titleMedium); Text("${project.drawings.size} drawn characters"); if (vm.activeProjectIndex == index) Text("Open", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
    vm.activeProject?.let { project ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(edit, Modifier.weight(1f)) { Text("Edit letters") }
            Button(vm::generate, Modifier.weight(1f)) { Text("Generate") }
            vm.generatedFont?.let { file -> ShareButton(file, project.name) }
        }
    }
    if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun ShareButton(file: java.io.File, name: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "font/ttf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share $name")) }) { Text("Share") }
}

@Composable private fun LettersScreen(vm: FontCreatorViewModel, back: () -> Unit, spacing: () -> Unit) = Page("Letters · ${vm.activeProject?.name.orEmpty()}", back) {
    var text by remember { mutableStateOf("") }
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

@Composable private fun SpacingScreen(vm: FontCreatorViewModel, back: () -> Unit) = Page("Letter spacing", back) {
    var letter by remember(vm.activeProject) { mutableStateOf(vm.activeProject?.letterSpacingMm?.toString().orEmpty()) }
    var word by remember(vm.activeProject) { mutableStateOf(vm.activeProject?.wordSpacingMm?.toString().orEmpty()) }
    Text("Character spacing", style = MaterialTheme.typography.titleMedium)
    Text("Extra distance between every pair of letters. Use a negative value to bring letters closer.")
    OutlinedTextField(letter, { letter = it }, Modifier.fillMaxWidth(), label = { Text("Extra letter spacing (mm)") }, singleLine = true)
    Text("Word spacing", style = MaterialTheme.typography.titleMedium)
    Text("Width of the blank space character between words.")
    OutlinedTextField(word, { word = it }, Modifier.fillMaxWidth(), label = { Text("Word space width (mm)") }, singleLine = true)
    Button({ vm.setSpacing(letter, word) }, Modifier.fillMaxWidth()) { Text("Save spacing") }
    if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun PreviewScreen(vm: FontCreatorViewModel, back: () -> Unit, fonts: () -> Unit) = Page("Preview", back) {
    var text by remember { mutableStateOf("The quick brown fox jumps over the lazy dog 123") }
    if (vm.previewTypeface == null) { Text("Generate ${vm.activeProject?.name.orEmpty()} before previewing it."); Button(fonts) { Text("Go to My fonts") } }
    else { OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Preview text") }, textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(vm.previewTypeface!!)), minLines = 5); Button(vm::generate, Modifier.fillMaxWidth()) { Text("Generate again") } }
    if (vm.status.isNotBlank()) Text(vm.status)
}

@Composable private fun ImageScreen(vm: FontCreatorViewModel, back: () -> Unit, selected: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(selected) }
    Page("Write on image", back) { Text("Choose one image, then add text using ${vm.activeProject?.name.orEmpty()}."); Button({ picker.launch("image/*") }, Modifier.fillMaxWidth(), enabled = vm.previewTypeface != null) { Text("Choose image") } }
}

@Composable private fun AppearanceScreen(dark: Boolean, change: (Boolean) -> Unit, back: () -> Unit) = Page("Appearance", back) {
    Text("Theme", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(!dark, { change(false) }); Text("Light") }
    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(dark, { change(true) }); Text("Dark") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GlyphEditorScreen(codePoint: Int, initial: GlyphDrawing?, pagingMode: Boolean, onCancel: () -> Unit, onSave: (GlyphDrawing) -> Unit) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember(codePoint) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember(codePoint) { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    Scaffold(topBar = { TopAppBar(title = { Text("Draw ${codePoint.toChar()}") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gray: ascender/descender · Red: baseline", style = MaterialTheme.typography.bodySmall)
            if (pagingMode) Text("Reference: ${codePoint.toChar()}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp).background(Color.White).border(1.dp, Color.Gray).pointerInput(codePoint, strokes) { detectDragGestures(onDragStart = { active = listOf(GlyphPoint(it.x, it.y)) }, onDrag = { change, _ -> change.consume(); val next = GlyphPoint(change.position.x, change.position.y); if (active.lastOrNull()?.let { hypotSquared(it, next) > 9f } != false) active = active + next }, onDragEnd = { if (active.size > 1) strokes = strokes + GlyphStroke(active); active = emptyList() }, onDragCancel = { active = emptyList() }) }) {
                canvasSize = size.width to size.height
                drawLine(Color.LightGray, Offset(0f, size.height * .1f), Offset(size.width, size.height * .1f), 2f); drawLine(Color.Red, Offset(0f, size.height * .78f), Offset(size.width, size.height * .78f), 3f); drawLine(Color.LightGray, Offset(0f, size.height * .94f), Offset(size.width, size.height * .94f), 2f)
                (strokes.map { it.points } + listOf(active)).forEach { points -> if (points.size > 1) drawPath(Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }, Color.Black, style = Stroke(8f)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onCancel) { Text("Cancel") }; OutlinedButton({ strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Undo") }; Button({ onSave(GlyphDrawing(codePoint, strokes, canvasSize.first, canvasSize.second)) }, Modifier.weight(1f), enabled = strokes.isNotEmpty()) { Text(if (pagingMode) "Save & next" else "Save letter") } }
        }
    }
}

private fun hypotSquared(a: GlyphPoint, b: GlyphPoint): Float { val dx = a.x - b.x; val dy = a.y - b.y; return dx * dx + dy * dy }

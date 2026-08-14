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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider

private const val DEFAULT_PREVIEW_TEXT = "The quick brown fox jumps over the lazy dog 123"
private const val USE_SELECTED_FONT_KEY = "use_selected_font_for_app"

private enum class Screen { Home, Library, Fonts, Letters, Spacing, Image, PdfFont, Signature, Settings }
private enum class LibraryTab(val label: String) { Fonts("Fonts"), Signatures("Signatures & Stamps") }

@Composable
fun FontCreatorApp(viewModel: FontCreatorViewModel) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", 0) }
    var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
    var useSelectedFont by remember { mutableStateOf(preferences.getBoolean(USE_SELECTED_FONT_KEY, false)) }
    var previewText by remember { mutableStateOf(preferences.getString("preview_text", DEFAULT_PREVIEW_TEXT) ?: DEFAULT_PREVIEW_TEXT) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageTypeface by remember { mutableStateOf<Typeface?>(null) }
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        typography = appTypography(viewModel.previewTypeface?.takeIf { useSelectedFont }?.let(::FontFamily)),
    ) {
        val referenceTypeface = remember(viewModel.referenceFontKey, viewModel.projects.size, viewModel.importedFonts.size) {
            viewModel.referenceTypeface()
        }
        when {
            imageUri != null && imageTypeface != null -> ImageTextEditorScreen(imageUri!!, imageTypeface!!) { imageUri = null; imageTypeface = null }
            viewModel.selectedCodePoint != null -> GlyphEditorScreen(
                codePoint = viewModel.selectedCodePoint!!,
                initial = viewModel.drawings[viewModel.selectedCodePoint],
                pagingMode = viewModel.isPagingMode,
                pagingProgress = viewModel.pagingProgress,
                referenceTypeface = referenceTypeface,
                onCancel = viewModel::closeEditor,
                onSkip = viewModel::skipLetter,
                onSave = viewModel::saveDrawing,
            )
            else -> when (screen) {
                Screen.Home -> HomeScreen(viewModel, { screen = it })
                Screen.Library -> LibraryScreen(
                    vm = viewModel,
                    back = { screen = Screen.Home },
                    openFontManager = { screen = Screen.Fonts },
                    openSignatureManager = { screen = Screen.Signature },
                    openLetterEditor = { screen = Screen.Letters },
                )
                Screen.Fonts -> FontsScreen(viewModel, previewText, { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() }, { screen = Screen.Home }, { screen = Screen.Letters }, { screen = Screen.Spacing })
                Screen.Letters -> LettersScreen(viewModel, { screen = Screen.Home })
                Screen.Spacing -> SpacingScreen(viewModel, previewText, { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() }) { screen = Screen.Fonts }
                Screen.Image -> ImageScreen(viewModel, { screen = Screen.Home }) { tf, uri -> imageTypeface = tf; imageUri = uri }
                Screen.PdfFont -> PdfFontScreen(viewModel) { screen = Screen.Home }
                Screen.Signature -> SignatureScreen(viewModel) { screen = Screen.Home }
                Screen.Settings -> SettingsScreen(
                    vm = viewModel,
                    dark = darkTheme,
                    change = { value -> darkTheme = value; preferences.edit().putBoolean("dark_theme", value).apply() },
                    useSelectedFont = useSelectedFont,
                    changeUseSelectedFont = { value -> useSelectedFont = value; preferences.edit().putBoolean(USE_SELECTED_FONT_KEY, value).apply() },
                    previewText = previewText,
                    changePreviewText = { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() },
                ) { screen = Screen.Home }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Page(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
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

@Composable private fun HomeScreen(vm: FontCreatorViewModel, go: (Screen) -> Unit) = Page("Home", actions = {
    TextButton(onClick = { go(Screen.Settings) }) { Text("Settings") }
}) {
    Text("Choose an action", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    HomeButton("Create a Font", "Start or continue your generated font workflow") { go(Screen.Fonts) }
    HomeButton("Add Text to an Image", "Use the existing write-on-image workflow") { go(Screen.Image) }
    HomeButton("Change Text in a Document", "Use PDF Font Converter (includes OCR inside this flow)") { go(Screen.PdfFont) }
    HomeButton("Sign or Stamp a Document", "Open your signature and stamp workflow for images and PDFs") { go(Screen.Signature) }
    HomeButton("My Library", "Browse and manage your fonts and signatures") { go(Screen.Library) }
    vm.activeProject?.let { Text("Open font: ${it.name}", style = MaterialTheme.typography.titleMedium) }
}

@Composable private fun HomeButton(title: String, detail: String, enabled: Boolean = true, click: () -> Unit) {
    OutlinedButton(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(title, fontWeight = FontWeight.Bold); Text(detail, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun LibraryScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    openFontManager: () -> Unit,
    openSignatureManager: () -> Unit,
    openLetterEditor: () -> Unit,
) = Page("My Library", back) {
    var tab by remember { mutableStateOf(LibraryTab.Fonts) }
    TabRow(selectedTabIndex = tab.ordinal) {
        LibraryTab.entries.forEach { section ->
            Tab(
                selected = tab == section,
                onClick = { tab = section },
                text = { Text(section.label) }
            )
        }
    }
    when (tab) {
        LibraryTab.Fonts -> {
            Text("${vm.projects.size} generated · ${vm.importedFonts.size} imported")
            if (vm.projects.isEmpty() && vm.importedFonts.isEmpty()) {
                Text("No fonts in your library yet.")
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.projects.isNotEmpty()) {
                        item { Text("Generated fonts", style = MaterialTheme.typography.titleSmall) }
                        items(vm.projects) { project ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${project.drawings.size} drawn characters")
                                    }
                                    OutlinedButton(onClick = {
                                        val index = vm.projects.indexOf(project)
                                        if (index >= 0) {
                                            vm.openProject(index)
                                            openLetterEditor()
                                        }
                                    }) { Text("Edit letters") }
                                }
                            }
                        }
                    }
                    if (vm.importedFonts.isNotEmpty()) {
                        item { Text("Imported fonts", style = MaterialTheme.typography.titleSmall) }
                        items(vm.importedFonts) { font ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(font.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text("Imported · read-only", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(openFontManager, Modifier.fillMaxWidth()) { Text("Open font manager") }
        }
        LibraryTab.Signatures -> {
            val sigCount = vm.signatures.count { it.imageFileName == null }
            val stampCount = vm.signatures.count { it.imageFileName != null }
            Text("$sigCount signature${if (sigCount == 1) "" else "s"} · $stampCount stamp${if (stampCount == 1) "" else "s"}")
            if (vm.signatures.isEmpty()) {
                Text("No signatures or stamps in your library yet.")
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.signatures) { signature ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(signature.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (signature.imageFileName != null) "Stamp" else "Signature",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(openSignatureManager, Modifier.fillMaxWidth()) { Text("Open Signatures & Stamps") }
        }
    }
}

@Composable private fun FontsScreen(vm: FontCreatorViewModel, previewText: String, changePreviewText: (String) -> Unit, back: () -> Unit, edit: () -> Unit, spacing: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddCharsSetupDialog by remember { mutableStateOf(false) }
    var setupCharsText by remember { mutableStateOf("") }
    var showImportNameDialog by remember { mutableStateOf(false) }
    var importPendingUri by remember { mutableStateOf<Uri?>(null) }
    var importDisplayName by remember { mutableStateOf("") }

    val fontFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Pre-fill display name from filename
            val raw = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Imported Font"
            importDisplayName = raw.substringBeforeLast('.').trim().ifEmpty { "Imported Font" }
            importPendingUri = uri
            showImportNameDialog = true
        }
    }

    Page("My fonts", back, actions = {
        IconButton(onClick = { fontFilePicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*")) }) {
            ActionIcon(ActionIconType.Import, "Import font")
        }
        IconButton(onClick = { showCreateDialog = true }) {
            ActionIcon(ActionIconType.Add, "Add font")
        }
    }) {
        Text("Each font is saved separately on this device.")
        if (vm.importStatus.isNotBlank()) Text(vm.importStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            if (vm.importedFonts.isNotEmpty()) {
                item {
                    Text("Imported fonts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                }
                itemsIndexed(vm.importedFonts) { _, font ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(font.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("Imported · read-only", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { vm.deleteImportedFont(font.displayName) }) { Text("Remove") }
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
            OutlinedButton(spacing, Modifier.fillMaxWidth()) { Text("Adjust spacing") }
        }
        if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
    }
    if (showCreateDialog) AlertDialog(
        onDismissRequest = { showCreateDialog = false; name = "" },
        title = { Text("Add font") },
        text = { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Font name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (vm.createProject(name)) { showCreateDialog = false; name = ""; setupCharsText = ""; showAddCharsSetupDialog = true } }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = { showCreateDialog = false; name = "" }) { Text("Cancel") } }
    )
    if (showImportNameDialog) AlertDialog(
        onDismissRequest = { showImportNameDialog = false; importPendingUri = null },
        title = { Text("Import font") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a display name for this font.")
                OutlinedTextField(importDisplayName, { importDisplayName = it }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val uri = importPendingUri
                if (uri != null) vm.importFont(context.contentResolver, uri, importDisplayName)
                showImportNameDialog = false; importPendingUri = null
            }, enabled = importDisplayName.isNotBlank()) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = { showImportNameDialog = false; importPendingUri = null }) { Text("Cancel") } }
    )
    if (showAddCharsSetupDialog) AlertDialog(
        onDismissRequest = { showAddCharsSetupDialog = false; edit() },
        title = { Text("Add characters from text (optional)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste sample text to identify which supported characters to draw first. You can skip this step and add characters later.")
                OutlinedTextField(setupCharsText, { setupCharsText = it }, Modifier.fillMaxWidth(), label = { Text("Sample text") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = { if (setupCharsText.isNotBlank()) vm.drawMissingCharacters(setupCharsText); showAddCharsSetupDialog = false; edit() }) { Text("Start drawing") }
        },
        dismissButton = { OutlinedButton(onClick = { showAddCharsSetupDialog = false; edit() }) { Text("Skip") } },
    )
}

@Composable private fun ShareButton(file: java.io.File, name: String) {
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

@Composable private fun LettersScreen(vm: FontCreatorViewModel, back: () -> Unit) = Page("Letters · ${vm.activeProject?.name.orEmpty()}", back) {
    var showFonts by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var showAddCharsDialog by remember { mutableStateOf(false) }
    var addCharsText by remember { mutableStateOf("") }
    val selectedLanguages = vm.activeProject?.selectedLanguages ?: setOf(LanguageScript.BASIC_LATIN)
    var pendingLanguages by remember(selectedLanguages) { mutableStateOf(selectedLanguages) }
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
    OutlinedButton({ pendingLanguages = selectedLanguages; showLanguages = true }, Modifier.fillMaxWidth()) {
        Text("Languages: ${selectedLanguages.joinToString(", ") { it.displayName }}")
    }
    if (showLanguages) {
        AlertDialog(
            onDismissRequest = { showLanguages = false },
            title = { Text("Select languages") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LanguageScript.entries.forEach { script ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                pendingLanguages = pendingLanguages.toMutableSet().apply {
                                    if (contains(script)) remove(script) else add(script)
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(
                                checked = pendingLanguages.contains(script),
                                onCheckedChange = { checked ->
                                    pendingLanguages = pendingLanguages.toMutableSet().apply {
                                        if (checked) add(script) else remove(script)
                                    }
                                }
                            )
                            Column {
                                Text(script.displayName)
                                Text("${script.codePoints.size} characters", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (vm.setLanguages(pendingLanguages)) showLanguages = false
                }) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLanguages = false }) { Text("Cancel") }
            }
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ addCharsText = ""; showAddCharsDialog = true }, Modifier.weight(1f)) { Text("Add more characters") }
        Button(vm::startPaging, Modifier.weight(1f)) { Text("Draw all missing") }
    }
    Text("Tap one character to draw it.", style = MaterialTheme.typography.bodySmall)
    if (showAddCharsDialog) AlertDialog(
        onDismissRequest = { showAddCharsDialog = false },
        title = { Text("Add characters from text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste sample text. The app will find supported characters you haven't drawn yet and begin them.")
                OutlinedTextField(addCharsText, { addCharsText = it }, Modifier.fillMaxWidth(), label = { Text("Sample text") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = { vm.drawMissingCharacters(addCharsText); showAddCharsDialog = false }, enabled = addCharsText.isNotBlank()) { Text("Start drawing") }
        },
        dismissButton = { OutlinedButton(onClick = { showAddCharsDialog = false }) { Text("Cancel") } },
    )
    LazyVerticalGrid(GridCells.Adaptive(52.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(vm.activeCharacterOrder, key = { it }) { code ->
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

@Composable private fun ImageScreen(vm: FontCreatorViewModel, back: () -> Unit, selected: (Typeface, Uri) -> Unit) {
    val context = LocalContext.current
    val allFonts = remember(vm.projects, vm.importedFonts) { vm.allFontOptions() }
    val systemFonts = listOf("Default" to Typeface.DEFAULT, "Serif" to Typeface.SERIF, "Sans-Serif" to Typeface.SANS_SERIF, "Monospace" to Typeface.MONOSPACE)
    val allAvailable = systemFonts + allFonts
    var selectedFontLabel by remember(allAvailable) { mutableStateOf(allAvailable.firstOrNull()?.first ?: "Default") }
    var expanded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tf = allAvailable.firstOrNull { it.first == selectedFontLabel }?.second ?: Typeface.DEFAULT
            selected(tf, uri)
        }
    }
    Page("Write on image", back) {
        Text("Choose a font, then choose an image to write on.")
        Box {
            OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("Font: $selectedFontLabel") }
            DropdownMenu(expanded, { expanded = false }) {
                allAvailable.forEach { (label, _) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { selectedFontLabel = label; expanded = false },
                        trailingIcon = { if (label == selectedFontLabel) Text("✓") },
                    )
                }
            }
        }
        Button({ picker.launch("image/*") }, Modifier.fillMaxWidth()) { Text("Choose image") }
    }
}

@Composable private fun SettingsScreen(
    vm: FontCreatorViewModel,
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
    // Letter reference font picker
    val referenceFontOptions = remember(vm.projects, vm.importedFonts) {
        val builtIn = listOf("Default", "Sans-serif", "Serif", "Monospace")
        val userFontNames = vm.allFontOptions().map { it.first }
        builtIn + userFontNames
    }
    var refExpanded by remember { mutableStateOf(false) }
    Text("Letter reference font", style = MaterialTheme.typography.titleMedium)
    Text(
        "A faint guide glyph shown while drawing letters. Does not affect the app font.",
        style = MaterialTheme.typography.bodySmall,
    )
    Box {
        OutlinedButton({ refExpanded = true }, Modifier.fillMaxWidth()) { Text(vm.referenceFontKey) }
        DropdownMenu(refExpanded, { refExpanded = false }) {
            referenceFontOptions.forEach { key ->
                DropdownMenuItem(
                    text = { Text(key) },
                    onClick = { vm.setReferenceFont(key); refExpanded = false },
                    trailingIcon = { if (key == vm.referenceFontKey) Text("✓") },
                )
            }
        }
    }
    HorizontalDivider()
    Text("Default preview text", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        previewText,
        changePreviewText,
        Modifier.fillMaxWidth(),
        label = { Text("Preview characters") },
        supportingText = { Text("Shown in the preview box on My fonts") },
        minLines = 3
    )
    TextButton(onClick = { changePreviewText(DEFAULT_PREVIEW_TEXT) }, enabled = previewText != DEFAULT_PREVIEW_TEXT) { Text("Restore default") }
    HorizontalDivider()
    Text("Privacy & storage", style = MaterialTheme.typography.titleMedium)
    Text(
        "All fonts, signatures, and generated files are stored locally on this device.",
        style = MaterialTheme.typography.bodySmall,
    )
    Text("You can remove imported fonts and signatures from their library screens at any time.", style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
    Text("Help & about", style = MaterialTheme.typography.titleMedium)
    Text("Use Home to start common tasks and My Library to manage saved assets.", style = MaterialTheme.typography.bodySmall)
    Text("App data stays on-device unless you explicitly share exported files.", style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GlyphEditorScreen(codePoint: Int, initial: GlyphDrawing?, pagingMode: Boolean, pagingProgress: Pair<Int, Int>?, referenceTypeface: Typeface?, onCancel: () -> Unit, onSkip: () -> Unit, onSave: (GlyphDrawing) -> Unit) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember(codePoint) { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember(codePoint) { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val initialStrokes = remember(codePoint) { initial?.strokes ?: emptyList<GlyphStroke>() }
    val isDirty = strokes != initialStrokes
    val handleBack = { if (isDirty) showDiscardDialog = true else onCancel() }
    BackHandler(onBack = handleBack)
    val char = codePoint.toChar().toString()
    val title = if (pagingProgress != null) "Draw $char · ${pagingProgress.first} of ${pagingProgress.second}" else "Draw $char"
    val isLastInQueue = pagingProgress != null && pagingProgress.first == pagingProgress.second
    val saveLabel = when {
        isLastInQueue -> "Save & Finish"
        pagingMode -> "Save & Next"
        else -> "Save letter"
    }
    Scaffold(topBar = { AppTopBar(title, handleBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Gray: ascender/descender · Red: baseline", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.weight(.3f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline),
                    contentAlignment = Alignment.Center
                ) { Text(char, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold) }
                Canvas(Modifier.weight(.7f).fillMaxHeight().background(Color.White).border(1.dp, Color.Gray).pointerInput(codePoint, strokes) { detectDragGestures(onDragStart = { active = listOf(GlyphPoint(it.x, it.y)) }, onDrag = { change, _ -> change.consume(); val next = GlyphPoint(change.position.x, change.position.y); if (active.lastOrNull()?.let { hypotSquared(it, next) > 9f } != false) active = active + next }, onDragEnd = { if (active.size > 1) strokes = strokes + GlyphStroke(active); active = emptyList() }, onDragCancel = { active = emptyList() }) }) {
                    canvasSize = size.width to size.height
                    if (referenceTypeface != null) {
                        drawIntoCanvas { canvas ->
                            val refPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                typeface = referenceTypeface
                                textSize = size.height * 0.72f
                                color = android.graphics.Color.argb(40, 0, 0, 0)
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            canvas.nativeCanvas.drawText(char, size.width / 2, size.height * 0.82f, refPaint)
                        }
                    }
                    drawCoordinateRulers()
                    drawLine(Color.LightGray, Offset(0f, size.height * .1f), Offset(size.width, size.height * .1f), 2f); drawLine(Color.Red, Offset(0f, size.height * .78f), Offset(size.width, size.height * .78f), 3f); drawLine(Color.LightGray, Offset(0f, size.height * .94f), Offset(size.width, size.height * .94f), 2f)
                    (strokes.map { it.points } + listOf(active)).forEach { points -> if (points.size > 1) drawPath(Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }, Color.Black, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Undo") }
                OutlinedButton({ strokes = emptyList(); active = emptyList() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
                if (pagingMode) TextButton(onSkip) { Text("Skip") }
                Button({ onSave(GlyphDrawing(codePoint, strokes, canvasSize.first, canvasSize.second)) }, Modifier.weight(1f), enabled = strokes.isNotEmpty()) { Text(saveLabel) }
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

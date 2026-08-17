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

private val ModernLightColors = lightColorScheme(
    primary = Color(0xFF3859C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8FF),
    onPrimaryContainer = Color(0xFF10205B),
    secondary = Color(0xFF59627A),
    secondaryContainer = Color(0xFFE1E7F9),
    onSecondaryContainer = Color(0xFF151B2C),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191B22),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF45464F),
    outlineVariant = Color(0xFFC5C6D0),
)

private val ModernDarkColors = darkColorScheme(
    primary = Color(0xFFBBC6FF),
    onPrimary = Color(0xFF09206D),
    primaryContainer = Color(0xFF243E9F),
    onPrimaryContainer = Color(0xFFE2E8FF),
    secondary = Color(0xFFC1C6DC),
    secondaryContainer = Color(0xFF41475A),
    onSecondaryContainer = Color(0xFFE1E7F9),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE2E2EA),
    surface = Color(0xFF191B22),
    onSurface = Color(0xFFE2E2EA),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outlineVariant = Color(0xFF45464F),
)

private enum class Screen { Home, Library, Fonts, FontReady, Letters, Spacing, Image, PdfFont, Signature, FillMark, Settings }
private enum class LibraryTab(val label: String) { Fonts("Fonts"), Signatures("Signatures & Stamps") }
private enum class LibrarySignaturePage { Hub, Draw, ImportStamp }

@Composable
fun FontCreatorApp(viewModel: FontCreatorViewModel, sharedUri: Uri? = null) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", 0) }
    var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
    var useSelectedFont by remember { mutableStateOf(preferences.getBoolean(USE_SELECTED_FONT_KEY, false)) }
    var previewText by remember { mutableStateOf(preferences.getString("preview_text", DEFAULT_PREVIEW_TEXT) ?: DEFAULT_PREVIEW_TEXT) }
    var screen by remember { mutableStateOf(if (sharedUri != null) Screen.FillMark else Screen.Home) }
    var fillMarkUri by remember { mutableStateOf<Uri?>(sharedUri) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageTypeface by remember { mutableStateOf<Typeface?>(null) }
    var preferredImageFontName by remember { mutableStateOf<String?>(null) }
    MaterialTheme(
        colorScheme = if (darkTheme) ModernDarkColors else ModernLightColors,
        typography = appTypography(viewModel.previewTypeface?.takeIf { useSelectedFont }?.let(::FontFamily)),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
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
                    openLetterEditor = { screen = Screen.Letters },
                )
                Screen.Fonts -> FontsScreen(
                    vm = viewModel,
                    back = { screen = Screen.Home },
                    editLetters = { screen = Screen.Letters },
                    showReady = { screen = Screen.FontReady },
                    useOnImage = { fontName -> preferredImageFontName = fontName; screen = Screen.Image },
                    setPreviewText = { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() },
                )
                Screen.FontReady -> FontReadyScreen(
                    vm = viewModel,
                    previewText = previewText,
                    changePreviewText = { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() },
                    back = { screen = Screen.Fonts },
                    editLetters = { screen = Screen.Letters },
                    adjustSpacing = { screen = Screen.Spacing },
                    useOnImage = { fontName -> preferredImageFontName = fontName; screen = Screen.Image },
                )
                Screen.Letters -> LettersScreen(
                    vm = viewModel,
                    back = { screen = Screen.Home },
                    preview = { screen = Screen.FontReady },
                    setPreviewText = { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() },
                )
                Screen.Spacing -> SpacingScreen(viewModel, previewText, { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() }) { screen = Screen.Fonts }
                Screen.Image -> ImageScreen(
                    vm = viewModel,
                    back = { preferredImageFontName = null; screen = Screen.Home },
                    initiallySelectedFont = preferredImageFontName,
                ) { tf, uri -> imageTypeface = tf; imageUri = uri; preferredImageFontName = null }
                Screen.PdfFont -> PdfFontScreen(viewModel) { screen = Screen.Home }
                Screen.Signature -> SignatureScreen(
                    vm = viewModel,
                    back = { screen = Screen.Home },
                )
                Screen.FillMark -> FillMarkScreen(
                    vm = viewModel,
                    initialUri = fillMarkUri,
                    back = {
                        fillMarkUri = null
                        screen = Screen.Home
                    },
                )
                Screen.Settings -> SettingsScreen(
                    vm = viewModel,
                    dark = darkTheme,
                    change = { value -> darkTheme = value; preferences.edit().putBoolean("dark_theme", value).apply() },
                    useSelectedFont = useSelectedFont,
                    changeUseSelectedFont = { value -> useSelectedFont = value; preferences.edit().putBoolean(USE_SELECTED_FONT_KEY, value).apply() },
                ) { screen = Screen.Home }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Page(title: String, back: (() -> Unit)? = null, scrollable: Boolean = false, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { AppTopBar(title, back, actions) }) { padding ->
        val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).then(scrollModifier),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AppTopBar(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Aa", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Text(if (title == "Studio") "Font Creator" else title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
        navigationIcon = {
            if (back != null) {
                IconButton(onClick = back) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { BackIcon() }
                    }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
        ),
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

@Composable private fun HomeScreen(vm: FontCreatorViewModel, go: (Screen) -> Unit) = Page(
    "Studio",
    scrollable = true,
    actions = { TextButton(onClick = { go(Screen.Settings) }) { Text("Settings") } },
) {
    HomeTaskCard(
        label = "DOCUMENT",
        title = "Complete a document",
        detail = "Add text, dates, signatures, or stamps.",
        featured = true,
    ) { go(Screen.FillMark) }

    Text("Create", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HomeTaskCard(
        label = "FONT",
        title = "Create a font",
        detail = "Make a font or import one.",
    ) { go(Screen.Fonts) }
    HomeTaskCard(
        label = "IMAGE",
        title = "Edit an image",
        detail = "Add text, fonts, or stamps to an image.",
    ) { go(Screen.Image) }

    Text("Your workspace", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    HomeTaskCard(
        label = "LIBRARY",
        title = "My library",
        detail = "Your fonts, signatures, and stamps.",
    ) { go(Screen.Library) }
    HomeTaskCard(
        label = "BETA",
        title = "Restyle scanned text",
        detail = "Beta: make a new PDF with a different font.",
    ) { go(Screen.PdfFont) }

    vm.activeProject?.let { project ->
        Text("Current font: ${project.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HomeTaskCard(
    label: String,
    title: String,
    detail: String,
    featured: Boolean = false,
    click: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (featured) colors.primaryContainer else colors.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (featured) colors.primaryContainer else colors.outlineVariant,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                color = if (featured) colors.primary else colors.secondaryContainer,
                contentColor = if (featured) colors.onPrimary else colors.onSecondaryContainer,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    openFontManager: () -> Unit,
    openLetterEditor: () -> Unit,
) {
    var tab by remember { mutableStateOf(LibraryTab.Fonts) }
    var signaturePage by remember { mutableStateOf(LibrarySignaturePage.Hub) }
    var selectedLibraryMark by remember { mutableStateOf<SavedSignature?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var libraryStatus by remember { mutableStateOf("") }
    when (signaturePage) {
        LibrarySignaturePage.Draw -> {
            SignatureEditorScreen(
                vm = vm,
                onSaved = {
                    signaturePage = LibrarySignaturePage.Hub
                    libraryStatus = "Signature saved."
                },
                back = { signaturePage = LibrarySignaturePage.Hub },
            )
            return
        }
        LibrarySignaturePage.ImportStamp -> {
            ImportStampFromImageScreen(
                vm = vm,
                onSaved = {
                    signaturePage = LibrarySignaturePage.Hub
                    libraryStatus = "Stamp saved."
                },
                back = { signaturePage = LibrarySignaturePage.Hub },
            )
            return
        }
        LibrarySignaturePage.Hub -> Unit
    }
    Page("My Library", back) {
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
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("No signatures yet.", style = MaterialTheme.typography.titleMedium)
                        Text("Create your first signature. Draw it once and reuse it whenever you sign a document.")
                        Button(onClick = { signaturePage = LibrarySignaturePage.Draw }, modifier = Modifier.fillMaxWidth()) {
                            Text("Create signature")
                        }
                    }
                }
            } else {
                val signatures = vm.signatures.filter { it.imageFileName == null }
                val stamps = vm.signatures.filter { it.imageFileName != null }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("SIGNATURES · ${signatures.size}", style = MaterialTheme.typography.titleSmall) }
                    items(signatures, key = { it.name }) { signature ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable { selectedLibraryMark = signature },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SignaturePreview(Modifier.size(88.dp, 56.dp), signature)
                                Column(Modifier.weight(1f)) {
                                    Text(signature.name, style = MaterialTheme.typography.titleMedium)
                                    if (vm.defaultSignatureName == signature.name) {
                                        Text("Default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { signaturePage = LibrarySignaturePage.Draw }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Add signature")
                        }
                    }
                    item { Text("STAMPS · ${stamps.size}", style = MaterialTheme.typography.titleSmall) }
                    items(stamps, key = { it.name }) { stamp ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable { selectedLibraryMark = stamp },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SignaturePreview(Modifier.size(88.dp, 56.dp), stamp)
                                Column(Modifier.weight(1f)) {
                                    Text(stamp.name, style = MaterialTheme.typography.titleMedium)
                                    if (vm.defaultStampName == stamp.name) {
                                        Text("Default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { signaturePage = LibrarySignaturePage.ImportStamp }, modifier = Modifier.fillMaxWidth()) {
                            Text("+ Add stamp")
                        }
                    }
                }
            }
            if (libraryStatus.isNotBlank()) {
                Text(libraryStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (selectedLibraryMark != null) {
        ModalBottomSheet(onDismissRequest = { selectedLibraryMark = null }) {
            val mark = selectedLibraryMark ?: return@ModalBottomSheet
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SignaturePreview(Modifier.fillMaxWidth().height(140.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant), mark)
                Text(mark.name, style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        if (mark.imageFileName == null) vm.setDefaultSignature(mark.name) else vm.setDefaultStamp(mark.name)
                        libraryStatus = "${mark.name} will be used by default in Complete a document."
                        selectedLibraryMark = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Use in a document") }
                OutlinedButton(
                    onClick = {
                        renameValue = mark.name
                        showRenameDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Rename") }
                TextButton(
                    onClick = {
                        vm.deleteSignature(mark.name)
                        libraryStatus = "Deleted ${mark.name}."
                        selectedLibraryMark = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete") }
            }
        }
    }
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename asset") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val mark = selectedLibraryMark
                    if (mark != null && vm.renameSignature(mark.name, renameValue)) {
                        if (mark.imageFileName == null) vm.setDefaultSignature(renameValue.trim()) else vm.setDefaultStamp(renameValue.trim())
                        libraryStatus = "Renamed to ${renameValue.trim()}."
                        selectedLibraryMark = vm.signatures.firstOrNull { it.name == renameValue.trim() }
                        showRenameDialog = false
                    } else {
                        libraryStatus = "Name unavailable. Try a different one."
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable private fun FontsScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    editLetters: () -> Unit,
    showReady: () -> Unit,
    useOnImage: (String) -> Unit,
    setPreviewText: (String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddCharsSetupDialog by remember { mutableStateOf(false) }
    var setupCharsText by remember { mutableStateOf("") }
    var showImportNameDialog by remember { mutableStateOf(false) }
    var importPendingUri by remember { mutableStateOf<Uri?>(null) }
    var importDisplayName by remember { mutableStateOf("") }
    val featuredIndex = vm.activeProjectIndex ?: vm.projects.indices.lastOrNull()
    val featuredProject = featuredIndex?.let { vm.projects.getOrNull(it) }

    val fontFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Imported Font"
            importDisplayName = raw.substringBeforeLast('.').trim().ifEmpty { "Imported Font" }
            importPendingUri = uri
            showImportNameDialog = true
        }
    }

    Page("Fonts", back, scrollable = true) {
        Text("Your font studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Build a handwriting font or keep the fonts you already use in one place.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        featuredProject?.let { project ->
            val selectedCharacters = project.selectedLanguages.flatMap { it.codePoints }.filter { it != 0x20 }.toSet()
            val total = selectedCharacters.size
            val drawn = project.drawings.count { it.codePoint in selectedCharacters }
            val ready = vm.hasGeneratedFont(project.name)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (ready) "YOUR FONT IS READY" else "CONTINUE WHERE YOU LEFT OFF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(project.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (ready) "Ready to use." else "$drawn of $total letters drawn", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (!ready) LinearProgressIndicator(progress = if (total == 0) 0f else drawn.toFloat() / total, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        featuredIndex?.let(vm::openProject)
                        if (ready) showReady() else editLetters()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (ready) "Try your font" else if (drawn == 0) "Start drawing" else "Continue drawing")
                    }
                }
            }
        }

        Text("Start something new", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Create a handwriting font") }
        OutlinedButton(onClick = { fontFilePicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import a font") }
        if (vm.importStatus.isNotBlank()) Text(vm.importStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

        val otherProjects = vm.projects.filterIndexed { index, _ -> index != featuredIndex }
        if (otherProjects.isNotEmpty()) {
            Text("All handwriting fonts", style = MaterialTheme.typography.titleMedium)
            otherProjects.forEach { project ->
                val index = vm.projects.indexOf(project)
                val ready = vm.hasGeneratedFont(project.name)
                Row(Modifier.fillMaxWidth().clickable {
                    vm.openProject(index)
                    if (ready) showReady() else editLetters()
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("Aa", fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (ready) "Preview ready" else "${project.drawings.size} letters drawn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (ready) "Try" else "Draw", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (vm.importedFonts.isNotEmpty()) {
            Text("Imported fonts", style = MaterialTheme.typography.titleMedium)
            vm.importedFonts.forEach { font ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("Aa", fontWeight = FontWeight.Bold) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(font.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("Ready to use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { useOnImage(font.displayName) }) { Text("Use") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (featuredProject == null && vm.importedFonts.isEmpty()) {
            Text("Start by creating your own handwriting font or importing a .ttf or .otf file.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showCreateDialog) AlertDialog(
        onDismissRequest = { showCreateDialog = false; name = "" },
        title = { Text("Name your font") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Next, you will choose the letters to draw. You can add more any time.")
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Font name") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (vm.createProject(name)) {
                    showCreateDialog = false
                    name = ""
                    setupCharsText = ""
                    showAddCharsSetupDialog = true
                }
            }, enabled = name.isNotBlank()) { Text("Choose letters") }
        },
        dismissButton = { TextButton(onClick = { showCreateDialog = false; name = "" }) { Text("Cancel") } },
    )
    if (showImportNameDialog) AlertDialog(
        onDismissRequest = { showImportNameDialog = false; importPendingUri = null },
        title = { Text("Name imported font") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This name is only used inside the app.")
                OutlinedTextField(importDisplayName, { importDisplayName = it }, Modifier.fillMaxWidth(), label = { Text("Font name") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                importPendingUri?.let { vm.importFont(context.contentResolver, it, importDisplayName) }
                showImportNameDialog = false
                importPendingUri = null
            }, enabled = importDisplayName.isNotBlank()) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = { showImportNameDialog = false; importPendingUri = null }) { Text("Cancel") } },
    )
    if (showAddCharsSetupDialog) AlertDialog(
        onDismissRequest = { showAddCharsSetupDialog = false; editLetters() },
        title = { Text("Start with the letters you need") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Optional: paste a useful phrase. We will put its letters first. You can also choose letters yourself.")
                OutlinedTextField(setupCharsText, { setupCharsText = it }, Modifier.fillMaxWidth(), label = { Text("Useful phrase") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (setupCharsText.isNotBlank()) {
                    vm.drawMissingCharacters(setupCharsText)
                    setPreviewText(setupCharsText)
                }
                showAddCharsSetupDialog = false
                editLetters()
            }) { Text(if (setupCharsText.isBlank()) "Choose letters myself" else "Use this phrase") }
        },
        dismissButton = { TextButton(onClick = { showAddCharsSetupDialog = false; editLetters() }) { Text("Choose letters myself") } },
    )
}

@Composable private fun FontReadyScreen(
    vm: FontCreatorViewModel,
    previewText: String,
    changePreviewText: (String) -> Unit,
    back: () -> Unit,
    editLetters: () -> Unit,
    adjustSpacing: () -> Unit,
    useOnImage: (String) -> Unit,
) {
    val project = vm.activeProject
    if (project == null) {
        Page("Font preview", back) { Text("Choose a handwriting font first.") }
        return
    }
    val requiredCharacters = vm.activeCharacterOrder.toSet()
    val total = requiredCharacters.size
    val drawnCodePoints = project.drawings.map { it.codePoint }.toSet()
    val drawn = drawnCodePoints.intersect(requiredCharacters).size
    val complete = requiredCharacters.isNotEmpty() && requiredCharacters.all { it in drawnCodePoints }
    Page(if (complete) "Your font" else "Try your font", back, scrollable = true) {
        Text(project.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (vm.previewTypeface == null) {
            Text("Creating your font…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Turning $drawn drawn letters into a usable font preview.", style = MaterialTheme.typography.bodySmall)
        } else {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text(if (complete) "FONT FILE READY" else "$drawn LETTERS INCLUDED", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(if (complete) "Your font is ready to use." else "Your preview uses the letters you have drawn so far.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = previewText, onValueChange = changePreviewText, modifier = Modifier.fillMaxWidth(), label = { Text("Try typing with your font") }, minLines = 3, textStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily(vm.previewTypeface!!)))
            Button(onClick = { useOnImage(project.name) }, modifier = Modifier.fillMaxWidth()) { Text("Use on an image") }
            if (!complete) OutlinedButton(onClick = editLetters, modifier = Modifier.fillMaxWidth()) { Text("Continue drawing") }
            TextButton(onClick = adjustSpacing, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Adjust spacing") }
            vm.generatedFont?.let { file ->
                Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    ShareButton(file, project.name)
                    Text("Export font", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
    }
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

@Composable private fun LettersScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    preview: () -> Unit,
    setPreviewText: (String) -> Unit,
) = Page("Build ${vm.activeProject?.name.orEmpty()}", back) {
    var showLanguages by remember { mutableStateOf(false) }
    var showPhraseDialog by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    val selectedLanguages = vm.activeProject?.selectedLanguages ?: setOf(LanguageScript.BASIC_LATIN)
    var pendingLanguages by remember(selectedLanguages) { mutableStateOf(selectedLanguages) }
    val total = vm.activeCharacterOrder.size
    val drawn = vm.drawings.size
    val nextCode = vm.activeCharacterOrder.firstOrNull { it !in vm.drawings }

    Text("Draw one letter at a time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$drawn of $total letters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (total > 0) LinearProgressIndicator(progress = (drawn.toFloat() / total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
        }
    }

    if (drawn == 0) {
        Text("Start your font", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Button(onClick = { phrase = ""; showPhraseDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Use a phrase")
        }
        TextButton(onClick = vm::startPaging, modifier = Modifier.fillMaxWidth()) {
            Text("Start with the alphabet")
        }
    } else if (nextCode != null) {
        Text("Keep building your font.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { vm.startPaging() }, modifier = Modifier.fillMaxWidth()) {
            Text("Continue drawing")
        }
        TextButton(onClick = { phrase = ""; showPhraseDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Draw letters from a phrase instead")
        }
        OutlinedButton(onClick = { vm.generate(); preview() }, modifier = Modifier.fillMaxWidth()) {
            Text("Try your font")
        }
    } else {
        Text("Your letter set is complete.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Button(onClick = { vm.generate(); preview() }, modifier = Modifier.fillMaxWidth()) { Text("Try your font") }
        Text("You can start another font from Fonts whenever you are ready.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    TextButton(onClick = { pendingLanguages = selectedLanguages; showLanguages = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Letter options")
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

    if (showLanguages) AlertDialog(
        onDismissRequest = { showLanguages = false },
        title = { Text("Letter options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose the languages for this font.", style = MaterialTheme.typography.bodySmall)
                LanguageScript.entries.forEach { script ->
                    Row(Modifier.fillMaxWidth().clickable {
                        pendingLanguages = pendingLanguages.toMutableSet().apply { if (contains(script)) remove(script) else add(script) }
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = pendingLanguages.contains(script), onCheckedChange = { checked ->
                            pendingLanguages = pendingLanguages.toMutableSet().apply { if (checked) add(script) else remove(script) }
                        })
                        Column {
                            Text(script.displayName)
                            Text("${script.codePoints.size} letters", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (vm.setLanguages(pendingLanguages)) showLanguages = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { showLanguages = false }) { Text("Cancel") } },
    )
}

@Composable private fun SpacingScreen(vm: FontCreatorViewModel, previewText: String, changePreviewText: (String) -> Unit, back: () -> Unit) = Page("Letter spacing", back, scrollable = true) {
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

@Composable private fun ImageScreen(vm: FontCreatorViewModel, back: () -> Unit, initiallySelectedFont: String? = null, selected: (Typeface, Uri) -> Unit) {
    val context = LocalContext.current
    val allFonts = remember(vm.projects, vm.importedFonts) { vm.allFontOptions() }
    val systemFonts = listOf("Default" to Typeface.DEFAULT, "Serif" to Typeface.SERIF, "Sans-Serif" to Typeface.SANS_SERIF, "Monospace" to Typeface.MONOSPACE)
    val allAvailable = systemFonts + allFonts
    var selectedFontLabel by remember(allAvailable, initiallySelectedFont) { mutableStateOf(allAvailable.firstOrNull { it.first == initiallySelectedFont }?.first ?: allAvailable.firstOrNull()?.first ?: "Default") }
    var expanded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val tf = allAvailable.firstOrNull { it.first == selectedFontLabel }?.second ?: Typeface.DEFAULT
            selected(tf, uri)
        }
    }
    Page("Edit an image", back, scrollable = true) {
        Text("Choose a font and add styled text, a signature, or a stamp to an image.")
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
    back: () -> Unit,
) = Page("Settings", back, scrollable = true) {
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
    val title = if (pagingProgress != null) "Continue drawing · ${pagingProgress.first} of ${pagingProgress.second}" else "Draw a character"
    val isLastInQueue = pagingProgress != null && pagingProgress.first == pagingProgress.second
    val saveLabel = when {
        isLastInQueue -> "Save & Finish"
        pagingMode -> "Save & Next"
        else -> "Save letter"
    }
    Scaffold(topBar = { AppTopBar(title, handleBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
                            style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo")
                }
                IconButton({ strokes = emptyList(); active = emptyList() }, enabled = strokes.isNotEmpty()) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
                if (pagingMode) TextButton(onSkip) { Text("Skip") }
                Button({ onSave(GlyphDrawing(codePoint, strokes, canvasSize.first, canvasSize.second)) }, Modifier.weight(1f), enabled = strokes.isNotEmpty()) {
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

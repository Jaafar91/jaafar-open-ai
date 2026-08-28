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
import com.jaafar.remoteconfig.R

internal const val DEFAULT_PREVIEW_TEXT = "The quick brown fox jumps over the lazy dog 123"

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
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF2F3F7),
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
    surfaceContainerLowest = Color(0xFF11131A),
    surfaceContainerLow = Color(0xFF191B22),
    surfaceContainer = Color(0xFF191B22),
    surfaceContainerHigh = Color(0xFF20232C),
    surfaceContainerHighest = Color(0xFF282B35),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    outlineVariant = Color(0xFF45464F),
)

internal enum class Screen { Home, Fonts, Signatures, Stamps, FontCelebration, FontReady, Letters, Signature, FillMark, Settings }

@Composable
fun FontCreatorApp(
    viewModel: FontCreatorViewModel,
    sharedUri: Uri? = null,
    shareRequestId: Int = 0,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", 0) }
    var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
    var showTutorial by remember { mutableStateOf(sharedUri == null && !preferences.getBoolean("feature_tutorial_seen", false)) }
    var previewText by remember { mutableStateOf(preferences.getString("preview_text", DEFAULT_PREVIEW_TEXT) ?: DEFAULT_PREVIEW_TEXT) }
    var screen by remember { mutableStateOf(if (sharedUri != null) Screen.FillMark else Screen.Home) }
    var fillMarkUri by remember { mutableStateOf<Uri?>(sharedUri) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var preferredImageFontName by remember { mutableStateOf<String?>(null) }
    var initialImageText by remember { mutableStateOf("") }
    var fontWorkspaceBack by remember { mutableStateOf(Screen.Home) }
    var pendingSignatureMark by remember { mutableStateOf<String?>(null) }
    var showCreateFontDialog by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        if (uri == null) {
            preferredImageFontName = null
            initialImageText = ""
        }
    }

    // A running activity receives subsequent Share-sheet requests through onNewIntent.
    // Route every request straight to the editor, including a re-shared copy of the same file.
    LaunchedEffect(sharedUri, shareRequestId) {
        sharedUri ?: return@LaunchedEffect
        showTutorial = false
        fillMarkUri = sharedUri
        screen = Screen.FillMark
    }

    MaterialTheme(
        colorScheme = if (darkTheme) ModernDarkColors else ModernLightColors,
        typography = appTypography(null),
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
        val imageFontOptions = remember(viewModel.projects.toList(), viewModel.importedFonts.toList(), viewModel.generatedFont) {
            listOf(
                "Default" to Typeface.DEFAULT,
                "Serif" to Typeface.SERIF,
                "Sans-Serif" to Typeface.SANS_SERIF,
                "Monospace" to Typeface.MONOSPACE,
            ) + viewModel.allFontOptions()
        }
        when {
            showTutorial -> FeatureTutorial(
                onFinished = {
                    preferences.edit().putBoolean("feature_tutorial_seen", true).apply()
                    showTutorial = false
                },
            )
            imageUri != null -> ImageTextEditorScreen(
                imageUri = imageUri!!,
                fontOptions = imageFontOptions,
                initiallySelectedFont = preferredImageFontName,
                initialText = initialImageText,
            ) {
                imageUri = null
                preferredImageFontName = null
                initialImageText = ""
            }
            viewModel.selectedCodePoint != null -> GlyphEditorScreen(
                codePoint = viewModel.selectedCodePoint!!,
                initial = viewModel.drawings[viewModel.selectedCodePoint],
                defaultStrokeWidth = viewModel.lastStrokeWidth,
                drawings = viewModel.drawings,
                characterOrder = viewModel.editorCharacterOrder,
                pagingMode = viewModel.isPagingMode,
                pagingProgress = viewModel.pagingProgress,
                canGoPrevious = viewModel.canGoToPreviousLetter,
                referenceTypeface = referenceTypeface,
                phraseModeEnabled = viewModel.phraseModeEnabled,
                phraseText = viewModel.lastPhrase,
                onCreatePhrase = { phrase ->
                    if (viewModel.startPhrase(phrase)) {
                        previewText = phrase.trim()
                        preferences.edit().putString("preview_text", previewText).apply()
                        true
                    } else {
                        false
                    }
                },
                onDisablePhrase = viewModel::disablePhraseMode,
                onCancel = viewModel::closeEditor,
                onPrevious = viewModel::previousLetter,
                onSelectCharacter = viewModel::edit,
                onSkip = viewModel::skipLetter,
                onSave = { drawing ->
                    viewModel.saveDrawing(drawing)
                    if (viewModel.selectedCodePoint == null) {
                        viewModel.disablePhraseMode()
                        viewModel.generate()
                        screen = if (viewModel.activeProject?.let(viewModel::isProjectComplete) == true) {
                            Screen.FontCelebration
                        } else {
                            Screen.FontReady
                        }
                    }
                },
                onSaveAndContinue = { drawing ->
                    viewModel.saveDrawingAndContinue(drawing)
                    if (viewModel.selectedCodePoint == null) {
                        viewModel.disablePhraseMode()
                        viewModel.generate()
                        screen = if (viewModel.activeProject?.let(viewModel::isProjectComplete) == true) {
                            Screen.FontCelebration
                        } else {
                            Screen.FontReady
                        }
                    }
                },
                onSaveAndStay = viewModel::saveDrawingAndStay,
            )
            else -> {
                when (screen) {
                Screen.Home -> DashboardScreen(
                    vm = viewModel,
                    openSettings = { screen = Screen.Settings },
                    createFont = { showCreateFontDialog = true },
                    continueFont = { index ->
                        viewModel.openProject(index)
                        fontWorkspaceBack = Screen.Home
                        screen = Screen.Letters
                        viewModel.editLetters()
                    },
                    useFontOnImage = { fontName ->
                        preferredImageFontName = fontName
                        initialImageText = ""
                        imagePicker.launch("image/*")
                    },
                    openFillMark = { screen = Screen.FillMark },
                    openFonts = { screen = Screen.Fonts },
                    openSignatures = { screen = Screen.Signatures },
                    openStamps = { screen = Screen.Stamps },
                )
                Screen.Fonts -> FontsModuleScreen(
                    vm = viewModel,
                    back = { screen = Screen.Home },
                    createFont = { showCreateFontDialog = true },
                    openProject = { index ->
                        viewModel.openProject(index)
                        fontWorkspaceBack = Screen.Fonts
                        screen = Screen.Letters
                    },
                )
                Screen.Signatures -> SignaturesModuleScreen(
                    vm = viewModel,
                    back = { screen = Screen.Home },
                    useInDocument = { markName ->
                        pendingSignatureMark = markName
                        screen = Screen.Signature
                    },
                )
                Screen.Stamps -> StampsModuleScreen(
                    vm = viewModel,
                    back = { screen = Screen.Home },
                    useInDocument = { markName ->
                        pendingSignatureMark = markName
                        screen = Screen.Signature
                    },
                )
                Screen.FontCelebration -> {
                    val project = viewModel.activeProject
                    FontCelebrationScreen(
                        fontName = project?.name.orEmpty().ifBlank { "My handwriting" },
                        characterCount = viewModel.activeCharacterOrder.size,
                        fineTune = {
                            viewModel.generate()
                            screen = Screen.FontReady
                        },
                        backToLetters = {
                            screen = Screen.Letters
                            viewModel.editLetters()
                        },
                    )
                }
                Screen.FontReady -> FontReadyScreen(
                    vm = viewModel,
                    previewText = previewText,
                    changePreviewText = { value -> previewText = value; preferences.edit().putString("preview_text", value).apply() },
                    back = { screen = Screen.Letters },
                    startDrawing = {
                        viewModel.disablePhraseMode()
                        screen = Screen.Letters
                        val nextMissing = viewModel.activeCharacterOrder.firstOrNull { it !in viewModel.drawings }
                        if (nextMissing != null) viewModel.edit(nextMissing) else viewModel.editLetters()
                    },
                    useOnImage = { fontName, text ->
                        preferredImageFontName = fontName
                        initialImageText = text
                        imagePicker.launch("image/*")
                    },
                )
                Screen.Letters -> LettersScreen(
                    vm = viewModel,
                    back = { screen = fontWorkspaceBack },
                    fineTune = {
                        viewModel.generate()
                        screen = Screen.FontReady
                    },
                    useOnImage = { fontName ->
                        preferredImageFontName = fontName
                        initialImageText = previewText
                        imagePicker.launch("image/*")
                    },
                )
                Screen.Signature -> SignatureScreen(
                    vm = viewModel,
                    initialMarkName = pendingSignatureMark,
                    onInitialMarkConsumed = { pendingSignatureMark = null },
                    back = { pendingSignatureMark = null; screen = Screen.Home },
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
                ) { screen = Screen.Home }
                }
                if (showCreateFontDialog) {
                    CreateFontDialog(
                        vm = viewModel,
                        onCreated = {
                            showCreateFontDialog = false
                            fontWorkspaceBack = Screen.Home
                            screen = Screen.Letters
                            viewModel.editLetters()
                        },
                        onDismiss = { showCreateFontDialog = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Page(title: String, back: (() -> Unit)? = null, scrollable: Boolean = false, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(enabled = back != null) { back?.invoke() }
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
                        Text("Aa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

@Composable private fun SettingsScreen(
    vm: FontCreatorViewModel,
    dark: Boolean,
    change: (Boolean) -> Unit,
    back: () -> Unit,
) = Page("Settings", back, scrollable = true) {
    val context = LocalContext.current
    Text("Appearance", style = MaterialTheme.typography.titleMedium)
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.clickable { change(false) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !dark, onClick = { change(false) })
            Text("Light")
        }
        Row(Modifier.clickable { change(true) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = dark, onClick = { change(true) })
            Text("Dark")
        }
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
    OutlinedButton(
        onClick = { openPlayStoreListing(context) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Rate this app") }
}

private fun openPlayStoreListing(context: android.content.Context) {
    val packageName = context.packageName
    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
        setPackage("com.android.vending")
    }
    runCatching { context.startActivity(playStoreIntent) }
        .onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
}

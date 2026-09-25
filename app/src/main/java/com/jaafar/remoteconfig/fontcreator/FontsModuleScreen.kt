package com.jaafar.remoteconfig.fontcreator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Matches iOS's "Complete" green -- Material has no built-in success color. */
internal val CompleteGreen = Color(0xFF2E7D32)

@Composable
internal fun FontsModuleScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    createFont: () -> Unit,
    openProject: (Int) -> Unit,
) {
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var importName by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<FontProject?>(null) }
    var importedToDelete by remember { mutableStateOf<ImportedFont?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showImportProDialog by remember { mutableStateOf(false) }
    var showAddMenuProDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: uri.lastPathSegment ?: "Imported Font"
            importName = raw.substringAfterLast('/').substringAfterLast(':').substringBeforeLast('.').ifBlank { "Imported Font" }
            pendingImport = uri
        }
    }

    Page(
        "Fonts",
        back,
        actions = {
            IconButton(onClick = { showAddMenu = true }) { ActionIcon(ActionIconType.Add, "Add font") }
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Draw new font") },
                    onClick = {
                        showAddMenu = false
                        if (vm.hasReachedFreeFontLimit) showAddMenuProDialog = true else createFont()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Import font file") },
                    onClick = {
                        showAddMenu = false
                        if (vm.hasReachedFreeFontLimit) {
                            showAddMenuProDialog = true
                        } else {
                            picker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"))
                        }
                    },
                )
            }
        },
    ) {
        if (vm.projects.isEmpty() && vm.importedFonts.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.TextFields,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("No fonts yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Tap + to draw your handwriting font or import one you own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.projects.isNotEmpty()) {
                    item { Text("CREATED FONTS", style = MaterialTheme.typography.titleSmall) }
                    items(vm.projects, key = { it.name }) { project ->
                        val complete = vm.isProjectComplete(project)
                        val total = vm.characterCount(project).coerceAtLeast(1)
                        val drawn = vm.progressCount(project).coerceAtMost(total)
                        val percentage = (drawn * 100 / total).coerceIn(0, 100)
                        // The *real* generated font, loaded off the main thread -- the same one
                        // Fine-tune shows -- so the name and thumbnail here match that screen
                        // exactly instead of approximating it from raw pen strokes.
                        val previewTypeface by produceState<android.graphics.Typeface?>(null, project.name, project.drawings, complete) {
                            value = if (complete) vm.typefaceForPreview(project) else null
                        }
                        OutlinedCard(Modifier.fillMaxWidth().clickable { openProject(vm.projects.indexOf(project)) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AaThumbnail(previewTypeface)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            project.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = previewTypeface?.let { androidx.compose.ui.text.font.FontFamily(it) },
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        FontStatusBadge(if (complete) "Complete" else "$percentage%", showCheck = complete)
                                    }
                                    Text(
                                        "Created font",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LinearProgressIndicator(
                                        progress = drawn.toFloat() / total,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = if (complete) CompleteGreen else MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { projectToDelete = project }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${project.name}")
                                }
                            }
                        }
                    }
                }
                if (vm.importedFonts.isNotEmpty()) {
                    item { Text("IMPORTED FONTS", style = MaterialTheme.typography.titleSmall) }
                    items(vm.importedFonts, key = { it.fileName }) { font ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FontThumbnail(drawing = null, placeholderIcon = Icons.Filled.TextFields)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            font.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        FontStatusBadge("Ready", showCheck = true)
                                    }
                                    Text(
                                        "Imported font",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { importedToDelete = font }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${font.displayName}")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (vm.importStatus.isNotBlank()) Text(vm.importStatus, style = MaterialTheme.typography.bodySmall)
    }

    pendingImport?.let { uri ->
        val importCapReached = vm.hasReachedFreeFontLimit
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Name imported font") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(importName, { importName = it }, label = { Text("Font name") }, singleLine = true)
                    if (importCapReached) {
                        Text(
                            "Free plan allows ${FontCreatorViewModel.FREE_FONT_LIMIT} font. Upgrade to Pro for unlimited fonts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                if (importCapReached) {
                    Button(onClick = { showImportProDialog = true }) { Text("See Pro benefits") }
                } else {
                    Button(
                        onClick = { vm.importFont(context.contentResolver, uri, importName); pendingImport = null },
                        enabled = importName.isNotBlank(),
                    ) {
                        Text("Import")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
        if (showImportProDialog) {
            ProFeaturesDialog(vm = vm) { showImportProDialog = false }
        }
    }
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete font?") },
            text = { Text("Delete \"${project.name}\" and its generated font file? This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.deleteProject(project.name); projectToDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("Cancel") } },
        )
    }
    importedToDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { importedToDelete = null },
            title = { Text("Delete imported font?") },
            text = { Text("Delete \"${font.displayName}\" from your fonts?") },
            confirmButton = { TextButton(onClick = { vm.deleteImportedFont(font.fileName); importedToDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { importedToDelete = null }) { Text("Cancel") } },
        )
    }
    if (showAddMenuProDialog) {
        ProFeaturesDialog(vm = vm) { showAddMenuProDialog = false }
    }
}

/** 56dp rounded thumbnail: the given letter drawing if there is one, else a placeholder icon. */
@Composable
private fun FontThumbnail(
    drawing: GlyphDrawing?,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Box(
        Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (drawing != null) {
            GlyphBarPreview(drawing, MaterialTheme.colorScheme.primary, Modifier.fillMaxSize().padding(10.dp))
        } else {
            Icon(placeholderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** 56dp rounded thumbnail for a drawn font: "Aa" rendered in the font itself once it's
 *  generated, else an edit icon prompting them to keep drawing. */
@Composable
private fun AaThumbnail(typeface: android.graphics.Typeface?) {
    Box(
        Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (typeface != null) {
            Text(
                "Aa",
                fontFamily = androidx.compose.ui.text.font.FontFamily(typeface),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Small colored capsule, e.g. "Complete" or "42%". */
@Composable
internal fun FontStatusBadge(text: String, showCheck: Boolean) {
    val color = if (showCheck) CompleteGreen else MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCheck) Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun CreateFontDialog(vm: FontCreatorViewModel, onCreated: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var showProDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val duplicate = name.trim().isNotEmpty() && vm.hasFontName(name)
    val capReached = vm.hasReachedFreeFontLimit
    // keyboard.show() below opens the IME for this dialog's own field, but dismissing the
    // dialog never hands focus to another text field -- Create jumps straight into the
    // glyph-drawing canvas, which has none -- so without an explicit hide() here the keyboard
    // is left floating over whatever's shown next.
    val dismiss = { keyboard?.hide(); onDismiss() }

    // Asked every time a font is created, not just for a brand-new user -- each font can be
    // drawn for a different purpose, so the goal shouldn't only be decided once.
    var askingGoal by remember { mutableStateOf(true) }
    var goal by remember { mutableStateOf(FontGoal.EXPORT) }

    if (askingGoal) {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("What's this font for?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This just decides how many characters you'll need to draw to finish -- you can always keep drawing more later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GoalOption(
                        icon = Icons.Filled.Image,
                        title = "Use it on images",
                        detail = "Skip punctuation and symbols as you go -- the fastest way to start writing on photos.",
                        selected = goal == FontGoal.USE_ON_IMAGE,
                        onClick = { goal = FontGoal.USE_ON_IMAGE },
                    )
                    GoalOption(
                        icon = Icons.Filled.Description,
                        title = "Export a full font",
                        detail = "Every character, including punctuation -- for installing or sharing the font file.",
                        selected = goal == FontGoal.EXPORT,
                        onClick = { goal = FontGoal.EXPORT },
                    )
                }
            },
            confirmButton = { Button(onClick = { askingGoal = false }) { Text("Next") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Name your font") },
        text = {
            LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Give your font a name. You can change it later.")
                OutlinedTextField(
                    name,
                    { name = it },
                    Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text("Font name") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = { if (duplicate) Text("A font with that name already exists.") },
                )
                if (capReached) {
                    Text(
                        "Free plan allows ${FontCreatorViewModel.FREE_FONT_LIMIT} font. Upgrade to Pro for unlimited fonts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (capReached) {
                Button(onClick = { showProDialog = true }) { Text("See Pro benefits") }
            } else {
                Button(
                    onClick = { if (vm.createProject(name, goal)) { keyboard?.hide(); onCreated() } },
                    enabled = name.isNotBlank() && !duplicate,
                ) { Text("Create font") }
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
    if (showProDialog) {
        ProFeaturesDialog(vm = vm) { showProDialog = false }
    }
}

@Composable
private fun GoalOption(icon: ImageVector, title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

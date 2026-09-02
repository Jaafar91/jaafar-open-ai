package com.jaafar.remoteconfig.fontcreator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Matches iOS's "Complete" green -- Material has no built-in success color. */
private val CompleteGreen = Color(0xFF2E7D32)

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
                DropdownMenuItem(text = { Text("Draw new font") }, onClick = { showAddMenu = false; createFont() })
                DropdownMenuItem(
                    text = { Text("Import font file") },
                    onClick = {
                        showAddMenu = false
                        picker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"))
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
                        val drawn = project.drawings.size.coerceAtMost(total)
                        val thumbnail = project.drawings.filter { it.strokes.isNotEmpty() }.minByOrNull { it.codePoint }
                        OutlinedCard(Modifier.fillMaxWidth().clickable { openProject(vm.projects.indexOf(project)) }) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FontThumbnail(thumbnail, placeholderIcon = Icons.Filled.Edit)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            project.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        FontStatusBadge(if (complete) "Complete" else "$drawn of $total", showCheck = complete)
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
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Name imported font") },
            text = { OutlinedTextField(importName, { importName = it }, label = { Text("Font name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = { vm.importFont(context.contentResolver, uri, importName); pendingImport = null }, enabled = importName.isNotBlank()) {
                    Text("Import")
                }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
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

/** Small colored capsule, e.g. "Complete" or "12 of 94". */
@Composable
private fun FontStatusBadge(text: String, showCheck: Boolean) {
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
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val duplicate = name.trim().isNotEmpty() && vm.hasFontName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
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
            }
        },
        confirmButton = {
            Button(onClick = { if (vm.createProject(name)) onCreated() }, enabled = name.isNotBlank() && !duplicate) { Text("Create font") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

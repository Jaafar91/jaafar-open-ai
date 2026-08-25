package com.jaafar.remoteconfig.fontcreator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

    Page("Fonts", back) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = createFont, modifier = Modifier.weight(1f)) { Text("Create font") }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*")) },
                modifier = Modifier.weight(1f),
            ) { Text("Import font") }
        }
        if (vm.projects.isEmpty() && vm.importedFonts.isEmpty()) {
            Text("No fonts yet. Create your handwriting font or import a font you own.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.projects.isNotEmpty()) {
                    item { Text("CREATED FONTS", style = MaterialTheme.typography.titleSmall) }
                    items(vm.projects, key = { it.name }) { project ->
                        OutlinedCard(Modifier.fillMaxWidth().clickable { openProject(vm.projects.indexOf(project)) }) {
                            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${project.drawings.size} of ${vm.characterCount(project)} characters")
                                }
                                TextButton(onClick = { projectToDelete = project }) { Text("Delete") }
                            }
                        }
                    }
                }
                if (vm.importedFonts.isNotEmpty()) {
                    item { Text("IMPORTED FONTS", style = MaterialTheme.typography.titleSmall) }
                    items(vm.importedFonts, key = { it.fileName }) { font ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(font.displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { importedToDelete = font }) { Text("Delete") }
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

@Composable
internal fun CreateFontDialog(vm: FontCreatorViewModel, onCreated: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val duplicate = name.trim().isNotEmpty() && vm.hasProjectName(name)
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

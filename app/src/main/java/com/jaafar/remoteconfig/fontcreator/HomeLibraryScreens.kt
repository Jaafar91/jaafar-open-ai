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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jaafar.remoteconfig.R

/** Home and saved-asset screens. Kept separate from the font studio workflow. */

@Composable internal fun HomeScreen(vm: FontCreatorViewModel, go: (Screen) -> Unit) = Page(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    openFontManager: () -> Unit,
    openLetterEditor: () -> Unit,
    openSignatureWithMark: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(LibraryTab.Fonts) }
    var signaturePage by remember { mutableStateOf(LibrarySignaturePage.Hub) }
    var selectedLibraryMark by remember { mutableStateOf<SavedSignature?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var libraryStatus by remember { mutableStateOf("") }
    var projectToDelete by remember { mutableStateOf<FontProject?>(null) }
    var importedFontToDelete by remember { mutableStateOf<ImportedFont?>(null) }
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
            if (vm.projects.isEmpty() && vm.importedFonts.isEmpty()) {
                Text("No fonts in your library yet.")
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.projects.isNotEmpty()) {
                        item { Text("Generated fonts", style = MaterialTheme.typography.titleSmall) }
                        items(vm.projects, key = { it.name }) { project ->
                            FontLibrarySwipeToDelete(onDelete = { projectToDelete = project }) {
                                OutlinedCard(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val index = vm.projects.indexOf(project)
                                            if (index >= 0) {
                                                vm.openProject(index)
                                                openLetterEditor()
                                            }
                                        },
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${project.drawings.size} drawn characters")
                                    }
                                }
                            }
                        }
                    }
                    if (vm.importedFonts.isNotEmpty()) {
                        item { Text("Imported fonts", style = MaterialTheme.typography.titleSmall) }
                        items(vm.importedFonts, key = { it.fileName }) { font ->
                            FontLibrarySwipeToDelete(onDelete = { importedFontToDelete = font }) {
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text(font.displayName, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(openFontManager, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create or import font")
            }
            if (libraryStatus.isNotBlank()) {
                Text(libraryStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
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
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
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
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add signature")
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
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add stamp")
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
        ModalBottomSheet(onDismissRequest = { selectedLibraryMark = null; showRenameDialog = false }) {
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
                        selectedLibraryMark = null
                        openSignatureWithMark(mark.name)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use in a document")
                }
                OutlinedButton(
                    onClick = {
                        renameValue = mark.name
                        showRenameDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rename")
                }
                TextButton(
                    onClick = {
                        vm.deleteSignature(mark.name)
                        libraryStatus = "Deleted ${mark.name}."
                        selectedLibraryMark = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Delete")
                }
            }
        }
    }
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete font?") },
            text = { Text("Delete \"${project.name}\" and its generated font file? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProject(project.name)
                    libraryStatus = "Deleted ${project.name}."
                    projectToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { projectToDelete = null }) { Text("Cancel") } },
        )
    }
    importedFontToDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { importedFontToDelete = null },
            title = { Text("Delete imported font?") },
            text = { Text("Delete \"${font.displayName}\" from your library? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteImportedFont(font.fileName)
                    libraryStatus = "Deleted ${font.displayName}."
                    importedFontToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { importedFontToDelete = null }) { Text("Cancel") } },
        )
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
                    if (mark == null) {
                        libraryStatus = "Rename failed: asset no longer available."
                        showRenameDialog = false
                        return@TextButton
                    }
                    if (vm.renameSignature(mark.name, renameValue)) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontLibrarySwipeToDelete(
    onDelete: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error),
            )
        },
        content = content,
    )
}

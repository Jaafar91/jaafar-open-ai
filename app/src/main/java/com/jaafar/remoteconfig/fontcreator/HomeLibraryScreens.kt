package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jaafar.remoteconfig.R

/** Home and saved-asset screens. Kept separate from the font studio workflow. */

private data class HomeGridAction(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val destination: Screen,
    val primary: Boolean = false,
)

@Composable
internal fun HomeScreen(vm: FontCreatorViewModel, go: (Screen) -> Unit) = Page(
    "Studio",
    actions = {
        IconButton(onClick = { go(Screen.Settings) }) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    },
) {
    val actions = listOf(
        HomeGridAction("Create font", "Draw your style", Icons.Filled.TextFields, Screen.Fonts, primary = true),
        HomeGridAction("Write on image", "Add text or stamps", Icons.Filled.Image, Screen.Image),
        HomeGridAction("Fill & mark", "Complete a document", Icons.Filled.Description, Screen.FillMark),
        HomeGridAction("My Library", "Fonts and saved items", Icons.Filled.Folder, Screen.Library),
    )

    Text("Create your own font", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "Draw letters, then use your font on images and documents.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(actions, key = { it.title }) { action ->
            HomeGridTile(action = action) { go(action.destination) }
        }
    }
    vm.activeProject?.let { project ->
        Text(
            "Current font: ${project.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeGridTile(action: HomeGridAction, click: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = click),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (action.primary) colors.primaryContainer else colors.surface,
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (action.primary) colors.primary else colors.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                color = if (action.primary) colors.primary else colors.secondaryContainer,
                contentColor = if (action.primary) colors.onPrimary else colors.onSecondaryContainer,
                shape = RoundedCornerShape(20.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(action.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                action.detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private data class TutorialPage(
    val title: String,
    val detail: String,
    val icon: ImageVector,
)

@Composable
internal fun FeatureTutorial(onFinished: () -> Unit) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val pages = listOf(
        TutorialPage(
            "Create a font",
            "Turn your handwriting into a font. Start with the letters you use most, then try a phrase.",
            Icons.Filled.TextFields,
        ),
        TutorialPage(
            "Write on an image",
            "Choose a photo, type your words, then move them into place. Use your font or a stamp.",
            Icons.Filled.Image,
        ),
        TutorialPage(
            "Fill & mark",
            "Open a PDF or image, then add text, dates, a signature, or a stamp. Export when you’re done.",
            Icons.Filled.Description,
        ),
        TutorialPage(
            "My Library",
            "Your fonts, signatures, and stamps stay here. Reuse them whenever you edit an image or document.",
            Icons.Filled.Folder,
        ),
    )
    val page = pages[pageIndex]
    val isLastPage = pageIndex == pages.lastIndex

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinished) { Text("Skip") }
            }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.size(144.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(page.icon, contentDescription = null, modifier = Modifier.size(76.dp))
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                page.detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(
                progress = (pageIndex + 1).toFloat() / pages.size,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (isLastPage) onFinished() else pageIndex++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isLastPage) "Get started" else "Next")
            }
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
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        content = content,
    )
}

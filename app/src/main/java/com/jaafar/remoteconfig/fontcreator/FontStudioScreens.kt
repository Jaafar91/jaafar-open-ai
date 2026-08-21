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

/** Font creation, preview, export, and import screens. */

@Composable internal fun FontsScreen(
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

    Page("Add a font", back) {
        Text("Create or import", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Create a handwriting font or import a font you already own.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Create a handwriting font")
        }
        OutlinedButton(
            onClick = {
                fontFilePicker.launch(
                    arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*"),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Import a font")
        }
        if (vm.importStatus.isNotBlank()) {
            Text(
                vm.importStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
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

@Composable internal fun FontReadyScreen(
    vm: FontCreatorViewModel,
    previewText: String,
    changePreviewText: (String) -> Unit,
    back: () -> Unit,
    editLetters: () -> Unit,
    startDrawing: () -> Unit,
    useOnImage: (String, String) -> Unit,
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
            Button(onClick = { useOnImage(project.name, previewText) }, modifier = Modifier.fillMaxWidth()) { Text("Use on an image") }
            if (!complete) OutlinedButton(onClick = startDrawing, modifier = Modifier.fillMaxWidth()) { Text("Continue drawing") }
        }
        }
        if (vm.status.isNotBlank()) Text(vm.status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun ShareButton(file: java.io.File, name: String) {
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


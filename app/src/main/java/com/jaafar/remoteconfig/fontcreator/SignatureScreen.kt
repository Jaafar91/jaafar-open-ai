package com.jaafar.remoteconfig.fontcreator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ImportStampFromImageScreen(
    vm: FontCreatorViewModel,
    onSaved: (String) -> Unit,
    back: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(vm.suggestedSignatureName("My stamp")) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var removeWhiteBackground by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val duplicateName = name.trim().isNotEmpty() && vm.hasSavedSignatureName(name)
    DisposableEffect(rawBitmap) {
        val bitmapToRecycle = rawBitmap
        onDispose { bitmapToRecycle?.recycle() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedUri = uri
        status = ""
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(context.contentResolver, uri) }
            rawBitmap = bitmap
            if (bitmap == null) status = "Could not load image."
        }
    }

    var processedPreview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(rawBitmap, removeWhiteBackground) {
        val old = processedPreview
        processedPreview = null
        old?.recycle()
        val src = rawBitmap ?: return@LaunchedEffect
        if (!removeWhiteBackground) return@LaunchedEffect
        var processed: Bitmap? = null
        try {
            processed = withContext(Dispatchers.Default) { removeNearWhitePixels(src) }
            processedPreview = processed
            processed = null
        } finally {
            processed?.recycle()
        }
    }
    DisposableEffect(Unit) {
        onDispose { processedPreview?.recycle() }
    }

    Page("Import Stamp", back, scrollable = true) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Stamp name") },
            singleLine = true,
            isError = duplicateName,
            supportingText = {
                if (duplicateName) Text("A saved signature or stamp already uses that name.")
            },
        )
        OutlinedButton(
            onClick = { picker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        ) { Text(if (selectedUri == null) "Choose image" else "Choose a different image") }
        if (rawBitmap != null) {
            val bitmap = rawBitmap!!
            val displayBitmap = processedPreview ?: bitmap
            Box(
                Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()).border(1.dp, ComposeColor.Gray)
            ) {
                Image(
                    bitmap = displayBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = removeWhiteBackground,
                    onCheckedChange = { removeWhiteBackground = it },
                )
                Text("Remove white background", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text("Select an image to preview and import it as a reusable stamp.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val uri = selectedUri ?: return@Button
                if (duplicateName) {
                    status = "A saved signature or stamp already uses that name."
                    return@Button
                }
                scope.launch {
                    saving = true
                    status = "Saving stamp\u2026"
                    val savedName = withContext(Dispatchers.IO) {
                        runCatching { vm.saveSignatureFromImage(context.contentResolver, uri, name, removeWhiteBackground) }.getOrNull()
                    }
                    saving = false
                    if (savedName != null) {
                        status = "Stamp saved."
                        onSaved(savedName)
                    } else {
                        status = "Could not save this stamp image."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && selectedUri != null && !duplicateName,
        ) { Text("Save stamp") }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SignatureEditorScreen(
    vm: FontCreatorViewModel,
    onSaved: (String) -> Unit,
    back: () -> Unit,
    existing: SavedSignature? = null,
    useInFillMark: ((String) -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name ?: vm.suggestedSignatureName("My signature")) }
    var strokes by remember { mutableStateOf(existing?.strokes ?: emptyList()) }
    var active by remember { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(1f to 1f) }
    var status by remember { mutableStateOf("") }
    // Excludes the signature's own current name -- editing it back to what it already was
    // isn't a duplicate, unlike creating a brand new one under a name already in use.
    val duplicateName = name.trim().isNotEmpty() &&
        !name.trim().equals(existing?.name, ignoreCase = true) &&
        vm.hasSavedSignatureName(name)

    Page(if (existing != null) "Edit Signature" else "New Signature", back, scrollable = true) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Signature name") },
            singleLine = true,
            isError = duplicateName,
            supportingText = {
                if (duplicateName) Text("A saved signature or stamp already uses that name.")
            },
        )
        Canvas(
            Modifier.fillMaxWidth().height(220.dp)
                .background(ComposeColor.White)
                .border(1.dp, ComposeColor.Gray)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { active = listOf(GlyphPoint(it.x, it.y)) },
                        onDrag = { change, _ ->
                            change.consume()
                            val next = GlyphPoint(change.position.x, change.position.y)
                            if (active.lastOrNull()?.let { hypotSquared(it, next) > 9f } != false) {
                                active = active + next
                            }
                        },
                        onDragEnd = {
                            if (active.size > 1) strokes = strokes + GlyphStroke(active)
                            active = emptyList()
                        },
                        onDragCancel = { active = emptyList() },
                    )
                }
        ) {
            canvasSize = size.width to size.height
            (strokes.map { it.points } + listOf(active)).forEach { points ->
                if (points.size > 1) {
                    drawPath(
                        ComposePath().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        },
                        ComposeColor.Black,
                        style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { strokes = emptyList(); active = emptyList() }, enabled = strokes.isNotEmpty()) { Text("Clear") }
            OutlinedButton(onClick = { strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Undo") }
            Button(
                onClick = {
                    if (duplicateName) {
                        status = "A saved signature or stamp already uses that name."
                        return@Button
                    }
                    val savedName = if (existing != null) {
                        if (vm.updateSignature(existing.name, name, strokes, canvasSize.first, canvasSize.second)) name.trim().ifEmpty { existing.name } else null
                    } else {
                        vm.saveSignature(name, strokes, canvasSize.first, canvasSize.second)
                    }
                    if (savedName != null) {
                        status = "Saved."
                        onSaved(savedName)
                    } else {
                        status = "A saved signature or stamp already uses that name."
                    }
                },
                enabled = strokes.isNotEmpty() && !duplicateName,
                modifier = Modifier.weight(1f),
            ) { Text(if (existing != null) "Save changes" else "Save signature") }
        }
        // Signing/stamping a document is now Fill & Mark's job -- this just gets you there
        // with this signature ready to place, instead of a separate bespoke sign-a-document
        // screen duplicating what Fill & Mark already does.
        if (existing != null && useInFillMark != null) {
            OutlinedButton(onClick = { useInFillMark(existing.name) }, modifier = Modifier.fillMaxWidth()) {
                Text("Use in Fill & Mark")
            }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SignaturePreview(modifier: Modifier, signature: SavedSignature) {
    val context = LocalContext.current
    val imageBitmap = rememberImageBitmap(
        signature.imageFileName?.let { fileName -> File(context.filesDir, fileName).takeIf { it.exists() } }
    )
    if (imageBitmap != null) {
        Box(modifier.background(ComposeColor.White).border(1.dp, ComposeColor.LightGray)) {
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Canvas(modifier.background(ComposeColor.White).border(1.dp, ComposeColor.LightGray)) {
            val points = signature.strokes.flatMap { it.points }
            val minX = points.minOfOrNull { it.x } ?: 0f
            val minY = points.minOfOrNull { it.y } ?: 0f
            val maxX = points.maxOfOrNull { it.x } ?: 1f
            val maxY = points.maxOfOrNull { it.y } ?: 1f
            val width = (maxX - minX).coerceAtLeast(1f)
            val height = (maxY - minY).coerceAtLeast(1f)
            val scale = minOf(size.width * .82f / width, size.height * .62f / height)
            val left = (size.width - width * scale) / 2f
            val top = (size.height - height * scale) / 2f
            signature.strokes.forEach { stroke ->
                if (stroke.points.size > 1) {
                    drawPath(
                        ComposePath().apply {
                            moveTo(left + (stroke.points.first().x - minX) * scale, top + (stroke.points.first().y - minY) * scale)
                            stroke.points.drop(1).forEach { point ->
                                lineTo(left + (point.x - minX) * scale, top + (point.y - minY) * scale)
                            }
                        },
                        ComposeColor.Black,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
    }
}

@Composable
internal fun rememberImageBitmap(file: File?): Bitmap? {
    val path = file?.absolutePath
    val bitmapState = produceState<Bitmap?>(initialValue = null, path) {
        value = null
        val decoded = if (path == null) null else withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
        value = decoded
        try {
            awaitCancellation()
        } finally {
            decoded?.recycle()
        }
    }
    return bitmapState.value
}

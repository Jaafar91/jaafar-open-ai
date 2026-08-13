package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

@Composable
fun FontCreatorApp(viewModel: FontCreatorViewModel) {
    MaterialTheme {
        val selected = viewModel.selectedCodePoint
        if (selected == null) FontGridScreen(viewModel) else GlyphEditorScreen(
            codePoint = selected,
            initial = viewModel.drawings[selected],
            onCancel = viewModel::closeEditor,
            onSave = viewModel::saveDrawing,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontGridScreen(viewModel: FontCreatorViewModel) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf("The quick brown fox jumps over the lazy dog 123") }
    Scaffold(topBar = { TopAppBar(title = { Text("Font Creator") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Basic Latin", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Choose a character, then draw it between the metric guides.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(52.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items((32..126).toList(), key = { it }) { code ->
                    val complete = viewModel.drawings.containsKey(code)
                    Box(
                        Modifier.aspectRatio(1f).background(if (complete) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .clickable { viewModel.edit(code) },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (code == 32) "SP" else code.toChar().toString()) }
                }
            }
            OutlinedTextField(
                value = preview, onValueChange = { preview = it }, label = { Text("Preview") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = viewModel.previewTypeface?.let { FontFamily(it) },
                ),
            )
            if (viewModel.status.isNotBlank()) Text(viewModel.status, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(viewModel::generate, Modifier.weight(1f)) { Text("Generate font") }
                viewModel.generatedFont?.let { font ->
                    OutlinedButton(onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", font)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "font/ttf"; putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Share font"))
                    }) { Text("Share") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlyphEditorScreen(
    codePoint: Int,
    initial: GlyphDrawing?,
    onCancel: () -> Unit,
    onSave: (GlyphDrawing) -> Unit,
) {
    var strokes by remember(codePoint) { mutableStateOf(initial?.strokes ?: emptyList()) }
    var active by remember { mutableStateOf<List<GlyphPoint>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(initial?.let { it.canvasWidth to it.canvasHeight } ?: (1f to 1f)) }
    Scaffold(topBar = { TopAppBar(title = { Text("Draw ${if (codePoint == 32) "space" else codePoint.toChar()}") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Red: baseline  •  Gray: ascender and descender", style = MaterialTheme.typography.bodySmall)
            Canvas(
                Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp)
                    .background(Color.White).border(1.dp, Color.Gray)
                    .pointerInput(codePoint, strokes) {
                        detectDragGestures(
                            onDragStart = { active = listOf(GlyphPoint(it.x, it.y)) },
                            onDrag = { change, _ ->
                                change.consume()
                                val next = GlyphPoint(change.position.x, change.position.y)
                                if (active.lastOrNull()?.let { hypotSquared(it, next) > 9f } != false) active = active + next
                            },
                            onDragEnd = { if (active.size > 1) strokes = strokes + GlyphStroke(active); active = emptyList() },
                            onDragCancel = { active = emptyList() },
                        )
                    },
            ) {
                canvasSize = size.width to size.height
                drawLine(Color.LightGray, Offset(0f, size.height * .1f), Offset(size.width, size.height * .1f), 2f)
                drawLine(Color.Red, Offset(0f, size.height * .78f), Offset(size.width, size.height * .78f), 3f)
                drawLine(Color.LightGray, Offset(0f, size.height * .94f), Offset(size.width, size.height * .94f), 2f)
                (strokes.map { it.points } + listOf(active)).forEach { points ->
                    if (points.size > 1) {
                        val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(path, Color.Black, style = Stroke(width = 8f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                OutlinedButton(onClick = { strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) { Text("Undo") }
                Button(
                    onClick = { onSave(GlyphDrawing(codePoint, strokes, canvasSize.first, canvasSize.second)) },
                    enabled = strokes.isNotEmpty() || codePoint == 32, modifier = Modifier.weight(1f),
                ) { Text("Save glyph") }
            }
        }
    }
}

private fun hypotSquared(a: GlyphPoint, b: GlyphPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}

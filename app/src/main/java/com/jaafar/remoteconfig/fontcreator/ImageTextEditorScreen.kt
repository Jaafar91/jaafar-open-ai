package com.jaafar.remoteconfig.fontcreator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageTextEditorScreen(imageUri: Uri, typeface: Typeface, onBack: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(imageUri) { loadBitmap(context.contentResolver, imageUri) }
    var text by remember { mutableStateOf("") }
    var sizePercent by remember { mutableFloatStateOf(10f) }
    var whiteText by remember { mutableStateOf(true) }

    Scaffold(topBar = { TopAppBar(title = { Text("Write on image") }, navigationIcon = {
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bitmap == null) {
                Text("This image could not be opened.", color = MaterialTheme.colorScheme.error)
            } else {
                ComposeCanvas(
                    Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                ) {
                    val scale = minOf(size.width / bitmap.width, size.height / bitmap.height)
                    val width = (bitmap.width * scale).roundToInt()
                    val height = (bitmap.height * scale).roundToInt()
                    val left = ((size.width - width) / 2f).roundToInt()
                    val top = ((size.height - height) / 2f).roundToInt()
                    drawImage(bitmap.asImageBitmap(), dstOffset = IntOffset(left, top), dstSize = IntSize(width, height))
                    drawIntoCanvas { canvas ->
                        val paint = overlayPaint(typeface, height * sizePercent / 100f, whiteText)
                        canvas.nativeCanvas.drawText(text, size.width / 2f, top + height * .85f, paint)
                    }
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(typeface),
                    ),
                )
                Text("Text size")
                Slider(value = sizePercent, onValueChange = { sizePercent = it }, valueRange = 4f..24f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { whiteText = !whiteText }, modifier = Modifier.weight(1f)) {
                        Text(if (whiteText) "Color: white" else "Color: black")
                    }
                    Button(
                        enabled = text.isNotBlank(), modifier = Modifier.weight(1f),
                        onClick = { shareImage(context, renderImage(bitmap, text, typeface, sizePercent, whiteText)) },
                    ) { Text("Share image") }
                }
            }
        }
    }
}

private fun loadBitmap(resolver: android.content.ContentResolver, uri: Uri): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    } else resolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input) }
}.getOrNull()

private fun overlayPaint(typeface: Typeface, textSize: Float, white: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    this.textSize = textSize
    color = if (white) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    textAlign = Paint.Align.CENTER
    setShadowLayer(textSize / 18f, 0f, textSize / 30f, if (white) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
}

private fun renderImage(source: Bitmap, text: String, typeface: Typeface, sizePercent: Float, white: Boolean): Bitmap {
    val output = source.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(output).drawText(text, output.width / 2f, output.height * .85f, overlayPaint(typeface, output.height * sizePercent / 100f, white))
    return output
}

private fun shareImage(context: android.content.Context, bitmap: Bitmap) {
    val file = File(context.cacheDir, "font-image-${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Share image"))
}

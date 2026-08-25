package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class TutorialPage(val title: String, val detail: String, val icon: ImageVector)

@Composable
internal fun FeatureTutorial(onFinished: () -> Unit) {
    val pages = listOf(
        TutorialPage("Create your font", "Draw each character once and save your handwriting as a reusable font.", Icons.Filled.TextFields),
        TutorialPage("Use it on an image", "Choose an image and write with the font you created.", Icons.Filled.Image),
        TutorialPage("Fill & mark documents", "Reuse your text, signatures, and stamps on PDFs or images.", Icons.Filled.Description),
    )
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = pages[pageIndex]
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onFinished) { Text("Skip") } }
            Spacer(Modifier.weight(1f))
            Surface(
                modifier = Modifier.size(144.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(44.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(page.icon, contentDescription = null, modifier = Modifier.size(76.dp)) } }
            Spacer(Modifier.height(32.dp))
            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(page.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(progress = (pageIndex + 1).toFloat() / pages.size, modifier = Modifier.fillMaxWidth().height(4.dp))
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (pageIndex == pages.lastIndex) onFinished() else pageIndex++ },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (pageIndex == pages.lastIndex) "Get started" else "Next") }
        }
    }
}

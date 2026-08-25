package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class DashboardAction(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val click: () -> Unit,
)

@Composable
internal fun DashboardScreen(
    vm: FontCreatorViewModel,
    openSettings: () -> Unit,
    createFont: () -> Unit,
    continueFont: (Int) -> Unit,
    useFontOnImage: (String) -> Unit,
    openFillMark: () -> Unit,
    openFonts: () -> Unit,
    openSignatures: () -> Unit,
    openStamps: () -> Unit,
) = Page(
    "Studio",
    actions = {
        IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    },
) {
    val unfinishedIndex = vm.projects.indexOfFirst { !vm.isProjectComplete(it) }.takeIf { it >= 0 }
    val preferredFont = vm.activeProject?.name
        ?: vm.projects.lastOrNull()?.name
        ?: vm.importedFonts.firstOrNull()?.displayName
    val hasAnyFont = vm.projects.isNotEmpty() || vm.importedFonts.isNotEmpty()

    Text("Create and use", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    when {
        !hasAnyFont -> DashboardHero(
            title = "Create your first font",
            detail = "Draw your characters once, then reuse them on images.",
            icon = Icons.Filled.TextFields,
            click = createFont,
        )
        unfinishedIndex != null -> {
            val project = vm.projects[unfinishedIndex]
            DashboardHero(
                title = "Continue drawing",
                detail = "${project.name} · ${project.drawings.size} of ${vm.characterCount(project)} characters",
                icon = Icons.Filled.Edit,
                click = { continueFont(unfinishedIndex) },
            )
        }
        preferredFont != null -> DashboardHero(
            title = "Use your font on an image",
            detail = preferredFont,
            icon = Icons.Filled.Image,
            click = { useFontOnImage(preferredFont) },
        )
    }

    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = openFillMark)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Fill & Mark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Add text, signatures, or stamps to a document", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Text("Your assets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    val assets = listOf(
        DashboardAction("Fonts", "${vm.projects.size + vm.importedFonts.size} saved", Icons.Filled.TextFields, openFonts),
        DashboardAction("Signatures", "${vm.signatures.count { it.imageFileName == null }} saved", Icons.Filled.Draw, openSignatures),
        DashboardAction("Stamps", "${vm.signatures.count { it.imageFileName != null }} saved", Icons.Filled.Approval, openStamps),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(assets, key = { it.title }) { action ->
            OutlinedCard(Modifier.fillMaxWidth().aspectRatio(.82f).clickable(onClick = action.click)) {
                Column(
                    Modifier.fillMaxSize().padding(10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(action.icon, contentDescription = null, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(action.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(action.detail, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DashboardHero(title: String, detail: String, icon: ImageVector, click: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = click),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) } }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

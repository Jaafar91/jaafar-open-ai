package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun StampsModuleScreen(vm: FontCreatorViewModel, back: () -> Unit, useInDocument: (String) -> Unit) {
    var importing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SavedSignature?>(null) }
    if (importing) {
        ImportStampFromImageScreen(vm = vm, onSaved = { importing = false }, back = { importing = false })
        return
    }
    val stamps = vm.signatures.filter { it.imageFileName != null }
    Page(
        "Stamps",
        back,
        // Matches iOS's rule: the "+" only shows once there's something to add to -- an
        // empty list already has its own prominent action below, so showing both would be
        // a redundant second way to do the same thing.
        actions = {
            if (stamps.isNotEmpty()) {
                IconButton(onClick = { importing = true }) { ActionIcon(ActionIconType.Add, "Add stamp") }
            }
        },
    ) {
        if (stamps.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Filled.Approval, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text("No stamps yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Import an image once and reuse it on documents.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { importing = true }) { Text("Add stamp") }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stamps, key = { it.name }) { stamp ->
                    SavedMarkCard(stamp, vm.defaultStampName == stamp.name) { selected = stamp }
                }
            }
        }
    }
    selected?.let { mark -> SavedMarkActionsSheet(vm, mark, useInDocument) { selected = null } }
}

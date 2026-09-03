package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun SignaturesModuleScreen(vm: FontCreatorViewModel, back: () -> Unit, useInDocument: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SavedSignature?>(null) }
    if (creating) {
        SignatureEditorScreen(vm = vm, onSaved = { creating = false }, back = { creating = false })
        return
    }
    val signatures = vm.signatures.filter { it.imageFileName == null }
    Page(
        "Signatures",
        back,
        // Matches iOS's rule: the "+" only shows once there's something to add to -- an
        // empty list already has its own prominent action below, so showing both would be
        // a redundant second way to do the same thing.
        actions = {
            if (signatures.isNotEmpty()) {
                IconButton(onClick = { creating = true }) { ActionIcon(ActionIconType.Add, "Create signature") }
            }
        },
    ) {
        if (signatures.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text("No signatures yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Draw one once and reuse it whenever you sign a document.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { creating = true }) { Text("Create signature") }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(signatures, key = { it.name }) { signature ->
                    SavedMarkCard(signature, vm.defaultSignatureName == signature.name) { selected = signature }
                }
            }
        }
    }
    selected?.let { mark -> SavedMarkActionsSheet(vm, mark, useInDocument) { selected = null } }
}

package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
    var editing by remember { mutableStateOf<SavedSignature?>(null) }
    if (creating) {
        SignatureEditorScreen(vm = vm, onSaved = { creating = false }, back = { creating = false })
        return
    }
    // Opened by tapping a row below -- rename, redraw, and "Use in Fill & Mark" all live here
    // now, instead of a separate action dialog over the list.
    editing?.let { mark ->
        SignatureEditorScreen(
            vm = vm,
            existing = mark,
            onSaved = { editing = null },
            useInFillMark = { name -> editing = null; useInDocument(name) },
            back = { editing = null },
        )
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
                    SignatureRow(vm, signature) { editing = signature }
                }
            }
        }
    }
}

/**
 * One signature row: delete only, no inline rename -- tapping the row opens the signature
 * editor, where the name (and the drawing itself) can be changed, and from which it can be
 * sent straight into Fill & Mark.
 */
@Composable
private fun SignatureRow(vm: FontCreatorViewModel, mark: SavedSignature, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignaturePreview(Modifier.size(88.dp, 56.dp), mark)
            Text(mark.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.deleteSignature(mark.name) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${mark.name}")
            }
        }
    }
}

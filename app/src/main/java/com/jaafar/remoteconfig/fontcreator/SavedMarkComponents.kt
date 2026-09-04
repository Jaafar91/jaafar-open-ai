package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One saved signature/stamp row. Tapping the row itself goes straight to signing/stamping a
 * document with it -- no intermediate "what do you want to do with this" dialog. Rename and
 * delete are inline icons on the row instead, and there's no "make default" action: the last
 * one actually used already becomes the default automatically (see FillMarkScreen's placement
 * logic), so a manual toggle here was a redundant second way to set the same thing.
 */
@Composable
internal fun SavedMarkCard(vm: FontCreatorViewModel, mark: SavedSignature, useInDocument: (String) -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember(mark.name) { mutableStateOf(mark.name) }
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename ${if (mark.imageFileName == null) "signature" else "stamp"}") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = { if (vm.renameSignature(mark.name, name)) renaming = false }, enabled = name.isNotBlank()) {
                    Text("Rename")
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    OutlinedCard(Modifier.fillMaxWidth().clickable { useInDocument(mark.name) }) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignaturePreview(Modifier.size(88.dp, 56.dp), mark)
            Text(mark.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { renaming = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename ${mark.name}")
            }
            IconButton(onClick = { vm.deleteSignature(mark.name) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${mark.name}")
            }
        }
    }
}

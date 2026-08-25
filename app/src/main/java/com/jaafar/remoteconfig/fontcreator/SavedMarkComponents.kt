package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SavedMarkCard(mark: SavedSignature, isDefault: Boolean, click: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = click)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignaturePreview(Modifier.size(88.dp, 56.dp), mark)
            Column {
                Text(mark.name, style = MaterialTheme.typography.titleMedium)
                if (isDefault) Text("Default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedMarkActionsSheet(
    vm: FontCreatorViewModel,
    mark: SavedSignature,
    useInDocument: (String) -> Unit,
    dismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember(mark.name) { mutableStateOf(mark.name) }
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename ${if (mark.imageFileName == null) "signature" else "stamp"}") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = { if (vm.renameSignature(mark.name, name)) { renaming = false; dismiss() } }, enabled = name.isNotBlank()) {
                    Text("Rename")
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SignaturePreview(Modifier.fillMaxWidth().height(130.dp), mark)
            Text(mark.name, style = MaterialTheme.typography.titleMedium)
            Button(onClick = { useInDocument(mark.name) }, modifier = Modifier.fillMaxWidth()) { Text("Use in a document") }
            OutlinedButton(
                onClick = {
                    if (mark.imageFileName == null) vm.setDefaultSignature(mark.name) else vm.setDefaultStamp(mark.name)
                    dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Make default") }
            OutlinedButton(onClick = { renaming = true }, modifier = Modifier.fillMaxWidth()) { Text("Rename") }
            TextButton(onClick = { vm.deleteSignature(mark.name); dismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
        }
    }
}

package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    Page("Signatures", back) {
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Create signature") }
        if (signatures.isEmpty()) Text("No signatures yet. Draw one once and reuse it whenever you sign a document.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(signatures, key = { it.name }) { signature ->
                SavedMarkCard(signature, vm.defaultSignatureName == signature.name) { selected = signature }
            }
        }
    }
    selected?.let { mark -> SavedMarkActionsSheet(vm, mark, useInDocument) { selected = null } }
}

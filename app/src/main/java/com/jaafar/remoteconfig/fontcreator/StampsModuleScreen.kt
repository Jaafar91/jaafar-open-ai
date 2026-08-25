package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    Page("Stamps", back) {
        Button(onClick = { importing = true }, modifier = Modifier.fillMaxWidth()) { Text("Add stamp") }
        if (stamps.isEmpty()) Text("No stamps yet. Import an image once and reuse it on documents.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(stamps, key = { it.name }) { stamp ->
                SavedMarkCard(stamp, vm.defaultStampName == stamp.name) { selected = stamp }
            }
        }
    }
    selected?.let { mark -> SavedMarkActionsSheet(vm, mark, useInDocument) { selected = null } }
}

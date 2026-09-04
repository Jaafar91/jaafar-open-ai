package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One saved signature/stamp row: delete only, no inline rename -- tapping the row opens that
 * mark's own editor instead (rename, redraw/replace the image, and "Use in Fill & Mark" all
 * live there), matching how both Signatures and Stamps handle this the same way.
 */
@Composable
internal fun SavedMarkCard(vm: FontCreatorViewModel, mark: SavedSignature, onClick: () -> Unit) {
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

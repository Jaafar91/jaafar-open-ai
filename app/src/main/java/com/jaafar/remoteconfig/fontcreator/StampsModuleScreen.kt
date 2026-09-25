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
internal fun StampsModuleScreen(
    vm: FontCreatorViewModel,
    back: () -> Unit,
    useInDocument: (String) -> Unit,
    // True when Fill & Mark sent the customer here because they had no saved stamp yet -- jumps
    // straight into the import screen (skipping the list they'd otherwise see first, which would
    // always be empty anyway) and, once saved, returns straight to Fill & Mark with it already
    // placed instead of just closing back to a list.
    autoCreate: Boolean = false,
) {
    var importing by remember { mutableStateOf(autoCreate) }
    var editing by remember { mutableStateOf<SavedSignature?>(null) }
    var showProDialog by remember { mutableStateOf(false) }
    // Already at the free plan's 1-stamp cap -- go straight to the Pro pitch instead of opening
    // the import screen just to disable its Save button once they get there.
    val startImporting = { if (vm.hasReachedFreeStampLimit) showProDialog = true else importing = true }
    if (showProDialog) {
        ProFeaturesDialog(vm = vm) { showProDialog = false }
    }
    if (importing) {
        ImportStampFromImageScreen(
            vm = vm,
            onSaved = { name -> if (autoCreate) useInDocument(name) else importing = false },
            back = { if (autoCreate) back() else importing = false },
        )
        return
    }
    // Opened by tapping a row below -- rename, replace the image, and "Use in Fill & Mark" all
    // live here now, instead of a separate action dialog over the list.
    editing?.let { mark ->
        ImportStampFromImageScreen(
            vm = vm,
            existing = mark,
            onSaved = { editing = null },
            useInFillMark = { name -> editing = null; useInDocument(name) },
            back = { editing = null },
        )
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
                IconButton(onClick = startImporting) { ActionIcon(ActionIconType.Add, "Add stamp") }
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
                Button(onClick = startImporting) { Text("Add stamp") }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(stamps, key = { it.name }) { stamp ->
                    SavedMarkCard(vm, stamp) { editing = stamp }
                }
            }
        }
    }
}

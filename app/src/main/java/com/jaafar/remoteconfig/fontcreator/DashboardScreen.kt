package com.jaafar.remoteconfig.fontcreator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class DashboardAction(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val click: () -> Unit,
)

/** A square, icon-over-title-over-detail card -- the "asset" tile style from Home's "Your
 *  assets" row. Shared (not Home-only) so any screen wanting that same at-a-glance-tappable look
 *  gets it pixel-identical instead of a close approximation. */
@Composable
internal fun AssetStyleCard(title: String, detail: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(modifier.aspectRatio(.82f).clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
}

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
) {
    var showProDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Page(
        "Studio",
        actions = {
            IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
        // Scrollable so the extra Pro tile below the assets can't be clipped on a short screen.
        scrollable = true,
    ) {
    // A Home banner, not a dialog popping up the moment a share completes -- that collided with
    // the native share sheet still closing. Shown right at the top so it's seen without
    // scrolling; dismissing only hides it for this session (see dismissRatingPromptForNow), so
    // it's still there next time the app is opened, not just the one time it first appeared.
    if (vm.showRatingPrompt) {
        RatingPromptBanner(
            onRate = { openPlayStoreListing(context); vm.markRatingPromptAnswered() },
            onDismiss = vm::dismissRatingPromptForNow,
        )
    }
    // Picked by lastModifiedAt, matching the iOS app's equivalent defaults -- createProject
    // appends new projects at the end of the list, so indexOfFirst/lastOrNull only ever
    // reflected creation order and went stale the moment an *older* project was edited
    // instead of a brand new one being added.
    val unfinishedIndex = vm.projects.withIndex()
        .filter { (_, project) -> !vm.isProjectComplete(project) }
        .maxByOrNull { (_, project) -> project.lastModifiedAt }
        ?.index
    val preferredFont = vm.activeProject?.name
        ?: vm.projects.maxByOrNull { it.lastModifiedAt }?.name
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
            val total = vm.characterCount(project).coerceAtLeast(1)
            val percentage = (project.drawings.size * 100 / total).coerceIn(0, 100)
            DashboardHero(
                title = "Continue ${project.name}",
                detail = "$percentage% complete · Nice progress—keep going!",
                icon = Icons.Filled.Edit,
                click = { continueFont(unfinishedIndex) },
            )
        }
    }

    if (preferredFont != null) {
        DashboardRowAction(
            title = "Use font on image",
            detail = "Write with your font style on a photo",
            icon = Icons.Filled.Image,
            click = { useFontOnImage(preferredFont) },
        )
    }
    DashboardRowAction(
        title = "Fill & Mark",
        detail = "Add text, signatures, or stamps to a document",
        icon = Icons.Filled.Description,
        click = openFillMark,
    )

    Text("Your assets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    val assets = listOf(
        DashboardAction("Fonts", "${vm.projects.size + vm.importedFonts.size} saved", Icons.Filled.TextFields, openFonts),
        DashboardAction("Signatures", "${vm.signatures.count { it.imageFileName == null }} saved", Icons.Filled.Draw, openSignatures),
        DashboardAction("Stamps", "${vm.signatures.count { it.imageFileName != null }} saved", Icons.Filled.Approval, openStamps),
    )
    // A fixed 3-item row (Fonts/Signatures/Stamps), not a growing list -- a plain Row sizes to
    // its own content instead of a Lazy grid's weight(1f) claiming all leftover height and
    // leaving a large gap above the pinned Pro tile below.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        assets.forEach { action ->
            AssetStyleCard(action.title, action.detail, action.icon, Modifier.weight(1f), action.click)
        }
    }
    // Sits directly under the assets (not pinned to the screen bottom): a pinned tile leaves a
    // tall empty gap above it on any phone taller than the content.
    if (!vm.isPro) {
        ProUpgradeBanner(onClick = { showProDialog = true })
    }
    }
    if (showProDialog) {
        ProFeaturesDialog(vm = vm) { showProDialog = false }
    }
}

/** Shown on Home once the customer has had a few successful shares (see
 *  [FontCreatorViewModel.showRatingPrompt]) -- reuses the same Play Store listing Settings' own
 *  "Rate this app" button opens, just surfaced at a moment they just had a good experience
 *  instead of only when they went looking for it. */
@Composable
private fun RatingPromptBanner(onRate: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Enjoying Font Maker?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("A quick rating on Google Play helps a lot.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRate, contentPadding = PaddingValues(vertical = 4.dp)) { Text("Rate now") }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
        }
    }
}

/** Home's own entry point into the same "upgrade to Pro" journey shown at every free-plan cap --
 *  discoverable on its own, not only after hitting a limit. Hidden once already Pro. */
@Composable
private fun ProUpgradeBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Font Maker Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Unlock unlimited fonts, signatures, stamps & exports", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

/** An icon-and-two-lines-of-text row card -- Home's style for a full-width tappable action. */
@Composable
internal fun DashboardRowAction(title: String, detail: String, icon: ImageVector, click: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = click)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The big primary-colored "main call to action" card -- Home's style for the one most
 *  prominent action on a screen. */
@Composable
internal fun DashboardHero(title: String, detail: String, icon: ImageVector, click: () -> Unit) {
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

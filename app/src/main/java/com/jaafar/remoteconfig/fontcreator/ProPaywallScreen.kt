package com.jaafar.remoteconfig.fontcreator

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val PRO_BENEFITS = listOf(
    "Unlimited saved fonts",
    "Unlimited saved signatures",
    "Unlimited saved stamps",
    "Use your font on image",
    "Fill & Mark documents",
)

/**
 * Shown instead of a locked feature (Use font on image, Fill & Mark) or reachable any time from
 * Settings. [lockedFeature], when set, names the specific feature that led here, so the pitch
 * reads as "you need Pro for this" rather than a generic upsell.
 */
@Composable
internal fun ProPaywallScreen(vm: FontCreatorViewModel, lockedFeature: String? = null, back: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val priceLabel = vm.billing.proPriceLabel
    Page("Font Maker Pro", back) {
        Text(
            if (lockedFeature != null) "$lockedFeature is a Pro feature" else "Unlock Font Maker Pro",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "One-time purchase, yours forever -- no subscription.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PRO_BENEFITS.forEach { benefit ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(benefit, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Button(
            onClick = { activity?.let(vm.billing::launchPurchase) },
            modifier = Modifier.fillMaxWidth(),
            enabled = activity != null && priceLabel != null,
        ) {
            Text(if (priceLabel != null) "Upgrade to Pro — $priceLabel" else "Loading price…")
        }
        Text(
            "Already purchased? Reopening this screen restores it automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

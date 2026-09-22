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
    "Unlimited use of Use font on image",
    "Unlimited use of Fill & Mark",
)

/**
 * A one-tap "Upgrade to Pro" button that launches the real purchase flow right where a free-plan
 * cap was just hit (a creation dialog, a save screen) -- so upgrading is part of what the user
 * was already doing instead of a detour through Settings. Mirrors ProPaywallScreen's own button.
 */
@Composable
internal fun UpgradeToProButton(vm: FontCreatorViewModel, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as? Activity
    val priceLabel = vm.billing.proPriceLabel
    Button(onClick = { activity?.let(vm.billing::launchPurchase) }, modifier = modifier, enabled = activity != null) {
        Text(if (priceLabel != null) "Upgrade to Pro — $priceLabel" else "Upgrade to Pro")
    }
}

/**
 * Shown once a locked feature's free monthly quota (Use font on image, Fill & Mark -- see
 * FontCreatorViewModel.FREE_MONTHLY_FEATURE_EXPORTS) is used up, or reachable any time from
 * Settings. [lockedFeature], when set, names the specific feature that led here, so the pitch
 * reads as "you've used this month's free X" rather than a generic upsell.
 */
@Composable
internal fun ProPaywallScreen(vm: FontCreatorViewModel, lockedFeature: String? = null, back: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val priceLabel = vm.billing.proPriceLabel
    Page("Font Maker Pro", back) {
        Text(
            if (lockedFeature != null) {
                "You've used this month's ${FontCreatorViewModel.FREE_MONTHLY_FEATURE_EXPORTS} free \"$lockedFeature\" exports"
            } else {
                "Unlock Font Maker Pro"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (lockedFeature != null) {
            Text(
                "More free exports next month, or upgrade to Pro for unlimited, right now.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
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

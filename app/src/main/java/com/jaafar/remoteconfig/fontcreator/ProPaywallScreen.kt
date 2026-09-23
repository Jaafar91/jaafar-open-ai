package com.jaafar.remoteconfig.fontcreator

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
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
 * The single "upgrade to Pro" dialog used everywhere a Pro-gated action is blocked (creating a
 * font, importing one, saving a signature/stamp, the monthly export quota on Use font on
 * image/Fill & Mark) as well as its own entry points (Settings, the dashboard's Pro card) --
 * one consistent pitch and purchase flow instead of a different treatment per call site.
 * [lockedFeature], when set, names the specific feature that led here, so the pitch reads as
 * "you've used this month's free X" rather than a generic upsell. Auto-dismisses once the
 * purchase actually completes (isPro flips), so a caller stacking this on top of its own dialog
 * (e.g. CreateFontDialog) naturally returns the user to that still-open dialog, now unlocked.
 */
@Composable
internal fun ProFeaturesDialog(vm: FontCreatorViewModel, lockedFeature: String? = null, onDismiss: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val priceLabel = vm.billing.proPriceLabel
    LaunchedEffect(vm.isPro) { if (vm.isPro) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font Maker Pro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (lockedFeature != null) {
                        "You've used this month's ${FontCreatorViewModel.FREE_MONTHLY_FEATURE_EXPORTS} free \"$lockedFeature\" exports. " +
                            "More next month, or unlock unlimited right now."
                    } else {
                        "Unlock unlimited fonts, signatures, stamps, and exports -- one-time purchase, yours forever, no subscription."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRO_BENEFITS.forEach { benefit ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(benefit, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { activity?.let(vm.billing::launchPurchase) },
                enabled = activity != null && priceLabel != null,
            ) {
                Text(if (priceLabel != null) "Upgrade to Pro — $priceLabel" else "Loading price…")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

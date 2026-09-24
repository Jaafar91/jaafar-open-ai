package com.jaafar.remoteconfig.fontcreator

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/** One-time, non-consumable "Pro" unlock: removes the free tier's 1-font/1-signature/1-stamp
 *  caps and unlocks "Use font on image" and "Fill & Mark". Must match the in-app product ID
 *  created in Play Console (Monetize -> Products -> In-app products) exactly. */
internal const val PRO_PRODUCT_ID = "pro_unlock"

/**
 * Owns the Play Billing connection and this app's one Pro entitlement. Play is the source of
 * truth: [isPro] is overwritten by every successful [queryPurchasesAsync] result (app launch and
 * every [refreshPurchases] call from onResume), so a refund or a purchase made on another device
 * is picked up. The last confirmed value is cached in SharedPreferences purely as the starting
 * value, so a paying user isn't treated as free before Play answers, while offline, or on a device
 * without Play services -- only a *successful* Play response ever changes it (a failed/absent one
 * never revokes Pro).
 */
internal class BillingManager(application: Application) {
    private val prefs = application.getSharedPreferences("billing", Context.MODE_PRIVATE)

    var isPro by mutableStateOf(prefs.getBoolean(KEY_IS_PRO, false))
        private set

    /** User-facing reason the last billing action failed or Play is unavailable; null if none. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Play-formatted price ("$4.99" etc.) for the paywall, once product details load. This is
     *  the cheapest offer the user is currently eligible for (a discount offer if there is one). */
    var proPriceLabel by mutableStateOf<String?>(null)
        private set

    /** Regular (undiscounted) price, only set when [proPriceLabel] is a genuine discount from it,
     *  so the paywall can show it crossed out. */
    var proOriginalPriceLabel by mutableStateOf<String?>(null)
        private set

    /** Offer token of the offer priced at [proPriceLabel] -- what launchPurchase buys. */
    private var proOfferToken: String? = null

    private var proProductDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> purchases?.forEach(::handlePurchase)
            BillingResponseCode.USER_CANCELED -> Unit
            // Already bought (e.g. reinstall before the launch query finished): just re-sync.
            BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> message = "The purchase didn't go through. You haven't been charged."
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(application)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingResponseCode.OK) {
                    message = null
                    queryProProductDetails()
                    refreshPurchases()
                } else {
                    message = "Google Play isn't available right now, so Pro can't be purchased."
                }
            }

            // enableAutoServiceReconnection() above handles retrying the connection itself;
            // nothing to do here.
            override fun onBillingServiceDisconnected() {}
        })
    }

    /** Re-checks ownership directly with Play -- call from onResume too, so a purchase
     *  completed elsewhere (or a refund) while this screen wasn't in the foreground is
     *  picked up without needing to relaunch the app. */
    fun refreshPurchases() {
        if (!billingClient.isReady) return
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                updateEntitlement(
                    purchases.any {
                        it.products.contains(PRO_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
                    },
                )
                purchases.forEach(::handlePurchase)
            }
        }
    }

    private fun queryProProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingResponseCode.OK) {
                val details = result.productDetailsList.firstOrNull()
                proProductDetails = details
                // The list holds the regular purchase option plus any discount offer this user
                // is eligible for -- the app must pick the cheapest, not just the first.
                val offers = details?.oneTimePurchaseOfferDetailsList.orEmpty()
                val best = offers.minByOrNull { it.priceAmountMicros }
                val regular = offers.maxByOrNull { it.priceAmountMicros }
                proOfferToken = best?.offerToken
                proPriceLabel = best?.formattedPrice
                proOriginalPriceLabel =
                    if (best != null && regular != null && regular.priceAmountMicros > best.priceAmountMicros) regular.formattedPrice else null
                if (details == null) message = "Pro isn't available to buy right now. Please try again later."
            } else {
                message = "Couldn't load the Pro price from Google Play."
            }
        }
    }

    /** Starts the Play purchase UI for the Pro unlock. No-ops (rather than crashing) if
     *  product details haven't loaded yet -- callers should keep the buy button disabled
     *  until [proPriceLabel] is non-null, but a stale tap during that window should be safe. */
    fun launchPurchase(activity: Activity) {
        val details = proProductDetails
        val offerToken = proOfferToken
        if (details == null || offerToken == null) {
            message = "Pro isn't available to buy right now. Please try again later."
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode != BillingResponseCode.OK) message = "Couldn't open Google Play checkout."
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return
        updateEntitlement(true)
        // Play auto-refunds an unacknowledged purchase after 3 days, so every purchase this
        // listener/query sees must be acknowledged -- there's nothing else to deliver (the
        // unlock is just this flag), so acknowledge immediately rather than deferring it.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            billingClient.acknowledgePurchase(ackParams) { }
        }
    }

    private fun updateEntitlement(value: Boolean) {
        isPro = value
        prefs.edit().putBoolean(KEY_IS_PRO, value).apply()
    }

    private companion object {
        const val KEY_IS_PRO = "is_pro"
    }
}

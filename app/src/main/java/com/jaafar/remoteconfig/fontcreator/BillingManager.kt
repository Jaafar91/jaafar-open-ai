package com.jaafar.remoteconfig.fontcreator

import android.app.Activity
import android.app.Application
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
 * Owns the Play Billing connection and this app's one Pro entitlement. [isPro] is never set
 * from a locally cached flag -- only from what Play itself reports via [queryPurchasesAsync],
 * on every connection (app launch) and every [refreshPurchases] call (app foreground), so a
 * reinstall, a refund, or a purchase completed on another device is always reflected correctly.
 */
internal class BillingManager(application: Application) {
    var isPro by mutableStateOf(false)
        private set

    /** Play-formatted price ("$4.99" etc.) for the paywall, once product details load. */
    var proPriceLabel by mutableStateOf<String?>(null)
        private set

    private var proProductDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingResponseCode.OK) {
            purchases?.forEach(::handlePurchase)
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
                    queryProProductDetails()
                    refreshPurchases()
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
                isPro = purchases.any {
                    it.products.contains(PRO_PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
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
                proPriceLabel = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            }
        }
    }

    /** Starts the Play purchase UI for the Pro unlock. No-ops (rather than crashing) if
     *  product details haven't loaded yet -- callers should keep the buy button disabled
     *  until [proPriceLabel] is non-null, but a stale tap during that window should be safe. */
    fun launchPurchase(activity: Activity) {
        val details = proProductDetails ?: return
        val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken ?: return
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
        billingClient.launchBillingFlow(activity, params)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return
        isPro = true
        // Play auto-refunds an unacknowledged purchase after 3 days, so every purchase this
        // listener/query sees must be acknowledged -- there's nothing else to deliver (the
        // unlock is just this flag), so acknowledge immediately rather than deferring it.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            billingClient.acknowledgePurchase(ackParams) { }
        }
    }
}

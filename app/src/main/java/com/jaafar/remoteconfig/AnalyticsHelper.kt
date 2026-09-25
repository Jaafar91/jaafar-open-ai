package com.jaafar.remoteconfig

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Centralizes "which feature did the customer use" event logging behind one seam, so every call
 * site just reports what happened rather than knowing how it's recorded. Reports to Firebase
 * Analytics (this app's one exception, alongside Play Billing, to being otherwise fully offline
 * -- see CLAUDE.md) and mirrors every event to Logcat for local debugging.
 *
 * Deliberately calls only the plain Java surface -- FirebaseAnalytics.getInstance(context) and
 * .logEvent(String, Bundle) -- rather than the Kotlin "Firebase.analytics" extension property or
 * the logEvent(name) { param(...) } DSL builder. Those are Kotlin-specific sugar with their own
 * compiled metadata, and this project's Kotlin compiler (2.0.21) isn't guaranteed to read
 * metadata from a newer Kotlin version, the same constraint that keeps BillingManager and
 * AppUpdateHelper on the plain (non-KTX) artifacts of their own libraries.
 */
internal fun logFeatureEvent(context: Context, feature: String, params: Map<String, String> = emptyMap()) {
    Log.d("AnalyticsHelper", "feature=$feature" + if (params.isEmpty()) "" else " params=$params")
    val bundle = Bundle()
    params.forEach { (key, value) -> bundle.putString(key, value) }
    FirebaseAnalytics.getInstance(context).logEvent(feature, bundle)
}

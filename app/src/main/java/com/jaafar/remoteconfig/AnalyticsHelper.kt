package com.jaafar.remoteconfig

import android.content.Context
import android.util.Log

/**
 * Centralizes "which feature did the customer use" event logging behind one seam, so every call
 * site just reports what happened rather than knowing how it's recorded. Currently logs locally
 * only (Logcat) -- this app has no analytics backend wired in yet (see CLAUDE.md: fully offline
 * except Play Billing). Swapping the body of [logFeatureEvent] for real Firebase Analytics calls
 * once a google-services.json exists for this app is meant to be the *only* change needed; every
 * call site below should never need touching again.
 */
internal fun logFeatureEvent(context: Context, feature: String, params: Map<String, String> = emptyMap()) {
    Log.d("AnalyticsHelper", "feature=$feature" + if (params.isEmpty()) "" else " params=$params")
    // TODO(firebase): once google-services.json is added and the Firebase Analytics dependency
    // is wired in, replace the line above with something like:
    //   FirebaseAnalytics.getInstance(context).logEvent(feature) {
    //       params.forEach { (key, value) -> param(key, value) }
    //   }
}

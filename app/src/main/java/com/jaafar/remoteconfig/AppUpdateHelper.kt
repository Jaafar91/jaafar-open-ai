package com.jaafar.remoteconfig

import android.app.Activity
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Forces the customer onto the latest Play-published build using Play's own IMMEDIATE in-app
 * update flow -- a full-screen Play UI that blocks app use until the update finishes, unlike the
 * dismissible FLEXIBLE flow. This isn't an independent kill-switch: it only ever reflects
 * whatever version is actually live/rolled out on Play, checked fresh on every resume so it also
 * catches a customer who leaves the app running in the background rather than only a cold start.
 *
 * Not every release deserves this. Play lets each release carry an "in-app update priority" (an
 * integer 0-5, set at upload time -- e.g. via the r0adkll/upload-google-play GitHub Action's
 * inAppUpdatePriority input, or the Play Developer Publishing API directly) which this app reads
 * back via [AppUpdateInfo.updatePriority]. Only a release published at or above
 * [FORCE_UPDATE_MIN_PRIORITY] forces anyone; anything below that (the default for a routine
 * release with no priority set) is silently skipped -- the customer updates whenever they next
 * visit the Play Store or their device auto-updates it, same as any other app.
 */
internal class AppUpdateHelper(private val activity: Activity) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    /** Starts (or re-shows, if one was interrupted) the forced update flow when Play has a newer,
     *  high-enough-priority build available. Safe to call on every resume -- a no-op once the
     *  customer is current, or once the available update is below the force threshold. */
    fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.appUpdateInfo
            .addOnSuccessListener { info -> maybeStartImmediateUpdate(info, launcher) }
            .addOnFailureListener { e -> Log.w("AppUpdateHelper", "appUpdateInfo lookup failed", e) }
    }

    private fun maybeStartImmediateUpdate(info: AppUpdateInfo, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val shouldForceUpdate = when (info.updateAvailability()) {
            // updatePriority() is only defined once availability is UPDATE_AVAILABLE (per Play's
            // own docs) -- this is the one place the threshold is actually decided.
            UpdateAvailability.UPDATE_AVAILABLE -> info.updatePriority() >= FORCE_UPDATE_MIN_PRIORITY
            // Covers an IMMEDIATE flow that was already started and then interrupted (e.g. the OS
            // reclaimed the app while the customer was on Play's update screen) -- its priority
            // already cleared the threshold once to get this far, and updatePriority() isn't
            // guaranteed meaningful in this state, so just resume it rather than re-checking.
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> true
            else -> false
        }
        if (!shouldForceUpdate || !info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return
        try {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        } catch (e: Exception) {
            Log.w("AppUpdateHelper", "Failed to start immediate update flow", e)
        }
    }

    private companion object {
        // Play's priority scale tops out at 5. Reserving 4-5 for "force it" (a critical/breaking
        // release) and leaving 0-3 -- including the unset default of 0 -- to pass through
        // unforced keeps every routine release from blocking testers/customers by accident; a
        // release only becomes forced when someone deliberately publishes it at this priority.
        const val FORCE_UPDATE_MIN_PRIORITY = 4
    }
}

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
 */
internal class AppUpdateHelper(private val activity: Activity) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    /** Starts (or re-shows, if one was interrupted) the forced update flow when Play has a newer
     *  build available. Safe to call on every resume -- a no-op once the customer is current. */
    fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.appUpdateInfo
            .addOnSuccessListener { info -> maybeStartImmediateUpdate(info, launcher) }
            .addOnFailureListener { e -> Log.w("AppUpdateHelper", "appUpdateInfo lookup failed", e) }
    }

    private fun maybeStartImmediateUpdate(info: AppUpdateInfo, launcher: ActivityResultLauncher<IntentSenderRequest>) {
        // DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS covers an IMMEDIATE flow that was interrupted
        // (e.g. the OS reclaimed the app while the customer was on Play's update screen) --
        // re-launching it resumes rather than restarts, so it isn't shown to them twice.
        val shouldForceUpdate = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        if (!shouldForceUpdate || !info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) return
        try {
            manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        } catch (e: Exception) {
            Log.w("AppUpdateHelper", "Failed to start immediate update flow", e)
        }
    }
}

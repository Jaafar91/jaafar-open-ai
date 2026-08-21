package com.jaafar.remoteconfig

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.jaafar.remoteconfig.fontcreator.FontCreatorApp
import com.jaafar.remoteconfig.fontcreator.FontCreatorViewModel

class MainActivity : ComponentActivity() {
    private var sharedUri by mutableStateOf<Uri?>(null)
    private var shareRequestId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[FontCreatorViewModel::class.java]
        sharedUri = extractSharedDocumentUri(intent)
        setContent { FontCreatorApp(viewModel, sharedUri, shareRequestId) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedDocumentUri(intent)?.let { uri ->
            sharedUri = uri
            shareRequestId += 1
        }
    }

    /**
     * Returns the first PDF or image received from a share or edit request.
     * Google Photos may use either a stream, ClipData, or the intent data URI.
     */
    private fun extractSharedDocumentUri(intent: Intent?): Uri? {
        val action = intent?.action
        val isShare = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
        val isImageEdit = action == Intent.ACTION_EDIT ||
            action == "com.google.android.apps.photos.OEM_EDIT"
        if (!isShare && !isImageEdit) return null

        val streamUri = when (action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        ?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        ?.firstOrNull()
                }
            }
            else -> null
        }
        val uri = streamUri ?: intent.clipData?.getItemAt(0)?.uri ?: intent.data ?: return null
        val mimeType = intent.type ?: contentResolver.getType(uri)
        return if (mimeType == "application/pdf" || mimeType?.startsWith("image/") == true) uri else null
    }

}

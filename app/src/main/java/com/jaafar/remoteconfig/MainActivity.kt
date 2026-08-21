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

    /** Returns a PDF or image URI received via the Android Share sheet, or null. */
    private fun extractSharedDocumentUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val mimeType = intent.type ?: return null
        if (mimeType != "application/pdf" && !mimeType.startsWith("image/")) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

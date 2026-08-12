package com.jaafar.remoteconfig

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val BACKEND_URL = "https://jaafar-agents.onrender.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var message by remember { mutableStateOf("Loading remote configuration…") }
            var enabled by remember { mutableStateOf(true) }
            var loading by remember { mutableStateOf(false) }
            val repository = remember { RemoteConfigRepository(BACKEND_URL) }
            fun refresh() {
                loading = true
                repository.fetch { result ->
                    runOnUiThread {
                        result.onSuccess { config -> message = config.message; enabled = config.enabled }
                            .onFailure { message = "Could not load configuration. Check the backend URL." }
                        loading = false
                    }
                }
            }
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (enabled) message else "This feature is currently disabled.", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = ::refresh, enabled = !loading, modifier = Modifier.padding(top = 20.dp)) {
                        Text(if (loading) "Refreshing…" else "Refresh configuration")
                    }
                }
            }
        }
    }
}

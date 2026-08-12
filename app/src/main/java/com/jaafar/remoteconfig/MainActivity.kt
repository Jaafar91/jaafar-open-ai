package com.jaafar.remoteconfig

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
                        result.onSuccess { config ->
                            message = config.message
                            enabled = config.enabled
                        }.onFailure {
                            message = "Could not load configuration. Check the backend URL."
                        }
                        loading = false
                    }
                }
            }

            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (enabled) message else "This feature is currently disabled.",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )

                    Button(onClick = ::refresh, enabled = !loading) {
                        Text(if (loading) "Refreshing…" else "Refresh configuration")
                    }

                    if (enabled) {
                        Calculator()
                    }
                }
            }
        }
    }
}

@Composable
private fun Calculator() {
    var display by remember { mutableStateOf("0") }
    var storedValue by remember { mutableStateOf<Double?>(null) }
    var pendingOperation by remember { mutableStateOf<String?>(null) }
    var enteringNewNumber by remember { mutableStateOf(true) }

    fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    fun applyOperation(operation: String) {
        val currentValue = display.toDoubleOrNull() ?: return
        val previousValue = storedValue
        if (previousValue != null && pendingOperation != null) {
            val result = when (pendingOperation) {
                "+" -> previousValue + currentValue
                "−" -> previousValue - currentValue
                "×" -> previousValue * currentValue
                "÷" -> if (currentValue == 0.0) null else previousValue / currentValue
                else -> currentValue
            }
            if (result == null) {
                display = "Error"
                storedValue = null
                pendingOperation = null
                enteringNewNumber = true
                return
            }
            display = format(result)
            storedValue = result
        } else {
            storedValue = currentValue
        }
        pendingOperation = operation
        enteringNewNumber = true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Calculator",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = display,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.End,
        )

        listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "="),
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { label ->
                    val isOperation = label in setOf("÷", "×", "−", "+")
                    val isPrimary = label == "="
                    val modifier = Modifier.weight(if (label == "0") 2f else 1f)

                    if (isPrimary) {
                        Button(
                            onClick = {
                                val operation = pendingOperation
                                if (operation != null) {
                                    applyOperation(operation)
                                    pendingOperation = null
                                }
                            },
                            modifier = modifier,
                        ) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                when {
                                    label == "C" -> {
                                        display = "0"
                                        storedValue = null
                                        pendingOperation = null
                                        enteringNewNumber = true
                                    }
                                    label == "±" -> {
                                        display.toDoubleOrNull()?.let { display = format(-it) }
                                    }
                                    label == "%" -> {
                                        display.toDoubleOrNull()?.let { display = format(it / 100) }
                                    }
                                    isOperation -> applyOperation(label)
                                    label == "." -> {
                                        if (enteringNewNumber || display == "Error") {
                                            display = "0."
                                            enteringNewNumber = false
                                        } else if (!display.contains(".")) {
                                            display += "."
                                        }
                                    }
                                    else -> {
                                        display = if (enteringNewNumber || display == "0" || display == "Error") {
                                            label
                                        } else {
                                            display + label
                                        }
                                        enteringNewNumber = false
                                    }
                                }
                            },
                            modifier = modifier,
                        ) {
                            Text(label)
                        }
                    }
                }
                repeat(4 - row.size - if (row.contains("0")) 1 else 0) {
                    Row(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

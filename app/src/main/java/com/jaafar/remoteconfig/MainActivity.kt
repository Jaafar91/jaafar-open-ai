package com.jaafar.remoteconfig

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

            LaunchedEffect(repository) {
                refresh()
            }

            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Jaafar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (enabled) message else "This feature is currently disabled.",
                        style = MaterialTheme.typography.bodyLarge,
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
    val titleMedium = MaterialTheme.typography.titleMedium
    val colorScheme = MaterialTheme.colorScheme
    var display by remember { mutableStateOf("0") }
    var storedValue by remember { mutableStateOf<Double?>(null) }
    var pendingOperation by remember { mutableStateOf<String?>(null) }
    var enteringNewNumber by remember { mutableStateOf(true) }

    fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    fun calculate(operation: String) {
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Calculator",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = pendingOperation ?: "Ready",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = display,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }

        listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "="),
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { label ->
                    val isOperation = label in setOf("÷", "×", "−", "+")
                    val isPrimary = label == "="
                    val onClick: () -> Unit = {
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
                            isOperation -> calculate(label)
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
                    }

                    when {
                        isPrimary -> Button(
                            onClick = {
                                val operation = pendingOperation
                                if (operation != null) {
                                    calculate(operation)
                                    pendingOperation = null
                                }
                            },
                            modifier = Modifier
                                .width(72.dp)
                                .height(56.dp),
                        ) {
                            Text(label, style = titleMedium)
                        }
                        isOperation -> Button(
                            onClick = onClick,
                            modifier = Modifier
                                .width(72.dp)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.secondary,
                                contentColor = colorScheme.onSecondary,
                            ),
                        ) {
                            Text(label, style = titleMedium)
                        }
                        label in setOf("C", "±", "%") -> FilledTonalButton(
                            onClick = onClick,
                            modifier = Modifier
                                .width(72.dp)
                                .height(56.dp),
                        ) {
                            Text(label, style = titleMedium)
                        }
                        else -> OutlinedButton(
                            onClick = onClick,
                            modifier = Modifier
                                .width(72.dp)
                                .height(56.dp),
                        ) {
                            Text(label, style = titleMedium)
                        }
                    }
                }
            }
        }
    }
}

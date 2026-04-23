package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RootWarningDialog(
    evidence: Set<String>,
    onAcknowledge: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Root access detected") },
        text = {
            Column {
                Text(
                    "Your device appears to be rooted. Any app with root access on this device can read ToolNeuron's encrypted files and, in principle, extract session data while the app is running.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your PIN is still hardware-sealed by the Android Keystore — but root lowers the protection floor. Use at your own risk.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (evidence.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Evidence: ${evidence.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAcknowledge) {
                Text("I understand")
            }
        },
    )
}

package com.moorixlabs.sagachat.ui.screens.roleplay_chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moorixlabs.sagachat.ui.icons.TnIcons

@Composable
fun NoModelInstalledSheet(
    onBrowseModels: () -> Unit,
    onImportModel: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = TnIcons.Cpu,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text      = "No model installed",
            style     = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "SagaChat runs entirely on your device. To start a conversation, " +
                    "you need to install a language model first.\n\n" +
                    "A good starting point is Qwen2.5-3B — it fits on most phones " +
                    "and works well for roleplay.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick  = onBrowseModels,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(TnIcons.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Browse Models")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick  = onImportModel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(TnIcons.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import from device")
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onBack) {
            Text("Go back")
        }
    }
}

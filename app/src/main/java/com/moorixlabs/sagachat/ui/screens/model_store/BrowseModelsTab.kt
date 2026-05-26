package com.moorixlabs.sagachat.ui.screens.model_store

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moorixlabs.download_manager.HxdState
import com.moorixlabs.sagachat.model.HuggingFaceModel
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.theme.LocalDimens
import com.moorixlabs.sagachat.viewmodel.ModelStoreViewModel

@Composable
internal fun BrowseModelsTab(
    models: List<HuggingFaceModel>,
    isLoading: Boolean,
    error: String?,
    downloadStates: Map<String, HxdState>,
    extractingIds: Set<String>,
    extractingFile: Map<String, String>,
    installedModelIds: Set<String>,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetry: () -> Unit
) {
    val dimens = LocalDimens.current

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading && models.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                }
            }
            error != null && models.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingLg)
                    ) {
                        Icon(TnIcons.AlertTriangle, null, Modifier.size(48.dp), MaterialTheme.colorScheme.error)
                        Text("Error loading models", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
            models.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)
                    ) {
                        Icon(TnIcons.SearchOff, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No models found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(models, key = { it.id }) { model ->
                        CatalogModelCard(
                            model = model,
                            isInstalled = model.id in installedModelIds,
                            downloadState = downloadStates[model.id],
                            isExtracting = model.id in extractingIds,
                            extractingEntryName = extractingFile[model.id],
                            onDownload = { onDownload(model) },
                            onCancel = { onCancelDownload(model.id) }
                        )
                    }
                }
            }
        }
    }
}

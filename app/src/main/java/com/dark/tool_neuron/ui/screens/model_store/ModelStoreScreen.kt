package com.dark.tool_neuron.ui.screens.model_store

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.download_manager.HxdState
import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel

private val tabs = listOf("Models", "Installed")

@Composable
fun ModelStoreScreen(
    innerPadding: PaddingValues,
    viewModel: ModelStoreViewModel
) {
    val selectedTab by viewModel.selectedTab.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { viewModel.selectTab(index) },
                    text = { Text(title) }
                )
            }
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()) },
            label = "storeTab"
        ) { tab ->
            when (tab) {
                0 -> BrowseTab(viewModel)
                1 -> InstalledTab(viewModel)
            }
        }
    }
}

@Composable
private fun BrowseTab(viewModel: ModelStoreViewModel) {
    val dimens = LocalDimens.current
    val catalogModels by viewModel.catalogModels.collectAsState()
    val installedModels by viewModel.installedModels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val installedIds = installedModels.map { it.id }.toSet()

    when {
        isLoading && catalogModels.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.height(dimens.spacingSm))
                    Text(
                        "Loading models...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        error != null && catalogModels.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        TnIcons.AlertTriangle, null,
                        Modifier.size(32.dp),
                        MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(dimens.spacingSm))
                    Text(
                        error ?: "Error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(dimens.spacingMd))
                    ActionTextButton(
                        onClickListener = { viewModel.refreshCatalog(true) },
                        icon = TnIcons.Refresh,
                        text = "Retry"
                    )
                }
            }
        }
        catalogModels.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No models available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        else -> {
            ModelList(
                models = catalogModels,
                installedIds = installedIds,
                downloadStates = downloadStates,
                onDownload = viewModel::downloadModel,
                onCancel = viewModel::cancelDownload,
            )
        }
    }
}

@Composable
private fun ModelList(
    models: List<HuggingFaceModel>,
    installedIds: Set<String>,
    downloadStates: Map<String, HxdState>,
    onDownload: (HuggingFaceModel) -> Unit,
    onCancel: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    val grouped = models.groupBy { it.repoId }

    LazyColumn(
        contentPadding = PaddingValues(dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)
    ) {
        grouped.forEach { (repoId, repoModels) ->
            val repoName = repoModels.firstOrNull()?.name?.substringBefore(" ") ?: repoId
            item(key = "header_$repoId") {
                Text(
                    text = repoName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = dimens.spacingMd, bottom = dimens.spacingXs)
                )
            }
            items(repoModels, key = { it.id }) { model ->
                CatalogModelCard(
                    model = model,
                    isInstalled = model.id in installedIds,
                    downloadState = downloadStates[model.id],
                    onDownload = { onDownload(model) },
                    onCancel = { onCancel(model.id) },
                )
            }
        }
    }
}

@Composable
private fun InstalledTab(viewModel: ModelStoreViewModel) {
    val dimens = LocalDimens.current
    val models by viewModel.installedModels.collectAsState()
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "model.gguf"
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = it.getString(nameIdx)
                    if (sizeIdx >= 0) size = it.getLong(sizeIdx)
                }
            }
            viewModel.importLocalModel(uri, name, size)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)
    ) {
        item(key = "import") {
            ImportLocalCard(onClick = {
                filePicker.launch(arrayOf("application/octet-stream", "*/*"))
            })
        }

        items(models, key = { it.id }) { model ->
            InstalledModelCard(
                model = model,
                onLoad = { viewModel.loadModel(model) },
                onUnload = { viewModel.unloadModel() },
                onDelete = { viewModel.deleteModel(model) },
            )
        }

        if (models.isEmpty()) {
            item(key = "empty") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            TnIcons.Download, null,
                            Modifier.size(32.dp),
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(dimens.spacingSm))
                        Text(
                            "No models installed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Download from the Store or import a local file",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportLocalCard(onClick: () -> Unit) {
    val dimens = LocalDimens.current
    Surface(
        onClick = onClick,
        shape = LocalTnShapes.current.card,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)
        ) {
            Icon(TnIcons.Download, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
            Column {
                Text(
                    "Import local GGUF",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Pick a .gguf file from storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

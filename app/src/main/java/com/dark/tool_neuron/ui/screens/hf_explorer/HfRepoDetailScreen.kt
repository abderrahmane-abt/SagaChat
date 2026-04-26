package com.dark.tool_neuron.ui.screens.hf_explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.download_manager.formatBytes
import com.dark.tool_neuron.repo.ExplorerRepo
import com.dark.tool_neuron.repo.HfRepoDetail
import com.dark.tool_neuron.repo.RepoFile
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.HfExplorerViewModel
import com.dark.tool_neuron.viewmodel.HfFileFilter
import com.dark.tool_neuron.viewmodel.HfFileSizeBucket
import com.dark.tool_neuron.viewmodel.HfRepoDetailState

@Composable
fun HfRepoDetailScreen(
    innerPadding: PaddingValues,
    repoPath: String,
    vm: HfExplorerViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val state by vm.detailState.collectAsStateWithLifecycle()
    val filter by vm.fileFilter.collectAsStateWithLifecycle()
    val bucket by vm.fileSizeBucket.collectAsStateWithLifecycle()
    val existing by vm.existingRepoPaths.collectAsStateWithLifecycle()

    LaunchedEffect(repoPath) { vm.loadRepoDetail(repoPath) }

    when (val s = state) {
        HfRepoDetailState.Idle, HfRepoDetailState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    CaptionText(text = "Loading $repoPath…")
                }
            }
        }
        is HfRepoDetailState.Failed -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
                ) {
                    Icon(
                        TnIcons.AlertTriangle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp),
                    )
                    CaptionText(text = s.reason, color = MaterialTheme.colorScheme.error)
                    ActionTextButton(
                        onClickListener = { vm.loadRepoDetail(repoPath) },
                        icon = TnIcons.Refresh,
                        text = "Retry",
                    )
                }
            }
        }
        is HfRepoDetailState.Success -> {
            DetailContent(
                innerPadding = innerPadding,
                detail = s.detail,
                filter = filter,
                bucket = bucket,
                isAdded = existing.contains(s.detail.id.lowercase()),
                visibleFiles = vm.visibleFiles(s.detail),
                onAdd = { vm.addRepository(repoFromDetail(s.detail)) },
                onFilterChange = vm::setFileFilter,
                onBucketChange = vm::setFileSizeBucket,
            )
        }
    }
}

private fun repoFromDetail(detail: HfRepoDetail) =
    ExplorerRepo(
        id = detail.id,
        author = detail.author,
        downloads = detail.downloads,
        likes = detail.likes,
        gated = detail.gated,
    )

@Composable
private fun DetailContent(
    innerPadding: PaddingValues,
    detail: HfRepoDetail,
    filter: HfFileFilter,
    bucket: HfFileSizeBucket,
    isAdded: Boolean,
    visibleFiles: List<RepoFile>,
    onAdd: () -> Unit,
    onFilterChange: (HfFileFilter) -> Unit,
    onBucketChange: (HfFileSizeBucket) -> Unit,
) {
    val dimens = LocalDimens.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(
            horizontal = dimens.screenPadding,
            vertical = dimens.spacingMd,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        item { HeaderCard(detail = detail, isAdded = isAdded, onAdd = onAdd) }
        item { FilterCard(filter = filter, bucket = bucket, onFilterChange = onFilterChange, onBucketChange = onBucketChange) }
        item {
            SectionHeader(
                title = "Files · ${visibleFiles.size}",
                action = {
                    CaptionText(
                        text = "Total " + formatBytes(visibleFiles.sumOf { it.sizeBytes }),
                    )
                },
            )
        }
        items(visibleFiles, key = { it.path }) { file ->
            FileRow(file = file)
        }
        if (visibleFiles.isEmpty()) {
            item { CaptionText("No files match the current filter.") }
        }
    }
}

@Composable
private fun HeaderCard(
    detail: HfRepoDetail,
    isAdded: Boolean,
    onAdd: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    StandardCard(
        title = detail.id,
        description = detail.author,
        icon = TnIcons.Server,
        trailing = {
            if (isAdded) {
                Box(
                    modifier = Modifier
                        .size(dimens.actionIconSize)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            RoundedCornerShape(50),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        TnIcons.Check,
                        contentDescription = "Already added",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                ActionButton(
                    onClickListener = onAdd,
                    icon = TnIcons.Plus,
                    contentDescription = "Add repository",
                )
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Stat(label = "Downloads", value = formatCount(detail.downloads))
            Stat(label = "Likes", value = detail.likes.toString())
            Stat(label = "GGUF total", value = formatBytes(detail.totalGgufBytes))
            if (detail.gated) {
                Surface(
                    shape = tnShapes.full,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        "Gated",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXxs),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        CaptionText(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FilterCard(
    filter: HfFileFilter,
    bucket: HfFileSizeBucket,
    onFilterChange: (HfFileFilter) -> Unit,
    onBucketChange: (HfFileSizeBucket) -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Filter files",
        icon = TnIcons.Wrench,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            CaptionText("File type")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                HfFileFilter.entries.forEach { f ->
                    Pill(
                        text = when (f) {
                            HfFileFilter.ALL -> "All"
                            HfFileFilter.GGUF -> "GGUF"
                            HfFileFilter.MMPROJ -> "mmproj"
                        },
                        selected = filter == f,
                        onClick = { onFilterChange(f) },
                    )
                }
            }
            CaptionText("Max size")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                HfFileSizeBucket.entries.forEach { b ->
                    Pill(
                        text = b.label,
                        selected = bucket == b,
                        onClick = { onBucketChange(b) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary
    Surface(
        shape = tnShapes.full,
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingXs,
            ),
        )
    }
}

@Composable
private fun FileRow(file: RepoFile) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val isGguf = file.path.endsWith(".gguf", ignoreCase = true)
    val isMmproj = file.path.contains("mmproj", ignoreCase = true)
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = dimens.spacingSm,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.Database,
                contentDescription = null,
                tint = if (isMmproj) MaterialTheme.colorScheme.tertiary
                else if (isGguf) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                file.path,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            CaptionText(
                text = if (file.sizeBytes > 0) formatBytes(file.sizeBytes) else "—",
            )
        }
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

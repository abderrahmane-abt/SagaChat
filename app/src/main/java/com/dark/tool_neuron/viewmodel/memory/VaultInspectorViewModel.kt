package com.dark.tool_neuron.viewmodel.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.screen.memory.EmptyStateCard
import com.dark.tool_neuron.ui.screen.memory.VaultLogger
import com.dark.tool_neuron.ui.screen.memory.formatBytes
import com.dark.tool_neuron.ui.screen.memory.formatDate
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.vault.VaultHelper
import com.memoryvault.core.BlockMetadata
import com.memoryvault.core.BlockType
import kotlinx.coroutines.launch

class VaultInspectorViewModel : ViewModel() {
    var blockMetadata by mutableStateOf<List<BlockMetadata>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var selectedBlock by mutableStateOf<BlockMetadata?>(null)
        private set

    // Use private backing fields with public getters to avoid JVM signature clash
    private var _filterType by mutableStateOf<BlockType?>(null)
    val filterType: BlockType? get() = _filterType

    private var _sortBy by mutableStateOf(SortOption.TIMESTAMP_DESC)
    val sortBy: SortOption get() = _sortBy

    private var _searchQuery by mutableStateOf("")
    val searchQuery: String get() = _searchQuery

    var statusMessage by mutableStateOf("")
        private set

    var showError by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf("")
        private set

    init {
        loadBlockMetadata()
    }

    fun loadBlockMetadata() {
        viewModelScope.launch {
            try {
                isLoading = true
                // Access vault's getAllMetadata through VaultHelper
                blockMetadata = VaultHelper.getVault().getAllMetadata()
                statusMessage = "Loaded ${blockMetadata.size} blocks"
                VaultLogger.info("Inspector", "Loaded ${blockMetadata.size} block metadata entries")
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to load block metadata"
                VaultLogger.error("Inspector", "Failed to load metadata", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun selectBlock(block: BlockMetadata?) {
        selectedBlock = block
        if (block != null) {
            VaultLogger.debug("Inspector", "Selected block: ${block.blockId}")
        }
    }

    fun setFilterType(type: BlockType?) {
        _filterType = type
        VaultLogger.debug("Inspector", "Filter changed to: ${type?.name ?: "ALL"}")
    }

    fun setSortOption(option: SortOption) {
        _sortBy = option
        VaultLogger.debug("Inspector", "Sort changed to: ${option.name}")
    }

    fun setSearchQuery(query: String) {
        _searchQuery = query
    }

    fun getFilteredAndSortedBlocks(): List<BlockMetadata> {
        var filtered = blockMetadata

        // Apply type filter
        if (_filterType != null) {
            filtered = filtered.filter { it.blockType == _filterType }
        }

        // Apply search
        if (_searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.blockId.toString()
                    .contains(_searchQuery, ignoreCase = true) || it.category?.contains(
                    _searchQuery,
                    ignoreCase = true
                ) == true || it.tags.any { tag ->
                    tag.contains(
                        _searchQuery,
                        ignoreCase = true
                    )
                } || it.searchableText?.contains(_searchQuery, ignoreCase = true) == true
            }
        }

        // Apply sorting
        filtered = when (_sortBy) {
            SortOption.TIMESTAMP_ASC -> filtered.sortedBy { it.timestamp }
            SortOption.TIMESTAMP_DESC -> filtered.sortedByDescending { it.timestamp }
            SortOption.SIZE_ASC -> filtered.sortedBy { it.compressedSize }
            SortOption.SIZE_DESC -> filtered.sortedByDescending { it.compressedSize }
            SortOption.TYPE -> filtered.sortedBy { it.blockType.name }
        }

        return filtered
    }

    fun dismissError() {
        showError = false
        errorMessage = ""
    }
}

enum class SortOption(val displayName: String) {
    TIMESTAMP_DESC("Newest First"),
    TIMESTAMP_ASC("Oldest First"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    TYPE("By Type")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultInspectorScreen(
    viewModel: VaultInspectorViewModel = viewModel()
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val filteredBlocks = remember(
        viewModel.blockMetadata, viewModel.filterType, viewModel.sortBy, viewModel.searchQuery
    ) {
        viewModel.getFilteredAndSortedBlocks()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(
                        "Vault Inspector", fontSize = rSp(18.sp), fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${filteredBlocks.size} blocks",
                        fontSize = rSp(11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }, actions = {
                // Sort Menu
                Box {
                    ActionButton(
                        onClickListener = { showSortMenu = true },
                        icon = Icons.Default.List,
                        contentDescription = "Sort"
                    )

                    DropdownMenu(
                        expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(text = {
                                Text(
                                    option.displayName, fontSize = rSp(13.sp)
                                )
                            }, onClick = {
                                viewModel.setSortOption(option)
                                showSortMenu = false
                            }, leadingIcon = {
                                if (viewModel.sortBy == option) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            })
                        }
                    }
                }

                Spacer(Modifier.width(rDp(8.dp)))

                // Filter Menu
                Box {
                    ActionButton(
                        onClickListener = { showFilterMenu = true },
                        icon = Icons.Default.Search,
                        contentDescription = "Filter"
                    )

                    DropdownMenu(
                        expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All Types", fontSize = rSp(13.sp)) },
                            onClick = {
                                viewModel.setFilterType(null)
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                if (viewModel.filterType == null) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            })

                        Divider()

                        BlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name, fontSize = rSp(13.sp)) },
                                onClick = {
                                    viewModel.setFilterType(type)
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    if (viewModel.filterType == type) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                })
                        }
                    }
                }

                Spacer(Modifier.width(rDp(8.dp)))

                ActionButton(
                    onClickListener = { viewModel.loadBlockMetadata() },
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            })
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            Surface(
                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(rDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    OutlinedTextField(
                        value = viewModel.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Search blocks...", fontSize = rSp(13.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (viewModel.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(rDp(8.dp))
                    )
                }
            }

            // Active Filters Display
            AnimatedVisibility(
                visible = viewModel.filterType != null || viewModel.searchQuery.isNotEmpty()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rDp(12.dp), vertical = rDp(8.dp)),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(16.dp)),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            buildString {
                                append("Filters: ")
                                if (viewModel.filterType != null) {
                                    append(viewModel.filterType!!.name)
                                }
                                if (viewModel.searchQuery.isNotEmpty()) {
                                    if (viewModel.filterType != null) append(", ")
                                    append("\"${viewModel.searchQuery}\"")
                                }
                            },
                            fontSize = rSp(12.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Loading Indicator
            if (viewModel.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Block List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(rDp(12.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                if (filteredBlocks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Face,
                            message = if (viewModel.searchQuery.isNotEmpty() || viewModel.filterType != null) "No blocks match your filters"
                            else "No blocks in vault"
                        )
                    }
                } else {
                    items(filteredBlocks) { block ->
                        BlockMetadataCard(
                            metadata = block,
                            isSelected = viewModel.selectedBlock?.blockId == block.blockId,
                            onClick = { viewModel.selectBlock(block) })
                    }
                }
            }
        }

        // Block Detail Sheet
        viewModel.selectedBlock?.let { block ->
            BlockDetailSheet(
                metadata = block, onDismiss = { viewModel.selectBlock(null) })
        }

        // Error Dialog
        if (viewModel.showError) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("Error", fontSize = rSp(18.sp), fontWeight = FontWeight.Bold) },
                text = { Text(viewModel.errorMessage, fontSize = rSp(14.sp)) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("OK")
                    }
                })
        }
    }
}

@Composable
fun BlockMetadataCard(
    metadata: BlockMetadata, isSelected: Boolean, onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(rDp(12.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(rDp(32.dp))
                            .clip(RoundedCornerShape(rDp(8.dp)))
                            .background(getBlockTypeColor(metadata.blockType)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            getBlockTypeIcon(metadata.blockType),
                            contentDescription = null,
                            modifier = Modifier.size(rDp(18.dp)),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Column {
                        Text(
                            metadata.blockType.name,
                            fontSize = rSp(13.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            metadata.blockId.toString().take(13) + "...",
                            fontSize = rSp(10.sp),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        formatBytes(metadata.compressedSize),
                        fontSize = rSp(12.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        formatDate(metadata.timestamp),
                        fontSize = rSp(10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tags and Category
            if (metadata.category != null || metadata.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    metadata.category?.let {
                        SuggestionChip(onClick = { }, label = {
                            Text(
                                it, fontSize = rSp(10.sp)
                            )
                        }, icon = {
                            Icon(
                                Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(rDp(12.dp))
                            )
                        })
                    }

                    metadata.tags.take(3).forEach { tag ->
                        SuggestionChip(onClick = { }, label = {
                            Text(
                                tag, fontSize = rSp(10.sp)
                            )
                        })
                    }

                    if (metadata.tags.size > 3) {
                        Text(
                            "+${metadata.tags.size - 3}",
                            fontSize = rSp(10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = rDp(4.dp))
                        )
                    }
                }
            }

            // Compression Info
            Divider()

            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Compressed: ${formatBytes(metadata.compressedSize)}",
                    fontSize = rSp(11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Uncompressed: ${formatBytes(metadata.uncompressedSize)}",
                    fontSize = rSp(11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val compressionRatio = if (metadata.uncompressedSize > 0) {
                (metadata.compressedSize.toFloat() / metadata.uncompressedSize.toFloat() * 100).toInt()
            } else 100

            LinearProgressIndicator(
                progress = { compressionRatio / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Compression: $compressionRatio%",
                fontSize = rSp(10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockDetailSheet(
    metadata: BlockMetadata, onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = rDp(16.dp), topEnd = rDp(16.dp))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            item {
                Text(
                    "Block Details", fontSize = rSp(20.sp), fontWeight = FontWeight.Bold
                )
            }

            item { Divider() }

            item {
                DetailRow("Block ID", metadata.blockId.toString())
            }

            item {
                DetailRow("Type", metadata.blockType.name)
            }

            item {
                DetailRow("File Offset", "${metadata.fileOffset} bytes")
            }

            item {
                DetailRow("Compressed Size", formatBytes(metadata.compressedSize))
            }

            item {
                DetailRow("Uncompressed Size", formatBytes(metadata.uncompressedSize))
            }

            item {
                DetailRow("Timestamp", formatDate(metadata.timestamp))
            }

            if (metadata.category != null) {
                item {
                    DetailRow("Category", metadata.category!!)
                }
            }

            if (metadata.tags.isNotEmpty()) {
                item {
                    DetailRow("Tags", metadata.tags.joinToString(", "))
                }
            }

            item {
                DetailRow("Content Hash", metadata.contentHash.take(32) + "...")
            }

            if (metadata.searchableText != null) {
                item {
                    DetailRow("Searchable Text", metadata.searchableText!!.take(100) + "...")
                }
            }

            item {
                Spacer(Modifier.height(rDp(32.dp)))
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
    ) {
        Text(
            label,
            fontSize = rSp(12.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(8.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                value,
                fontSize = rSp(13.sp),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(rDp(12.dp))
            )
        }
    }
}

fun getBlockTypeIcon(type: BlockType): ImageVector {
    return when (type) {
        BlockType.MESSAGE -> Icons.Default.Email
        BlockType.FILE -> Icons.Default.Face
        BlockType.CUSTOM_DATA -> Icons.Default.Star
        BlockType.EMBEDDING -> Icons.Default.Face
        BlockType.REFERENCE -> Icons.Default.Face
        BlockType.METADATA -> Icons.Default.Info
    }
}

fun getBlockTypeColor(type: BlockType): Color {
    return when (type) {
        BlockType.MESSAGE -> Color(0xFF2196F3)
        BlockType.FILE -> Color(0xFF4CAF50)
        BlockType.CUSTOM_DATA -> Color(0xFFFF9800)
        BlockType.EMBEDDING -> Color(0xFF9C27B0)
        BlockType.REFERENCE -> Color(0xFF00BCD4)
        BlockType.METADATA -> Color(0xFF607D8B)
    }
}
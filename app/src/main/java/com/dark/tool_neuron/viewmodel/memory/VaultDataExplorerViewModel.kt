package com.dark.tool_neuron.viewmodel.memory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.vault.VaultHelper
import com.memoryvault.core.BlockMetadata
import com.memoryvault.core.BlockType
import kotlinx.coroutines.launch

class VaultDataExplorerViewModel : ViewModel() {
    var allMetadata by mutableStateOf<List<BlockMetadata>>(emptyList())
        private set

    var filteredItems by mutableStateOf<List<BlockMetadata>>(emptyList())
        private set

    var typeCounts by mutableStateOf<Map<BlockType?, Int>>(emptyMap())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var selectedItem by mutableStateOf<BlockMetadata?>(null)
        private set

    private var currentFilter: BlockType? = null
    private var currentSearchQuery: String = ""

    fun loadAllData() {
        viewModelScope.launch {
            try {
                isLoading = true
                allMetadata = VaultHelper.getVault().getAllMetadata()
                    .sortedByDescending { it.timestamp }
                updateTypeCounts()
                applyFilters()
            } catch (e: Exception) {
                allMetadata = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    fun filterByType(type: BlockType?) {
        currentFilter = type
        applyFilters()
    }

    fun search(query: String) {
        currentSearchQuery = query
        applyFilters()
    }

    fun selectItem(metadata: BlockMetadata) {
        selectedItem = metadata
    }

    fun clearSelection() {
        selectedItem = null
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                VaultHelper.getVault().delete(id)
                loadAllData()
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading = false
            }
        }
    }

    private fun updateTypeCounts() {
        val counts = mutableMapOf<BlockType?, Int>()
        allMetadata.forEach { metadata ->
            val type = metadata.blockType
            counts[type] = (counts[type] ?: 0) + 1
        }
        typeCounts = counts
    }

    private fun applyFilters() {
        var result = allMetadata

        // Apply type filter
        currentFilter?.let { type ->
            result = result.filter { it.blockType == type }
        }

        // Apply search filter
        if (currentSearchQuery.isNotBlank()) {
            val query = currentSearchQuery.lowercase()
            result = result.filter { metadata ->
                metadata.searchableText?.lowercase()?.contains(query) == true ||
                metadata.category?.lowercase()?.contains(query) == true ||
                metadata.tags.any { it.lowercase().contains(query) } ||
                metadata.blockId.toString().lowercase().contains(query)
            }
        }

        filteredItems = result
    }
}
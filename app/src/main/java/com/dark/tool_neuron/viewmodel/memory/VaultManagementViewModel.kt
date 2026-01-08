package com.dark.tool_neuron.viewmodel.memory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.models.vault.MessageSearchResult
import com.dark.tool_neuron.vault.VaultHelper
import com.memoryvault.core.VaultStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ViewModel for Vault Management
class VaultManagementViewModel : ViewModel() {
    var vaultStats by mutableStateOf<VaultStats?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf("")
        private set

    var showError by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf("")
        private set

    var defragProgress by mutableStateOf(0f)
        private set

    var isDefragging by mutableStateOf(false)
        private set

    var chatList by mutableStateOf<List<ChatInfo>>(emptyList())
        private set

    var selectedChatId by mutableStateOf<String?>(null)
        private set

    var chatMessages by mutableStateOf<List<Messages>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<MessageSearchResult>>(emptyList())
        private set

    var autoRefresh by mutableStateOf(false)
        private set

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                if (autoRefresh && !isLoading) {
                    loadVaultStats()
                }
                delay(5000) // Refresh every 5 seconds
            }
        }
    }

    fun loadVaultStats() {
        viewModelScope.launch {
            try {
                isLoading = true
                vaultStats = VaultHelper.getVault().getStats()
                statusMessage = "Stats loaded successfully"
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to load vault stats"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadChatList() {
        viewModelScope.launch {
            try {
                isLoading = true
                chatList = VaultHelper.getAllChats()
                statusMessage = "Loaded ${chatList.size} chats"
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to load chats"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadChatMessages(chatId: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                selectedChatId = chatId
                chatMessages = VaultHelper.getMessagesForChat(chatId)
                statusMessage = "Loaded ${chatMessages.size} messages"
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to load messages"
            } finally {
                isLoading = false
            }
        }
    }

    fun performSearch(query: String) {
        searchQuery = query
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                isLoading = true
                searchResults = VaultHelper.searchMessages(query)
                statusMessage = "Found ${searchResults.size} results"
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Search failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun performDefragmentation() {
        viewModelScope.launch {
            try {
                isDefragging = true
                defragProgress = 0f
                VaultHelper.performMaintenance()
                defragProgress = 1f
                statusMessage = "Defragmentation completed"
                loadVaultStats()
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Defragmentation failed"
            } finally {
                isDefragging = false
                defragProgress = 0f
            }
        }
    }

    fun createBackup(path: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                val success = VaultHelper.createBackup(path)
                statusMessage = if (success) "Backup created successfully" else "Backup failed"
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Backup creation failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun restoreBackup(path: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                val success = VaultHelper.restoreBackup(path)
                statusMessage = if (success) "Backup restored successfully" else "Restore failed"
                if (success) {
                    loadVaultStats()
                    loadChatList()
                }
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Backup restore failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearVault(context: android.content.Context) {
        viewModelScope.launch {
            try {
                isLoading = true
                VaultHelper.clearVault(context)
                statusMessage = "Vault cleared successfully"
                loadVaultStats()
                chatList = emptyList()
                chatMessages = emptyList()
                selectedChatId = null
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to clear vault"
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                isLoading = true
                VaultHelper.deleteChat(chatId)
                statusMessage = "Chat deleted successfully"
                loadChatList()
                if (selectedChatId == chatId) {
                    selectedChatId = null
                    chatMessages = emptyList()
                }
            } catch (e: Exception) {
                showError = true
                errorMessage = e.message ?: "Failed to delete chat"
            } finally {
                isLoading = false
            }
        }
    }

    fun toggleAutoRefresh() {
        autoRefresh = !autoRefresh
        statusMessage = if (autoRefresh) "Auto-refresh enabled" else "Auto-refresh disabled"
    }

    fun dismissError() {
        showError = false
        errorMessage = ""
    }
}


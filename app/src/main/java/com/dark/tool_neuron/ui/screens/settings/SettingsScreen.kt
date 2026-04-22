package com.dark.tool_neuron.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.screens.settings.components.SettingsDialogHost
import com.dark.tool_neuron.ui.screens.settings.components.SettingsItemRow
import com.dark.tool_neuron.ui.screens.settings.model.SettingsSection
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSnackbar()
    }

    Scaffold(
        topBar = { SettingsTopBar(onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = dimens.screenPadding,
                vertical = dimens.spacingSm,
            ),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            state.sections.forEach { section ->
                item(key = "header-${section.id}") { SectionHeader(section) }
                items(section.items, key = { "${section.id}-${it.id}" }) { item ->
                    SettingsItemRow(
                        item = item,
                        onOpenChoice = { choice -> viewModel.requestChoiceDialog(choice) },
                    )
                }
            }
        }
    }

    SettingsDialogHost(
        dialog = state.dialog,
        onDismiss = viewModel::dismissDialog,
    )
}

@Composable
private fun SectionHeader(section: SettingsSection) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.spacingMd, bottom = dimens.spacingXxs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            section.icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconMd),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!section.description.isNullOrBlank()) {
            Text(
                text = section.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

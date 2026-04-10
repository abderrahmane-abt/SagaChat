package com.dark.tool_neuron.ui.screens.model_store

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelStoreTopBar(
    onBack: () -> Unit,
) {
    val dimens = LocalDimens.current
    val activity = LocalContext.current as ComponentActivity
    val viewModel: ModelStoreViewModel = hiltViewModel(activity)

    CenterAlignedTopAppBar(
        title = { Text("Model Store") },
        navigationIcon = {
            ActionButton(
                onClickListener = onBack,
                icon = TnIcons.ArrowLeft,
                contentDescription = "Back",
                modifier = Modifier.padding(start = dimens.screenPadding)
            )
        },
        actions = {
            ActionButton(
                onClickListener = { viewModel.refreshCatalog(forceRefresh = true) },
                icon = TnIcons.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.padding(end = dimens.screenPadding)
            )
        }
    )
}

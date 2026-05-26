package com.moorixlabs.sagachat.ui.screens.model_store

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moorixlabs.sagachat.ui.components.TnTextField
import com.moorixlabs.sagachat.ui.icons.TnIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    TopAppBar(
        title = {
            TnTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search models...",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(TnIcons.ArrowLeft, "Close search")
            }
        },
    )
}

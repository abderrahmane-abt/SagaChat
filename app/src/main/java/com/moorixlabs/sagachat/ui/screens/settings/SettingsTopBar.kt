package com.moorixlabs.sagachat.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.moorixlabs.sagachat.ui.components.ActionButton
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.theme.LocalDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    onBack: () -> Unit,
    title: String = "Settings",
    subtitle: String? = "App preferences, privacy, and storage",
    isMenu: Boolean = false,
    onMenuClick: () -> Unit = {},
) {
    val dimens = LocalDimens.current
    CenterAlignedTopAppBar(
        title = {
            Column(
                verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        navigationIcon = {
            if (isMenu) {
                ActionButton(
                    onClickListener = onMenuClick,
                    icon = TnIcons.Menu,
                    contentDescription = "Menu",
                    modifier = Modifier.padding(start = dimens.screenPadding),
                )
            } else {
                ActionButton(
                    onClickListener = onBack,
                    icon = TnIcons.ArrowLeft,
                    contentDescription = "Back",
                    modifier = Modifier.padding(start = dimens.screenPadding),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

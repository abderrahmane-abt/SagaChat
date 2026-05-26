package com.moorixlabs.sagachat.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moorixlabs.sagachat.ui.components.ActionToggleGroup
import com.moorixlabs.sagachat.ui.components.StandardCard
import com.moorixlabs.sagachat.ui.components.SwitchRow
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.theme.LocalDimens
import com.moorixlabs.sagachat.ui.theme.LocalTnShapes
import com.moorixlabs.sagachat.ui.theme.Motion
import com.moorixlabs.sagachat.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@Composable
fun SettingsChatRpScreen(
    innerPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val displayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val lengthStyle by viewModel.responseLengthStyle.collectAsStateWithLifecycle()
    val memoryEnabled by viewModel.memoryEnabled.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(60); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = dimens.screenPadding,
                vertical = dimens.spacingSm,
            ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
    ) {
        AnimatedSection(visible = visible, stagger = 0) {
            StandardCard(
                title = "Your display name",
                description = "How characters refer to you during roleplay.",
                icon = TnIcons.MessageCircle,
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = viewModel::setUserDisplayName,
                    singleLine = true,
                    shape = tnShapes.cardSmall,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f),
                    )
                )
            }
        }

        AnimatedSection(visible = visible, stagger = 60) {
            StandardCard(
                title = "Response length",
                description = "Configure target message length for assistant generation.",
                icon = TnIcons.Prompt,
            ) {
                ActionToggleGroup(
                    items = listOf("short", "medium", "long"),
                    selectedItem = lengthStyle,
                    onItemSelected = viewModel::setResponseLengthStyle,
                    itemLabel = { option ->
                        when (option) {
                            "short" -> "Short"
                            "medium" -> "Medium"
                            "long" -> "Long"
                            else -> option
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = dimens.toggleGroupHeight + 8.dp,
                )
            }
        }

        AnimatedSection(visible = visible, stagger = 120) {
            SwitchRow(
                title = "Conversation Memory",
                description = "Auto-summarise context dynamically every 8 turns to retain long-term details.",
                checked = memoryEnabled,
                onCheckedChange = viewModel::setMemoryEnabled,
                icon = TnIcons.Database,
            )
        }

        Spacer(Modifier.height(dimens.spacingLg))
    }
}

@Composable
private fun AnimatedSection(
    visible: Boolean,
    stagger: Int,
    content: @Composable () -> Unit,
) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            if (stagger > 0) delay(stagger.toLong())
            ready = true
        } else {
            ready = false
        }
    }
    AnimatedVisibility(
        visible = ready,
        enter = fadeIn(Motion.entrance()) +
            slideInVertically(Motion.entrance()) { it / 6 },
    ) {
        content()
    }
}

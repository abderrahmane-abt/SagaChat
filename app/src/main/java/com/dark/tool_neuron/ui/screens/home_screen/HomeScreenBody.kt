package com.dark.tool_neuron.ui.screens.home_screen

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.models.ui.ActionIcon
import com.dark.tool_neuron.models.ui.ActionItem
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.components.BodyLabel
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.CuteSwitch
import com.dark.tool_neuron.ui.components.ExpandCollapseIcon
import com.dark.tool_neuron.ui.components.InfoBadge
import com.dark.tool_neuron.ui.components.InfoCard
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.components.PasswordTextField
import com.dark.tool_neuron.ui.components.SectionDivider
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.StatusBadge
import com.dark.tool_neuron.ui.components.SwitchRow
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens

@Composable
fun HomeScreen(innerPadding: PaddingValues) {
    val dimens = LocalDimens.current

    var cuteChecked by remember { mutableStateOf(false) }
    var actionSwitchChecked by remember { mutableStateOf(true) }
    var switchRowChecked by remember { mutableStateOf(false) }
    var toggleChecked by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val toggleItems = listOf("CPU", "NPU", "GPU")
    var selectedItem by remember { mutableStateOf("CPU") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = dimens.screenPadding)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)
    ) {
        Spacer(Modifier.height(dimens.spacingSm))

        SectionHeader(title = "Action Buttons")

        ActionButton(onClickListener = {}, icon = TnIcons.Menu, contentDescription = "Menu")
        ActionProgressButton(onClickListener = {})
        ActionTextButton(onClickListener = {}, icon = TnIcons.Wrench, text = "Configure")

        MultiActionButton(
            actions = listOf(
                ActionItem(ActionIcon.Vector(TnIcons.Menu), onClick = {}, contentDescription = "Menu"),
                ActionItem(ActionIcon.Vector(TnIcons.More), onClick = {}, contentDescription = "More"),
                ActionItem(ActionIcon.Vector(TnIcons.X), onClick = {}, contentDescription = "Close")
            )
        )

        SectionDivider(label = "Toggle Controls")

        ActionToggleButton(
            checked = toggleChecked,
            onCheckedChange = { toggleChecked = it },
            icon = TnIcons.Menu
        )

        ActionToggleGroup(
            items = toggleItems,
            selectedItem = selectedItem,
            onItemSelected = { selectedItem = it },
            itemLabel = { it },
            modifier = Modifier.fillMaxWidth()
        )

        SectionDivider(label = "Switches")

        CuteSwitch(
            checked = cuteChecked,
            onCheckedChange = { cuteChecked = it }
        )

        SwitchRow(
            title = "Enable feature",
            description = "Turns on the experimental feature",
            checked = switchRowChecked,
            onCheckedChange = { switchRowChecked = it },
            icon = TnIcons.Wrench
        )

        SectionDivider(label = "Cards")

        StandardCard(
            title = "Model Settings",
            description = "Configure runtime and precision",
            icon = TnIcons.Wrench
        ) {
            BodyLabel(text = "Content slot — place any composable here.")
        }

        InfoCard(
            title = "Backend",
            value = selectedItem,
            icon = TnIcons.Menu
        )

        SectionDivider(label = "Badges")

        InfoBadge(text = "Q4_K_M")
        StatusBadge(text = "Running", isActive = true)
        StatusBadge(text = "Stopped", isActive = false)

        SectionDivider(label = "Text Components")

        BodyLabel(text = "This is a BodyLabel — regular reading copy.")
        CaptionText(text = "This is a CaptionText — secondary info.")

        SectionDivider(label = "Expand / Collapse Icon")

        ExpandCollapseIcon(
            isExpanded = isExpanded,
            size = 28.dp
        )
        ActionButton(
            onClickListener = { isExpanded = !isExpanded },
            icon = TnIcons.ChevronDown,
            contentDescription = "Toggle expand"
        )

        SectionDivider(label = "Password Field")

        PasswordTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(dimens.spacingXl))
    }
}

package com.moorixlabs.sagachat.ui.screens.home_screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moorixlabs.sagachat.model.ModelInfo
import com.moorixlabs.sagachat.ui.components.SectionHeader
import com.moorixlabs.sagachat.ui.components.model_list.InstalledModelList
import com.moorixlabs.sagachat.ui.theme.LocalDimens
import com.moorixlabs.sagachat.ui.theme.LocalTnShapes
import com.moorixlabs.sagachat.viewmodel.home_vm.ModelLoadState

private val WindowHeight = 240.dp

@Composable
fun LoadModelWindow(
    models: List<ModelInfo>,
    loadState: ModelLoadState,
    onLoad: (ModelInfo) -> Unit,
    onUnload: (ModelInfo) -> Unit,
    onBrowseStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(WindowHeight)
            .padding(bottom = dimens.spacingSm),
        shape = tnShapes.xl,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            SectionHeader(title = "Models")
            InstalledModelList(
                models = models,
                loadState = loadState,
                onLoad = onLoad,
                onUnload = onUnload,
                onBrowseStore = onBrowseStore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

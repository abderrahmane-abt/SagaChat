package com.dark.tool_neuron.ui.screens.hf_explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.repo.ExplorerRepo
import com.dark.tool_neuron.repo.GatedFilter
import com.dark.tool_neuron.repo.HfFilterTaxonomy
import com.dark.tool_neuron.repo.HfFilters
import com.dark.tool_neuron.repo.HfSort
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.StandardCard
import com.dark.tool_neuron.ui.components.TnTextField
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.viewmodel.HfExplorerViewModel

@Composable
fun HfExplorerScreen(
    innerPadding: PaddingValues,
    navController: NavHostController,
    vm: HfExplorerViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val keyboard = LocalSoftwareKeyboardController.current

    val query by vm.query.collectAsStateWithLifecycle()
    val isSearching by vm.isSearching.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val hideAdded by vm.hideAdded.collectAsStateWithLifecycle()
    val existingPaths by vm.existingRepoPaths.collectAsStateWithLifecycle()

    var advancedOpen by remember { mutableStateOf(false) }
    val visible = vm.visibleResults()
    val totalRaw = results.size
    val filteredOut = totalRaw - visible.size

    val onSubmit: () -> Unit = {
        keyboard?.hide()
        vm.search()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(
            horizontal = dimens.screenPadding,
            vertical = dimens.spacingMd,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        item {
            SearchHero(
                query = query,
                isSearching = isSearching,
                onQueryChange = vm::setQuery,
                onSubmit = onSubmit,
            )
        }

        if (history.isNotEmpty() && results.isEmpty() && !isSearching) {
            item {
                HistoryStrip(
                    history = history,
                    onPick = { entry ->
                        vm.searchHistoryEntry(entry)
                        keyboard?.hide()
                    },
                    onRemove = vm::removeHistoryEntry,
                    onClear = vm::clearHistory,
                )
            }
        }

        item {
            SortRow(sort = filters.sort, onChange = vm::setSort)
        }

        item {
            QuickToggleRow(
                gated = filters.gated,
                inferenceWarm = filters.inferenceWarm,
                hideAdded = hideAdded,
                onGatedChange = vm::setGated,
                onInferenceWarmChange = vm::setInferenceWarm,
                onHideAddedChange = vm::setHideAdded,
            )
        }

        item {
            AdvancedFiltersSection(
                expanded = advancedOpen,
                filters = filters,
                onToggle = { advancedOpen = !advancedOpen },
                onPipelineTagChange = vm::setPipelineTag,
                onLibraryToggle = vm::toggleLibrary,
                onAppToggle = vm::toggleApp,
                onProviderToggle = vm::toggleProvider,
                onLanguageToggle = vm::toggleLanguage,
                onLicenseToggle = vm::toggleLicense,
                onRegionToggle = vm::toggleRegion,
                onOtherTagToggle = vm::toggleOtherTag,
                onQuantToggle = vm::toggleQuantTag,
                onAuthorChange = vm::setAuthor,
                onTrainedDatasetChange = vm::setTrainedDataset,
                onParamsRangeChange = vm::setParamsRange,
                onReset = vm::resetFilters,
            )
        }

        if (searchError != null && !isSearching) {
            item { ErrorBanner(message = searchError!!) }
        }

        if (isSearching) {
            item { LoadingRow() }
        }

        if (visible.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Results · ${visible.size}",
                    action = {
                        if (filteredOut > 0) {
                            CaptionText(text = "$filteredOut hidden")
                        }
                    },
                )
            }
            items(visible, key = { it.id }) { repo ->
                ExplorerCard(
                    repo = repo,
                    isAdded = existingPaths.contains(repo.id.lowercase()),
                    onOpen = {
                        navController.navigate(NavScreens.HfRepoDetail.routeFor(repo.id))
                    },
                    onAdd = { vm.addRepository(repo) },
                )
            }
        } else if (!isSearching && query.isNotBlank() && searchError == null && results.isEmpty()) {
            item { CaptionText("No results yet — tap Search.") }
        } else if (!isSearching && results.isNotEmpty() && visible.isEmpty()) {
            item {
                EmptyState(
                    title = "All filtered out",
                    detail = "Adjust filters in Advanced or tap Reset.",
                    actionLabel = "Reset filters",
                    onAction = vm::resetFilters,
                )
            }
        } else if (!isSearching && results.isEmpty() && history.isEmpty() && query.isBlank()) {
            item {
                EmptyState(
                    title = "Find any model on HuggingFace",
                    detail = "Type a model family, author, or quant — press Enter or tap Search.",
                )
            }
        }
    }
}

@Composable
private fun SearchHero(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.card,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Icon(
                    imageVector = TnIcons.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Search HuggingFace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                shape = tnShapes.cardSmall,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = dimens.spacingXs),
                ) {
                    TnTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = "qwen, mistral, deepseek, coder…",
                        singleLine = true,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        trailingIcon = if (query.isNotEmpty()) {
                            @Composable {
                                Icon(
                                    imageVector = TnIcons.X,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onQueryChange("") },
                                )
                            }
                        } else null,
                    )
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(start = 4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        ActionButton(
                            onClickListener = onSubmit,
                            icon = TnIcons.Search,
                            contentDescription = "Search",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryStrip(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    val dimens = LocalDimens.current
    StandardCard(
        title = "Recent searches",
        icon = TnIcons.Refresh,
        trailing = {
            ActionTextButton(
                onClickListener = onClear,
                icon = TnIcons.Trash,
                text = "Clear",
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                history.forEach { entry ->
                    HistoryChip(text = entry, onClick = { onPick(entry) }, onRemove = { onRemove(entry) })
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = dimens.spacingSm, end = dimens.spacingXs, top = dimens.spacingXxs, bottom = dimens.spacingXxs),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = TnIcons.X,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(start = dimens.spacingXs)
                    .size(14.dp)
                    .clickable(onClick = onRemove),
            )
        }
    }
}

@Composable
private fun SortRow(sort: HfSort, onChange: (HfSort) -> Unit) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
        SectionHeader(title = "Sort")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            HfSort.entries.forEach { s ->
                Pill(text = s.label, selected = sort == s, onClick = { onChange(s) })
            }
        }
    }
}

@Composable
private fun QuickToggleRow(
    gated: GatedFilter,
    inferenceWarm: Boolean,
    hideAdded: Boolean,
    onGatedChange: (GatedFilter) -> Unit,
    onInferenceWarmChange: (Boolean) -> Unit,
    onHideAddedChange: (Boolean) -> Unit,
) {
    val dimens = LocalDimens.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
        SectionHeader(title = "Quick filters")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            GatedFilter.entries.forEach { g ->
                Pill(
                    text = "Gated · ${g.label}",
                    selected = gated == g,
                    onClick = { onGatedChange(g) },
                )
            }
            Pill(
                text = "Inference warm",
                selected = inferenceWarm,
                onClick = { onInferenceWarmChange(!inferenceWarm) },
            )
            Pill(
                text = "Hide added",
                selected = hideAdded,
                onClick = { onHideAddedChange(!hideAdded) },
            )
        }
    }
}

@Composable
private fun AdvancedFiltersSection(
    expanded: Boolean,
    filters: HfFilters,
    onToggle: () -> Unit,
    onPipelineTagChange: (String?) -> Unit,
    onLibraryToggle: (String) -> Unit,
    onAppToggle: (String) -> Unit,
    onProviderToggle: (String) -> Unit,
    onLanguageToggle: (String) -> Unit,
    onLicenseToggle: (String) -> Unit,
    onRegionToggle: (String) -> Unit,
    onOtherTagToggle: (String) -> Unit,
    onQuantToggle: (String) -> Unit,
    onAuthorChange: (String) -> Unit,
    onTrainedDatasetChange: (String) -> Unit,
    onParamsRangeChange: (Long, Long) -> Unit,
    onReset: () -> Unit,
) {
    val dimens = LocalDimens.current
    val activeCount = filters.activeCount
    StandardCard(
        title = if (activeCount == 0) "Advanced filters" else "Advanced filters · $activeCount active",
        icon = TnIcons.Wrench,
        trailing = {
            ActionButton(
                onClickListener = onToggle,
                icon = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        },
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {

                FilterGroupHeader("Parameter count")
                ParamRangeSlider(
                    minMillions = filters.paramsMinMillions,
                    maxMillions = filters.paramsMaxMillions,
                    onChange = onParamsRangeChange,
                )

                FilterGroupHeader("Task")
                ChipFlow(
                    values = HfFilterTaxonomy.PIPELINE_TAGS_TOP,
                    moreValues = HfFilterTaxonomy.PIPELINE_TAGS_ALL.minus(HfFilterTaxonomy.PIPELINE_TAGS_TOP.toSet()),
                    isSelected = { it == filters.pipelineTag },
                    onToggle = { tag ->
                        onPipelineTagChange(if (filters.pipelineTag == tag) null else tag)
                    },
                    label = HfFilterTaxonomy::pipelineTagLabel,
                )

                FilterGroupHeader("Libraries")
                ChipFlow(
                    values = HfFilterTaxonomy.LIBRARIES_TOP,
                    moreValues = HfFilterTaxonomy.LIBRARIES_ALL.minus(HfFilterTaxonomy.LIBRARIES_TOP.toSet()),
                    isSelected = { filters.libraries.contains(it) },
                    onToggle = onLibraryToggle,
                )

                FilterGroupHeader("Quantization")
                ChipFlow(
                    values = HfFilterTaxonomy.QUANT_TAGS,
                    moreValues = emptyList(),
                    isSelected = { filters.quantTags.contains(it) },
                    onToggle = onQuantToggle,
                )

                FilterGroupHeader("Apps")
                ChipFlow(
                    values = HfFilterTaxonomy.APPS,
                    moreValues = emptyList(),
                    isSelected = { filters.apps.contains(it) },
                    onToggle = onAppToggle,
                )

                FilterGroupHeader("Inference providers")
                ChipFlow(
                    values = HfFilterTaxonomy.INFERENCE_PROVIDERS,
                    moreValues = emptyList(),
                    isSelected = { filters.providers.contains(it) },
                    onToggle = onProviderToggle,
                )

                FilterGroupHeader("Languages")
                ChipFlow(
                    values = HfFilterTaxonomy.LANGUAGES_TOP,
                    moreValues = HfFilterTaxonomy.LANGUAGES_ALL.minus(HfFilterTaxonomy.LANGUAGES_TOP.toSet()),
                    isSelected = { filters.languages.contains(it) },
                    onToggle = onLanguageToggle,
                    label = HfFilterTaxonomy::languageLabel,
                )

                FilterGroupHeader("Licenses")
                ChipFlow(
                    values = HfFilterTaxonomy.LICENSES_TOP,
                    moreValues = HfFilterTaxonomy.LICENSES_ALL.minus(HfFilterTaxonomy.LICENSES_TOP.toSet()),
                    isSelected = { filters.licenses.contains(it) },
                    onToggle = onLicenseToggle,
                )

                FilterGroupHeader("Regions")
                ChipFlow(
                    values = HfFilterTaxonomy.REGIONS,
                    moreValues = emptyList(),
                    isSelected = { filters.regions.contains(it) },
                    onToggle = onRegionToggle,
                    label = { if (it == "us") "🇺🇸 US" else if (it == "eu") "🇪🇺 EU" else it },
                )

                FilterGroupHeader("Other tags")
                ChipFlow(
                    values = HfFilterTaxonomy.OTHER_TAGS,
                    moreValues = emptyList(),
                    isSelected = { filters.otherTags.contains(it) },
                    onToggle = onOtherTagToggle,
                )

                FilterGroupHeader("Author contains")
                LightInputBox {
                    TnTextField(
                        value = filters.author,
                        onValueChange = onAuthorChange,
                        placeholder = "bartowski, ggml-org, meta-llama…",
                        singleLine = true,
                        maxLines = 1,
                    )
                }

                FilterGroupHeader("Trained on dataset")
                LightInputBox {
                    TnTextField(
                        value = filters.trainedDataset,
                        onValueChange = onTrainedDatasetChange,
                        placeholder = "imagenet, common-voice, glue…",
                        singleLine = true,
                        maxLines = 1,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    ActionTextButton(
                        onClickListener = onReset,
                        icon = TnIcons.Refresh,
                        text = "Reset filters",
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun LightInputBox(content: @Composable () -> Unit) {
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun ChipFlow(
    values: List<String>,
    moreValues: List<String>,
    isSelected: (String) -> Boolean,
    onToggle: (String) -> Unit,
    label: (String) -> String = { it },
) {
    val dimens = LocalDimens.current
    var expanded by remember { mutableStateOf(false) }
    val all = if (expanded) values + moreValues else values
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)) {
                all.forEach { v ->
                    Pill(text = label(v), selected = isSelected(v), onClick = { onToggle(v) })
                }
                if (!expanded && moreValues.isNotEmpty()) {
                    Pill(
                        text = "+${moreValues.size} more",
                        selected = false,
                        onClick = { expanded = true },
                    )
                }
                if (expanded && moreValues.isNotEmpty()) {
                    Pill(
                        text = "Less",
                        selected = false,
                        onClick = { expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParamRangeSlider(
    minMillions: Long,
    maxMillions: Long,
    onChange: (Long, Long) -> Unit,
) {
    val dimens = LocalDimens.current
    val steps = HfFilterTaxonomy.PARAM_STEPS_MILLIONS
    val maxIndex = steps.size - 1

    val initialMin = remember(minMillions) {
        steps.indexOfFirst { it >= minMillions }.coerceIn(0, maxIndex).toFloat()
    }
    val initialMax = remember(maxMillions) {
        if (maxMillions == 0L) maxIndex.toFloat()
        else steps.indexOfLast { it <= maxMillions }.coerceAtLeast(0).toFloat()
    }
    var range by remember { mutableStateOf(initialMin..initialMax) }

    val minLabel = HfFilterTaxonomy.paramStepLabel(steps[range.start.toInt()])
    val maxIdx = range.endInclusive.toInt()
    val maxLabel = if (maxIdx == maxIndex) "∞" else HfFilterTaxonomy.paramStepLabel(steps[maxIdx])

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$minLabel — $maxLabel",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (minMillions > 0L || maxMillions > 0L) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            range = 0f..maxIndex.toFloat()
                            onChange(0L, 0L)
                        }
                        .padding(horizontal = dimens.spacingSm, vertical = dimens.spacingXxs),
                )
            }
        }
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..maxIndex.toFloat(),
            steps = maxIndex - 1,
            onValueChangeFinished = {
                val lo = steps[range.start.toInt()]
                val hi = if (range.endInclusive.toInt() == maxIndex) 0L
                else steps[range.endInclusive.toInt()]
                onChange(lo, hi)
            },
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            ),
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.primary
    Surface(
        shape = tnShapes.full,
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingXs,
            ),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LoadingRow() {
    val dimens = LocalDimens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        modifier = Modifier.padding(horizontal = dimens.spacingSm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
        )
        CaptionText(text = "Searching HuggingFace…")
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        Icon(
            imageVector = TnIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        CaptionText(text = detail)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(dimens.spacingXxs))
            ActionTextButton(
                onClickListener = onAction,
                icon = TnIcons.Refresh,
                text = actionLabel,
            )
        }
    }
}

@Composable
private fun ExplorerCard(
    repo: ExplorerRepo,
    isAdded: Boolean,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.cardSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.padding(dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = repo.author.take(2).uppercase().ifBlank { "??" },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        repo.id,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                    ) {
                        CaptionText(text = repo.author)
                        if (repo.gated) {
                            Surface(
                                shape = tnShapes.full,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    "Gated",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                if (isAdded) {
                    Box(
                        modifier = Modifier
                            .size(dimens.actionIconSize)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                RoundedCornerShape(50),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            TnIcons.Check,
                            contentDescription = "Added",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    ActionButton(
                        onClickListener = onAdd,
                        icon = TnIcons.Plus,
                        contentDescription = "Add to repositories",
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatPill(label = "↓", value = formatCount(repo.downloads))
                StatPill(label = "♥", value = repo.likes.toString())
                if (repo.pipelineTag != null) {
                    StatPill(label = "•", value = HfFilterTaxonomy.pipelineTagLabel(repo.pipelineTag))
                }
                Spacer(Modifier.weight(1f))
                CaptionText(text = "Inspect →")
            }
            if (repo.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                ) {
                    repo.tags.take(8).forEach { tag ->
                        Surface(
                            shape = tnShapes.full,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = dimens.spacingSm,
                vertical = 2.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

# Phase 08 — UI: Character Create Screen

`CharacterCreateScreen` is a four-step wizard for creating and editing
characters. It is full-screen (no bottom bar per Phase 06) and reuses the
draft state in `CharacterViewModel`.

The same screen handles **new** and **edit** flows — when a `characterId`
query parameter is passed, the VM loads the existing character into the
draft.

---

## 1. Navigation setup — optional `characterId` argument

Before writing the screen file, update the `TNavigation.kt` composable
entry for `CharacterCreate` to accept an optional query param:

```kotlin
composable(
    route = NavScreens.CharacterCreate.route + "?characterId={characterId}",
    arguments = listOf(
        navArgument("characterId") {
            type = NavType.StringType
            defaultValue = ""
            nullable = false
        },
    ),
) { backStackEntry ->
    val characterId = backStackEntry.arguments?.getString("characterId").orEmpty()
    val charVm: CharacterViewModel = hiltViewModel()
    CharacterCreateScreen(
        characterId = characterId,
        onBack = { navController.popBackStack() },
        onSaved = { savedId ->
            // Pop create + go to detail
            navController.popBackStack()
            navController.navigate(NavScreens.CharacterDetail.routeFor(savedId))
        },
        viewModel = charVm,
    )
}
```

---

## 2. `ui/screens/character_create/CharacterCreateScreen.kt`

Create file:
`app/src/main/java/com/dark/tool_neuron/ui/screens/character_create/CharacterCreateScreen.kt`

```kotlin
package com.dark.tool_neuron.ui.screens.character_create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dark.tool_neuron.viewmodel.CharacterViewModel

// Total number of wizard steps
private const val TOTAL_STEPS = 4

@Composable
fun CharacterCreateScreen(
    characterId: String = "",
    onBack: () -> Unit,
    onSaved: (savedId: String) -> Unit,
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    // Load existing character into draft (edit flow)
    LaunchedEffect(characterId) {
        if (characterId.isNotBlank()) {
            viewModel.loadDraft(characterId)
        } else {
            viewModel.resetDraft()
        }
    }

    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var currentStep by remember { mutableIntStateOf(0) }

    val isEditMode = characterId.isNotBlank()
    val screenTitle = if (isEditMode) "Edit Character" else "New Character"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = screenTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stepTitle(currentStep),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
        bottomBar = {
            StepNavigationBar(
                currentStep = currentStep,
                totalSteps = TOTAL_STEPS,
                isSaving = isSaving,
                canGoNext = canGoNext(currentStep, draft.name, draft.personality),
                onPrev = { if (currentStep > 0) currentStep-- },
                onNext = {
                    if (currentStep < TOTAL_STEPS - 1) {
                        currentStep++
                    } else {
                        viewModel.saveCharacter { savedId -> onSaved(savedId) }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->

        // Step progress indicator
        LinearProgressIndicator(
            progress = { (currentStep + 1).toFloat() / TOTAL_STEPS.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = innerPadding.calculateTopPadding()),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "step_content",
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = innerPadding.calculateBottomPadding(),
                ),
        ) { step ->
            when (step) {
                0 -> StepBasicInfo(
                    name = draft.name,
                    chatName = draft.chatName,
                    bio = draft.bio,
                    onNameChange = viewModel::updateDraftName,
                    onChatNameChange = viewModel::updateDraftChatName,
                    onBioChange = viewModel::updateDraftBio,
                )
                1 -> StepPersonality(
                    personality = draft.personality,
                    onPersonalityChange = viewModel::updateDraftPersonality,
                )
                2 -> StepScenarioAndDialog(
                    scenario = draft.scenario,
                    initialMessage = draft.initialMessage,
                    exampleDialogs = draft.exampleDialogs,
                    onScenarioChange = viewModel::updateDraftScenario,
                    onInitialMessageChange = viewModel::updateDraftInitialMessage,
                    onExampleDialogsChange = viewModel::updateDraftExampleDialogs,
                )
                3 -> StepTagsAndAvatar(
                    tags = draft.tags,
                    avatarUri = draft.avatarUri,
                    chatName = draft.chatName.ifBlank { draft.name },
                    onTagsChange = viewModel::updateDraftTags,
                    onAvatarUriChange = viewModel::updateDraftAvatarUri,
                )
                else -> Unit
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Step 0 — Basic Info
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepBasicInfo(
    name: String,
    chatName: String,
    bio: String,
    onNameChange: (String) -> Unit,
    onChatNameChange: (String) -> Unit,
    onBioChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHint("The character's full name and the name they go by in chat.")

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full name") },
            placeholder = { Text("e.g. Jace Mercer") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            supportingText = { Text("The character's full legal or world name") },
        )

        OutlinedTextField(
            value = chatName,
            onValueChange = onChatNameChange,
            label = { Text("Chat name") },
            placeholder = { Text("e.g. Jace (defaults to full name)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            supportingText = { Text("The name used inside the system prompt ({{char}})") },
        )

        OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            label = { Text("Bio / Background") },
            placeholder = { Text("Age, occupation, history, appearance…") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            minLines = 4,
            maxLines = 10,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            supportingText = { Text("Injected as CHARACTER PROFILE → Background") },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Step 1 — Personality
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepPersonality(
    personality: String,
    onPersonalityChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHint(
            "Describe how the character thinks, speaks, and behaves. " +
            "Be specific — this section has the strongest effect on character voice."
        )

        OutlinedTextField(
            value = personality,
            onValueChange = onPersonalityChange,
            label = { Text("Personality") },
            placeholder = { Text("Traits, speech patterns, habits, fears, desires…") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            minLines = 6,
            maxLines = 20,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
        )

        // Quick trait chips for inspiration
        Text(
            text = "Quick traits",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val quickTraits = listOf(
            "Sarcastic", "Protective", "Cold", "Flirtatious", "Blunt",
            "Caring", "Mysterious", "Arrogant", "Playful", "Stoic",
            "Jealous", "Loyal", "Manipulative", "Gentle", "Reckless",
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickTraits.forEach { trait ->
                FilterChip(
                    selected = personality.contains(trait, ignoreCase = true),
                    onClick = {
                        if (!personality.contains(trait, ignoreCase = true)) {
                            val separator = if (personality.isBlank()) "" else ", "
                            onPersonalityChange("$personality$separator$trait".trim())
                        }
                    },
                    label = { Text(trait, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Step 2 — Scenario & Dialog
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepScenarioAndDialog(
    scenario: String,
    initialMessage: String,
    exampleDialogs: String,
    onScenarioChange: (String) -> Unit,
    onInitialMessageChange: (String) -> Unit,
    onExampleDialogsChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHint("The world context the story begins in, the character's opening line, and example interactions.")

        OutlinedTextField(
            value = scenario,
            onValueChange = onScenarioChange,
            label = { Text("Scenario") },
            placeholder = { Text("Where and when the story takes place. What situation are they in?") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            minLines = 3,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            supportingText = { Text("Injected as CURRENT SCENARIO in the system prompt") },
        )

        OutlinedTextField(
            value = initialMessage,
            onValueChange = onInitialMessageChange,
            label = { Text("Opening message") },
            placeholder = {
                Text(
                    "*He looks up from his desk as you walk in.*\n\"You're late.\""
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            minLines = 3,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            supportingText = { Text("The first assistant message shown when a chat opens. Use *actions* and \"dialogue\".") },
        )

        OutlinedTextField(
            value = exampleDialogs,
            onValueChange = onExampleDialogsChange,
            label = { Text("Example dialogs") },
            placeholder = {
                Text(
                    "{{user}}: Hey, can we talk?\n" +
                    "{{char}}: *glances up, expression unreadable* \"Depends what you want.\"\n\n" +
                    "{{user}}: You seem upset.\n" +
                    "{{char}}: \"I'm fine. Don't make it weird.\""
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            minLines = 4,
            maxLines = 20,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
            ),
            supportingText = { Text("Use {{char}} and {{user}} — they are replaced automatically.") },
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Step 3 — Tags & Avatar
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepTagsAndAvatar(
    tags: List<String>,
    avatarUri: String,
    chatName: String,
    onTagsChange: (List<String>) -> Unit,
    onAvatarUriChange: (String) -> Unit,
) {
    var tagInput by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) onAvatarUriChange(uri.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        StepHint("Optional — an avatar image and searchable tags to organise characters.")

        // ── Avatar picker ──────────────────────────────────────
        Text(
            text = "Avatar",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Preview
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUri.isNotBlank()) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = chatName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AddAPhoto,
                        contentDescription = "Pick avatar",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                    Text("Choose Image")
                }
                if (avatarUri.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onAvatarUriChange("") },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Remove")
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Tags ──────────────────────────────────────────────
        Text(
            text = "Tags",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = tagInput,
                onValueChange = { tagInput = it.take(24) },
                label = { Text("Add tag") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                trailingIcon = {
                    if (tagInput.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val t = tagInput.trim()
                                if (t.isNotBlank() && !tags.contains(t)) {
                                    onTagsChange(tags + t)
                                }
                                tagInput = ""
                            },
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add")
                        }
                    }
                },
            )
        }

        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                        trailingIcon = {
                            IconButton(
                                onClick = { onTagsChange(tags - tag) },
                                modifier = Modifier.size(16.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove $tag",
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        },
                    )
                }
            }
        }

        // Preset tags
        val presetTags = listOf("Romance", "Action", "Fantasy", "Sci-fi", "Slice of life", "Dark", "Comedy", "Tsundere", "Yandere", "OC")
        Text(
            text = "Preset tags",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presetTags.forEach { preset ->
                val already = tags.contains(preset)
                SuggestionChip(
                    onClick = {
                        if (!already) onTagsChange(tags + preset)
                        else onTagsChange(tags - preset)
                    },
                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                    enabled = !already,
                    modifier = Modifier.height(28.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Step navigation bar
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepNavigationBar(
    currentStep: Int,
    totalSteps: Int,
    isSaving: Boolean,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val isLastStep = currentStep == totalSteps - 1

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Step dots
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(totalSteps) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentStep)
                                    MaterialTheme.colorScheme.primary
                                else if (index < currentStep)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.outline,
                            ),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentStep > 0) {
                    OutlinedButton(onClick = onPrev) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                }

                Button(
                    onClick = onNext,
                    enabled = canGoNext && !isSaving,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else if (isLastStep) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    } else {
                        Text("Next")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────

@Composable
private fun StepHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
    )
}

private fun stepTitle(step: Int): String = when (step) {
    0 -> "Step 1 of 4 — Basic Info"
    1 -> "Step 2 of 4 — Personality"
    2 -> "Step 3 of 4 — Scenario & Dialog"
    3 -> "Step 4 of 4 — Tags & Avatar"
    else -> ""
}

private fun canGoNext(step: Int, name: String, personality: String): Boolean = when (step) {
    0 -> name.isNotBlank()       // name is required to proceed past step 0
    1 -> personality.isNotBlank() // personality is required
    else -> true
}
```

---

## 3. Verification

```bash
./gradlew :app:compileDebugKotlin
```

After this phase only `RoleplayChatScreen` remains unresolved in
`TNavigation.kt`. Everything else compiles.

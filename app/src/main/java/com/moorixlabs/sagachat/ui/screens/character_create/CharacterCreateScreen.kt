package com.moorixlabs.sagachat.ui.screens.character_create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.components.CharacterAvatar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moorixlabs.sagachat.viewmodel.CharacterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCreateScreen(
    editCharacterId: String? = null,
    onBack: () -> Unit,
    onSaved: (characterId: String) -> Unit,
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    val isEditMode = editCharacterId != null
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.updateDraftAvatarUri(uri.toString())
        }
    }

    LaunchedEffect(editCharacterId) {
        if (editCharacterId != null) {
            viewModel.loadDraft(editCharacterId)
        } else {
            viewModel.resetDraft()
        }
    }

    // Tag input state
    var tagInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "Edit Character" else "New Character",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetDraft()
                        onBack()
                    }) {
                        Icon(TnIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.saveCharacter(onSaved)
                            },
                            enabled = draft.name.isNotBlank() && draft.chatName.isNotBlank(),
                        ) {
                            Icon(
                                TnIcons.Check,
                                contentDescription = "Save",
                                tint = if (draft.name.isNotBlank() && draft.chatName.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // Avatar picker row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    CharacterAvatar(
                        avatarUri = draft.avatarUri,
                        contentDescription = "Profile Image",
                        modifier = Modifier.fillMaxSize(),
                        fallbackIcon = TnIcons.HatGlasses,
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Character Portrait",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Choose Image", style = MaterialTheme.typography.labelMedium)
                        }
                        if (draft.avatarUri.isNotBlank()) {
                            TextButton(
                                onClick = { viewModel.updateDraftAvatarUri("") },
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Remove", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            SectionLabel(text = "Identity")

            FormField(
                value = draft.name,
                onValueChange = viewModel::updateDraftName,
                label = "Full name *",
                placeholder = "e.g. Elara Nightwood",
                singleLine = true,
            )

            FormField(
                value = draft.chatName,
                onValueChange = viewModel::updateDraftChatName,
                label = "Display name *",
                placeholder = "How {{char}} refers to herself (e.g. Elara)",
                singleLine = true,
                supportingText = "Used in prompts and the chat screen header.",
            )

            SectionLabel(text = "Profile")

            FormField(
                value = draft.bio,
                onValueChange = viewModel::updateDraftBio,
                label = "Bio",
                placeholder = "Brief background: origin, occupation, history.",
                minLines = 3,
            )

            FormField(
                value = draft.personality,
                onValueChange = viewModel::updateDraftPersonality,
                label = "Personality",
                placeholder = "Traits, mannerisms, speech style, values.",
                minLines = 3,
                supportingText = "Tip: use comma-separated adjectives, e.g. \"cold, sharp, guarded, secretly caring\"",
            )

            FormField(
                value = draft.scenario,
                onValueChange = viewModel::updateDraftScenario,
                label = "Scenario",
                placeholder = "Where and when does the story begin? Set the stage.",
                minLines = 3,
            )

            SectionLabel(text = "Opening")

            FormField(
                value = draft.initialMessage,
                onValueChange = viewModel::updateDraftInitialMessage,
                label = "Initial message",
                placeholder = "First message {{char}} sends. Use *actions* and \"dialogue\".",
                minLines = 4,
            )

            FormField(
                value = draft.exampleDialogs,
                onValueChange = viewModel::updateDraftExampleDialogs,
                label = "Example dialogs (optional)",
                placeholder = "{{user}}: Hi there.\n{{char}}: *glances up* What do you want?\n{{user}}: ...\n{{char}}: I said, what do you want.",
                minLines = 4,
                supportingText = "Use {{char}} and {{user}} as placeholders.",
            )

            SectionLabel(text = "Tags")

            // Tag chip input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Add tag") },
                    placeholder = { Text("e.g. fantasy, tsundere") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                )
                TextButton(
                    onClick = {
                        val clean = tagInput.trim().lowercase()
                        if (clean.isNotBlank() && !draft.tags.contains(clean)) {
                            viewModel.updateDraftTags(draft.tags + clean)
                        }
                        tagInput = ""
                    },
                    enabled = tagInput.isNotBlank(),
                ) { Text("Add") }
            }

            if (draft.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    draft.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        viewModel.updateDraftTags(draft.tags - tag)
                                    },
                                    modifier = Modifier.size(16.dp),
                                ) {
                                    Icon(
                                        TnIcons.X,
                                        contentDescription = "Remove $tag",
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Helper composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
    )
}

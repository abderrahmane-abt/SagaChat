package com.moorixlabs.sagachat.ui.screens.character_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.util.shareJsonFile
import com.moorixlabs.sagachat.viewmodel.CharacterViewModel

@Composable
fun CharacterDetailScreen(
    characterId: String,
    onBack: () -> Unit,
    onStartChat: (characterId: String) -> Unit,
    onEdit: (characterId: String) -> Unit,
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    val character = remember(characterId) { viewModel.getCharacter(characterId) }
    val context = LocalContext.current

    if (character == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${character.chatName}?") },
            text = { Text("This will delete the character and all associated memories. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCharacter(characterId)
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Start New Chat?") },
            text = { Text("This will permanently delete the current conversation and all associated memories with ${character.chatName}. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.startNewChat(characterId)
                        showWipeDialog = false
                        onStartChat(characterId)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Start Fresh") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEdit(characterId) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(TnIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
                if (character.linkedChatId.isNotBlank()) {
                    OutlinedButton(
                        onClick = { showWipeDialog = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(TnIcons.Trash, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New")
                    }
                }
                Button(
                    onClick = { onStartChat(characterId) },
                    modifier = Modifier.weight(if (character.linkedChatId.isNotBlank()) 1.2f else 2f),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(TnIcons.MessageCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (character.linkedChatId.isNotBlank()) "Resume" else "Start Chat")
                }
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Hero header ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Icon(
                    imageVector = TnIcons.HatGlasses,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .size(80.dp)
                        .align(Alignment.Center),
                )
                // Back + Delete row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        ),
                    ) {
                        Icon(TnIcons.ArrowLeft, contentDescription = "Back")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = {
                                val exportJson = viewModel.exportJson(characterId)
                                val exportFileName = viewModel.exportFileName(characterId)
                                if (exportJson != null) {
                                    shareJsonFile(context, exportJson, exportFileName)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            ),
                        ) {
                            Icon(
                                TnIcons.Share,
                                contentDescription = "Export",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            ),
                        ) {
                            Icon(
                                TnIcons.Trash,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                // Name overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column {
                        Text(
                            text = character.chatName.ifBlank { character.name },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (character.chatName.isNotBlank() && character.name != character.chatName) {
                            Text(
                                text = character.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Tags ──────────────────────────────────────────────────
            if (character.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    character.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(26.dp),
                        )
                    }
                }
            }

            // ── Profile sections ──────────────────────────────────────
            data class Section(val title: String, val icon: ImageVector, val content: String)

            val sections: List<Section> = buildList {
                if (character.bio.isNotBlank())
                    add(Section("Bio", TnIcons.Info, character.bio))
                if (character.personality.isNotBlank())
                    add(Section("Personality", TnIcons.HatGlasses, character.personality))
                if (character.scenario.isNotBlank())
                    add(Section("Scenario", TnIcons.Compass, character.scenario))
                if (character.initialMessage.isNotBlank())
                    add(Section("Opening Message", TnIcons.MessageCircle, character.initialMessage))
                if (character.exampleDialogs.isNotBlank())
                    add(Section("Example Dialogs", TnIcons.Edit, character.exampleDialogs))
            }

            sections.forEach { section ->
                ProfileSection(title = section.title, icon = section.icon, content = section.content)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Profile section card
// ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: String,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

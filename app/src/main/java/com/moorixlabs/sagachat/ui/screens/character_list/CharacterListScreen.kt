package com.moorixlabs.sagachat.ui.screens.character_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moorixlabs.sagachat.model.Character
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.components.CharacterAvatar
import com.moorixlabs.sagachat.viewmodel.CharacterViewModel

@Composable
fun CharacterListScreen(
    innerPadding: PaddingValues,
    onOpenCharacter: (characterId: String) -> Unit,
    onCreateCharacter: () -> Unit,
    onImportCharacter: () -> Unit,
    onMenuClick: () -> Unit,
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Characters", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(TnIcons.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onImportCharacter) {
                        Icon(TnIcons.FileImport, contentDescription = "Import character")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateCharacter,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(TnIcons.Plus, contentDescription = "Create character")
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->
        if (characters.isEmpty()) {
            CharacterEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(scaffoldPadding),
                onCreateClick = onCreateCharacter,
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(scaffoldPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(characters, key = { it.id }) { character ->
                    CharacterCard(
                        character = character,
                        onClick = { onOpenCharacter(character.id) },
                    )
                }
                // Bottom spacer so FAB doesn't cover last row
                item { Spacer(Modifier.height(80.dp)) }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Character Card
// ─────────────────────────────────────────────────────────────

@Composable
private fun CharacterCard(
    character: Character,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Avatar
            CharacterAvatar(
                avatarUri = character.avatarUri,
                contentDescription = character.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                fallbackIcon = TnIcons.HatGlasses,
            )

            // Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = character.chatName.ifBlank { character.name },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (character.bio.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = character.bio,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (character.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    TagChipRow(tags = character.tags.take(3))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Tag chip row
// ─────────────────────────────────────────────────────────────

@Composable
private fun TagChipRow(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { tag ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.height(18.dp),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────

@Composable
private fun CharacterEmptyState(
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No characters yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tap + to create your first character",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreateClick) {
            Icon(TnIcons.Plus, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create Character")
        }
    }
}

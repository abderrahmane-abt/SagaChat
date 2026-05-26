# Phase 07 — UI: Character List & Character Detail Screens

Two screens. `CharacterListScreen` is the app's home tab — it shows all
saved characters in a card grid and hosts the FAB to create a new one.
`CharacterDetailScreen` is a full profile viewer with actions to start a
chat, edit, or delete.

Both screens live in their own sub-packages under `ui/screens/`.

---

## 1. `ui/screens/character_list/CharacterListScreen.kt`

Create file:
`app/src/main/java/com/dark/tool_neuron/ui/screens/character_list/CharacterListScreen.kt`

```kotlin
package com.dark.tool_neuron.ui.screens.character_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dark.tool_neuron.model.Character
import com.dark.tool_neuron.viewmodel.CharacterViewModel

@Composable
fun CharacterListScreen(
    innerPadding: PaddingValues,
    onCharacterClick: (characterId: String) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create character")
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
                onCreateClick = onCreateClick,
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
                        onClick = { onCharacterClick(character.id) },
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (character.avatarUri.isNotBlank()) {
                    AsyncImage(
                        model = character.avatarUri,
                        contentDescription = character.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

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
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Create Character")
        }
    }
}
```

---

## 2. `ui/screens/character_detail/CharacterDetailScreen.kt`

Create file:
`app/src/main/java/com/dark/tool_neuron/ui/screens/character_detail/CharacterDetailScreen.kt`

This screen is full-height (no top bar — it manages its own header as
specified in Phase 06). It shows the complete character card and two
primary actions: **Chat** and **Edit**.

```kotlin
package com.dark.tool_neuron.ui.screens.character_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.dark.tool_neuron.model.Character
import com.dark.tool_neuron.viewmodel.CharacterViewModel

@Composable
fun CharacterDetailScreen(
    characterId: String,
    onBack: () -> Unit,
    onStartChat: (characterId: String) -> Unit,
    onEdit: (characterId: String) -> Unit,
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    val character = remember(characterId) { viewModel.getCharacter(characterId) }

    if (character == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Primary actions pinned at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onEdit(characterId) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
                Button(
                    onClick = { onStartChat(characterId) },
                    modifier = Modifier.weight(2f),
                ) {
                    Icon(Icons.Rounded.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Chat")
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
                    .height(280.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                if (character.avatarUri.isNotBlank()) {
                    AsyncImage(
                        model = character.avatarUri,
                        contentDescription = character.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .size(80.dp)
                            .align(Alignment.Center),
                    )
                }
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        ),
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                // Name overlay at bottom of hero
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
            val sections = buildList {
                if (character.bio.isNotBlank())
                    add(Triple("Bio", Icons.Rounded.Info, character.bio))
                if (character.personality.isNotBlank())
                    add(Triple("Personality", Icons.Rounded.Psychology, character.personality))
                if (character.scenario.isNotBlank())
                    add(Triple("Scenario", Icons.Rounded.Landscape, character.scenario))
                if (character.initialMessage.isNotBlank())
                    add(Triple("Opening Message", Icons.Rounded.Forum, character.initialMessage))
                if (character.exampleDialogs.isNotBlank())
                    add(Triple("Example Dialogs", Icons.Rounded.Chat, character.exampleDialogs))
            }

            sections.forEach { (title, icon, content) ->
                ProfileSection(title = title, icon = icon, content = content)
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
    var expanded by remember { mutableStateOf(true) }

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
            // Header row — tappable to collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
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
```

---

## 3. Wire screens into `TNavigation.kt`

Open `ui/navigation/TNavigation.kt`. Replace the stub placeholders for
`CharacterList` and `CharacterDetail` with:

```kotlin
composable(NavScreens.CharacterList.route) {
    val charVm: CharacterViewModel = hiltViewModel()
    CharacterListScreen(
        innerPadding = innerPadding,
        onCharacterClick = { id -> navController.navigate(NavScreens.CharacterDetail.routeFor(id)) },
        onCreateClick = { navController.navigate(NavScreens.CharacterCreate.route) },
        viewModel = charVm,
    )
}

composable(
    route = NavScreens.CharacterDetail.route,
    arguments = listOf(navArgument(NavScreens.CharacterDetail.ARG_CHARACTER_ID) {
        type = NavType.StringType
    }),
) { backStackEntry ->
    val characterId = backStackEntry.arguments?.getString(NavScreens.CharacterDetail.ARG_CHARACTER_ID).orEmpty()
    CharacterDetailScreen(
        characterId = characterId,
        onBack = { navController.popBackStack() },
        onStartChat = { id -> navController.navigate(NavScreens.RoleplayChat.routeFor(id)) },
        onEdit = { id ->
            navController.navigate(NavScreens.CharacterCreate.route + "?characterId=$id")
        },
    )
}
```

> **Note on edit navigation:** `CharacterCreate.route` is reused for edit
> by passing an optional `?characterId=...` query parameter. Phase 08
> handles the `SavedStateHandle` read.

---

## 4. Required dependency — Coil

The screens use `coil.compose.AsyncImage` for avatar display.
Confirm `coil-compose` is in `app/build.gradle.kts`:

```kotlin
implementation("io.coil-kt:coil-compose:2.6.0")
```

If it's already present (it's a common dep in the existing app), no change
needed.

---

## 5. Verification

```bash
./gradlew :app:compileDebugKotlin
```

After this phase only `CharacterCreateScreen` and `RoleplayChatScreen`
remain as unresolved symbols in `TNavigation.kt`. Both are built in
Phases 08 and 09.

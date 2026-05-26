# Phase 06 — AppScaffold, Top Bar & Bottom Bar Wiring

Adapt the existing `AppScaffold`, `AppTopBar`, and `AppBottomBar` (or
equivalent scaffold composables) so the navigation entry point lands on
`CharacterList` instead of `HomeScreen`, and the bottom bar shows the
correct tabs for a roleplay app.

---

## 1. Locate the scaffold entry point

Find the file that contains the `AppScaffold` composable (usually
`ui/AppScaffold.kt` or `ui/MainScreen.kt`) and the `MainActivity.kt`.

In `MainActivity.kt` or wherever `startDestination` is decided, look for
logic like:

```kotlin
val startDestination = when {
    !setupComplete -> NavScreens.SetupScreen.route
    locked         -> NavScreens.PasswordScreen.route
    else           -> NavScreens.HomeScreen.route   // ← change this
}
```

Change the final `else` branch to:

```kotlin
else -> NavScreens.CharacterList.route
```

---

## 2. Update the bottom navigation bar

Find `AppBottomBar.kt` (or wherever bottom tab items are defined).
Replace all existing tab items with these four:

```kotlin
data class BottomTab(
    val route: String,
    val labelRes: String,
    val icon: ImageVector,
)

val rpBottomTabs = listOf(
    BottomTab(
        route    = NavScreens.CharacterList.route,
        labelRes = "Characters",
        icon     = Icons.Rounded.People,
    ),
    BottomTab(
        route    = NavScreens.ModelStore.route,
        labelRes = "Models",
        icon     = Icons.Rounded.Memory,
    ),
    BottomTab(
        route    = NavScreens.Storage.route,
        labelRes = "Storage",
        icon     = Icons.Rounded.FolderOpen,
    ),
    BottomTab(
        route    = NavScreens.Settings.route,
        labelRes = "Settings",
        icon     = Icons.Rounded.Settings,
    ),
)
```

The bottom bar must be hidden on these routes (full-screen experiences):
```kotlin
val hideBottomBarRoutes = setOf(
    NavScreens.IntroScreen.route,
    NavScreens.PasswordScreen.route,
    NavScreens.SetupScreen.route,
    NavScreens.SetupTheme.route,
    NavScreens.ModelSetup.route,
    NavScreens.RoleplayChat.route,       // ← hide during active chat
    NavScreens.CharacterCreate.route,    // ← hide during creation
)
```

To check if the current route matches `RoleplayChat` (which has args):
```kotlin
val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
val showBottomBar = hideBottomBarRoutes.none { currentRoute?.startsWith(it.substringBefore("{")) == true }
```

---

## 3. Update the top app bar

Find `AppTopBar.kt`. The top bar title and actions should be route-aware.

Add a helper that returns the title for each route:

```kotlin
fun topBarTitleFor(route: String?): String = when {
    route == null                                       -> ""
    route == NavScreens.CharacterList.route             -> "Characters"
    route == NavScreens.ModelStore.route                -> "Models"
    route == NavScreens.HfExplorer.route                -> "Browse HuggingFace"
    route == NavScreens.ModelManager.route              -> "Installed Models"
    route == NavScreens.Downloads.route                 -> "Downloads"
    route == NavScreens.Settings.route                  -> "Settings"
    route == NavScreens.Storage.route                   -> "Storage"
    route == NavScreens.Credits.route                   -> "Credits"
    route.startsWith("character_detail")                -> ""   // detail has own header
    route.startsWith("roleplay_chat")                   -> ""   // chat has own header
    route.startsWith("character_create")                -> "New Character"
    route.startsWith("model_config")                    -> "Model Config"
    route.startsWith("hf_repo")                         -> "Repository"
    route.startsWith("settings_")                       -> "Settings"
    else                                                -> ""
}
```

Hide the top bar on these routes (screens manage their own header):
```kotlin
val hideTopBarRoutes = setOf(
    NavScreens.IntroScreen.route,
    NavScreens.PasswordScreen.route,
    NavScreens.SetupScreen.route,
    NavScreens.SetupTheme.route,
    NavScreens.ModelSetup.route,
)
val showTopBar = hideTopBarRoutes.none { currentRoute?.startsWith(it) == true }
    && !currentRoute.orEmpty().startsWith("roleplay_chat")
    && !currentRoute.orEmpty().startsWith("character_detail")
```

---

## 4. Settings screen — remove dead sections

Open `ui/screens/settings/SettingsScreen.kt`. Remove every `SettingsItem`
or navigation row that points to a deleted route:

- `NavScreens.SettingsVoice`
- `NavScreens.SettingsVision`
- `NavScreens.SettingsPlugins`
- `NavScreens.SettingsChatRag`
- `NavScreens.GuideServer`
- `NavScreens.GuideRag`
- `NavScreens.GuideVoice`
- `NavScreens.GuidePlugins`
- `NavScreens.GuideImages`

Add a new settings item for Roleplay settings pointing to
`NavScreens.SettingsChatRp.route`:

```kotlin
SettingsItem(
    title    = "Roleplay",
    subtitle = "Display name, response length, memory",
    icon     = Icons.Rounded.AutoAwesome,
    onClick  = { onNavigate(NavScreens.SettingsChatRp.route) },
)
```

---

## 5. `SettingsChatRpScreen.kt` (new small screen)

Create file: `app/src/main/java/com/dark/tool_neuron/ui/screens/settings/SettingsChatRpScreen.kt`

This screen manages the three RP settings added to `AppPreferences` in Phase 02.

```kotlin
package com.dark.tool_neuron.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.viewmodel.SettingsViewModel

@Composable
fun SettingsChatRpScreen(
    innerPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val displayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
    val lengthStyle by viewModel.responseLengthStyle.collectAsStateWithLifecycle()
    val memoryEnabled by viewModel.memoryEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text  = "Your display name",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value         = displayName,
            onValueChange = viewModel::setUserDisplayName,
            label         = { Text("How characters refer to you") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text(
            text  = "Response length",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf("short", "medium", "long").forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = option.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                )
                RadioButton(
                    selected = lengthStyle == option,
                    onClick  = { viewModel.setResponseLengthStyle(option) },
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Memory", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text  = "Auto-summarize every 8 turns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked         = memoryEnabled,
                onCheckedChange = viewModel::setMemoryEnabled,
            )
        }
    }
}
```

---

## 6. Add new fields to `SettingsViewModel.kt`

Add these three state flows and setters to `SettingsViewModel`:

```kotlin
// In SettingsViewModel

private val _userDisplayName = MutableStateFlow(appPrefs.userDisplayName)
val userDisplayName: StateFlow<String> = _userDisplayName.asStateFlow()

private val _responseLengthStyle = MutableStateFlow(appPrefs.responseLengthStyle)
val responseLengthStyle: StateFlow<String> = _responseLengthStyle.asStateFlow()

private val _memoryEnabled = MutableStateFlow(appPrefs.memoryEnabled)
val memoryEnabled: StateFlow<Boolean> = _memoryEnabled.asStateFlow()

fun setUserDisplayName(v: String) {
    _userDisplayName.value = v
    appPrefs.userDisplayName = v
}

fun setResponseLengthStyle(v: String) {
    _responseLengthStyle.value = v
    appPrefs.responseLengthStyle = v
}

fun setMemoryEnabled(v: Boolean) {
    _memoryEnabled.value = v
    appPrefs.memoryEnabled = v
}
```

---

## 7. Verification

```bash
./gradlew :app:compileDebugKotlin
```

After this phase the only remaining unresolved symbols in `TNavigation.kt`
are the 4 screen composables: `CharacterListScreen`, `CharacterCreateScreen`,
`CharacterDetailScreen`, `RoleplayChatScreen`. Everything else compiles.

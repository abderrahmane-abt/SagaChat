package com.moorixlabs.sagachat.ui.screens.character_import

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.util.CharacterImporter
import com.moorixlabs.sagachat.viewmodel.CharacterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterImportScreen(
    onBack: () -> Unit,
    onImported: (characterId: String) -> Unit,
    viewModel: CharacterViewModel = hiltViewModel(),
) {
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Paste JSON", "From File")

    var showHelpDialog by remember { mutableStateOf(false) }

    // Clears result when leaving the screen
    DisposableEffect(Unit) { onDispose { viewModel.clearImportResult() } }

    if (showHelpDialog) {
        ImportHelpDialog(onDismiss = { showHelpDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Import Character", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(TnIcons.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(TnIcons.InfoCircle, contentDescription = "Help")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = {
                            selectedTab = i
                            viewModel.clearImportResult()
                        },
                        text = { Text(title) },
                    )
                }
            }

            when (selectedTab) {
                0 -> PasteJsonTab(
                    importResult = importResult,
                    isSaving     = isSaving,
                    onJsonChanged = viewModel::parseImportJson,
                    onImport      = { viewModel.confirmImport(onImported) },
                )
                1 -> FromFileTab(
                    importResult  = importResult,
                    isSaving      = isSaving,
                    onFileParsed  = viewModel::parseImportJson,
                    onImport      = { viewModel.confirmImport(onImported) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 — Paste JSON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PasteJsonTab(
    importResult: CharacterImporter.ImportResult?,
    isSaving: Boolean,
    onJsonChanged: (String) -> Unit,
    onImport: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text  = "Paste a SagaChat, TavernAI V1, or SillyTavern V2 character card below.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Monospace JSON editor box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .border(
                    width = 1.dp,
                    color = when (importResult) {
                        is CharacterImporter.ImportResult.Failure -> MaterialTheme.colorScheme.error
                        is CharacterImporter.ImportResult.Success -> MaterialTheme.colorScheme.primary
                        null -> MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "{ \"name\": \"...\", \"chat_name\": \"...\", ... }",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 13.sp,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    ),
                )
            }
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onJsonChanged(it)
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize   = 13.sp,
                    color      = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Parse result preview
        AnimatedVisibility(visible = importResult != null) {
            importResult?.let { ParseResultCard(it) }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onImport,
            enabled  = importResult is CharacterImporter.ImportResult.Success && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color    = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(TnIcons.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import Character")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 — From File
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FromFileTab(
    importResult: CharacterImporter.ImportResult?,
    isSaving: Boolean,
    onFileParsed: (String) -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "file.json"
        try {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                .orEmpty()
            onFileParsed(json)
        } catch (e: Exception) {
            onFileParsed("") // triggers a Failure with empty-JSON message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // File picker card
        OutlinedCard(
            onClick   = { filePicker.launch("application/json") },
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = TnIcons.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                if (selectedFileName != null) {
                    Text(
                        text  = selectedFileName!!,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text  = "Tap to choose a different file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text  = "Tap to choose a JSON file",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text  = "Supports SagaChat, TavernAI V1, SillyTavern V2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Parse result preview
        AnimatedVisibility(visible = importResult != null) {
            importResult?.let { ParseResultCard(it) }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick  = onImport,
            enabled  = importResult is CharacterImporter.ImportResult.Success && !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color    = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(TnIcons.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import Character")
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared — Parse result preview card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParseResultCard(result: CharacterImporter.ImportResult) {
    when (result) {
        is CharacterImporter.ImportResult.Failure -> {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = TnIcons.AlertCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                    )
                    Text(
                        text  = result.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        is CharacterImporter.ImportResult.Success -> {
            val c = result.character
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Format badge + name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FormatBadge(result.detectedFormat)
                        Text(
                            text  = c.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Field detection checklist
                    FieldRow("Display name",     c.chatName.isNotBlank())
                    FieldRow("Bio",              c.bio.isNotBlank())
                    FieldRow("Personality",      c.personality.isNotBlank())
                    FieldRow("Scenario",         c.scenario.isNotBlank())
                    FieldRow("Initial message",  c.initialMessage.isNotBlank())
                    FieldRow("Example dialogs",  c.exampleDialogs.isNotBlank())
                    if (c.tags.isNotEmpty()) {
                        FieldRow("Tags (${c.tags.size})", true)
                    }

                    // Warnings
                    if (result.warnings.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        result.warnings.forEach { w ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    TnIcons.AlertTriangle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                )
                                Text(
                                    text  = w,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(format: CharacterImporter.Format) {
    val label = when (format) {
        CharacterImporter.Format.SAGACHAT_V1 -> "SagaChat"
        CharacterImporter.Format.TAVERN_V1   -> "TavernAI V1"
        CharacterImporter.Format.TAVERN_V2   -> "SillyTavern V2"
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text     = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun FieldRow(label: String, present: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (present) TnIcons.Check else TnIcons.Minus,
            contentDescription = null,
            tint = if (present)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (present)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Help Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ImportHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val helpJson = """
{
  "name": "Character Name",
  "chat_name": "Display Name",
  "bio": "Background & History",
  "personality": "Core traits",
  "scenario": "Setting the scene",
  "first_mes": "Initial message",
  "mes_example": "Example dialogs",
  "tags": ["tag1", "tag2"]
}
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Supported JSON Format")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "You can paste standard TavernAI V1, SillyTavern V2, or SagaChat JSON files. The core fields supported are:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = helpJson,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(helpJson))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(TnIcons.Copy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

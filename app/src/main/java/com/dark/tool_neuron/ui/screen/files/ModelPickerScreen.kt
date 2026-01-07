package com.dark.tool_neuron.ui.screen.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.rDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerScreen(
    finishWithPath: (String) -> Unit, onClose: () -> Unit
) {
    val context = LocalContext.current
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }

    var hasAllFiles by remember { mutableStateOf(hasAllFilesAccess()) }
    var currentPath by rememberSaveable { mutableStateOf(rootPath) }
    var listState by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var detailTarget by remember { mutableStateOf<FileItem?>(null) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(hasAllFiles, currentPath) {
        if (hasAllFiles) {
            loading = true
            error = null
            listState = try {
                withContext(Dispatchers.IO) { listChildrenFiltered(currentPath) }
            } catch (t: Throwable) {
                error = t.message
                emptyList()
            }
            loading = false
        }
    }

    BackHandler(true) {
        if (currentPath != rootPath) {
            currentPath = File(currentPath).parentFile?.absolutePath ?: rootPath
        } else onClose()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            ), navigationIcon = {
                IconButton(onClick = {
                    if (currentPath != rootPath) {
                        currentPath = File(currentPath).parentFile?.absolutePath ?: rootPath
                    } else onClose()
                }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                }
            }, title = { PathBreadcrumbText(currentPath) }, actions = {
                if (!hasAllFiles) {
                    CompactIconButton(
                        icon = Icons.Outlined.Settings,
                        onClick = { openAllFilesAccessSettings(context) },
                        contentDescription = "Grant access"
                    )
                } else {
                    CompactIconButton(
                        icon = Icons.Outlined.Home,
                        onClick = { currentPath = rootPath },
                        contentDescription = "Home"
                    )
                }
            }, scrollBehavior = scrollBehavior
            )
        }) { inner ->
        val blurRadius = if (detailTarget != null) 12.dp else 0.dp
        Box(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            when {
                !hasAllFiles -> PermissionGate(
                    modifier = Modifier.fillMaxSize()
                ) { hasAllFiles = hasAllFilesAccess() }

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> ErrorState(
                    error = error!!, onRetry = { hasAllFiles = hasAllFilesAccess() })

                else -> FileList(
                    modifier = Modifier.fillMaxSize(),
                    items = listState,
                    rootPath = rootPath,
                    currentPath = currentPath,
                    onNavigate = { folder -> currentPath = folder.absolutePath },
                    onPick = { file -> finishWithPath(file.absolutePath) },
                    onLongPress = { detailTarget = it })
            }
        }

        detailTarget?.let { item ->
            FileDetailDialog(
                item = item,
                onDismiss = { detailTarget = null },
                onSelect = { finishWithPath(item.file.absolutePath) })
        }
    }
}

@Composable
private fun CompactIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(rDp(40.dp)),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.06f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(rDp(20.dp)))
    }
}

@Composable
private fun PathBreadcrumbText(path: String) {
    val scroll = rememberScrollState()
    val normalized = remember(path) { path.removeSuffix("/") }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crossfade(normalized, label = "path") {
            Text(
                text = it,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PermissionGate(modifier: Modifier = Modifier, onCheck: () -> Unit) {
    val ctx = LocalContext.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(24.dp)),
            shape = RoundedCornerShape(rDp(20.dp)),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = rDp(2.dp)
        ) {
            Column(
                Modifier.padding(rDp(24.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(rDp(48.dp)),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Storage Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Allow 'All files access' to browse folders and select .gguf model files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(rDp(8.dp)))
                Row(horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))) {
                    CompactIconButton(
                        icon = Icons.Outlined.Settings,
                        onClick = { openAllFilesAccessSettings(ctx) },
                        contentDescription = "Open Settings"
                    )
                    CompactIconButton(
                        icon = Icons.Outlined.Refresh,
                        onClick = onCheck,
                        contentDescription = "Refresh"
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(rDp(24.dp)),
            shape = RoundedCornerShape(rDp(20.dp)),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Column(
                Modifier.padding(rDp(24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                Text(
                    "Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                CompactIconButton(
                    icon = Icons.Outlined.Refresh, onClick = onRetry, contentDescription = "Retry"
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    modifier: Modifier,
    items: List<FileItem>,
    rootPath: String,
    currentPath: String,
    onNavigate: (File) -> Unit,
    onPick: (File) -> Unit,
    onLongPress: (FileItem) -> Unit
) {
    val parent = File(currentPath).parentFile
    LazyColumn(
        modifier = modifier.padding(horizontal = rDp(12.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        if (parent != null && currentPath != rootPath) {
            item("..parent") {
                FileListItem(
                    icon = {
                    Icon(
                        Icons.Outlined.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                    title = "..",
                    subtitle = "Parent folder",
                    onClick = { onNavigate(parent) },
                    onLongClick = null,
                    trailing = null
                )
            }
        }
        items(items, key = { it.file.absolutePath }) { item ->
            FileListItem(
                icon = {
                when(item.isDir){
                    true -> Icon(
                         Icons.Outlined.Folder,
                        null, tint = MaterialTheme.colorScheme.primary

                    )
                    false -> Icon(
                        painterResource(R.drawable.ai_model),
                        null,
                        tint =  MaterialTheme.colorScheme.secondary
                    )
                }
            },
                title = item.name,
                subtitle = if (item.isDir) "Folder" else humanSize(item.size),
                onClick = { if (item.isDir) onNavigate(item.file) else onPick(item.file) },
                onLongClick = if (!item.isDir) ({ onLongPress(item) }) else null,
                trailing = if (!item.isDir) {
                    {
                        Row(horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                            CompactIconButton(
                                icon = Icons.Outlined.Info,
                                onClick = { onLongPress(item) },
                                contentDescription = "Info",
                                modifier = Modifier.size(rDp(32.dp))
                            )
                        }
                    }
                } else null)
        }
    }
}

@Composable
private fun FileListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(rDp(12.dp)))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = rDp(14.dp), vertical = rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(rDp(32.dp))
                    .clip(RoundedCornerShape(rDp(8.dp)))
                    .background(MaterialTheme.colorScheme.primary.copy(0.08f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(rDp(12.dp)))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.let {
                Spacer(Modifier.width(rDp(8.dp)))
                it()
            }
        }
    }
}

@Composable
private fun FileDetailDialog(
    item: FileItem, onDismiss: () -> Unit, onSelect: () -> Unit
) {
    var quickSha by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(item.file) {
        quickSha = computeSha256Prefix(item.file, limitBytes = 4L * 1024 * 1024)
    }

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = true)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = rDp(3.dp)
        ) {
            Column(Modifier.padding(rDp(24.dp))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Model Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    CompactIconButton(
                        icon = Icons.Outlined.Close,
                        onClick = onDismiss,
                        contentDescription = "Close",
                        modifier = Modifier.size(rDp(32.dp))
                    )
                }

                Spacer(Modifier.height(rDp(16.dp)))

                DetailInfoCard {
                    InfoRow("Name", item.name)
                    InfoRow("Size", "${humanSize(item.size)} (${item.size} B)")
                    InfoRow("Permissions", "R: ${item.file.canRead()} / W: ${item.file.canWrite()}")
                    guessQuant(item.name)?.let { InfoRow("Quantization", it) }
                    quickSha?.let { InfoRow("SHA-256 (4MB)", it) }
                }

                Spacer(Modifier.height(rDp(20.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))
                ) {
                    CompactIconButton(
                        icon = Icons.Outlined.Close,
                        onClick = onDismiss,
                        contentDescription = "Cancel",
                        modifier = Modifier
                            .weight(1f)
                            .height(rDp(48.dp))
                    )
                    FilledIconButton(
                        onClick = onSelect,
                        modifier = Modifier
                            .weight(1f)
                            .height(rDp(48.dp)),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(rDp(12.dp))
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Check, "Load", modifier = Modifier.size(rDp(20.dp)))
                            Text("Load Model", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(rDp(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(rDp(16.dp)), verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            k,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(rDp(4.dp)))
        Text(
            v,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Data and utility functions
private data class FileItem(
    val file: File, val name: String, val isDir: Boolean, val size: Long, val lastModified: Long
)

private suspend fun listChildrenFiltered(path: String): List<FileItem> =
    withContext(Dispatchers.IO) {
        val dir = File(path)
        val children = dir.listFiles()?.toList().orEmpty()
        children.filter {
            it.isDirectory || it.extension.equals("gguf", ignoreCase = true)
        }.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) })
        ).map {
            FileItem(
                file = it,
                name = it.name,
                isDir = it.isDirectory,
                size = if (it.isFile) it.length() else 0L,
                lastModified = it.lastModified()
            )
        }
    }

private fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

private fun openAllFilesAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        intent.data = "package:${context.packageName}".toUri()
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val group = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return String.format(
        Locale.getDefault(), "%.1f %s", bytes / 1024.0.pow(group.toDouble()), units[group]
    )
}

private fun guessQuant(name: String): String? {
    val rx = Regex("(?i)Q\\d+[_A-Z0-9]*")
    return rx.find(name)?.value
}

private suspend fun computeSha256Prefix(file: File, limitBytes: Long): String? =
    withContext(Dispatchers.IO) {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            var remaining = limitBytes
            FileInputStream(file).use { fis ->
                while (remaining > 0) {
                    val toRead = min(buf.size.toLong(), remaining).toInt()
                    val len = fis.read(buf, 0, toRead)
                    if (len <= 0) break
                    md.update(buf, 0, len)
                    remaining -= len
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            null
        }
    }
package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Log Level Enum
enum class LogLevel(val color: Color, val prefix: String) {
    DEBUG(Color(0xFF2196F3), "DEBUG"),
    INFO(Color(0xFF4CAF50), "INFO"),
    WARNING(Color(0xFFFFC107), "WARN"),
    ERROR(Color(0xFFF44336), "ERROR"),
    CRITICAL(Color(0xFFD32F2F), "CRIT")
}

// Log Entry Data Class
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
)

// Vault Logger Singleton
object VaultLogger {
    private val _logs = mutableStateListOf<LogEntry>()
    val logs: List<LogEntry> get() = _logs
    
    var isEnabled by mutableStateOf(true)
    var maxLogSize by mutableStateOf(500)
    
    fun log(level: LogLevel, tag: String, message: String, stackTrace: String? = null) {
        if (!isEnabled) return
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )
        
        _logs.add(0, entry) // Add to beginning for newest first
        
        // Trim logs if exceeds max size
        if (_logs.size > maxLogSize) {
            _logs.removeRange(maxLogSize, _logs.size)
        }
    }
    
    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warning(tag: String, message: String) = log(LogLevel.WARNING, tag, message)
    fun error(tag: String, message: String, exception: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, exception?.stackTraceToString())
    }
    fun critical(tag: String, message: String, exception: Throwable? = null) {
        log(LogLevel.CRITICAL, tag, message, exception?.stackTraceToString())
    }
    
    fun clear() {
        _logs.clear()
    }
    
    fun filterByLevel(level: LogLevel): List<LogEntry> {
        return _logs.filter { it.level == level }
    }
    
    fun filterByTag(tag: String): List<LogEntry> {
        return _logs.filter { it.tag.contains(tag, ignoreCase = true) }
    }
    
    fun search(query: String): List<LogEntry> {
        return _logs.filter {
            it.message.contains(query, ignoreCase = true) ||
            it.tag.contains(query, ignoreCase = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultLoggerScreen() {
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var selectedLog by remember { mutableStateOf<LogEntry?>(null) }
    
    val listState = rememberLazyListState()
    
    val filteredLogs = remember(searchQuery, filterLevel) {
        var logs = VaultLogger.logs
        
        if (filterLevel != null) {
            logs = logs.filter { it.level == filterLevel }
        }
        
        if (searchQuery.isNotBlank()) {
            logs = logs.filter {
                it.message.contains(searchQuery, ignoreCase = true) ||
                it.tag.contains(searchQuery, ignoreCase = true)
            }
        }
        
        logs
    }
    
    // Auto-scroll effect
    LaunchedEffect(VaultLogger.logs.size, autoScroll) {
        if (autoScroll && VaultLogger.logs.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Vault Logger",
                            fontSize = rSp(18.sp),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${filteredLogs.size} entries",
                            fontSize = rSp(11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    ActionToggleButton(
                        checked = autoScroll,
                        onCheckedChange = { autoScroll = it },
                        icon = android.R.drawable.ic_menu_recent_history,
                        contentDescription = "Auto Scroll"
                    )
                    
                    Spacer(Modifier.width(rDp(8.dp)))
                    
                    ActionToggleButton(
                        checked = VaultLogger.isEnabled,
                        onCheckedChange = { VaultLogger.isEnabled = it },
                        icon = if (VaultLogger.isEnabled) 
                            android.R.drawable.ic_media_pause 
                        else 
                            android.R.drawable.ic_media_play,
                        contentDescription = if (VaultLogger.isEnabled) "Pause" else "Resume"
                    )
                    
                    Spacer(Modifier.width(rDp(8.dp)))
                    
                    ActionButton(
                        onClickListener = { showSettings = true },
                        icon = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                    
                    Spacer(Modifier.width(rDp(8.dp)))
                    
                    ActionButton(
                        onClickListener = { VaultLogger.clear() },
                        icon = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search and Filter Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(rDp(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search logs...", fontSize = rSp(13.sp)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(rDp(8.dp))
                    )
                    
                    // Level Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
                    ) {
                        FilterChip(
                            selected = filterLevel == null,
                            onClick = { filterLevel = null },
                            label = { Text("All", fontSize = rSp(12.sp)) }
                        )
                        
                        LogLevel.entries.forEach { level ->
                            FilterChip(
                                selected = filterLevel == level,
                                onClick = { filterLevel = if (filterLevel == level) null else level },
                                label = { Text(level.prefix, fontSize = rSp(12.sp)) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(rDp(8.dp))
                                            .clip(RoundedCornerShape(rDp(4.dp)))
                                            .background(level.color)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // Log List
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(rDp(12.dp)),
                verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
            ) {
                if (filteredLogs.isEmpty()) {
                    item {
                        EmptyLogState(
                            message = if (searchQuery.isNotBlank() || filterLevel != null)
                                "No logs match your filters"
                            else
                                "No logs yet. Operations will appear here."
                        )
                    }
                } else {
                    items(filteredLogs, key = { it.timestamp }) { log ->
                        LogEntryCard(
                            entry = log,
                            onClick = { selectedLog = log }
                        )
                    }
                }
            }
        }
        
        // Settings Dialog
        if (showSettings) {
            LoggerSettingsDialog(
                onDismiss = { showSettings = false }
            )
        }
        
        // Log Detail Dialog
        selectedLog?.let { log ->
            LogDetailDialog(
                entry = log,
                onDismiss = { selectedLog = null }
            )
        }
    }
}

@Composable
fun LogEntryCard(
    entry: LogEntry,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(12.dp)),
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
        ) {
            // Level Indicator
            Box(
                modifier = Modifier
                    .width(rDp(4.dp))
                    .height(rDp(48.dp))
                    .clip(RoundedCornerShape(rDp(2.dp)))
                    .background(entry.level.color)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entry.level.prefix,
                        fontSize = rSp(11.sp),
                        fontWeight = FontWeight.Bold,
                        color = entry.level.color
                    )
                    
                    Text(
                        entry.tag,
                        fontSize = rSp(11.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(Modifier.weight(1f))
                    
                    Text(
                        formatLogTime(entry.timestamp),
                        fontSize = rSp(10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Text(
                    entry.message,
                    fontSize = rSp(12.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (entry.stackTrace != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(rDp(12.dp)),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Stack trace available",
                            fontSize = rSp(10.sp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogDetailDialog(
    entry: LogEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = entry.level.color
            )
        },
        title = {
            Text(
                "${entry.level.prefix} - ${entry.tag}",
                fontSize = rSp(16.sp),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
            ) {
                item {
                    LogDetailItem("Timestamp", formatLogTimeFull(entry.timestamp))
                }
                item {
                    LogDetailItem("Level", entry.level.prefix)
                }
                item {
                    LogDetailItem("Tag", entry.tag)
                }
                item {
                    LogDetailItem("Message", entry.message)
                }
                
                if (entry.stackTrace != null) {
                    item {
                        Divider(modifier = Modifier.padding(vertical = rDp(8.dp)))
                    }
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
                        ) {
                            Text(
                                "Stack Trace",
                                fontSize = rSp(12.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(rDp(8.dp)),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    entry.stackTrace,
                                    fontSize = rSp(10.sp),
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(rDp(8.dp))
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun LogDetailItem(label: String, value: String) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
    ) {
        Text(
            label,
            fontSize = rSp(11.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDp(6.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                value,
                fontSize = rSp(12.sp),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(rDp(8.dp))
            )
        }
    }
}

@Composable
fun LoggerSettingsDialog(
    onDismiss: () -> Unit
) {
    var maxLogSize by remember { mutableStateOf(VaultLogger.maxLogSize) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Settings, contentDescription = null)
        },
        title = {
            Text("Logger Settings", fontSize = rSp(18.sp), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
            ) {
                Column {
                    Text(
                        "Max Log Entries: $maxLogSize",
                        fontSize = rSp(14.sp),
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = maxLogSize.toFloat(),
                        onValueChange = { maxLogSize = it.toInt() },
                        valueRange = 100f..2000f,
                        steps = 18
                    )
                    Text(
                        "Older entries will be removed when limit is reached",
                        fontSize = rSp(11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Logging Enabled", fontSize = rSp(14.sp))
                    Switch(
                        checked = VaultLogger.isEnabled,
                        onCheckedChange = { VaultLogger.isEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    VaultLogger.maxLogSize = maxLogSize
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptyLogState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(32.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Icon(
                Icons.Default.List,
                contentDescription = null,
                modifier = Modifier.size(rDp(64.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Text(
                message,
                fontSize = rSp(14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// Utility Functions
fun formatLogTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatLogTimeFull(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// Extension function to integrate logging with VaultHelper
fun VaultLogger.logVaultOperation(
    operation: String,
    success: Boolean,
    details: String = "",
    error: Throwable? = null
) {
    val message = buildString {
        append("[$operation] ")
        if (success) {
            append("SUCCESS")
        } else {
            append("FAILED")
        }
        if (details.isNotEmpty()) {
            append(" - $details")
        }
    }
    
    if (success) {
        info("VaultOperation", message)
    } else {
        error("VaultOperation", message, error)
    }
}
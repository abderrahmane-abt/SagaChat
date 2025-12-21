package com.mp.user_data.helpers

import android.util.Log
import com.mp.user_data.ntds.neuron_tree.NeuronTree
import com.mp.user_data.ntds.neuron_tree.NodeType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper for managing system logs and debug information.
 * Logs are organized into sessions for better organization.
 *
 * Usage:
 * ```kotlin
 * val logHelper = LogHelper(tree)
 * logHelper.createSession("App Start")
 * logHelper.log("INFO", "App initialized successfully")
 * logHelper.log("ERROR", "Failed to load data", exception = e)
 * val logs = logHelper.getCurrentSessionLogs()
 * ```
 */
class LogHelper(tree: NeuronTree) : BaseHelper(tree, "systemLogs") {

    private var currentSessionId: String? = null

    /**
     * Log levels matching Android's Log class.
     */
    object Level {
        const val VERBOSE = "VERBOSE"
        const val DEBUG = "DEBUG"
        const val INFO = "INFO"
        const val WARN = "WARN"
        const val ERROR = "ERROR"
    }

    /**
     * Create a new log session.
     * @param sessionName Name for the session (e.g., "App Start", "User Action")
     * @return The ID of the newly created session
     */
    fun createSession(sessionName: String): String {
        val sessionData = JSONObject().apply {
            put("sessionName", sessionName)
            put("startTime", System.currentTimeMillis())
            put("logs", JSONArray())
        }
        
        val sessionId = addChildNode(sessionData.toString(), NodeType.HOLDER)
        currentSessionId = sessionId
        Log.d(TAG, "Created log session: $sessionName (ID: $sessionId)")
        return sessionId
    }

    /**
     * Log a message to the current session.
     * If no session exists, creates one automatically.
     *
     * @param level Log level (use Level constants)
     * @param message The log message
     * @param tag Optional tag for categorization
     * @param exception Optional exception to include
     * @return true if successful
     */
    fun log(
        level: String,
        message: String,
        tag: String = "System",
        exception: Throwable? = null
    ): Boolean {
        // Create default session if none exists
        if (currentSessionId == null) {
            createSession("Default Session")
        }
        
        val sessionId = currentSessionId ?: return false
        val node = getNodeById(sessionId) ?: return false
        val json = parseNodeContent(node) ?: return false
        
        val logsArray = json.optJSONArray("logs") ?: JSONArray()
        
        val logEntry = JSONObject().apply {
            put("level", level)
            put("message", message)
            put("tag", tag)
            put("timestamp", System.currentTimeMillis())
            
            if (exception != null) {
                put("exception", JSONObject().apply {
                    put("type", exception.javaClass.simpleName)
                    put("message", exception.message ?: "")
                    put("stackTrace", exception.stackTraceToString())
                })
            }
        }
        
        logsArray.put(logEntry)
        json.put("logs", logsArray)
        
        return updateNodeContent(sessionId, json.toString())
    }

    /**
     * Convenience methods for different log levels.
     */
    fun verbose(message: String, tag: String = "System") = 
        log(Level.VERBOSE, message, tag)
    
    fun debug(message: String, tag: String = "System") = 
        log(Level.DEBUG, message, tag)
    
    fun info(message: String, tag: String = "System") = 
        log(Level.INFO, message, tag)
    
    fun warn(message: String, tag: String = "System") = 
        log(Level.WARN, message, tag)
    
    fun error(message: String, tag: String = "System", exception: Throwable? = null) = 
        log(Level.ERROR, message, tag, exception)

    /**
     * Get all sessions.
     * @return List of LogSession objects
     */
    fun getAllSessions(): List<LogSession> {
        return getAllChildren().mapNotNull { node ->
            parseNodeContent(node)?.let { json ->
                LogSession(
                    id = node.id,
                    sessionName = json.getString("sessionName"),
                    startTime = json.getLong("startTime"),
                    logCount = json.optJSONArray("logs")?.length() ?: 0
                )
            }
        }
    }

    /**
     * Get a specific session by ID.
     * @return LogSessionData if found, null otherwise
     */
    fun getSession(sessionId: String): LogSessionData? {
        val node = getNodeById(sessionId) ?: return null
        val json = parseNodeContent(node) ?: return null
        
        val logs = mutableListOf<LogEntry>()
        val logsArray = json.optJSONArray("logs")
        if (logsArray != null) {
            for (i in 0 until logsArray.length()) {
                val logObj = logsArray.getJSONObject(i)
                
                val exception = logObj.optJSONObject("exception")?.let { exObj ->
                    ExceptionInfo(
                        type = exObj.getString("type"),
                        message = exObj.getString("message"),
                        stackTrace = exObj.getString("stackTrace")
                    )
                }
                
                logs.add(
                    LogEntry(
                        level = logObj.getString("level"),
                        message = logObj.getString("message"),
                        tag = logObj.getString("tag"),
                        timestamp = logObj.getLong("timestamp"),
                        exception = exception
                    )
                )
            }
        }
        
        return LogSessionData(
            id = node.id,
            sessionName = json.getString("sessionName"),
            startTime = json.getLong("startTime"),
            logs = logs
        )
    }

    /**
     * Get logs from the current session.
     * @return List of LogEntry objects, or empty list if no current session
     */
    fun getCurrentSessionLogs(): List<LogEntry> {
        val sessionId = currentSessionId ?: return emptyList()
        return getSession(sessionId)?.logs ?: emptyList()
    }

    /**
     * Delete a session by ID.
     * If deleting the current session, clears currentSessionId.
     * @return true if successful, false if session not found
     */
    fun deleteSession(sessionId: String): Boolean {
        if (sessionId == currentSessionId) {
            currentSessionId = null
        }
        return deleteNode(sessionId)
    }

    /**
     * Get the most recent sessions.
     * @param limit Maximum number of sessions to return
     * @return List of LogSession objects, sorted by start time (newest first)
     */
    fun getRecentSessions(limit: Int = 10): List<LogSession> {
        return getAllSessions()
            .sortedByDescending { it.startTime }
            .take(limit)
    }

    /**
     * Search logs across all sessions.
     * @param query The search query
     * @return Map of session ID to matching log entries
     */
    fun searchLogs(query: String): Map<String, List<LogEntry>> {
        val lowerQuery = query.lowercase()
        val results = mutableMapOf<String, List<LogEntry>>()
        
        getAllSessions().forEach { session ->
            val sessionData = getSession(session.id)
            val matchingLogs = sessionData?.logs?.filter { log ->
                log.message.lowercase().contains(lowerQuery) ||
                log.tag.lowercase().contains(lowerQuery)
            }
            
            if (!matchingLogs.isNullOrEmpty()) {
                results[session.id] = matchingLogs
            }
        }
        
        return results
    }

    /**
     * Get logs by level across all sessions.
     * @param level The log level to filter by
     * @return Map of session ID to matching log entries
     */
    fun getLogsByLevel(level: String): Map<String, List<LogEntry>> {
        val results = mutableMapOf<String, List<LogEntry>>()
        
        getAllSessions().forEach { session ->
            val sessionData = getSession(session.id)
            val matchingLogs = sessionData?.logs?.filter { it.level == level }
            
            if (!matchingLogs.isNullOrEmpty()) {
                results[session.id] = matchingLogs
            }
        }
        
        return results
    }

    /**
     * Export logs from a session as a formatted string.
     * @param sessionId The session ID
     * @return Formatted log string, or null if session not found
     */
    fun exportSession(sessionId: String): String? {
        val sessionData = getSession(sessionId) ?: return null
        
        val sb = StringBuilder()
        sb.appendLine("=== ${sessionData.sessionName} ===")
        sb.appendLine("Started: ${sessionData.startTime}")
        sb.appendLine("Total Logs: ${sessionData.logs.size}")
        sb.appendLine()
        
        sessionData.logs.forEach { log ->
            sb.appendLine("[${log.timestamp}] ${log.level}/${log.tag}: ${log.message}")
            log.exception?.let { ex ->
                sb.appendLine("  Exception: ${ex.type} - ${ex.message}")
            }
        }
        
        return sb.toString()
    }
}

/**
 * Summary information about a log session.
 */
data class LogSession(
    val id: String,
    val sessionName: String,
    val startTime: Long,
    val logCount: Int
)

/**
 * Complete log session data including all logs.
 */
data class LogSessionData(
    val id: String,
    val sessionName: String,
    val startTime: Long,
    val logs: List<LogEntry>
)

/**
 * A single log entry.
 */
data class LogEntry(
    val level: String,
    val message: String,
    val tag: String,
    val timestamp: Long,
    val exception: ExceptionInfo? = null
)

/**
 * Information about an exception.
 */
data class ExceptionInfo(
    val type: String,
    val message: String,
    val stackTrace: String
)
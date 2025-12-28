package com.sovworks.eds.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private const val MAX_LOGS = 500

    private fun maskSensitiveInfo(message: String): String {
        var masked = message
        // Mask potential passwords/keys: look for "password", "passphrase", "key" followed by ":" or "="
        val patterns = listOf(
            Regex("(?i)(password|passphrase|key|secret|token)[:=]\\s*[^\\s,;]+"),
            Regex("(?i)(pwd)[:=]\\s*[^\\s,;]+")
        )
        patterns.forEach { regex ->
            masked = regex.replace(masked) { matchResult ->
                val label = matchResult.groups[1]?.value ?: ""
                val separator = if (matchResult.value.contains(":")) ":" else "="
                "$label$separator********"
            }
        }
        return masked
    }

    @JvmStatic
    fun addLog(level: String, tag: String, message: String) {
        val maskedMessage = maskSensitiveInfo(message)
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $level/$tag: $maskedMessage"
        
        _logs.update { currentLogs ->
            val newList = currentLogs + logEntry
            if (newList.size > MAX_LOGS) {
                newList.drop(1)
            } else {
                newList
            }
        }
    }

    @JvmStatic
    fun addLog(level: String, tag: String, message: String, throwable: Throwable) {
        addLog(level, tag, "$message\n${android.util.Log.getStackTraceString(throwable)}")
    }

    @JvmStatic
    fun clear() {
        _logs.value = emptyList()
    }
}

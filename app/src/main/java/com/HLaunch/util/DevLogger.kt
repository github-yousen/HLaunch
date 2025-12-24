package com.HLaunch.util

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

/**
 * Developer Logger with cross-process support (file-based storage)
 */
object DevLogger {
    private const val PREFS_NAME = "dev_mode_prefs"
    private const val KEY_DEV_MODE = "dev_mode_enabled"
    private const val DEV_PASSWORD = "0515"
    private const val MAX_LOGS = 500
    private const val LOG_FILE_NAME = "dev_logs.txt"
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    @Volatile
    private var appContext: Context? = null
    
    data class LogEntry(
        val time: String,
        val tag: String,
        val level: String,
        val message: String,
        val processName: String
    ) {
        fun toLine(): String = "$time|$tag|$level|$processName|$message"
        
        companion object {
            fun fromLine(line: String): LogEntry? {
                val parts = line.split("|", limit = 5)
                if (parts.size < 5) return null
                return LogEntry(parts[0], parts[1], parts[2], parts[3], parts[4])
            }
        }
    }
    
    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    fun isDevModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEV_MODE, false)
    }
    
    fun enableDevMode(context: Context, password: String): Boolean {
        if (password == DEV_PASSWORD) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_DEV_MODE, true).apply()
            i("DevMode", "dev_mode_enabled")
            return true
        }
        return false
    }
    
    fun disableDevMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEV_MODE, false).apply()
        i("DevMode", "dev_mode_disabled")
    }
    
    private fun getProcessName(): String {
        return try {
            val pid = android.os.Process.myPid()
            val cmdline = File("/proc/$pid/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
            if (cmdline.contains(":")) cmdline.substringAfterLast(":") else "main"
        } catch (e: Exception) {
            "main"
        }
    }
    
    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }
    
    private fun log(level: String, tag: String, message: String) {
        val ctx = appContext ?: return
        
        val entry = LogEntry(
            time = dateFormat.format(Date()),
            tag = tag,
            level = level,
            message = message.replace("\n", " ").replace("|", "/"),
            processName = getProcessName()
        )
        
        val logMsg = "[${entry.processName}] $message"
        when (level) {
            "D" -> android.util.Log.d(tag, logMsg)
            "I" -> android.util.Log.i(tag, logMsg)
            "W" -> android.util.Log.w(tag, logMsg)
            "E" -> android.util.Log.e(tag, logMsg)
        }
        
        try {
            val logFile = getLogFile(ctx)
            RandomAccessFile(logFile, "rw").use { raf ->
                raf.channel.lock().use { _ ->
                    raf.seek(raf.length())
                    raf.writeBytes(entry.toLine() + "\n")
                }
            }
            trimLogsIfNeeded(ctx)
        } catch (e: Exception) {
            android.util.Log.e("DevLogger", "log_write_failed: ${e.message}")
        }
    }
    
    private fun trimLogsIfNeeded(context: Context) {
        try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) return
            
            val lines = logFile.readLines()
            if (lines.size > MAX_LOGS) {
                val trimmed = lines.takeLast(MAX_LOGS)
                logFile.writeText(trimmed.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            // ignore
        }
    }
    
    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)
    
    fun getLogs(context: Context): List<LogEntry> {
        return try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) return emptyList()
            logFile.readLines().mapNotNull { LogEntry.fromLine(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun clearLogs(context: Context) {
        try {
            val logFile = getLogFile(context)
            logFile.writeText("")
            i("DevMode", "logs_cleared")
        } catch (e: Exception) {
            android.util.Log.e("DevLogger", "clear_logs_failed: ${e.message}")
        }
    }
    
    fun getLogsAsText(context: Context): String {
        return getLogs(context).joinToString("\n") { entry ->
            "[${entry.time}][${entry.processName}][${entry.level}/${entry.tag}] ${entry.message}"
        }
    }
}

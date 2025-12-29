package com.HLaunch.util

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

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
    
    // 内存缓存，避免频繁读文件
    private val memoryCache = ConcurrentLinkedQueue<LogEntry>()
    @Volatile
    private var cacheLoaded = false
    
    // 写入队列，批量写入
    private val writeQueue = ConcurrentLinkedQueue<LogEntry>()
    private val writeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var writeJobRunning = false
    
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
        // 异步加载缓存
        writeScope.launch {
            loadCacheFromFile(context.applicationContext)
        }
    }
    
    private fun loadCacheFromFile(context: Context) {
        try {
            val logFile = getLogFile(context)
            if (logFile.exists()) {
                logFile.readLines().mapNotNull { LogEntry.fromLine(it) }.forEach { memoryCache.offer(it) }
            }
            cacheLoaded = true
        } catch (e: Exception) {
            cacheLoaded = true
        }
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
        
        // 输出到Logcat
        val logMsg = "[${entry.processName}] $message"
        when (level) {
            "D" -> android.util.Log.d(tag, logMsg)
            "I" -> android.util.Log.i(tag, logMsg)
            "W" -> android.util.Log.w(tag, logMsg)
            "E" -> android.util.Log.e(tag, logMsg)
        }
        
        // 添加到内存缓存
        memoryCache.offer(entry)
        while (memoryCache.size > MAX_LOGS) {
            memoryCache.poll()
        }
        
        // 添加到写入队列，异步批量写入
        writeQueue.offer(entry)
        scheduleWrite(ctx)
    }
    
    private fun scheduleWrite(context: Context) {
        if (writeJobRunning) return
        writeJobRunning = true
        
        writeScope.launch {
            delay(500) // 批量写入间隔
            flushWriteQueue(context)
            writeJobRunning = false
        }
    }
    
    private fun flushWriteQueue(context: Context) {
        val entries = mutableListOf<LogEntry>()
        while (true) {
            val entry = writeQueue.poll() ?: break
            entries.add(entry)
        }
        if (entries.isEmpty()) return
        
        try {
            val logFile = getLogFile(context)
            RandomAccessFile(logFile, "rw").use { raf ->
                raf.channel.lock().use { _ ->
                    raf.seek(raf.length())
                    val content = entries.joinToString("\n") { it.toLine() } + "\n"
                    raf.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            trimLogsIfNeeded(context)
        } catch (e: Exception) {
            android.util.Log.e("DevLogger", "log_write_failed: ${e.message}")
        }
    }
    
    private fun trimLogsIfNeeded(context: Context) {
        try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) return
            
            val lines = logFile.readLines(Charsets.UTF_8)
            if (lines.size > MAX_LOGS * 2) { // 只有超过2倍才裁剪，减少IO
                val trimmed = lines.takeLast(MAX_LOGS)
                logFile.writeText(trimmed.joinToString("\n") + "\n", Charsets.UTF_8)
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
        // 优先返回内存缓存
        if (cacheLoaded || memoryCache.isNotEmpty()) {
            return memoryCache.toList()
        }
        // 缓存未加载时从文件读取
        return try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) return emptyList()
            logFile.readLines().mapNotNull { LogEntry.fromLine(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun clearLogs(context: Context) {
        memoryCache.clear()
        writeScope.launch {
            try {
                val logFile = getLogFile(context)
                logFile.writeText("")
            } catch (e: Exception) {
                android.util.Log.e("DevLogger", "clear_logs_failed: ${e.message}")
            }
        }
        i("DevMode", "logs_cleared")
    }
    
    fun getLogsAsText(context: Context): String {
        return getLogs(context).joinToString("\n") { entry ->
            "[${entry.time}][${entry.processName}][${entry.level}/${entry.tag}] ${entry.message}"
        }
    }
}

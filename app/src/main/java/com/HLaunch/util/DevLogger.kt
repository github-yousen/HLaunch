package com.HLaunch.util

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.text.SimpleDateFormat
import java.util.*

/**
 * 开发者日志工具，支持跨进程日志记录（使用文件存储）
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
        // 序列化为单行文本
        fun toLine(): String = "$time|$tag|$level|$processName|$message"
        
        companion object {
            // 从单行文本解析
            fun fromLine(line: String): LogEntry? {
                val parts = line.split("|", limit = 5)
                if (parts.size < 5) return null
                return LogEntry(parts[0], parts[1], parts[2], parts[3], parts[4])
            }
        }
    }
    
    // 初始化（在Application中调用）
    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    // 检查开发者模式是否开启
    fun isDevModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEV_MODE, false)
    }
    
    // 验证密码并开启开发者模式
    fun enableDevMode(context: Context, password: String): Boolean {
        if (password == DEV_PASSWORD) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_DEV_MODE, true).apply()
            i("DevMode", "开发者模式已开启")
            return true
        }
        return false
    }
    
    // 关闭开发者模式
    fun disableDevMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEV_MODE, false).apply()
        i("DevMode", "开发者模式已关闭")
    }
    
    // 获取当前进程名
    private fun getProcessName(): String {
        return try {
            val pid = android.os.Process.myPid()
            val cmdline = File("/proc/$pid/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
            if (cmdline.contains(":")) cmdline.substringAfterLast(":") else "main"
        } catch (e: Exception) {
            "main"
        }
    }
    
    // 获取日志文件
    private fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }
    
    // 记录日志（跨进程安全，使用文件锁）
    private fun log(level: String, tag: String, message: String) {
        val ctx = appContext ?: return
        
        val entry = LogEntry(
            time = dateFormat.format(Date()),
            tag = tag,
            level = level,
            message = message.replace("\n", " ").replace("|", "/"),  // 移除换行和分隔符
            processName = getProcessName()
        )
        
        // 同时输出到Logcat
        val logMsg = "[${entry.processName}] $message"
        when (level) {
            "D" -> android.util.Log.d(tag, logMsg)
            "I" -> android.util.Log.i(tag, logMsg)
            "W" -> android.util.Log.w(tag, logMsg)
            "E" -> android.util.Log.e(tag, logMsg)
        }
        
        // 写入文件（带文件锁）
        try {
            val logFile = getLogFile(ctx)
            RandomAccessFile(logFile, "rw").use { raf ->
                raf.channel.lock().use { _ ->
                    raf.seek(raf.length())
                    raf.writeBytes(entry.toLine() + "\n")
                }
            }
            // 异步清理过多日志
            trimLogsIfNeeded(ctx)
        } catch (e: Exception) {
            android.util.Log.e("DevLogger", "写入日志失败: ${e.message}")
        }
    }
    
    // 限制日志数量
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
            // 忽略清理错误
        }
    }
    
    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)
    
    // 获取所有日志
    fun getLogs(context: Context): List<LogEntry> {
        return try {
            val logFile = getLogFile(context)
            if (!logFile.exists()) return emptyList()
            logFile.readLines().mapNotNull { LogEntry.fromLine(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // 清空日志
    fun clearLogs(context: Context) {
        try {
            val logFile = getLogFile(context)
            logFile.writeText("")
            i("DevMode", "日志已清空")
        } catch (e: Exception) {
            android.util.Log.e("DevLogger", "清空日志失败: ${e.message}")
        }
    }
    
    // 获取日志文本（用于分享）
    fun getLogsAsText(context: Context): String {
        return getLogs(context).joinToString("\n") { entry ->
            "[${entry.time}][${entry.processName}][${entry.level}/${entry.tag}] ${entry.message}"
        }
    }
}

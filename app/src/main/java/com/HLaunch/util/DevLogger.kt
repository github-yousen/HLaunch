package com.HLaunch.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 开发者日志工具，用于调试多任务问题
 */
object DevLogger {
    private const val PREFS_NAME = "dev_mode_prefs"
    private const val KEY_DEV_MODE = "dev_mode_enabled"
    private const val DEV_PASSWORD = "0515"
    private const val MAX_LOGS = 500
    
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    data class LogEntry(
        val time: String,
        val tag: String,
        val level: String,
        val message: String,
        val processName: String
    )
    
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
            val manager = java.io.File("/proc/$pid/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
            manager.substringAfterLast(":")
        } catch (e: Exception) {
            "main"
        }
    }
    
    // 记录日志
    private fun log(level: String, tag: String, message: String) {
        val entry = LogEntry(
            time = dateFormat.format(Date()),
            tag = tag,
            level = level,
            message = message,
            processName = getProcessName()
        )
        logs.add(entry)
        // 限制日志数量
        while (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
        // 同时输出到Logcat
        when (level) {
            "D" -> android.util.Log.d(tag, "[${entry.processName}] $message")
            "I" -> android.util.Log.i(tag, "[${entry.processName}] $message")
            "W" -> android.util.Log.w(tag, "[${entry.processName}] $message")
            "E" -> android.util.Log.e(tag, "[${entry.processName}] $message")
        }
    }
    
    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String) = log("W", tag, message)
    fun e(tag: String, message: String) = log("E", tag, message)
    
    // 获取所有日志
    fun getLogs(): List<LogEntry> = logs.toList()
    
    // 清空日志
    fun clearLogs() {
        logs.clear()
        i("DevMode", "日志已清空")
    }
    
    // 获取日志文本（用于分享）
    fun getLogsAsText(): String {
        return logs.joinToString("\n") { entry ->
            "[${entry.time}][${entry.processName}][${entry.level}/${entry.tag}] ${entry.message}"
        }
    }
}

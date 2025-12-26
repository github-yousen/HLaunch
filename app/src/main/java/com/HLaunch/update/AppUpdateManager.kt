package com.HLaunch.update

import android.content.Context
import com.HLaunch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager(private val context: Context) {
    
    companion object {
        // Gitee API 配置
        private const val GITEE_OWNER = "yousen912"
        private const val GITEE_REPO = "hlaunch"
        private const val GITEE_TOKEN = "130a7cabc039011bcc4cb468b9e01bc8"
        private const val VERSION_DIR = "version"
        
        // Gitee API 地址
        private const val GITEE_API_BASE = "https://gitee.com/api/v5"
    }
    
    private val updateDir = File(context.cacheDir, "update").also { it.mkdirs() }
    
    // 版本信息（仅检查时使用）
    data class VersionInfo(
        val remoteVersion: String,
        val fileName: String,
        val downloadUrl: String,
        val hasUpdate: Boolean,
        val commitMessage: String,
        val commitTime: Long,
        val fileSize: Long
    )
    
    // APK信息（下载完成后使用）
    data class ApkInfo(
        val apkFile: File,
        val fileName: String,
        val remoteVersion: String,
        val hasUpdate: Boolean,
        val commitMessage: String,
        val commitTime: Long
    )
    
    // 从APK文件名解析版本号，格式：xxx_v1.0.apk 或 xxx_v0.1.apk
    private fun parseVersionFromFileName(fileName: String): String? {
        val regex = Regex("""_v([\d.]+)\.apk$""", RegexOption.IGNORE_CASE)
        return regex.find(fileName)?.groupValues?.get(1)
    }
    
    // 比较版本号，返回：正数表示v1>v2，负数表示v1<v2，0表示相等
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
    
    /**
     * 检查更新（不下载APK，仅获取版本信息）
     * 使用 Gitee API 获取 version 目录下的文件列表
     */
    suspend fun checkUpdate(): VersionInfo = withContext(Dispatchers.IO) {
        // 1. 获取 version 目录下的文件列表
        val contentsUrl = "$GITEE_API_BASE/repos/$GITEE_OWNER/$GITEE_REPO/contents/$VERSION_DIR?access_token=$GITEE_TOKEN"
        val contentsJson = httpGet(contentsUrl)
        val filesArray = JSONArray(contentsJson)
        
        // 2. 筛选 APK 文件并找到版本号最高的
        var latestApk: JSONObject? = null
        var latestVersion: String? = null
        
        for (i in 0 until filesArray.length()) {
            val file = filesArray.getJSONObject(i)
            val fileName = file.getString("name")
            if (fileName.endsWith(".apk", ignoreCase = true)) {
                val version = parseVersionFromFileName(fileName)
                if (version != null) {
                    if (latestVersion == null || compareVersions(version, latestVersion) > 0) {
                        latestVersion = version
                        latestApk = file
                    }
                }
            }
        }
        
        if (latestApk == null || latestVersion == null) {
            throw Exception("未找到有效的APK文件")
        }
        
        val fileName = latestApk.getString("name")
        val downloadUrl = latestApk.getString("download_url")
        val fileSize = latestApk.optLong("size", 0)
        
        // 3. 获取该文件的最新 commit 信息
        val commitsUrl = "$GITEE_API_BASE/repos/$GITEE_OWNER/$GITEE_REPO/commits?access_token=$GITEE_TOKEN&path=$VERSION_DIR/$fileName&per_page=1"
        val commitsJson = httpGet(commitsUrl)
        val commitsArray = JSONArray(commitsJson)
        
        var commitMessage = "无更新说明"
        var commitTime = System.currentTimeMillis()
        
        if (commitsArray.length() > 0) {
            val commit = commitsArray.getJSONObject(0)
            val commitObj = commit.getJSONObject("commit")
            commitMessage = commitObj.getString("message").trim()
            val dateStr = commitObj.getJSONObject("committer").getString("date")
            commitTime = parseIso8601Date(dateStr)
        }
        
        // 4. 比较版本
        val currentVersion = BuildConfig.VERSION_NAME
        val hasUpdate = compareVersions(latestVersion, currentVersion) > 0
        
        VersionInfo(
            remoteVersion = latestVersion,
            fileName = fileName,
            downloadUrl = downloadUrl,
            hasUpdate = hasUpdate,
            commitMessage = commitMessage,
            commitTime = commitTime,
            fileSize = fileSize
        )
    }
    
    /**
     * 下载 APK 文件（带进度回调）
     */
    suspend fun downloadApk(
        versionInfo: VersionInfo,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit
    ): ApkInfo = withContext(Dispatchers.IO) {
        val targetFile = File(updateDir, "update.apk")
        if (targetFile.exists()) {
            targetFile.delete()
        }
        
        val url = URL(versionInfo.downloadUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "token $GITEE_TOKEN")
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        
        try {
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("下载失败: HTTP ${connection.responseCode}")
            }
            
            val totalSize = connection.contentLengthLong.takeIf { it > 0 } ?: versionInfo.fileSize
            var downloadedSize = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    var lastReportTime = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        // 限制回调频率，每100ms最多回调一次
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime >= 100 || downloadedSize == totalSize) {
                            val percent = if (totalSize > 0) ((downloadedSize * 100) / totalSize).toInt() else 0
                            onProgress(downloadedSize, totalSize, percent)
                            lastReportTime = now
                        }
                    }
                }
            }
            
            ApkInfo(
                apkFile = targetFile,
                fileName = versionInfo.fileName,
                remoteVersion = versionInfo.remoteVersion,
                hasUpdate = versionInfo.hasUpdate,
                commitMessage = versionInfo.commitMessage,
                commitTime = versionInfo.commitTime
            )
        } finally {
            connection.disconnect()
        }
    }
    
    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        
        try {
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP请求失败: ${connection.responseCode}")
            }
            
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
    
    private fun parseIso8601Date(dateStr: String): Long {
        return try {
            // 格式: 2024-01-01T12:00:00+08:00
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
            sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}

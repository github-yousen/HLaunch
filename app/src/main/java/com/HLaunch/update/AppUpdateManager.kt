package com.HLaunch.update

import android.content.Context
import com.HLaunch.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class AppUpdateManager(private val context: Context) {
    
    companion object {
        // 固定的更新仓库配置（优先使用Gitee，国内访问更快）
        private const val UPDATE_REPO_URL = "https://gitee.com/yousen912/hlaunch.git"
        private const val UPDATE_TOKEN = "130a7cabc039011bcc4cb468b9e01bc8"
        // GitHub备用配置（国内访问较慢）
        // private const val UPDATE_REPO_URL = "https://ghproxy.com/https://github.com/github-yousen/HLaunch.git"
        // private const val UPDATE_TOKEN = "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"
    }
    
    private val updateDir = File(context.cacheDir, "update").also { it.mkdirs() }
    
    // APK信息
    data class ApkInfo(
        val apkFile: File,
        val fileName: String,
        val lastModified: Long,
        val remoteVersion: String,
        val hasUpdate: Boolean,
        val commitMessage: String,
        val commitTime: Long
    )
    
    fun getUpdateRepoUrl(): String = UPDATE_REPO_URL
    fun getUpdateToken(): String = UPDATE_TOKEN
    
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
    
    // 检查并下载最新APK
    suspend fun checkAndDownloadLatestApk(repoUrl: String, token: String?, onProgress: (String) -> Unit): ApkInfo? = withContext(Dispatchers.IO) {
        val repoDir = File(updateDir, "update_repo")
        var git: Git? = null
        
        try {
            onProgress("正在克隆仓库...")
            
            // 克隆仓库
            if (repoDir.exists()) {
                repoDir.deleteRecursively()
            }
            repoDir.mkdirs()
            
            val cloneCommand = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(repoDir)
            
            if (!token.isNullOrEmpty()) {
                cloneCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider("oauth2", token)
                )
            }
            
            git = cloneCommand.call()
            
            onProgress("正在查找APK文件...")
            
            // 在version目录下查找最新的APK文件
            val versionDir = File(repoDir, "version")
            if (!versionDir.exists() || !versionDir.isDirectory) {
                throw Exception("未找到version目录")
            }
            
            // 查找所有APK文件，按修改时间排序取最新的
            val apkFiles = versionDir.listFiles { file -> 
                file.isFile && file.name.endsWith(".apk", ignoreCase = true)
            }
            
            if (apkFiles.isNullOrEmpty()) {
                throw Exception("version目录下未找到APK文件")
            }
            
            // 取修改时间最新的APK
            val latestApk = apkFiles.maxByOrNull { it.lastModified() }!!
            
            // 解析远程版本号
            val remoteVersion = parseVersionFromFileName(latestApk.name) ?: throw Exception("无法从文件名解析版本号: ${latestApk.name}")
            val currentVersion = BuildConfig.VERSION_NAME
            val hasUpdate = compareVersions(remoteVersion, currentVersion) > 0
            
            onProgress("正在获取更新信息...")
            
            // 获取该APK文件对应的commit信息
            val apkRelativePath = "version/${latestApk.name}"
            val logCommand = git.log()
                .addPath(apkRelativePath)
                .setMaxCount(1)
            val commits = logCommand.call()
            val latestCommit = commits.firstOrNull()
            val commitMessage = latestCommit?.fullMessage?.trim() ?: "无更新说明"
            val commitTime = latestCommit?.commitTime?.toLong()?.times(1000) ?: latestApk.lastModified()
            
            onProgress("正在复制APK文件...")
            
            // 复制到缓存目录
            val targetApk = File(updateDir, "update.apk")
            if (targetApk.exists()) {
                targetApk.delete()
            }
            latestApk.copyTo(targetApk)
            
            ApkInfo(
                apkFile = targetApk,
                fileName = latestApk.name,
                lastModified = latestApk.lastModified(),
                remoteVersion = remoteVersion,
                hasUpdate = hasUpdate,
                commitMessage = commitMessage,
                commitTime = commitTime
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            git?.close()
            // 清理临时仓库目录
            repoDir.deleteRecursively()
        }
    }
}

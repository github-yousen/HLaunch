package com.HLaunch.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class AppUpdateManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val updateDir = File(context.cacheDir, "update").also { it.mkdirs() }
    
    // APK信息
    data class ApkInfo(
        val apkFile: File,
        val fileName: String,
        val lastModified: Long
    )
    
    // 保存更新配置
    fun saveUpdateConfig(repoUrl: String, token: String?) {
        prefs.edit()
            .putString("update_repo_url", repoUrl)
            .putString("update_token", token)
            .apply()
    }
    
    fun getUpdateRepoUrl(): String? = prefs.getString("update_repo_url", null)
    fun getUpdateToken(): String? = prefs.getString("update_token", null)
    
    // 检查并下载最新APK
    suspend fun checkAndDownloadLatestApk(repoUrl: String, token: String?, onProgress: (String) -> Unit): ApkInfo? = withContext(Dispatchers.IO) {
        val repoDir = File(updateDir, "update_repo")
        
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
                .setDepth(1)
            
            if (!token.isNullOrEmpty()) {
                cloneCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider("oauth2", token)
                )
            }
            
            cloneCommand.call().close()
            
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
                lastModified = latestApk.lastModified()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            // 清理临时仓库目录
            repoDir.deleteRecursively()
        }
    }
}

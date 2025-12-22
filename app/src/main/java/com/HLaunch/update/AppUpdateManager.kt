package com.HLaunch.update

import android.content.Context
import com.HLaunch.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
    private val updateDir = File(context.cacheDir, "update").also { it.mkdirs() }
    private val gson = Gson()
    
    // 版本信息数据类
    data class VersionInfo(
        @SerializedName("version") val version: String,
        @SerializedName("versionCode") val versionCode: Int,
        @SerializedName("changelog") val changelog: String = "",
        @SerializedName("apkName") val apkName: String,
        @SerializedName("downloadUrl") val downloadUrl: String? = null
    )
    
    // 更新信息
    data class UpdateInfo(
        val version: String,
        val versionCode: Int,
        val changelog: String,
        val downloadUrl: String
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
    
    // 检查更新
    suspend fun checkForUpdate(repoUrl: String, token: String?): UpdateInfo? = withContext(Dispatchers.IO) {
        val repoDir = File(updateDir, "update_repo")
        
        try {
            // 克隆或拉取仓库
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
            
            // 读取version.json
            val versionFile = File(repoDir, "version.json")
            if (!versionFile.exists()) {
                throw Exception("未找到version.json文件")
            }
            
            val versionInfo = gson.fromJson(versionFile.readText(), VersionInfo::class.java)
            
            // 比较版本
            val currentVersionCode = BuildConfig.VERSION_CODE
            if (versionInfo.versionCode > currentVersionCode) {
                // 确定下载URL
                val downloadUrl = versionInfo.downloadUrl 
                    ?: "${repoUrl.removeSuffix(".git")}/raw/main/${versionInfo.apkName}"
                
                return@withContext UpdateInfo(
                    version = versionInfo.version,
                    versionCode = versionInfo.versionCode,
                    changelog = versionInfo.changelog,
                    downloadUrl = downloadUrl
                )
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            // 清理临时目录
            repoDir.deleteRecursively()
        }
    }
    
    // 下载APK
    suspend fun downloadApk(
        downloadUrl: String,
        token: String?,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val apkFile = File(updateDir, "update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            
            // 添加认证头
            if (!token.isNullOrEmpty()) {
                connection.setRequestProperty("Authorization", "token $token")
            }
            
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("下载失败: ${connection.responseCode}")
            }
            
            val totalSize = connection.contentLength.toLong()
            var downloadedSize = 0L
            
            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        if (totalSize > 0) {
                            onProgress(downloadedSize.toFloat() / totalSize)
                        }
                    }
                }
            }
            
            apkFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

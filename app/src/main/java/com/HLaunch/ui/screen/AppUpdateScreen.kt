package com.HLaunch.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.HLaunch.BuildConfig
import com.HLaunch.update.AppUpdateManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateManager = remember { AppUpdateManager(context) }
    
    // 状态
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var versionInfo by remember { mutableStateOf<AppUpdateManager.VersionInfo?>(null) }
    var apkInfo by remember { mutableStateOf<AppUpdateManager.ApkInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // 下载进度
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadedSize by remember { mutableLongStateOf(0L) }
    var totalSize by remember { mutableLongStateOf(0L) }
    
    val currentVersion = remember { BuildConfig.VERSION_NAME }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("应用更新") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 当前版本信息
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "当前版本",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "v$currentVersion",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // 检查更新按钮
            Button(
                onClick = {
                    scope.launch {
                        isChecking = true
                        errorMessage = null
                        versionInfo = null
                        apkInfo = null
                        
                        try {
                            versionInfo = updateManager.checkUpdate()
                        } catch (e: Exception) {
                            errorMessage = "检查更新失败: ${e.message}"
                        } finally {
                            isChecking = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isChecking && !isDownloading
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("正在检查...")
                } else {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("检查更新")
                }
            }
            
            // 版本信息展示
            versionInfo?.let { info ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (info.hasUpdate) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 标题
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (info.hasUpdate) Icons.Default.NewReleases else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (info.hasUpdate) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (info.hasUpdate) "发现新版本" else "已是最新版本",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (info.hasUpdate) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // 版本对比
                        Text(
                            text = "v$currentVersion → v${info.remoteVersion}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        
                        // 文件信息
                        if (info.fileSize > 0) {
                            Text(
                                text = "文件大小: ${formatFileSize(info.fileSize)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Text(
                            text = "更新时间: ${dateFormat.format(Date(info.commitTime))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        // 更新说明
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "更新说明",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = info.commitMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        // 下载/安装按钮
                        if (info.hasUpdate) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (apkInfo != null) {
                                // 已下载完成，显示安装按钮
                                Button(
                                    onClick = {
                                        apkInfo?.let { installApk(context, it.apkFile) }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.InstallMobile, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("安装更新")
                                }
                            } else if (isDownloading) {
                                // 下载中，显示进度
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${formatFileSize(downloadedSize)} / ${formatFileSize(totalSize)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "$downloadProgress%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            } else {
                                // 显示下载按钮
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isDownloading = true
                                            errorMessage = null
                                            downloadProgress = 0
                                            downloadedSize = 0
                                            totalSize = info.fileSize
                                            
                                            try {
                                                apkInfo = updateManager.downloadApk(info) { downloaded, total, percent ->
                                                    downloadedSize = downloaded
                                                    totalSize = total
                                                    downloadProgress = percent
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "下载失败: ${e.message}"
                                            } finally {
                                                isDownloading = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Download, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("下载更新")
                                }
                            }
                        }
                    }
                }
            }
            
            // 错误信息
            errorMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}

private fun installApk(context: android.content.Context, apkFile: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile
    )
    
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    
    context.startActivity(intent)
}

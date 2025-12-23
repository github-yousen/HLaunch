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
    
    var isUpdating by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var apkInfo by remember { mutableStateOf<AppUpdateManager.ApkInfo?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
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
            
            // 说明
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "从Git仓库的version目录拉取最新APK安装包进行更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 检查更新按钮
            Button(
                onClick = {
                    scope.launch {
                        isUpdating = true
                        errorMessage = null
                        apkInfo = null
                        
                        try {
                            val info = updateManager.checkAndDownloadLatestApk(
                                updateManager.getUpdateRepoUrl(), 
                                updateManager.getUpdateToken()
                            ) { progress ->
                                progressMessage = progress
                            }
                            apkInfo = info
                        } catch (e: Exception) {
                            errorMessage = "更新失败: ${e.message}"
                        } finally {
                            isUpdating = false
                            progressMessage = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isUpdating
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Download, null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isUpdating) progressMessage.ifEmpty { "获取中..." } else "获取最新版本")
            }
            
            // APK信息
            apkInfo?.let { info ->
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
                        
                        Text(
                            text = "当前版本: v$currentVersion → 远程版本: v${info.remoteVersion}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Text(
                            text = "文件名: ${info.fileName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "提交时间: ${dateFormat.format(Date(info.commitTime))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        // 更新说明
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "更新说明:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = info.commitMessage,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        // 安装按钮（仅有更新时显示）
                        if (info.hasUpdate) {
                            Button(
                                onClick = {
                                    installApk(context, info.apkFile)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.InstallMobile, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("安装更新")
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

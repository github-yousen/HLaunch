package com.HLaunch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.HLaunch.BuildConfig
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.util.DevLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    var devModeEnabled by remember { mutableStateOf(DevLogger.isDevModeEnabled(context)) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableIntStateOf(0) }
    
    // 密码输入对话框
    if (showPasswordDialog) {
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("开启开发者模式") },
            text = {
                Column {
                    Text("请输入开发者密码")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = false },
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = error,
                        supportingText = if (error) {{ Text("密码错误") }} else null,
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (DevLogger.enableDevMode(context, password)) {
                        devModeEnabled = true
                        showPasswordDialog = false
                    } else {
                        error = true
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 开发者选项（开启后显示）
            if (devModeEnabled) {
                item {
                    Text(
                        text = "开发者选项",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                item {
                    SettingsItem(
                        icon = Icons.Default.Close,
                        title = "关闭开发者模式",
                        subtitle = "关闭后隐藏开发者选项",
                        onClick = {
                            DevLogger.disableDevMode(context)
                            devModeEnabled = false
                        }
                    )
                }
            }
            
            // 关于
            item {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                // 连续点击5次版本号开启开发者模式
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "版本",
                    subtitle = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" + 
                        if (!devModeEnabled && versionClickCount > 0) " (再点${5 - versionClickCount}次)" else "",
                    onClick = {
                        if (!devModeEnabled) {
                            versionClickCount++
                            if (versionClickCount >= 5) {
                                showPasswordDialog = true
                                versionClickCount = 0
                            }
                        }
                    }
                )
            }
            
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "HLaunch",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "HTML微应用启动器 & 管理器",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• 运行HTML/JS/CSS微应用\n• 支持Git仓库同步\n• 类似微信小程序的多任务管理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

package com.HLaunch.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.HLaunch.data.entity.FileSource
import com.HLaunch.data.entity.HtmlFile
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.util.DevLogger
import com.HLaunch.viewmodel.HtmlFileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    fileViewModel: HtmlFileViewModel
) {
    val context = LocalContext.current
    val allFiles by fileViewModel.allFiles.collectAsState()
    val localFiles by fileViewModel.getLocalFiles().collectAsState(initial = emptyList())
    val importedFiles by fileViewModel.getImportedFiles().collectAsState(initial = emptyList())
    val gitFiles by fileViewModel.getGitFiles().collectAsState(initial = emptyList())
    val favoriteFiles by fileViewModel.favoriteFiles.collectAsState()
    val runningTasks by fileViewModel.runningTasks.collectAsState()
    val isDevMode = remember { DevLogger.isDevModeEnabled(context) }
    
    // 文件筛选：0-全部 1-本地 2-导入 3-Git
    var fileFilter by remember { mutableIntStateOf(0) }
    val filterLabels = listOf("全部文件", "本地", "导入", "Git")
    // 文件列表展开/收起
    var filesExpanded by remember { mutableStateOf(true) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HLaunch") },
                actions = {
                    // 开发者日志按钮（仅开发者模式显示）
                    if (isDevMode) {
                        IconButton(onClick = { navController.navigate(Screen.DevLog.route) }) {
                            Icon(Icons.Default.BugReport, "开发者日志")
                        }
                    }
                    // 运行中任务数量徽章
                    if (runningTasks.isNotEmpty()) {
                        BadgedBox(
                            badge = { Badge { Text("${runningTasks.size}") } }
                        ) {
                            IconButton(onClick = { navController.navigate(Screen.RunningTasks.route) }) {
                                Icon(Icons.Default.PlayCircle, "运行中")
                            }
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.CreateFile.route) }
            ) {
                Icon(Icons.Default.Add, "创建")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 快捷操作
            item {
                QuickActions(navController)
            }
            
            // 收藏
            if (favoriteFiles.isNotEmpty()) {
                item {
                    SectionHeader("收藏", Icons.Default.Star)
                }
                items(favoriteFiles.take(5)) { file ->
                    FileListItem(
                        file = file,
                        onClick = { navController.navigate(Screen.EditFile.createRoute(file.id)) },
                        onRun = {
                            navController.navigate(Screen.RunFile.createRoute(file.id))
                        },
                        onEdit = { navController.navigate(Screen.EditFile.createRoute(file.id)) },
                        onFavorite = { fileViewModel.toggleFavorite(file) }
                    )
                }
            }
            
            // 全部文件（排除已收藏的，最多显示10个）
            val filteredFiles = when (fileFilter) {
                1 -> localFiles
                2 -> importedFiles
                3 -> gitFiles
                else -> allFiles
            }
            val nonFavoriteFiles = filteredFiles.filter { !it.isFavorite }.take(10)
            if (nonFavoriteFiles.isNotEmpty() || fileFilter != 0) {
                item {
                    SectionHeader(
                        title = filterLabels[fileFilter],
                        icon = Icons.Default.Folder,
                        expanded = filesExpanded,
                        onExpandChange = { filesExpanded = it },
                        currentFilter = fileFilter,
                        onFilterChange = { fileFilter = it }
                    )
                }
                if (filesExpanded) {
                    if (nonFavoriteFiles.isEmpty()) {
                        item {
                            Text(
                                text = "暂无${filterLabels[fileFilter]}文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(nonFavoriteFiles) { file ->
                            FileListItem(
                                file = file,
                                onClick = { navController.navigate(Screen.EditFile.createRoute(file.id)) },
                                onRun = {
                                    navController.navigate(Screen.RunFile.createRoute(file.id))
                                },
                                onEdit = { navController.navigate(Screen.EditFile.createRoute(file.id)) },
                                onFavorite = { fileViewModel.toggleFavorite(file) }
                            )
                        }
                    }
                }
            }
            
            // 空状态
            if (allFiles.isEmpty()) {
                item {
                    EmptyState(
                        message = "还没有HTML文件\n点击右下角按钮创建",
                        icon = Icons.Default.Description
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActions(navController: NavController) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            modifier = Modifier.weight(1f),
            title = "所有文件",
            icon = Icons.Default.Folder,
            onClick = { navController.navigate(Screen.FileList.route) }
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            title = "Git仓库",
            icon = Icons.Default.Cloud,
            onClick = { navController.navigate(Screen.GitRepos.route) }
        )
        QuickActionCard(
            modifier = Modifier.weight(1f),
            title = "应用更新",
            icon = Icons.Default.SystemUpdate,
            onClick = { navController.navigate(Screen.AppUpdate.route) }
        )
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean? = null,
    onExpandChange: ((Boolean) -> Unit)? = null,
    currentFilter: Int? = null,
    onFilterChange: ((Int) -> Unit)? = null
) {
    var filterExpanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        // 筛选按钮
        if (onFilterChange != null) {
            Box {
                IconButton(onClick = { filterExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "筛选",
                        tint = if (currentFilter != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = filterExpanded,
                    onDismissRequest = { filterExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("全部文件") },
                        onClick = { filterExpanded = false; onFilterChange(0) },
                        leadingIcon = { Icon(Icons.Default.Folder, null) },
                        trailingIcon = { if (currentFilter == 0) Icon(Icons.Default.Check, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("本地") },
                        onClick = { filterExpanded = false; onFilterChange(1) },
                        leadingIcon = { Icon(Icons.Default.Description, null) },
                        trailingIcon = { if (currentFilter == 1) Icon(Icons.Default.Check, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("导入") },
                        onClick = { filterExpanded = false; onFilterChange(2) },
                        leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                        trailingIcon = { if (currentFilter == 2) Icon(Icons.Default.Check, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Git") },
                        onClick = { filterExpanded = false; onFilterChange(3) },
                        leadingIcon = { Icon(Icons.Default.Cloud, null) },
                        trailingIcon = { if (currentFilter == 3) Icon(Icons.Default.Check, null) }
                    )
                }
            }
        }
        // 展开/收起按钮
        if (onExpandChange != null && expanded != null) {
            IconButton(onClick = { onExpandChange(!expanded) }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FileListItem(
    file: HtmlFile,
    onClick: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
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
            // 来源图标
            Icon(
                imageVector = when (file.source) {
                    FileSource.LOCAL -> Icons.Default.Description
                    FileSource.IMPORTED -> Icons.Default.FileOpen
                    FileSource.GIT -> Icons.Default.Cloud
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "创建于 ${dateFormat.format(Date(file.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 启动按钮
            FilledTonalIconButton(onClick = onRun) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "启动",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // 收藏按钮
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (file.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏",
                    tint = if (file.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 编辑按钮
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑"
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

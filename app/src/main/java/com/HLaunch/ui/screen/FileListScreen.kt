package com.HLaunch.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.HLaunch.data.entity.FileSource
import com.HLaunch.data.entity.HtmlFile
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.viewmodel.HtmlFileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    navController: NavController,
    viewModel: HtmlFileViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("全部", "本地", "Git")
    
    val allFiles by viewModel.allFiles.collectAsState()
    val localFiles by viewModel.getLocalFiles().collectAsState(initial = emptyList())
    val gitFiles by viewModel.getGitFiles().collectAsState(initial = emptyList())
    
    val displayFiles = when (selectedTab) {
        0 -> allFiles
        1 -> localFiles
        2 -> gitFiles
        else -> allFiles
    }
    
    // 多选模式
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<Long>()) }
    
    // 退出多选模式
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedFiles = emptySet()
    }
    
    var showDeleteDialog by remember { mutableStateOf<HtmlFile?>(null) }
    // 批量删除对话框
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    // Git文件删除选项对话框
    var showGitDeleteDialog by remember { mutableStateOf(false) }
    var filesToDelete by remember { mutableStateOf<List<HtmlFile>>(emptyList()) }
    
    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedFiles.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, "取消")
                        }
                    },
                    actions = {
                        // 全选
                        IconButton(onClick = {
                            selectedFiles = if (selectedFiles.size == displayFiles.size) {
                                emptySet()
                            } else {
                                displayFiles.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                if (selectedFiles.size == displayFiles.size) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                "全选"
                            )
                        }
                        // 删除选中
                        IconButton(
                            onClick = {
                                filesToDelete = displayFiles.filter { it.id in selectedFiles }
                                val hasGitFiles = filesToDelete.any { it.source == FileSource.GIT }
                                if (hasGitFiles) {
                                    showGitDeleteDialog = true
                                } else {
                                    showBatchDeleteDialog = true
                                }
                            },
                            enabled = selectedFiles.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, "删除", tint = if (selectedFiles.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("文件管理") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.CreateFile.route) }
                ) {
                    Icon(Icons.Default.Add, "创建")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 标签页
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            // 文件列表
            if (displayFiles.isEmpty()) {
                EmptyState(
                    message = "暂无文件",
                    icon = Icons.Default.FolderOpen
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayFiles, key = { it.id }) { file ->
                        FileListItemWithDelete(
                            file = file,
                            isSelectionMode = isSelectionMode,
                            isSelected = file.id in selectedFiles,
                            onLongClick = {
                                isSelectionMode = true
                                selectedFiles = setOf(file.id)
                            },
                            onSelect = {
                                selectedFiles = if (file.id in selectedFiles) {
                                    selectedFiles - file.id
                                } else {
                                    selectedFiles + file.id
                                }
                                if (selectedFiles.isEmpty()) {
                                    isSelectionMode = false
                                }
                            },
                            onRun = {
                                navController.navigate(Screen.RunFile.createRoute(file.id))
                            },
                            onEdit = { navController.navigate(Screen.EditFile.createRoute(file.id)) },
                            onFavorite = { viewModel.toggleFavorite(file) },
                            onDelete = { 
                                filesToDelete = listOf(file)
                                if (file.source == FileSource.GIT) {
                                    showGitDeleteDialog = true
                                } else {
                                    showDeleteDialog = file
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 单个文件删除确认对话框
    showDeleteDialog?.let { file ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除 \"${file.name}\" 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(file)
                        showDeleteDialog = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
    
    // 批量删除确认对话框（无Git文件）
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${filesToDelete.size} 个文件吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        filesToDelete.forEach { viewModel.deleteFile(it) }
                        showBatchDeleteDialog = false
                        exitSelectionMode()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // Git文件删除选项对话框
    if (showGitDeleteDialog) {
        val gitFileCount = filesToDelete.count { it.source == FileSource.GIT }
        AlertDialog(
            onDismissRequest = { showGitDeleteDialog = false },
            title = { Text("删除包含Git文件") },
            text = { 
                Text("你所删除的文件包含 $gitFileCount 个Git仓库文件。\n\n选择\"取消Git跟踪\"后，相关仓库将不再自动同步这些文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 删除文件并取消Git跟踪
                        filesToDelete.forEach { file ->
                            if (file.source == FileSource.GIT && file.gitRepoId != null) {
                                viewModel.deleteFileAndDisableSync(file)
                            } else {
                                viewModel.deleteFile(file)
                            }
                        }
                        showGitDeleteDialog = false
                        exitSelectionMode()
                    }
                ) {
                    Text("删除并取消跟踪", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showGitDeleteDialog = false }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            // 仅删除文件
                            filesToDelete.forEach { viewModel.deleteFile(it) }
                            showGitDeleteDialog = false
                            exitSelectionMode()
                        }
                    ) {
                        Text("仅删除文件")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItemWithDelete(
    file: HtmlFile,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onSelect: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelect()
                    } else {
                        onEdit()
                    }
                },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelect() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // 来源图标
            Icon(
                imageVector = when (file.source) {
                    FileSource.LOCAL -> Icons.Default.Description
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "创建于 ${dateFormat.format(Date(file.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (!isSelectionMode) {
                // 启动按钮
                FilledTonalIconButton(onClick = onRun) {
                    Icon(
                        Icons.Default.PlayArrow, 
                        "启动",
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
                    Icon(Icons.Default.Edit, "编辑")
                }
                
                // 删除按钮
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

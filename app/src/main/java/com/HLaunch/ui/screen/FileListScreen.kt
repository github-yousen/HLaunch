package com.HLaunch.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.HLaunch.data.entity.FileSource
import com.HLaunch.data.entity.HtmlFile
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.viewmodel.HtmlFileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    navController: NavController,
    viewModel: HtmlFileViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("全部", "本地", "导入", "Git")
    
    val allFiles by viewModel.allFiles.collectAsState()
    val localFiles by viewModel.getLocalFiles().collectAsState(initial = emptyList())
    val importedFiles by viewModel.getImportedFiles().collectAsState(initial = emptyList())
    val gitFiles by viewModel.getGitFiles().collectAsState(initial = emptyList())
    
    val displayFiles = when (selectedTab) {
        0 -> allFiles
        1 -> localFiles
        2 -> importedFiles
        3 -> gitFiles
        else -> allFiles
    }
    
    var showDeleteDialog by remember { mutableStateOf<HtmlFile?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件管理") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
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
                            onClick = { navController.navigate(Screen.RunFile.createRoute(file.id)) },
                            onEdit = { navController.navigate(Screen.EditFile.createRoute(file.id)) },
                            onFavorite = { viewModel.toggleFavorite(file) },
                            onDelete = { showDeleteDialog = file }
                        )
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
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
}

@Composable
private fun FileListItemWithDelete(
    file: HtmlFile,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = when (file.source) {
                        FileSource.LOCAL -> "本地创建"
                        FileSource.IMPORTED -> "导入文件"
                        FileSource.GIT -> "Git同步"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 运行按钮
            IconButton(onClick = onClick) {
                Icon(Icons.Default.PlayArrow, "运行")
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

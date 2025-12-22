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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.HLaunch.data.entity.GitRepo
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.viewmodel.GitRepoViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitReposScreen(
    navController: NavController,
    viewModel: GitRepoViewModel
) {
    val repos by viewModel.allRepos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf<GitRepo?>(null) }
    
    // 显示消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            // Snackbar会自动显示
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Git仓库") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddRepo.route) }
            ) {
                Icon(Icons.Default.Add, "添加仓库")
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = remember { SnackbarHostState() }.also { host ->
                LaunchedEffect(errorMessage, successMessage) {
                    errorMessage?.let {
                        host.showSnackbar(it)
                        viewModel.clearError()
                    }
                    successMessage?.let {
                        host.showSnackbar(it)
                        viewModel.clearSuccess()
                    }
                }
            })
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (repos.isEmpty()) {
                EmptyState(
                    message = "还没有Git仓库\n点击右下角按钮添加",
                    icon = Icons.Default.Cloud
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(repos, key = { it.id }) { repo ->
                        RepoCard(
                            repo = repo,
                            onClick = { navController.navigate(Screen.RepoDetail.createRoute(repo.id)) },
                            onSync = { viewModel.pullRepo(repo) },
                            onDelete = { showDeleteDialog = repo },
                            isLoading = isLoading
                        )
                    }
                }
            }
            
            // 加载指示器
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
    
    // 删除确认对话框
    showDeleteDialog?.let { repo ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除仓库 \"${repo.name}\" 吗？\n这将同时删除所有关联的HTML文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRepo(repo)
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
private fun RepoCard(
    repo: GitRepo,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    isLoading: Boolean
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = repo.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分支和同步时间
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = repo.branch,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    repo.lastSyncAt?.let {
                        Text(
                            text = "上次同步: ${dateFormat.format(Date(it))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // 操作按钮
                Row {
                    IconButton(
                        onClick = onSync,
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Sync, "同步")
                    }
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
}

package com.HLaunch.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.viewmodel.HtmlFileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFileScreen(
    navController: NavController,
    viewModel: HtmlFileViewModel,
    fileId: Long
) {
    val scope = rememberCoroutineScope()
    var fileName by remember { mutableStateOf("") }
    var htmlContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var hasChanges by remember { mutableStateOf(false) }
    
    val originalFile = remember { mutableStateOf<com.HLaunch.data.entity.HtmlFile?>(null) }
    
    // 加载文件
    LaunchedEffect(fileId) {
        val file = viewModel.getFileById(fileId)
        file?.let {
            originalFile.value = it
            fileName = it.name
            htmlContent = it.content
        }
        isLoading = false
    }
    
    // 检测变化
    LaunchedEffect(fileName, htmlContent) {
        originalFile.value?.let {
            hasChanges = fileName != it.name || htmlContent != it.content
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑文件") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 运行按钮
                    IconButton(
                        onClick = { navController.navigate(Screen.RunFile.createRoute(fileId)) }
                    ) {
                        Icon(Icons.Default.PlayArrow, "运行")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (originalFile.value == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("文件不存在")
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 文件信息
                originalFile.value?.let { file ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "来源: ${when (file.source) {
                                    com.HLaunch.data.entity.FileSource.LOCAL -> "本地创建"
                                    com.HLaunch.data.entity.FileSource.GIT -> "Git同步"
                                }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (file.gitRepoId != null) {
                                Text(
                                    text = "关联Git仓库",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                // 文件名输入
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("文件名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Description, null) }
                )
                
                // HTML内容输入
                OutlinedTextField(
                    value = htmlContent,
                    onValueChange = { htmlContent = it },
                    label = { Text("HTML代码") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    minLines = 10
                )
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    
                    Button(
                        onClick = {
                            originalFile.value?.let { file ->
                                viewModel.updateFile(
                                    file.copy(
                                        name = fileName.trim(),
                                        content = htmlContent
                                    )
                                )
                                navController.popBackStack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = hasChanges && fileName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存")
                    }
                }
            }
        }
    }
}

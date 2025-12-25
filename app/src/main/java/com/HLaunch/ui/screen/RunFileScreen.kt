package com.HLaunch.ui.screen

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.HLaunch.data.entity.HtmlFile
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.viewmodel.HtmlFileViewModel
import com.HLaunch.webview.WebViewPool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunFileScreen(
    navController: NavController,
    viewModel: HtmlFileViewModel,
    fileId: Long
) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<HtmlFile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    val runningTasks by viewModel.runningTasks.collectAsState()
    
    // 加载文件并获取/创建WebView
    LaunchedEffect(fileId) {
        val loadedFile = viewModel.getFileById(fileId)
        loadedFile?.let {
            file = it
            viewModel.runFile(it)
            // 从池中获取或创建WebView
            webView = WebViewPool.getOrCreate(
                context = context,
                fileId = fileId,
                fileName = it.name,
                htmlContent = it.content,
                onTitleChanged = { title -> pageTitle = title }
            )
            // 恢复已有的页面标题
            pageTitle = WebViewPool.getPageTitle(fileId)
        }
        isLoading = false
    }
    
    // 更新canGoBack状态
    LaunchedEffect(webView) {
        webView?.let { canGoBack = it.canGoBack() }
    }
    
    // 页面离开时从父容器移除WebView（但不销毁）
    DisposableEffect(fileId) {
        onDispose {
            WebViewPool.detach(fileId)
        }
    }
    
    // 处理返回键
    BackHandler(enabled = canGoBack) {
        webView?.goBack()
        canGoBack = webView?.canGoBack() ?: false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = pageTitle.ifEmpty { file?.name ?: "运行中" },
                            maxLines = 1
                        )
                        if (runningTasks.size > 1) {
                            Text(
                                text = "${runningTasks.size}个任务运行中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 主页按钮
                    IconButton(onClick = { 
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    }) {
                        Icon(Icons.Default.Home, "主页")
                    }
                    // 多任务切换
                    if (runningTasks.size > 1) {
                        IconButton(onClick = { navController.navigate(Screen.RunningTasks.route) }) {
                            BadgedBox(badge = { Badge { Text("${runningTasks.size}") } }) {
                                Icon(Icons.Default.Layers, "多任务")
                            }
                        }
                    }
                    
                    // 刷新
                    IconButton(onClick = { 
                        file?.let { WebViewPool.reload(fileId, it.content) }
                    }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    
                    // 更多菜单
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑代码") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.EditFile.createRoute(fileId))
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭此任务") },
                                onClick = {
                                    showMenu = false
                                    WebViewPool.close(fileId)
                                    viewModel.closeTask(fileId)
                                    navController.popBackStack()
                                },
                                leadingIcon = { Icon(Icons.Default.Close, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭所有任务") },
                                onClick = {
                                    showMenu = false
                                    WebViewPool.closeAll()
                                    viewModel.closeAllTasks()
                                    navController.popBackStack()
                                },
                                leadingIcon = { Icon(Icons.Default.ClearAll, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (file == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("文件不存在")
                }
            } else {
                // 使用池中的WebView
                PooledWebView(
                    webView = webView,
                    onCanGoBackChanged = { canGoBack = it }
                )
            }
        }
    }
}

@Composable
private fun PooledWebView(
    webView: WebView?,
    onCanGoBackChanged: (Boolean) -> Unit
) {
    webView?.let { wv ->
        AndroidView(
            factory = { _ ->
                // 先从旧父容器移除
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv
            },
            update = { view ->
                view.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onCanGoBackChanged(view?.canGoBack() ?: false)
                    }
                    
                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onCanGoBackChanged(view?.canGoBack() ?: false)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

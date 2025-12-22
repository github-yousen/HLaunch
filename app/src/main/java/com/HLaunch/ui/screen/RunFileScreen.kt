package com.HLaunch.ui.screen

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunFileScreen(
    navController: NavController,
    viewModel: HtmlFileViewModel,
    fileId: Long
) {
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<HtmlFile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    
    val runningTasks by viewModel.runningTasks.collectAsState()
    
    // 加载文件并添加到运行任务
    LaunchedEffect(fileId) {
        val loadedFile = viewModel.getFileById(fileId)
        loadedFile?.let {
            file = it
            viewModel.runFile(it)
        }
        isLoading = false
    }
    
    // 处理返回键
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
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
                    // 多任务切换
                    if (runningTasks.size > 1) {
                        IconButton(onClick = { navController.navigate(Screen.RunningTasks.route) }) {
                            BadgedBox(badge = { Badge { Text("${runningTasks.size}") } }) {
                                Icon(Icons.Default.Layers, "多任务")
                            }
                        }
                    }
                    
                    // 刷新
                    IconButton(onClick = { webViewRef?.reload() }) {
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
                                    viewModel.closeTask(fileId)
                                    navController.popBackStack()
                                },
                                leadingIcon = { Icon(Icons.Default.Close, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("关闭所有任务") },
                                onClick = {
                                    showMenu = false
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
                // WebView
                HtmlWebView(
                    htmlContent = file!!.content,
                    onWebViewCreated = { webViewRef = it },
                    onCanGoBackChanged = { canGoBack = it },
                    onTitleChanged = { pageTitle = it }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWebView(
    htmlContent: String,
    onWebViewCreated: (WebView) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onTitleChanged: (String) -> Unit
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    mediaPlaybackRequiresUserGesture = false
                }
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onCanGoBackChanged(canGoBack())
                    }
                    
                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        onCanGoBackChanged(canGoBack())
                    }
                }
                
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        title?.let { onTitleChanged(it) }
                    }
                }
                
                onWebViewCreated(this)
                
                // 加载HTML内容
                loadDataWithBaseURL(
                    "file:///android_asset/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

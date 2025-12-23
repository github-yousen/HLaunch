package com.HLaunch

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.HLaunch.ui.theme.HLaunchTheme

/**
 * WebView Activity池管理器，类似微信小程序的实现方式
 */
object WebViewActivityPool {
    // 预定义5个Activity槽位
    private val activitySlots = arrayOf(
        WebViewActivity1::class.java,
        WebViewActivity2::class.java,
        WebViewActivity3::class.java,
        WebViewActivity4::class.java,
        WebViewActivity5::class.java
    )
    
    // 记录每个槽位当前运行的fileId，-1表示空闲
    private val slotFileIds = longArrayOf(-1, -1, -1, -1, -1)
    
    // 获取一个可用的Activity类，优先复用已有的，否则分配新的
    @Synchronized
    fun getActivityClass(fileId: Long): Class<out BaseWebViewActivity> {
        // 检查是否已经有该文件的Activity在运行
        for (i in slotFileIds.indices) {
            if (slotFileIds[i] == fileId) {
                return activitySlots[i]
            }
        }
        // 找一个空闲槽位
        for (i in slotFileIds.indices) {
            if (slotFileIds[i] == -1L) {
                slotFileIds[i] = fileId
                return activitySlots[i]
            }
        }
        // 没有空闲槽位，复用最早的槽位（槽位0）
        slotFileIds[0] = fileId
        return activitySlots[0]
    }
    
    // 释放槽位
    @Synchronized
    fun releaseSlot(fileId: Long) {
        for (i in slotFileIds.indices) {
            if (slotFileIds[i] == fileId) {
                slotFileIds[i] = -1
                break
            }
        }
    }
    
    // 启动WebView Activity
    fun launchWebView(context: Context, fileId: Long, fileName: String, htmlContent: String) {
        val activityClass = getActivityClass(fileId)
        val intent = Intent(context, activityClass).apply {
            putExtra("FILE_ID", fileId)
            putExtra("FILE_NAME", fileName)
            putExtra("HTML_CONTENT", htmlContent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * 基础WebView Activity，包含所有WebView逻辑
 */
abstract class BaseWebViewActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val fileId = intent.getLongExtra("FILE_ID", -1)
        val fileName = intent.getStringExtra("FILE_NAME") ?: "HTML应用"
        val htmlContent = intent.getStringExtra("HTML_CONTENT") ?: ""
        
        // 设置任务栏显示的标题和图标
        setTaskDescription(fileId, fileName)
        
        setContent {
            HLaunchTheme {
                WebViewScreen(
                    fileId = fileId,
                    fileName = fileName,
                    htmlContent = htmlContent,
                    onClose = { finish() },
                    onTitleChanged = { newTitle -> setTaskDescription(fileId, newTitle) }
                )
            }
        }
    }
    
    private fun setTaskDescription(fileId: Long, title: String) {
        val icon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(ActivityManager.TaskDescription.Builder()
                .setLabel(title)
                .setIcon(R.mipmap.ic_launcher)
                .build())
        } else {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(title, icon))
        }
    }
    
    override fun onDestroy() {
        val fileId = intent.getLongExtra("FILE_ID", -1)
        if (fileId != -1L) {
            WebViewActivityPool.releaseSlot(fileId)
        }
        super.onDestroy()
    }
}

// 预定义5个独立的WebView Activity，每个有独立的进程和taskAffinity
class WebViewActivity1 : BaseWebViewActivity()
class WebViewActivity2 : BaseWebViewActivity()
class WebViewActivity3 : BaseWebViewActivity()
class WebViewActivity4 : BaseWebViewActivity()
class WebViewActivity5 : BaseWebViewActivity()

// 保留原来的WebViewActivity类名以兼容旧代码
class WebViewActivity : BaseWebViewActivity()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewScreen(
    fileId: Long,
    fileName: String,
    htmlContent: String,
    onClose: () -> Unit,
    onTitleChanged: (String) -> Unit = {}
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    
    // 处理返回键
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = pageTitle.ifEmpty { fileName },
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                },
                actions = {
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
                                text = { Text("关闭应用") },
                                onClick = {
                                    showMenu = false
                                    onClose()
                                },
                                leadingIcon = { Icon(Icons.Default.Close, null) }
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
            if (htmlContent.isEmpty()) {
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
                    Text("内容为空")
                }
            } else {
                IndependentHtmlWebView(
                    fileId = fileId,
                    htmlContent = htmlContent,
                    onWebViewCreated = { webViewRef = it },
                    onCanGoBackChanged = { canGoBack = it },
                    onTitleChanged = { 
                        pageTitle = it
                        onTitleChanged(it)
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun IndependentHtmlWebView(
    fileId: Long,
    htmlContent: String,
    onWebViewCreated: (WebView) -> Unit,
    onCanGoBackChanged: (Boolean) -> Unit,
    onTitleChanged: (String) -> Unit
) {
    // 为每个HTML文件生成独立的origin，实现存储隔离
    val uniqueOrigin = "https://app-${fileId}.hlaunch.local/"
    
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
                
                // 使用独立origin加载HTML，实现localStorage/IndexedDB隔离
                loadDataWithBaseURL(
                    uniqueOrigin,
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

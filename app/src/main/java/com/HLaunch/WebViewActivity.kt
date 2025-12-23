package com.HLaunch

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebView Activity池管理器，类似微信小程序的实现方式
 * 使用 LRU 策略和持久化存储来管理 Slot 分配
 */
object WebViewActivityPool {
    private const val PREFS_NAME = "webview_pool_prefs"
    private const val KEY_POOL_STATE = "pool_state"
    private const val MAX_SLOTS = 5
    
    // 预定义5个Activity槽位
    private val activitySlots = arrayOf(
        WebViewActivity1::class.java,
        WebViewActivity2::class.java,
        WebViewActivity3::class.java,
        WebViewActivity4::class.java,
        WebViewActivity5::class.java
    )
    
    /**
     * 启动WebView Activity
     * 自动分配或复用槽位（LRU策略）
     */
    fun launchWebView(context: Context, fileId: Long, fileName: String, htmlContent: String) {
        val slotIndex = allocateSlot(context, fileId)
        val activityClass = activitySlots[slotIndex]
        
        val intent = Intent(context, activityClass).apply {
            putExtra("FILE_ID", fileId)
            putExtra("FILE_NAME", fileName)
            putExtra("HTML_CONTENT", htmlContent)
            // 关键标志组合（解决Vivo等厂商系统后台任务合并问题）：
            // FLAG_ACTIVITY_NEW_DOCUMENT: 以"文档"模式启动，在最近任务中显示为独立卡片
            // FLAG_ACTIVITY_MULTIPLE_TASK: 允许创建多个任务实例（配合documentLaunchMode="always"）
            // FLAG_ACTIVITY_NEW_TASK: 在新任务栈启动（非Activity上下文必需）
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 分配槽位算法
     * 1. 如果已存在该文件的槽位 -> 复用并更新为MRU（最近使用）
     * 2. 如果有空闲槽位 -> 分配新槽位
     * 3. 如果已满 -> 淘汰LRU（最久未使用）的槽位并分配给新文件
     */
    @Synchronized
    private fun allocateSlot(context: Context, fileId: Long): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_POOL_STATE, "[]")
        val jsonArray = JSONArray(jsonStr)
        
        // 解析当前状态: List<Pair<FileId, SlotIndex>>，列表顺序即为LRU顺序（末尾为MRU）
        val state = ArrayList<Pair<Long, Int>>()
        val usedSlots = HashSet<Int>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val fid = obj.getLong("f")
            val sid = obj.getInt("s")
            state.add(fid to sid)
            usedSlots.add(sid)
        }
        
        // 1. 查找是否已存在
        val existingIndex = state.indexOfFirst { it.first == fileId }
        if (existingIndex != -1) {
            // 命中：移到末尾（MRU）
            val item = state.removeAt(existingIndex)
            state.add(item)
            saveState(prefs, state)
            return item.second
        }
        
        // 2. 查找空闲槽位
        if (state.size < MAX_SLOTS) {
            var freeSlot = -1
            for (i in 0 until MAX_SLOTS) {
                if (i !in usedSlots) {
                    freeSlot = i
                    break
                }
            }
            if (freeSlot != -1) {
                state.add(fileId to freeSlot)
                saveState(prefs, state)
                return freeSlot
            }
        }
        
        // 3. 槽位已满，淘汰LRU（第一个）
        if (state.isNotEmpty()) {
            val evicted = state.removeAt(0)
            val reusedSlot = evicted.second
            // 复用该槽位
            state.add(fileId to reusedSlot)
            saveState(prefs, state)
            return reusedSlot
        }
        
        // 异常情况（不应发生），默认返回0
        return 0
    }
    
    private fun saveState(prefs: SharedPreferences, state: List<Pair<Long, Int>>) {
        val jsonArray = JSONArray()
        state.forEach {
            val obj = JSONObject()
            obj.put("f", it.first)
            obj.put("s", it.second)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_POOL_STATE, jsonArray.toString()).apply()
    }

    @Synchronized
    fun releaseSlot(context: Context, fileId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_POOL_STATE, "[]")
        val jsonArray = JSONArray(jsonStr)
        val newState = ArrayList<JSONObject>()
        
        var changed = false
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getLong("f") == fileId) {
                changed = true // Skip (remove)
            } else {
                newState.add(obj)
            }
        }
        
        if (changed) {
            val newJsonArray = JSONArray(newState)
            prefs.edit().putString(KEY_POOL_STATE, newJsonArray.toString()).apply()
        }
    }
}

/**
 * 基础WebView Activity，包含所有WebView逻辑
 */
abstract class BaseWebViewActivity : ComponentActivity() {
    
    // 使用 MutableState 管理Intent参数，以便在 onNewIntent 时触发重组
    private var currentFileId by mutableLongStateOf(-1L)
    private var currentFileName by mutableStateOf("")
    private var currentHtmlContent by mutableStateOf("")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // 多进程WebView需设置独立数据目录，防止数据冲突 crash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                // 使用类名作为后缀（如 WebViewActivity1），确保每个进程独享一个目录
                WebView.setDataDirectorySuffix(this.javaClass.simpleName)
            } catch (e: Exception) {
                // 如果已初始化过WebView，再次设置会抛出异常，忽略即可
            }
        }

        super.onCreate(savedInstanceState)
        
        // 初始加载Intent数据
        processIntent(intent)
        
        setContent {
            HLaunchTheme {
                // key(currentFileId) 确保当FileID变化时（槽位复用），整个Screen被重建
                // 从而强制 WebView 重新加载新的 origin 和 content
                key(currentFileId) {
                    WebViewScreen(
                        fileId = currentFileId,
                        fileName = currentFileName,
                        htmlContent = currentHtmlContent,
                        onClose = { finishAndRemoveTask() }, // 彻底关闭任务
                        onTitleChanged = { updateTaskDescription(it) }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 更新Activity内部Intent引用
        processIntent(intent) // 处理新Intent数据，触发UI刷新
    }
    
    private fun processIntent(intent: Intent) {
        val newFileId = intent.getLongExtra("FILE_ID", -1L)
        // 只有当ID有效时才更新，避免异常
        if (newFileId != -1L) {
            currentFileId = newFileId
            currentFileName = intent.getStringExtra("FILE_NAME") ?: "HTML应用"
            currentHtmlContent = intent.getStringExtra("HTML_CONTENT") ?: ""
            updateTaskDescription(currentFileName)
        }
    }
    
    private fun updateTaskDescription(title: String) {
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
    
    // 不再需要在 onDestroy 释放槽位，因为我们要保持后台驻留状态 (LRU策略管理)
    // 只有用户手动"关闭应用"时才可能需要释放，但在微信小程序模式下，通常保留状态直到被LRU挤掉
    // 如果需要手动清理，可以在 onClose 中调用
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

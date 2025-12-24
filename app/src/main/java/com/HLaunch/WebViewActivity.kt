package com.HLaunch

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
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
import com.HLaunch.util.DevLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebView Activity池管理器，类似微信小程序的实现方式
 * 使用 ShortcutManager 实现独立任务显示（vivo OriginOS兼容）
 */
object WebViewActivityPool {
    private const val TAG = "WebViewPool"
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
     * 启动WebView Activity（使用Shortcut方式实现独立任务）
     */
    fun launchWebView(context: Context, fileId: Long, fileName: String, htmlContent: String) {
        DevLogger.i(TAG, "========== 启动WebView开始 ==========")
        DevLogger.i(TAG, "fileId=$fileId, fileName=$fileName, contentLen=${htmlContent.length}")
        
        val slotIndex = allocateSlot(context, fileId)
        val activityClass = activitySlots[slotIndex]
        
        DevLogger.i(TAG, "分配槽位: slotIndex=$slotIndex, activityClass=${activityClass.simpleName}")
        
        // 创建基础Intent
        val intent = Intent(context, activityClass).apply {
            action = Intent.ACTION_VIEW
            putExtra("FILE_ID", fileId)
            putExtra("FILE_NAME", fileName)
            putExtra("HTML_CONTENT", htmlContent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS)
        }
        
        // 使用ShortcutManager启动（关键：让系统识别为独立应用入口）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                val shortcutId = "webapp_${fileId}"
                
                val shortcutIntent = Intent(context, activityClass).apply {
                    action = Intent.ACTION_MAIN
                    putExtra("FILE_ID", fileId)
                    putExtra("FILE_NAME", fileName)
                    putExtra("HTML_CONTENT", htmlContent)
                }
                
                val shortcut = ShortcutInfo.Builder(context, shortcutId)
                    .setShortLabel(fileName)
                    .setLongLabel(fileName)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(shortcutIntent)
                    .build()
                
                // 添加动态快捷方式
                val existingIds = shortcutManager.dynamicShortcuts.map { it.id }
                if (shortcutId in existingIds) {
                    shortcutManager.updateShortcuts(listOf(shortcut))
                } else {
                    // 限制快捷方式数量
                    if (shortcutManager.dynamicShortcuts.size >= shortcutManager.maxShortcutCountPerActivity) {
                        val oldest = shortcutManager.dynamicShortcuts.firstOrNull()
                        oldest?.let { shortcutManager.removeDynamicShortcuts(listOf(it.id)) }
                    }
                    shortcutManager.addDynamicShortcuts(listOf(shortcut))
                }
                
                DevLogger.i(TAG, "Shortcut已创建/更新: $shortcutId")
            } catch (e: Exception) {
                DevLogger.w(TAG, "Shortcut创建失败: ${e.message}")
            }
        }
        
        val flags = intent.flags
        DevLogger.i(TAG, "Intent Flags: 0x${Integer.toHexString(flags)}")
        DevLogger.i(TAG, "  NEW_TASK=${(flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0}")
        DevLogger.i(TAG, "  NEW_DOCUMENT=${(flags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0}")
        DevLogger.i(TAG, "  MULTIPLE_TASK=${(flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0}")
        DevLogger.i(TAG, "  RETAIN_IN_RECENTS=${(flags and Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS) != 0}")
        
        context.startActivity(intent)
        DevLogger.i(TAG, "========== 启动WebView完成 ==========")
    }

    /**
     * 分配槽位算法
     * 1. 如果已存在该文件的槽位 -> 复用并更新为MRU（最近使用）
     * 2. 如果有空闲槽位 -> 分配新槽位
     * 3. 如果已满 -> 淘汰LRU（最久未使用）的槽位并分配给新文件
     */
    @Synchronized
    private fun allocateSlot(context: Context, fileId: Long): Int {
        DevLogger.d(TAG, "allocateSlot: fileId=$fileId")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_POOL_STATE, "[]")
        val jsonArray = JSONArray(jsonStr)
        
        DevLogger.d(TAG, "当前池状态: $jsonStr")
        
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
        
        DevLogger.d(TAG, "已使用槽位: $usedSlots")
        
        // 1. 查找是否已存在
        val existingIndex = state.indexOfFirst { it.first == fileId }
        if (existingIndex != -1) {
            // 命中：移到末尾（MRU）
            val item = state.removeAt(existingIndex)
            state.add(item)
            saveState(prefs, state)
            DevLogger.i(TAG, "命中已有槽位: slotIndex=${item.second}")
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
                DevLogger.i(TAG, "分配空闲槽位: slotIndex=$freeSlot")
                return freeSlot
            }
        }
        
        // 3. 槽位已满，淘汰LRU（第一个）
        if (state.isNotEmpty()) {
            val evicted = state.removeAt(0)
            val reusedSlot = evicted.second
            DevLogger.w(TAG, "槽位已满，淘汰LRU: evictedFileId=${evicted.first}, reusedSlot=$reusedSlot")
            // 复用该槽位
            state.add(fileId to reusedSlot)
            saveState(prefs, state)
            return reusedSlot
        }
        
        // 异常情况（不应发生），默认返回0
        DevLogger.e(TAG, "异常：无法分配槽位，返回默认0")
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
        DevLogger.d(TAG, "保存池状态: $jsonArray")
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
                DevLogger.i(TAG, "释放槽位: fileId=$fileId")
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
    private val TAG = "WebViewActivity"
    
    // 使用 MutableState 管理Intent参数，以便在 onNewIntent 时触发重组
    private var currentFileId by mutableLongStateOf(-1L)
    private var currentFileName by mutableStateOf("")
    private var currentHtmlContent by mutableStateOf("")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val activityName = this.javaClass.simpleName
        DevLogger.i(TAG, "[$activityName] onCreate 开始")
        DevLogger.i(TAG, "[$activityName] taskId=${taskId}, isTaskRoot=$isTaskRoot")
        DevLogger.i(TAG, "[$activityName] processId=${android.os.Process.myPid()}")

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
                        onClose = { 
                            DevLogger.i(TAG, "[$activityName] 用户关闭任务")
                            finishAndRemoveTask() 
                        },
                        onTitleChanged = { updateTaskDescription(it) }
                    )
                }
            }
        }
        
        DevLogger.i(TAG, "[$activityName] onCreate 完成")
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val activityName = this.javaClass.simpleName
        DevLogger.i(TAG, "[$activityName] onNewIntent 收到新Intent")
        setIntent(intent) // 更新Activity内部Intent引用
        processIntent(intent) // 处理新Intent数据，触发UI刷新
    }
    
    override fun onStart() {
        super.onStart()
        DevLogger.d(TAG, "[${this.javaClass.simpleName}] onStart")
    }
    
    override fun onResume() {
        super.onResume()
        DevLogger.d(TAG, "[${this.javaClass.simpleName}] onResume, taskId=$taskId")
    }
    
    override fun onPause() {
        super.onPause()
        DevLogger.d(TAG, "[${this.javaClass.simpleName}] onPause")
    }
    
    override fun onStop() {
        super.onStop()
        DevLogger.d(TAG, "[${this.javaClass.simpleName}] onStop")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        DevLogger.i(TAG, "[${this.javaClass.simpleName}] onDestroy, taskId=$taskId")
    }
    
    private fun processIntent(intent: Intent) {
        val activityName = this.javaClass.simpleName
        val newFileId = intent.getLongExtra("FILE_ID", -1L)
        DevLogger.d(TAG, "[$activityName] processIntent: fileId=$newFileId")
        
        // 只有当ID有效时才更新，避免异常
        if (newFileId != -1L) {
            currentFileId = newFileId
            currentFileName = intent.getStringExtra("FILE_NAME") ?: "HTML应用"
            currentHtmlContent = intent.getStringExtra("HTML_CONTENT") ?: ""
            DevLogger.i(TAG, "[$activityName] 加载文件: id=$currentFileId, name=$currentFileName")
            updateTaskDescription(currentFileName)
        } else {
            DevLogger.e(TAG, "[$activityName] 无效的fileId: $newFileId")
        }
    }
    
    private fun updateTaskDescription(title: String) {
        val activityName = this.javaClass.simpleName
        DevLogger.d(TAG, "[$activityName] 更新任务描述: $title")
        
        // 动态设置任务描述，让系统识别为独立任务（影响任务栏显示）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(ActivityManager.TaskDescription.Builder()
                .setLabel(title)
                .setIcon(R.mipmap.ic_launcher)
                .build())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val icon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(
                title,
                icon,
                resources.getColor(R.color.purple_500, theme)  // 任务栏颜色
            ))
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

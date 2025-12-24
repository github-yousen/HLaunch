package com.HLaunch

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
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
import com.HLaunch.util.DevLogger
import org.json.JSONArray
import org.json.JSONObject

/**
 * WebView Activity Pool Manager (WeChat Mini-Program style)
 * Uses activity-alias + singleInstance for independent task display (vivo OriginOS compatible)
 */
object WebViewActivityPool {
    private const val TAG = "WebViewPool"
    private const val PREFS_NAME = "webview_pool_prefs"
    private const val KEY_POOL_STATE = "pool_state"
    private const val MAX_SLOTS = 5
    
    // Activity-Alias names for launching
    private val aliasNames = arrayOf(
        "com.HLaunch.WebApp1",
        "com.HLaunch.WebApp2",
        "com.HLaunch.WebApp3",
        "com.HLaunch.WebApp4",
        "com.HLaunch.WebApp5"
    )
    
    /**
     * Launch WebView Activity using activity-alias
     */
    fun launchWebView(context: Context, fileId: Long, fileName: String, htmlContent: String) {
        DevLogger.i(TAG, "========== LAUNCH_WEBVIEW_START ==========")
        DevLogger.i(TAG, "params: fileId=$fileId, fileName=$fileName, contentLen=${htmlContent.length}")
        
        val slotIndex = allocateSlot(context, fileId)
        val aliasName = aliasNames[slotIndex]
        
        DevLogger.i(TAG, "slot_allocated: slotIndex=$slotIndex, aliasName=$aliasName")
        
        // Use ComponentName to launch activity-alias
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(context.packageName, aliasName)
            putExtra("FILE_ID", fileId)
            putExtra("FILE_NAME", fileName)
            putExtra("HTML_CONTENT", htmlContent)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        val flags = intent.flags
        DevLogger.i(TAG, "intent_info: flags=0x${Integer.toHexString(flags)}, component=$aliasName")
        DevLogger.i(TAG, "flag_details: NEW_TASK=${(flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0}")
        
        context.startActivity(intent)
        DevLogger.i(TAG, "========== LAUNCH_WEBVIEW_END ==========")
    }

    /**
     * Slot allocation algorithm (LRU)
     */
    @Synchronized
    private fun allocateSlot(context: Context, fileId: Long): Int {
        DevLogger.d(TAG, "allocateSlot_start: fileId=$fileId")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_POOL_STATE, "[]")
        val jsonArray = JSONArray(jsonStr)
        
        DevLogger.d(TAG, "pool_state_current: $jsonStr")
        
        val state = ArrayList<Pair<Long, Int>>()
        val usedSlots = HashSet<Int>()
        
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val fid = obj.getLong("f")
            val sid = obj.getInt("s")
            state.add(fid to sid)
            usedSlots.add(sid)
        }
        
        DevLogger.d(TAG, "used_slots: $usedSlots")
        
        // 1. Check if already exists
        val existingIndex = state.indexOfFirst { it.first == fileId }
        if (existingIndex != -1) {
            val item = state.removeAt(existingIndex)
            state.add(item)
            saveState(prefs, state)
            DevLogger.i(TAG, "slot_hit_existing: slotIndex=${item.second}")
            return item.second
        }
        
        // 2. Find free slot
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
                DevLogger.i(TAG, "slot_allocated_free: slotIndex=$freeSlot")
                return freeSlot
            }
        }
        
        // 3. Evict LRU slot
        if (state.isNotEmpty()) {
            val evicted = state.removeAt(0)
            val reusedSlot = evicted.second
            DevLogger.w(TAG, "slot_evict_lru: evictedFileId=${evicted.first}, reusedSlot=$reusedSlot")
            state.add(fileId to reusedSlot)
            saveState(prefs, state)
            return reusedSlot
        }
        
        DevLogger.e(TAG, "slot_error: cannot allocate, return default 0")
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
        DevLogger.d(TAG, "pool_state_saved: $jsonArray")
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
                changed = true
                DevLogger.i(TAG, "slot_released: fileId=$fileId")
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
 * Base WebView Activity with all WebView logic
 */
abstract class BaseWebViewActivity : ComponentActivity() {
    private val TAG = "WebViewActivity"
    
    private var currentFileId by mutableLongStateOf(-1L)
    private var currentFileName by mutableStateOf("")
    private var currentHtmlContent by mutableStateOf("")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val activityName = this.javaClass.simpleName
        DevLogger.i(TAG, "[$activityName] onCreate_start")
        DevLogger.i(TAG, "[$activityName] task_info: taskId=$taskId, isTaskRoot=$isTaskRoot")
        DevLogger.i(TAG, "[$activityName] process_info: pid=${android.os.Process.myPid()}, uid=${android.os.Process.myUid()}")
        DevLogger.i(TAG, "[$activityName] activity_info: hashCode=${this.hashCode()}, componentName=${componentName}")
        
        // Get ActivityManager to check task details
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val tasks = am.appTasks
        DevLogger.i(TAG, "[$activityName] app_tasks_count: ${tasks.size}")
        tasks.forEachIndexed { index, task ->
            try {
                val info = task.taskInfo
                DevLogger.i(TAG, "[$activityName] task[$index]: id=${info.taskId}, numActivities=${info.numActivities}, baseActivity=${info.baseActivity?.shortClassName}")
            } catch (e: Exception) {
                DevLogger.w(TAG, "[$activityName] task[$index]: error=${e.message}")
            }
        }

        super.onCreate(savedInstanceState)
        
        processIntent(intent)
        
        setContent {
            HLaunchTheme {
                key(currentFileId) {
                    WebViewScreen(
                        fileId = currentFileId,
                        fileName = currentFileName,
                        htmlContent = currentHtmlContent,
                        onClose = { 
                            DevLogger.i(TAG, "[$activityName] user_close_task")
                            finishAndRemoveTask() 
                        },
                        onTitleChanged = { updateTaskDescription(it) }
                    )
                }
            }
        }
        
        DevLogger.i(TAG, "[$activityName] onCreate_end")
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val activityName = this.javaClass.simpleName
        DevLogger.i(TAG, "[$activityName] onNewIntent: received new intent")
        DevLogger.i(TAG, "[$activityName] onNewIntent_extras: FILE_ID=${intent.getLongExtra("FILE_ID", -1)}")
        setIntent(intent)
        processIntent(intent)
    }
    
    override fun onStart() {
        super.onStart()
        val activityName = this.javaClass.simpleName
        DevLogger.d(TAG, "[$activityName] onStart: taskId=$taskId")
    }
    
    override fun onResume() {
        super.onResume()
        val activityName = this.javaClass.simpleName
        DevLogger.d(TAG, "[$activityName] onResume: taskId=$taskId, isTaskRoot=$isTaskRoot")
    }
    
    override fun onPause() {
        super.onPause()
        val activityName = this.javaClass.simpleName
        DevLogger.d(TAG, "[$activityName] onPause: taskId=$taskId")
    }
    
    override fun onStop() {
        super.onStop()
        val activityName = this.javaClass.simpleName
        DevLogger.d(TAG, "[$activityName] onStop: taskId=$taskId")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        val activityName = this.javaClass.simpleName
        DevLogger.i(TAG, "[$activityName] onDestroy: taskId=$taskId, isFinishing=$isFinishing")
    }
    
    private fun processIntent(intent: Intent) {
        val activityName = this.javaClass.simpleName
        val newFileId = intent.getLongExtra("FILE_ID", -1L)
        DevLogger.d(TAG, "[$activityName] processIntent: fileId=$newFileId, action=${intent.action}, component=${intent.component}")
        
        if (newFileId != -1L) {
            currentFileId = newFileId
            currentFileName = intent.getStringExtra("FILE_NAME") ?: "HTML App"
            currentHtmlContent = intent.getStringExtra("HTML_CONTENT") ?: ""
            DevLogger.i(TAG, "[$activityName] file_loaded: id=$currentFileId, name=$currentFileName, contentLen=${currentHtmlContent.length}")
            updateTaskDescription(currentFileName)
        } else {
            DevLogger.e(TAG, "[$activityName] invalid_fileId: $newFileId")
        }
    }
    
    private fun updateTaskDescription(title: String) {
        val activityName = this.javaClass.simpleName
        DevLogger.d(TAG, "[$activityName] updateTaskDescription: title=$title")
        
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
                resources.getColor(R.color.purple_500, theme)
            ))
        }
        DevLogger.d(TAG, "[$activityName] taskDescription_updated: title=$title")
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

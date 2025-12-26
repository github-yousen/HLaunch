package com.HLaunch.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.HLaunch.util.DevLogger

/**
 * WebView池管理器，在内存中保持多个WebView实例，支持快速切换
 */
object WebViewPool {
    private const val MAX_POOL_SIZE = 5
    
    // fileId -> WebView实例
    private val webViewMap = LinkedHashMap<Long, WebViewHolder>()
    
    data class WebViewHolder(
        val webView: WebView,
        val fileId: Long,
        var fileName: String,
        var pageTitle: String = "",
        val createTime: Long = System.currentTimeMillis()
    )
    
    /**
     * 获取或创建WebView实例
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(
        context: Context,
        fileId: Long,
        fileName: String,
        htmlContent: String,
        onTitleChanged: (String) -> Unit
    ): WebView {
        // 已存在则直接返回
        webViewMap[fileId]?.let { holder ->
            // 移到末尾（LRU）
            webViewMap.remove(fileId)
            webViewMap[fileId] = holder
            return holder.webView
        }
        
        // 池满则淘汰最旧的
        if (webViewMap.size >= MAX_POOL_SIZE) {
            val oldest = webViewMap.entries.first()
            oldest.value.webView.destroy()
            webViewMap.remove(oldest.key)
        }
        
        // 创建新WebView
        val webView = createWebView(context, fileId, htmlContent, onTitleChanged)
        webViewMap[fileId] = WebViewHolder(webView, fileId, fileName)
        
        return webView
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(
        context: Context,
        fileId: Long,
        htmlContent: String,
        onTitleChanged: (String) -> Unit
    ): WebView {
        return WebView(context).apply {
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
            
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let { 
                        webViewMap[fileId]?.pageTitle = it
                        onTitleChanged(it) 
                    }
                }
                
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        val fileName = webViewMap[fileId]?.fileName ?: "unknown"
                        val msg = "[${fileName}] ${it.message()} (行${it.lineNumber()})"
                        when (it.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> DevLogger.e("WebViewJS", msg)
                            ConsoleMessage.MessageLevel.WARNING -> DevLogger.w("WebViewJS", msg)
                            else -> DevLogger.d("WebViewJS", msg)
                        }
                    }
                    return true
                }
            }
            
            webViewClient = WebViewClient()
            
            // 使用独立origin加载HTML，实现localStorage隔离
            val uniqueOrigin = "https://app-${fileId}.hlaunch.local/"
            loadDataWithBaseURL(uniqueOrigin, htmlContent, "text/html", "UTF-8", null)
        }
    }
    
    /**
     * 获取已存在的WebView（不创建新的）
     */
    fun get(fileId: Long): WebView? = webViewMap[fileId]?.webView
    
    /**
     * 获取WebView的页面标题
     */
    fun getPageTitle(fileId: Long): String = webViewMap[fileId]?.pageTitle ?: ""
    
    /**
     * 检查WebView是否存在
     */
    fun exists(fileId: Long): Boolean = webViewMap.containsKey(fileId)
    
    /**
     * 关闭并销毁指定WebView
     */
    fun close(fileId: Long) {
        webViewMap[fileId]?.let { holder ->
            holder.webView.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            webViewMap.remove(fileId)
        }
    }
    
    /**
     * 关闭所有WebView
     */
    fun closeAll() {
        webViewMap.values.forEach { holder ->
            holder.webView.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        }
        webViewMap.clear()
    }
    
    /**
     * 重新加载指定WebView的内容
     */
    fun reload(fileId: Long, htmlContent: String) {
        webViewMap[fileId]?.webView?.apply {
            val uniqueOrigin = "https://app-${fileId}.hlaunch.local/"
            loadDataWithBaseURL(uniqueOrigin, htmlContent, "text/html", "UTF-8", null)
        }
    }
    
    /**
     * 获取当前池中的所有fileId
     */
    fun getActiveFileIds(): Set<Long> = webViewMap.keys.toSet()
    
    /**
     * 从父容器移除WebView（不销毁）
     */
    fun detach(fileId: Long) {
        webViewMap[fileId]?.webView?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
    }
}

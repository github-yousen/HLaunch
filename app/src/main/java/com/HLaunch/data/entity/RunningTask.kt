package com.HLaunch.data.entity

// 运行中的任务（内存管理，不持久化）
data class RunningTask(
    val htmlFileId: Long,
    val htmlFileName: String,
    val startTime: Long = System.currentTimeMillis(),
    var isActive: Boolean = true,        // 是否当前激活
    var webViewState: String? = null     // WebView状态快照
)

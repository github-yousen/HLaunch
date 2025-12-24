package com.HLaunch

import android.app.Application
import com.HLaunch.data.database.AppDatabase
import com.HLaunch.data.repository.GitRepoRepository
import com.HLaunch.data.repository.HtmlFileRepository
import com.HLaunch.util.DevLogger

class HLaunchApp : Application() {
    
    val database by lazy { AppDatabase.getInstance(this) }
    val htmlFileRepository by lazy { HtmlFileRepository(database.htmlFileDao()) }
    val gitRepoRepository by lazy { GitRepoRepository(database.gitRepoDao()) }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        DevLogger.init(this)  // 初始化跨进程日志
    }
    
    companion object {
        lateinit var instance: HLaunchApp
            private set
    }
}

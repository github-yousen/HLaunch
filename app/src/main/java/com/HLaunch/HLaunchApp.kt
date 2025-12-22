package com.HLaunch

import android.app.Application
import com.HLaunch.data.database.AppDatabase
import com.HLaunch.data.repository.GitRepoRepository
import com.HLaunch.data.repository.HtmlFileRepository

class HLaunchApp : Application() {
    
    val database by lazy { AppDatabase.getInstance(this) }
    val htmlFileRepository by lazy { HtmlFileRepository(database.htmlFileDao()) }
    val gitRepoRepository by lazy { GitRepoRepository(database.gitRepoDao()) }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: HLaunchApp
            private set
    }
}

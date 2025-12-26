package com.HLaunch.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Git仓库实体
@Entity(tableName = "git_repos")
data class GitRepo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // 仓库名称
    val url: String,                     // 仓库地址
    val branch: String = "main",         // 分支
    val token: String? = null,           // 访问令牌
    val username: String? = null,        // 用户名
    val localPath: String,               // 本地存储路径
    val lastSyncAt: Long? = null,        // 最后同步时间
    val autoSync: Boolean = false,       // 是否自动同步
    val syncEnabled: Boolean = true,     // 是否启用同步（取消跟踪后为false）
    val createdAt: Long = System.currentTimeMillis()
)

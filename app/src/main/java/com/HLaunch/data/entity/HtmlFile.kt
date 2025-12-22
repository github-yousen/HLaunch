package com.HLaunch.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// HTML文件来源类型
enum class FileSource {
    LOCAL,      // 本地创建
    IMPORTED,   // 从存储导入
    GIT         // Git仓库同步
}

// HTML文件实体
@Entity(tableName = "html_files")
data class HtmlFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // 文件名
    val content: String,                 // HTML内容
    val source: FileSource,              // 来源类型
    val gitRepoId: Long? = null,         // 关联的Git仓库ID
    val gitFilePath: String? = null,     // 在Git仓库中的相对路径
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastRunAt: Long? = null,         // 最后运行时间
    val isFavorite: Boolean = false      // 是否收藏
)

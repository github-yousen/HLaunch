package com.HLaunch.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.HLaunch.data.dao.GitRepoDao
import com.HLaunch.data.dao.HtmlFileDao
import com.HLaunch.data.entity.GitRepo
import com.HLaunch.data.entity.HtmlFile

@Database(
    entities = [HtmlFile::class, GitRepo::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun htmlFileDao(): HtmlFileDao
    abstract fun gitRepoDao(): GitRepoDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        // 迁移：v1 -> v2，GitRepo新增syncEnabled字段
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE git_repos ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hlaunch_database"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // 兜底：迁移失败时重建数据库
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

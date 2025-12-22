package com.HLaunch.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.HLaunch.data.dao.GitRepoDao
import com.HLaunch.data.dao.HtmlFileDao
import com.HLaunch.data.entity.GitRepo
import com.HLaunch.data.entity.HtmlFile

@Database(
    entities = [HtmlFile::class, GitRepo::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun htmlFileDao(): HtmlFileDao
    abstract fun gitRepoDao(): GitRepoDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hlaunch_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.HLaunch.data.dao

import androidx.room.*
import com.HLaunch.data.entity.FileSource
import com.HLaunch.data.entity.HtmlFile
import kotlinx.coroutines.flow.Flow

@Dao
interface HtmlFileDao {
    
    @Query("SELECT * FROM html_files ORDER BY updatedAt DESC")
    fun getAllFiles(): Flow<List<HtmlFile>>
    
    @Query("SELECT * FROM html_files WHERE source = :source ORDER BY updatedAt DESC")
    fun getFilesBySource(source: FileSource): Flow<List<HtmlFile>>
    
    @Query("SELECT * FROM html_files WHERE gitRepoId = :repoId ORDER BY name ASC")
    fun getFilesByRepo(repoId: Long): Flow<List<HtmlFile>>
    
    @Query("SELECT * FROM html_files WHERE gitRepoId = :repoId")
    suspend fun getFilesByRepoSync(repoId: Long): List<HtmlFile>
    
    @Query("SELECT * FROM html_files WHERE id = :id")
    suspend fun getFileById(id: Long): HtmlFile?
    
    @Query("SELECT * FROM html_files WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteFiles(): Flow<List<HtmlFile>>
    
    @Query("SELECT * FROM html_files WHERE lastRunAt IS NOT NULL ORDER BY lastRunAt DESC LIMIT 10")
    fun getRecentFiles(): Flow<List<HtmlFile>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: HtmlFile): Long
    
    @Update
    suspend fun update(file: HtmlFile)
    
    @Delete
    suspend fun delete(file: HtmlFile)
    
    @Query("DELETE FROM html_files WHERE gitRepoId = :repoId")
    suspend fun deleteByRepoId(repoId: Long)
    
    @Query("UPDATE html_files SET lastRunAt = :time WHERE id = :id")
    suspend fun updateLastRunTime(id: Long, time: Long)
    
    @Query("UPDATE html_files SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
}

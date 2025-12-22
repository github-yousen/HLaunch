package com.HLaunch.data.repository

import com.HLaunch.data.dao.HtmlFileDao
import com.HLaunch.data.entity.FileSource
import com.HLaunch.data.entity.HtmlFile
import kotlinx.coroutines.flow.Flow

class HtmlFileRepository(private val dao: HtmlFileDao) {
    
    val allFiles: Flow<List<HtmlFile>> = dao.getAllFiles()
    val favoriteFiles: Flow<List<HtmlFile>> = dao.getFavoriteFiles()
    val recentFiles: Flow<List<HtmlFile>> = dao.getRecentFiles()
    
    fun getFilesBySource(source: FileSource): Flow<List<HtmlFile>> = dao.getFilesBySource(source)
    
    fun getFilesByRepo(repoId: Long): Flow<List<HtmlFile>> = dao.getFilesByRepo(repoId)
    
    suspend fun getFileById(id: Long): HtmlFile? = dao.getFileById(id)
    
    suspend fun insert(file: HtmlFile): Long = dao.insert(file)
    
    suspend fun update(file: HtmlFile) = dao.update(file)
    
    suspend fun delete(file: HtmlFile) = dao.delete(file)
    
    suspend fun deleteByRepoId(repoId: Long) = dao.deleteByRepoId(repoId)
    
    suspend fun updateLastRunTime(id: Long) = dao.updateLastRunTime(id, System.currentTimeMillis())
    
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = dao.updateFavorite(id, isFavorite)
}

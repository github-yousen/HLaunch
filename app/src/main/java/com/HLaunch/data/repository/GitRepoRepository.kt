package com.HLaunch.data.repository

import com.HLaunch.data.dao.GitRepoDao
import com.HLaunch.data.entity.GitRepo
import kotlinx.coroutines.flow.Flow

class GitRepoRepository(private val dao: GitRepoDao) {
    
    val allRepos: Flow<List<GitRepo>> = dao.getAllRepos()
    
    suspend fun getRepoById(id: Long): GitRepo? = dao.getRepoById(id)
    
    suspend fun getRepoByUrl(url: String): GitRepo? = dao.getRepoByUrl(url)
    
    suspend fun insert(repo: GitRepo): Long = dao.insert(repo)
    
    suspend fun update(repo: GitRepo) = dao.update(repo)
    
    suspend fun delete(repo: GitRepo) = dao.delete(repo)
    
    suspend fun updateLastSyncTime(id: Long) = dao.updateLastSyncTime(id, System.currentTimeMillis())
    
    suspend fun disableSync(id: Long) = dao.updateSyncEnabled(id, false)
    
    suspend fun enableSync(id: Long) = dao.updateSyncEnabled(id, true)
}

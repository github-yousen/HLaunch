package com.HLaunch.data.dao

import androidx.room.*
import com.HLaunch.data.entity.GitRepo
import kotlinx.coroutines.flow.Flow

@Dao
interface GitRepoDao {
    
    @Query("SELECT * FROM git_repos ORDER BY createdAt DESC")
    fun getAllRepos(): Flow<List<GitRepo>>
    
    @Query("SELECT * FROM git_repos WHERE id = :id")
    suspend fun getRepoById(id: Long): GitRepo?
    
    @Query("SELECT * FROM git_repos WHERE url = :url")
    suspend fun getRepoByUrl(url: String): GitRepo?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repo: GitRepo): Long
    
    @Update
    suspend fun update(repo: GitRepo)
    
    @Delete
    suspend fun delete(repo: GitRepo)
    
    @Query("UPDATE git_repos SET lastSyncAt = :time WHERE id = :id")
    suspend fun updateLastSyncTime(id: Long, time: Long)
    
    @Query("UPDATE git_repos SET syncEnabled = :enabled WHERE id = :id")
    suspend fun updateSyncEnabled(id: Long, enabled: Boolean)
}

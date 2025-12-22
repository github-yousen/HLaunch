package com.HLaunch.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.HLaunch.HLaunchApp
import com.HLaunch.data.entity.FileSource
import com.HLaunch.data.entity.GitRepo
import com.HLaunch.data.entity.HtmlFile
import com.HLaunch.git.GitManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GitRepoViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repoRepository = (application as HLaunchApp).gitRepoRepository
    private val fileRepository = (application as HLaunchApp).htmlFileRepository
    private val gitManager = GitManager(application)
    
    val allRepos: StateFlow<List<GitRepo>> = repoRepository.allRepos
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // 操作状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    // 添加Git仓库
    fun addRepo(name: String, url: String, branch: String, token: String?, username: String?, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val localPath = gitManager.getRepoLocalPath(name)
                val repo = GitRepo(
                    name = name,
                    url = url,
                    branch = branch,
                    token = token,
                    username = username,
                    localPath = localPath
                )
                
                // 克隆仓库
                val success = withContext(Dispatchers.IO) {
                    gitManager.cloneRepo(repo)
                }
                
                if (success) {
                    val repoId = repoRepository.insert(repo)
                    // 扫描并导入HTML文件
                    importHtmlFilesFromRepo(repoId, localPath)
                    _successMessage.value = "仓库添加成功"
                    onComplete(true)
                } else {
                    _errorMessage.value = "克隆仓库失败"
                    onComplete(false)
                }
            } catch (e: Exception) {
                _errorMessage.value = "添加仓库失败: ${e.message}"
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 拉取仓库更新
    fun pullRepo(repo: GitRepo, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val success = withContext(Dispatchers.IO) {
                    gitManager.pullRepo(repo)
                }
                
                if (success) {
                    repoRepository.updateLastSyncTime(repo.id)
                    // 重新扫描HTML文件
                    fileRepository.deleteByRepoId(repo.id)
                    importHtmlFilesFromRepo(repo.id, repo.localPath)
                    _successMessage.value = "同步成功"
                    onComplete(true)
                } else {
                    _errorMessage.value = "拉取更新失败"
                    onComplete(false)
                }
            } catch (e: Exception) {
                _errorMessage.value = "同步失败: ${e.message}"
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 推送文件到仓库
    fun pushFile(file: HtmlFile, repo: GitRepo, commitMessage: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val success = withContext(Dispatchers.IO) {
                    // 写入文件到仓库目录
                    val filePath = file.gitFilePath ?: "${file.name}.html"
                    val targetFile = File(repo.localPath, filePath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(file.content)
                    
                    // 提交并推送
                    gitManager.commitAndPush(repo, filePath, commitMessage)
                }
                
                if (success) {
                    // 更新文件关联
                    fileRepository.update(file.copy(
                        source = FileSource.GIT,
                        gitRepoId = repo.id,
                        gitFilePath = file.gitFilePath ?: "${file.name}.html"
                    ))
                    _successMessage.value = "推送成功"
                    onComplete(true)
                } else {
                    _errorMessage.value = "推送失败"
                    onComplete(false)
                }
            } catch (e: Exception) {
                _errorMessage.value = "推送失败: ${e.message}"
                onComplete(false)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // 删除仓库
    fun deleteRepo(repo: GitRepo) {
        viewModelScope.launch {
            try {
                // 删除关联的HTML文件记录
                fileRepository.deleteByRepoId(repo.id)
                // 删除本地文件
                withContext(Dispatchers.IO) {
                    File(repo.localPath).deleteRecursively()
                }
                // 删除仓库记录
                repoRepository.delete(repo)
                _successMessage.value = "仓库已删除"
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
            }
        }
    }
    
    // 从仓库目录导入HTML文件
    private suspend fun importHtmlFilesFromRepo(repoId: Long, localPath: String) {
        withContext(Dispatchers.IO) {
            val repoDir = File(localPath)
            if (!repoDir.exists()) return@withContext
            
            repoDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() == "html" }
                .forEach { htmlFile ->
                    val relativePath = htmlFile.relativeTo(repoDir).path
                    val content = htmlFile.readText()
                    val file = HtmlFile(
                        name = htmlFile.nameWithoutExtension,
                        content = content,
                        source = FileSource.GIT,
                        gitRepoId = repoId,
                        gitFilePath = relativePath
                    )
                    fileRepository.insert(file)
                }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    fun clearSuccess() {
        _successMessage.value = null
    }
}

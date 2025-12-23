package com.HLaunch.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.HLaunch.HLaunchApp
import com.HLaunch.data.entity.FileSource
import com.HLaunch.data.entity.HtmlFile
import com.HLaunch.data.entity.RunningTask
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HtmlFileViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = (application as HLaunchApp).htmlFileRepository
    
    val allFiles: StateFlow<List<HtmlFile>> = repository.allFiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val favoriteFiles: StateFlow<List<HtmlFile>> = repository.favoriteFiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val recentFiles: StateFlow<List<HtmlFile>> = repository.recentFiles
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // 运行中的任务列表（类似微信小程序多任务）
    private val _runningTasks = MutableStateFlow<List<RunningTask>>(emptyList())
    val runningTasks: StateFlow<List<RunningTask>> = _runningTasks.asStateFlow()
    
    init {
        // 初始化时从持久化的池状态恢复运行任务列表
        viewModelScope.launch {
            syncRunningTasksFromPool()
        }
    }

    /**
     * 从 WebViewActivityPool 的持久化状态同步任务列表
     */
    fun syncRunningTasksFromPool() {
        val context = getApplication<Application>()
        val runningIds = com.HLaunch.WebViewActivityPool.getRunningFileIds(context)
        
        viewModelScope.launch {
            val tasks = mutableListOf<RunningTask>()
            runningIds.forEach { id ->
                val file = repository.getFileById(id)
                if (file != null) {
                    tasks.add(RunningTask(htmlFileId = file.id, htmlFileName = file.name))
                }
            }
            _runningTasks.value = tasks
        }
    }
    
    // 当前激活的任务
    private val _activeTask = MutableStateFlow<RunningTask?>(null)
    val activeTask: StateFlow<RunningTask?> = _activeTask.asStateFlow()
    
    // 当前选中的文件（用于编辑/查看）
    private val _selectedFile = MutableStateFlow<HtmlFile?>(null)
    val selectedFile: StateFlow<HtmlFile?> = _selectedFile.asStateFlow()
    
    fun getLocalFiles(): Flow<List<HtmlFile>> = repository.getFilesBySource(FileSource.LOCAL)
    fun getImportedFiles(): Flow<List<HtmlFile>> = repository.getFilesBySource(FileSource.IMPORTED)
    fun getGitFiles(): Flow<List<HtmlFile>> = repository.getFilesBySource(FileSource.GIT)
    fun getFilesByRepo(repoId: Long): Flow<List<HtmlFile>> = repository.getFilesByRepo(repoId)
    
    // 创建新HTML文件
    fun createFile(name: String, content: String, source: FileSource = FileSource.LOCAL, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val file = HtmlFile(name = name, content = content, source = source)
            val id = repository.insert(file)
            onComplete(id)
        }
    }
    
    // 更新文件
    fun updateFile(file: HtmlFile) {
        viewModelScope.launch {
            repository.update(file.copy(updatedAt = System.currentTimeMillis()))
        }
    }
    
    // 删除文件
    fun deleteFile(file: HtmlFile) {
        viewModelScope.launch {
            repository.delete(file)
            // 同时关闭运行中的任务
            closeTask(file.id)
        }
    }
    
    // 切换收藏状态
    fun toggleFavorite(file: HtmlFile) {
        viewModelScope.launch {
            repository.toggleFavorite(file.id, !file.isFavorite)
        }
    }
    
    // 选中文件
    fun selectFile(file: HtmlFile?) {
        _selectedFile.value = file
    }
    
    // 运行HTML文件（添加到多任务）
    fun runFile(file: HtmlFile) {
        viewModelScope.launch {
            repository.updateLastRunTime(file.id)
            
            // 检查是否已在运行
            val existingTask = _runningTasks.value.find { it.htmlFileId == file.id }
            if (existingTask != null) {
                // 已存在，激活它
                activateTask(file.id)
            } else {
                // 新建任务
                val task = RunningTask(htmlFileId = file.id, htmlFileName = file.name)
                _runningTasks.value = _runningTasks.value + task
                activateTask(file.id)
            }
        }
    }
    
    // 激活某个任务
    fun activateTask(fileId: Long) {
        _runningTasks.value = _runningTasks.value.map { 
            it.copy(isActive = it.htmlFileId == fileId)
        }
        _activeTask.value = _runningTasks.value.find { it.htmlFileId == fileId }
    }
    
    // 关闭任务
    fun closeTask(fileId: Long) {
        _runningTasks.value = _runningTasks.value.filter { it.htmlFileId != fileId }
        if (_activeTask.value?.htmlFileId == fileId) {
            _activeTask.value = _runningTasks.value.firstOrNull()
            _activeTask.value?.let { activateTask(it.htmlFileId) }
        }
    }
    
    // 关闭所有任务
    fun closeAllTasks() {
        _runningTasks.value = emptyList()
        _activeTask.value = null
    }
    
    // 获取文件详情
    suspend fun getFileById(id: Long): HtmlFile? = repository.getFileById(id)
}

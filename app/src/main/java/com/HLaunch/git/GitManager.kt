package com.HLaunch.git

import android.content.Context
import com.HLaunch.data.entity.GitRepo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitManager(private val context: Context) {
    
    private val reposDir: File
        get() = File(context.filesDir, "git_repos").also { it.mkdirs() }
    
    // 获取仓库本地存储路径
    fun getRepoLocalPath(repoName: String): String {
        return File(reposDir, repoName.replace(Regex("[^a-zA-Z0-9_-]"), "_")).absolutePath
    }
    
    // 克隆仓库
    fun cloneRepo(repo: GitRepo): Boolean {
        return try {
            val localDir = File(repo.localPath)
            if (localDir.exists()) {
                localDir.deleteRecursively()
            }
            localDir.mkdirs()
            
            val cloneCommand = Git.cloneRepository()
                .setURI(repo.url)
                .setDirectory(localDir)
                .setBranch(repo.branch)
            
            if (!repo.token.isNullOrEmpty()) {
                val username = repo.username ?: "oauth2"
                cloneCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(username, repo.token)
                )
            }
            
            cloneCommand.call().close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // 拉取更新
    fun pullRepo(repo: GitRepo): Boolean {
        return try {
            val localDir = File(repo.localPath)
            if (!localDir.exists()) return false
            
            val git = Git.open(localDir)
            val pullCommand = git.pull()
            
            if (!repo.token.isNullOrEmpty()) {
                val username = repo.username ?: "oauth2"
                pullCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(username, repo.token)
                )
            }
            
            pullCommand.call()
            git.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // 提交并推送
    fun commitAndPush(repo: GitRepo, filePath: String, message: String): Boolean {
        return try {
            val localDir = File(repo.localPath)
            if (!localDir.exists()) return false
            
            val git = Git.open(localDir)
            
            // 添加文件
            git.add().addFilepattern(filePath).call()
            
            // 提交
            git.commit().setMessage(message).call()
            
            // 推送
            val pushCommand = git.push()
            if (!repo.token.isNullOrEmpty()) {
                val username = repo.username ?: "oauth2"
                pushCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(username, repo.token)
                )
            }
            pushCommand.call()
            
            git.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    // 检查仓库是否有更新
    fun checkForUpdates(repo: GitRepo): Boolean {
        return try {
            val localDir = File(repo.localPath)
            if (!localDir.exists()) return false
            
            val git = Git.open(localDir)
            val fetchCommand = git.fetch()
            
            if (!repo.token.isNullOrEmpty()) {
                val username = repo.username ?: "oauth2"
                fetchCommand.setCredentialsProvider(
                    UsernamePasswordCredentialsProvider(username, repo.token)
                )
            }
            
            val result = fetchCommand.call()
            git.close()
            
            result.trackingRefUpdates.isNotEmpty()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

package com.HLaunch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.HLaunch.ui.screen.*
import com.HLaunch.viewmodel.GitRepoViewModel
import com.HLaunch.viewmodel.HtmlFileViewModel

// 导航路由
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object FileList : Screen("file_list")
    object CreateFile : Screen("create_file")
    object EditFile : Screen("edit_file/{fileId}") {
        fun createRoute(fileId: Long) = "edit_file/$fileId"
    }
    object RunFile : Screen("run_file/{fileId}") {
        fun createRoute(fileId: Long) = "run_file/$fileId"
    }
    object RunningTasks : Screen("running_tasks")
    object GitRepos : Screen("git_repos")
    object AddRepo : Screen("add_repo")
    object RepoDetail : Screen("repo_detail/{repoId}") {
        fun createRoute(repoId: Long) = "repo_detail/$repoId"
    }
    object Settings : Screen("settings")
    object AppUpdate : Screen("app_update")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    fileViewModel: HtmlFileViewModel,
    gitViewModel: GitRepoViewModel
) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        
        composable(Screen.Home.route) {
            HomeScreen(
                navController = navController,
                fileViewModel = fileViewModel
            )
        }
        
        composable(Screen.FileList.route) {
            FileListScreen(
                navController = navController,
                viewModel = fileViewModel
            )
        }
        
        composable(Screen.CreateFile.route) {
            CreateFileScreen(
                navController = navController,
                viewModel = fileViewModel
            )
        }
        
        composable(
            route = Screen.EditFile.route,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getLong("fileId") ?: 0L
            EditFileScreen(
                navController = navController,
                viewModel = fileViewModel,
                fileId = fileId
            )
        }
        
        composable(
            route = Screen.RunFile.route,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getLong("fileId") ?: 0L
            RunFileScreen(
                navController = navController,
                viewModel = fileViewModel,
                fileId = fileId
            )
        }
        
        composable(Screen.RunningTasks.route) {
            RunningTasksScreen(
                navController = navController,
                viewModel = fileViewModel
            )
        }
        
        composable(Screen.GitRepos.route) {
            GitReposScreen(
                navController = navController,
                viewModel = gitViewModel
            )
        }
        
        composable(Screen.AddRepo.route) {
            AddRepoScreen(
                navController = navController,
                viewModel = gitViewModel
            )
        }
        
        composable(
            route = Screen.RepoDetail.route,
            arguments = listOf(navArgument("repoId") { type = NavType.LongType })
        ) { backStackEntry ->
            val repoId = backStackEntry.arguments?.getLong("repoId") ?: 0L
            RepoDetailScreen(
                navController = navController,
                gitViewModel = gitViewModel,
                fileViewModel = fileViewModel,
                repoId = repoId
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController
            )
        }
        
        composable(Screen.AppUpdate.route) {
            AppUpdateScreen(
                navController = navController
            )
        }
    }
}

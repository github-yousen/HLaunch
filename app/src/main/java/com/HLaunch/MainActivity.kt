package com.HLaunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.HLaunch.ui.navigation.NavGraph
import com.HLaunch.ui.theme.HLaunchTheme
import com.HLaunch.viewmodel.GitRepoViewModel
import com.HLaunch.viewmodel.HtmlFileViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HLaunchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val fileViewModel: HtmlFileViewModel = viewModel()
                    val gitViewModel: GitRepoViewModel = viewModel()
                    
                    NavGraph(
                        navController = navController,
                        fileViewModel = fileViewModel,
                        gitViewModel = gitViewModel
                    )
                }
            }
        }
    }
}
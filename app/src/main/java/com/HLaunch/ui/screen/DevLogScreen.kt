package com.HLaunch.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.HLaunch.util.DevLogger
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevLogScreen(navController: NavController) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(DevLogger.getLogs(context)) }
    val listState = rememberLazyListState()
    
    // 自动刷新日志，每2秒刷新一次
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val newLogs = DevLogger.getLogs(context)
            if (newLogs.size != logs.size) {
                logs = newLogs
                if (logs.isNotEmpty()) {
                    listState.animateScrollToItem(logs.size - 1)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者日志") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 复制
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("logs", DevLogger.getLogsAsText(context)))
                        Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, "复制")
                    }
                    // 清空
                    IconButton(onClick = {
                        DevLogger.clearLogs(context)
                        logs = DevLogger.getLogs(context)
                    }) {
                        Icon(Icons.Default.Delete, "清空")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 统计信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("总日志: ${logs.size}", style = MaterialTheme.typography.labelMedium)
                }
            }
            
            // 日志列表
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无日志", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                ) {
                    items(logs) { entry ->
                        LogItem(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItem(entry: DevLogger.LogEntry) {
    val levelColor = when (entry.level) {
        "D" -> Color(0xFF9E9E9E)
        "I" -> Color(0xFF4CAF50)
        "W" -> Color(0xFFFF9800)
        "E" -> Color(0xFFF44336)
        else -> Color.White
    }
    
    val processColor = when {
        entry.processName.contains("webapp") -> Color(0xFF64B5F6)
        else -> Color(0xFFCE93D8)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        // 时间
        Text(
            text = entry.time,
            color = Color(0xFF808080),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 进程
        Text(
            text = "[${entry.processName}]",
            color = processColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 级别/标签
        Text(
            text = "${entry.level}/${entry.tag}:",
            color = levelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 消息
        Text(
            text = entry.message,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

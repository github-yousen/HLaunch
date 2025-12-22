package com.HLaunch.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.HLaunch.data.entity.FileSource
import com.HLaunch.ui.navigation.Screen
import com.HLaunch.viewmodel.HtmlFileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFileScreen(
    navController: NavController,
    viewModel: HtmlFileViewModel
) {
    var fileName by remember { mutableStateOf("") }
    var htmlContent by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    var isFileNameManuallyEdited by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // 从HTML内容中提取title
    fun extractTitleFromHtml(html: String): String? {
        val regex = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
    
    // 当HTML内容变化时，自动提取title填充文件名（仅当用户未手动编辑文件名时）
    LaunchedEffect(htmlContent) {
        if (!isFileNameManuallyEdited && htmlContent.isNotBlank()) {
            extractTitleFromHtml(htmlContent)?.let { title ->
                fileName = title
            }
        }
    }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    htmlContent = content
                    // 优先从HTML title提取，否则用文件名
                    val title = extractTitleFromHtml(content)
                    if (title != null) {
                        fileName = title
                    } else {
                        val name = it.lastPathSegment?.substringAfterLast("/")?.substringBeforeLast(".") ?: "imported"
                        fileName = name
                    }
                    isFileNameManuallyEdited = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建HTML文件") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 导入文件
                    IconButton(onClick = { filePickerLauncher.launch(arrayOf("text/html", "*/*")) }) {
                        Icon(Icons.Default.FileOpen, "导入文件")
                    }
                    // 预览
                    IconButton(onClick = { showPreview = !showPreview }) {
                        Icon(
                            if (showPreview) Icons.Default.Code else Icons.Default.Preview,
                            "预览"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 文件名输入
            OutlinedTextField(
                value = fileName,
                onValueChange = { 
                    fileName = it
                    isFileNameManuallyEdited = true
                },
                label = { Text("文件名") },
                placeholder = { Text("自动从HTML title提取") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Description, null) }
            )
            
            // HTML内容输入
            OutlinedTextField(
                value = htmlContent,
                onValueChange = { htmlContent = it },
                label = { Text("HTML代码") },
                placeholder = { Text("粘贴或输入HTML代码...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                minLines = 10
            )
            
            // 快速模板
            Text(
                text = "快速模板",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { 
                        htmlContent = HTML_TEMPLATE_BASIC
                        isFileNameManuallyEdited = false
                    },
                    label = { Text("基础模板") }
                )
                AssistChip(
                    onClick = { 
                        htmlContent = HTML_TEMPLATE_RESPONSIVE
                        isFileNameManuallyEdited = false
                    },
                    label = { Text("响应式") }
                )
                AssistChip(
                    onClick = { 
                        htmlContent = HTML_TEMPLATE_CANVAS
                        isFileNameManuallyEdited = false
                    },
                    label = { Text("Canvas") }
                )
            }
            
            // 保存按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                
                Button(
                    onClick = {
                        if (fileName.isNotBlank() && htmlContent.isNotBlank()) {
                            viewModel.createFile(
                                name = fileName.trim(),
                                content = htmlContent,
                                source = FileSource.LOCAL
                            ) {
                                // 保存成功后返回首页
                                navController.popBackStack()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = fileName.isNotBlank() && htmlContent.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存")
                }
            }
        }
    }
}

// HTML模板
private val HTML_TEMPLATE_BASIC = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>我的页面</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            margin: 0;
            padding: 20px;
            background: #f5f5f5;
        }
        .container {
            max-width: 600px;
            margin: 0 auto;
            background: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
        }
        h1 { color: #333; }
    </style>
</head>
<body>
    <div class="container">
        <h1>Hello World!</h1>
        <p>这是一个基础HTML模板。</p>
    </div>
</body>
</html>
""".trimIndent()

private val HTML_TEMPLATE_RESPONSIVE = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>响应式页面</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        .card {
            background: white;
            border-radius: 16px;
            padding: 24px;
            margin-bottom: 16px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.15);
        }
        h1 { color: #333; margin-bottom: 16px; }
        p { color: #666; }
        .btn {
            display: inline-block;
            background: #667eea;
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            text-decoration: none;
            margin-top: 16px;
        }
    </style>
</head>
<body>
    <div class="card">
        <h1>响应式设计</h1>
        <p>这个模板会自动适应不同屏幕尺寸。</p>
        <a href="#" class="btn">点击按钮</a>
    </div>
</body>
</html>
""".trimIndent()

private val HTML_TEMPLATE_CANVAS = """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Canvas绘图</title>
    <style>
        body {
            margin: 0;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            background: #1a1a2e;
        }
        canvas {
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.3);
        }
    </style>
</head>
<body>
    <canvas id="canvas" width="300" height="300"></canvas>
    <script>
        const canvas = document.getElementById('canvas');
        const ctx = canvas.getContext('2d');
        
        // 绘制渐变背景
        const gradient = ctx.createLinearGradient(0, 0, 300, 300);
        gradient.addColorStop(0, '#667eea');
        gradient.addColorStop(1, '#764ba2');
        ctx.fillStyle = gradient;
        ctx.fillRect(0, 0, 300, 300);
        
        // 绘制圆形
        ctx.beginPath();
        ctx.arc(150, 150, 80, 0, Math.PI * 2);
        ctx.fillStyle = 'white';
        ctx.fill();
        
        // 绘制文字
        ctx.fillStyle = '#667eea';
        ctx.font = 'bold 24px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('Canvas', 150, 158);
    </script>
</body>
</html>
""".trimIndent()

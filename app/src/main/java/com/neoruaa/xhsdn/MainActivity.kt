package com.neoruaa.xhsdn

import android.Manifest
import android.R
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import com.neoruaa.xhsdn.utils.UrlUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neoruaa.xhsdn.BuildConfig
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import android.util.Size
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import java.io.File
import android.util.LruCache

// 缩略图内存缓存（最多缓存 50 张缩略图）
private val thumbnailCache = object : LruCache<String, ImageBitmap>(50) {}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()


    private val _autoDownloadIntentUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _autoDownloadIntentUrl.value = intent.getStringExtra("auto_download_url")
        intent.removeExtra("auto_download_url")

        if (Build.VERSION.SDK_INT >= 33) { // Android 13
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(permission), 200)
            }
        }
        
        enableEdgeToEdge()
        com.neoruaa.xhsdn.data.TaskManager.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val controller = ThemeController(ColorSchemeMode.System)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val topBarState = rememberTopAppBarState()
            val scrollBehavior = MiuixScrollBehavior(state = topBarState)
            
            // 处理自动下载
            val autoUrl by _autoDownloadIntentUrl
            LaunchedEffect(autoUrl) {
                autoUrl?.let { url ->
                     if (url.isNotEmpty()) {
                        viewModel.updateUrl(url)
                        ensureStoragePermission { 
                            viewModel.startDownload { showToast(it) } 
                        }
                     }
                     _autoDownloadIntentUrl.value = null // 消费完毕
                }
            }
            
            // 剪贴板检测相关状态
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember { context.getSharedPreferences("XHSDownloaderPrefs", MODE_PRIVATE) }
            var detectedXhsLink by remember { mutableStateOf<String?>(null) }
            
            // 监听生命周期 ON_RESUME 和 ON_PAUSE 进行剪贴板监听器管理
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            // 提取核心检测逻辑为可复用函数
            fun checkClipboard() {
                // 1. Refresh Preferences
                val currentAutoRead = prefs.getBoolean("auto_read_clipboard", false)
                val currentShowBubble = prefs.getBoolean("show_clipboard_bubble", true)
                
                android.util.Log.d("XHS_Debug", "checkClipboard: AutoRead=$currentAutoRead, ShowBubble=$currentShowBubble")

                // 2. Access Clipboard
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                if (clipboard.hasPrimaryClip()) {
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val clipText = clipData.getItemAt(0).text?.toString() ?: ""
                        android.util.Log.d("XHS_Debug", "ClipText: $clipText")
                        
                        val url = UrlUtils.extractFirstUrl(clipText)
                        android.util.Log.d("XHS_Debug", "Extracted URL: $url")
                        
                        if (url != null && UrlUtils.isXhsLink(url)) {
                            // 3. Logic Branching
                            
                            if (currentAutoRead) {
                                // A. Auto Download Priority
                                viewModel.updateUrl(clipText)
                                
                                // ... (Auto download logic)
                                android.util.Log.d("XHS_Debug", "Triggering Auto Download")
                                
                                // Trigger Download
                                viewModel.startDownload { showToast(it) }
                                
                                // Show Notification with Full Content
                                com.neoruaa.xhsdn.utils.NotificationHelper.showDownloadNotification(
                                    context, 
                                    System.currentTimeMillis().toInt(), 
                                    "开始下载",
                                    clipText, // Full content
                                    false
                                )
                                
                                // Clear Clipboard
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                                
                                // Ensure bubble is dismissed
                                detectedXhsLink = null
                                
                            } else if (currentShowBubble) {
                                // B. Show Bubble
                                android.util.Log.d("XHS_Debug", "Showing Bubble")
                                detectedXhsLink = clipText 
                            } else {
                                android.util.Log.d("XHS_Debug", "Bubble disabled in settings")
                            }
                        } else {
                            // Link invalid or not detected -> Disappear
                            android.util.Log.d("XHS_Debug", "Not XHS link or null -> Hide Bubble")
                            detectedXhsLink = null
                        }
                    } else {
                        // Clipboard empty -> Disappear
                        android.util.Log.d("XHS_Debug", "Clipboard empty/null data -> Hide Bubble")
                        detectedXhsLink = null
                    }
                } else {
                    // No clipboard -> Disappear
                    android.util.Log.d("XHS_Debug", "No Primary Clip -> Hide Bubble")
                    detectedXhsLink = null
                }
            }
            
            val scope = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycleScope
            
            val clipboardListener = remember {
                android.content.ClipboardManager.OnPrimaryClipChangedListener {
                     // 延迟检测，解决 listener 触发时 ClipData 可能尚未准备好的问题
                     scope.launch {
                         kotlinx.coroutines.delay(300) // 300ms 延迟
                         checkClipboard()
                     }
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        // 注册监听器
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.addPrimaryClipChangedListener(clipboardListener)
                        // 延迟检测：Android 10+ 需要等待窗口焦点才能访问剪贴板
                        scope.launch {
                            kotlinx.coroutines.delay(500)
                            checkClipboard()
                        }
                    } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                        // 移除监听器
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.removePrimaryClipChangedListener(clipboardListener)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.removePrimaryClipChangedListener(clipboardListener)
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MiuixTheme(controller = controller) {
                MainScreen(
                    uiState = uiState,
                    scrollBehavior = scrollBehavior,
                    onDownload = {
                        ensureStoragePermission {
                            // 先读取剪贴板
                            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            // 提取有效链接
                            val url = UrlUtils.extractFirstUrl(clipText)
                            if (UrlUtils.isXhsLink(url)) {
                                viewModel.updateUrl(clipText)
                                viewModel.startDownload { showToast(it) }
                                // 手动下载也清空剪贴板
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                            } else {
                                showToast("剪贴板中未检测到小红书链接")
                            }
                        }
                    },
                    onCopyText = { 
                        // 先读取剪贴板
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (clipText.isNotEmpty()) {
                            viewModel.updateUrl(clipText)
                        }
                        ensureStoragePermission { viewModel.copyDescription({ showToast("已复制文案") }, { showToast(it) }) } 
                    },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onWebCrawlFromClipboard = {
                        // 先读取剪贴板
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (clipText.isNotEmpty()) {
                            viewModel.updateUrl(clipText)
                            detectedXhsLink = null
                        }
                        launchWebView(clipText)
                    },
                    onMediaClick = { openFile(it) },
                    onCopyUrl = { url ->
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("xhs_url", url))
                        showToast("已复制链接")
                    },
                    onBrowseUrl = { url ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.Main) {
                                try {
                                    // 使用通用URL提取
                                    val cleanUrl = UrlUtils.extractFirstUrl(url)
                                    if (cleanUrl != null) {
                                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(cleanUrl))
                                        startActivity(intent)
                                    } else {
                                        showToast("未找到有效链接")
                                    }
                                } catch (e: Exception) {
                                    showToast("无法打开浏览器: ${e.message}")
                                }
                            }
                        }
                    },
                    onRetryTask = { task ->
                        ensureStoragePermission {
                            viewModel.retryTask(task) { showToast(it) }
                        }
                    },
                    onDeleteTask = { task ->
                        com.neoruaa.xhsdn.data.TaskManager.deleteTask(task.id)
                    },
                    onContinueTask = { task -> 
                        viewModel.continueTask(task)
                    },
                    onWebCrawlTask = { task ->
                        viewModel.updateUrl(task.noteUrl)
                        launchWebView(task.noteUrl, task.id)
                    },
                    onStopTask = { task ->
                         if (viewModel.currentTaskId == task.id) {
                             viewModel.cancelCurrentDownload()
                         }
                         com.neoruaa.xhsdn.data.BackgroundDownloadManager.stopTask(task.id)
                    },
                    detectedXhsLink = detectedXhsLink,
                    onDismissPrompt = { detectedXhsLink = null }
                )
            }
        }
    }

    private fun launchWebView(input: String, taskId: Long? = null) {
        val cleanUrl = UrlUtils.extractFirstUrl(input)
        if (cleanUrl == null) {
            showToast("未找到有效链接，请重新输入")
            return
        }
        viewModel.resetWebCrawlFlag()
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("url", cleanUrl)
        if (taskId != null && taskId > 0) {
            intent.putExtra("task_id", taskId)
        }
        startActivityForResult(intent, WEBVIEW_REQUEST_CODE)
    }



    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("auto_download_url")?.let {
            _autoDownloadIntentUrl.value = it
            intent.removeExtra("auto_download_url")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // 供旧 Java 下载逻辑回调调用，提示用户切换到网页模式
    fun showWebCrawlOption() {
        runOnUiThread {
            viewModel.notifyWebCrawlSuggestion()
        }
    }

    private fun ensureStoragePermission(onReady: () -> Unit) {
        if (hasStoragePermission()) {
            onReady()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
            showToast("请授予所有文件访问权限后重试")
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showToast("已获得存储权限，可继续下载")
            } else {
                showToast("缺少存储权限，无法保存文件")
            }
        }
    }

    private fun openFile(item: MediaItem) {
        val file = File(item.path)
        if (!file.exists()) {
            showToast("文件不存在：${item.path}")
            return
        }
        val mimeType = when (item.type) {
            MediaType.VIDEO -> "video/*"
            MediaType.IMAGE -> "image/*"
            MediaType.OTHER -> "*/*"
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        kotlin.runCatching { startActivity(intent) }.onFailure {
            showToast("无法打开文件：${it.message}")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WEBVIEW_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val urls = data.getStringArrayListExtra("image_urls") ?: emptyList()
            val content = data.getStringExtra("content_text")
            val taskId = data.getLongExtra("task_id", -1L).takeIf { it > 0 }
            if (urls.isNotEmpty()) {
                showToast("开始爬取，请等待任务完成")
                viewModel.onWebCrawlResult(urls, content, taskId)
            } else {
                showToast("未发现可下载的资源")
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 3001
        const val WEBVIEW_REQUEST_CODE = 3002
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(
    uiState: MainUiState,
    onDownload: () -> Unit,
    onCopyText: () -> Unit,
    onOpenSettings: () -> Unit,
    onWebCrawlFromClipboard: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onCopyUrl: (String) -> Unit,
    onBrowseUrl: (String) -> Unit,
    onRetryTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onStopTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onDeleteTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onContinueTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onWebCrawlTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    scrollBehavior: ScrollBehavior,
    detectedXhsLink: String?,
    onDismissPrompt: () -> Unit
) {
    val statusListState = rememberLazyListState()
    var menuExpanded by remember { mutableStateOf(false) }
    var overflowButtonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val density = LocalDensity.current
    val scrimInteraction = remember { MutableInteractionSource() }
    val menuWidth = 180.dp
    val menuWidthPx = with(density) { menuWidth.roundToPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
            topBar = {
                val title = "小红书下载器"
                TopAppBar(
                    title = title,
                    largeTitle = title,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(48.dp)
                                .onGloballyPositioned { overflowButtonBounds = it.boundsInWindow() }
                                .clickable { menuExpanded = !menuExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "···", fontSize = 20.sp)
                        }

                        Box(
                            modifier = Modifier
                                .padding(end = 26.dp)
                                .size(48.dp)
                                .clickable { onOpenSettings() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = "设置",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            HistoryPage(
                uiState = uiState,
                statusListState = statusListState,
                onDownload = onDownload,
                onMediaClick = onMediaClick,
                onCopyUrl = onCopyUrl,
                onBrowseUrl = onBrowseUrl,
                onRetryTask = onRetryTask,
                onContinueTask = onContinueTask,
                onWebCrawlTask = onWebCrawlTask,
                onStopTask = onStopTask,
                onDeleteTask = onDeleteTask,
                detectedXhsLink = detectedXhsLink,
                onDismissPrompt = onDismissPrompt,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }

        if (menuExpanded) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null
                    ) { menuExpanded = false }
            )

            val bounds = overflowButtonBounds
            if (bounds != null) {
                val gapPx = with(density) { 8.dp.roundToPx() }
                val menuLeftPx = (bounds.right - menuWidthPx).toInt().coerceAtLeast(with(density) { 8.dp.roundToPx() })
                val menuTopPx = bounds.bottom.toInt() + gapPx
                val pointerCenterPxRaw = bounds.center.x - menuLeftPx
                val pointerCenterPx = pointerCenterPxRaw
                    .coerceAtLeast(with(density) { 16.dp.toPx() })
                    .coerceAtMost(menuWidthPx.toFloat() - with(density) { 16.dp.toPx() })

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(menuLeftPx, menuTopPx),
                    onDismissRequest = { menuExpanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    val triangleWidth = 22.dp
                    val triangleHeight = 12.dp
                    val triangleOffsetX = with(density) { pointerCenterPx.toDp() - triangleWidth / 2 }
                    val bubbleColor = MiuixTheme.colorScheme.surface

                    Column(modifier = Modifier.width(menuWidth)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .offset(x = triangleOffsetX)
                                    .size(triangleWidth, triangleHeight)
                            ) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width / 2f, 0f)
                                    lineTo(0f, size.height)
                                    lineTo(size.width, size.height)
                                    close()
                                }
                                drawPath(path = path, color = bubbleColor)
                            }
                        }

                        Card(
                            cornerRadius = 18.dp,
                            colors = CardDefaults.defaultColors(color = bubbleColor)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp)) {
                                TextButton(
                                    text = "复制文案",
                                    onClick = {
                                        menuExpanded = false
                                        onCopyText()
                                    },
                                    enabled = !uiState.isDownloading,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    text = "网页爬取",
                                    onClick = {
                                        menuExpanded = false
                                        onWebCrawlFromClipboard()
                                    },
                                    enabled = !uiState.isDownloading,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(
    uiState: MainUiState,
    statusListState: androidx.compose.foundation.lazy.LazyListState,
    onDownload: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onCopyUrl: (String) -> Unit,
    onBrowseUrl: (String) -> Unit,

    onRetryTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onContinueTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onWebCrawlTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onStopTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    onDeleteTask: (com.neoruaa.xhsdn.data.DownloadTask) -> Unit,
    detectedXhsLink: String?,
    onDismissPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks by com.neoruaa.xhsdn.data.TaskManager.getAllTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val navPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val activeTask = tasks.firstOrNull {
        it.status == com.neoruaa.xhsdn.data.TaskStatus.DOWNLOADING || it.status == com.neoruaa.xhsdn.data.TaskStatus.QUEUED
    }
    
    var taskToDelete by remember { mutableStateOf<com.neoruaa.xhsdn.data.DownloadTask?>(null) }
    
    if (taskToDelete != null) {
        androidx.compose.material3.AlertDialog(
            shape = RoundedCornerShape(28.dp),
            containerColor = MiuixTheme.colorScheme.surface,
            titleContentColor = MiuixTheme.colorScheme.onSurface,
            textContentColor = MiuixTheme.colorScheme.onSurface,
            onDismissRequest = { taskToDelete = null },
            title = { Text("删除任务") },
            text = { Text("确定要删除这条下载记录吗？已下载的文件不会被删除。") },
            confirmButton = {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = {
                        taskToDelete?.let { onDeleteTask(it) }
                        taskToDelete = null
                    },
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                        Color(0xFFF44336), // Red (backgroundColor)
                        Color.White        // White (contentColor)
                    )
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                top.yukonga.miuix.kmp.basic.Button(
                    onClick = { taskToDelete = null },
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                        MiuixTheme.colorScheme.surfaceVariant, // Greyish (backgroundColor)
                        MiuixTheme.colorScheme.onSurface       // Black (contentColor)
                    )
                ) {
                    Text("取消", color = MiuixTheme.colorScheme.onSurface)
                }
            }
        )
    }
    
    Box(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 18.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 筛选标签栏
                var selectedFilter by remember { mutableStateOf(0) }
                val waitingCount = tasks.count { it.status == com.neoruaa.xhsdn.data.TaskStatus.WAITING_FOR_USER }
                val failedCount = tasks.count { it.status == com.neoruaa.xhsdn.data.TaskStatus.FAILED }
                val filterLabels = listOf("全部", "等待选择($waitingCount)", "失败($failedCount)")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterLabels.forEachIndexed { index, label ->
                        val isSelected = selectedFilter == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedFilter = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 根据筛选条件过滤任务
                val filteredTasks = when (selectedFilter) {
                    1 -> tasks.filter { it.status == com.neoruaa.xhsdn.data.TaskStatus.WAITING_FOR_USER }
                    2 -> tasks.filter { it.status == com.neoruaa.xhsdn.data.TaskStatus.FAILED }
                    else -> tasks
                }
                if (filteredTasks.isEmpty()) {
                    // 空状态
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂无下载任务",
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击底部按钮开始下载",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    LaunchedEffect(filteredTasks.size) {
                        if (filteredTasks.isNotEmpty()) {
                            statusListState.animateScrollToItem(0)
                        }
                    }

                    LazyColumn(
                        state = statusListState,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = navPadding + 140.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredTasks, key = { _, task -> task.id }) { _, task ->
                            TaskCell(
                                task = task,
                                // 只有正在下载的任务才使用 uiState.mediaItems
                                mediaItems = if (task.filePaths.isNotEmpty()) {
                                    task.filePaths.map { MediaItem(it, detectMediaType(it)) }
                                } else if (task.status == com.neoruaa.xhsdn.data.TaskStatus.DOWNLOADING && uiState.mediaItems.isNotEmpty()) {
                                    uiState.mediaItems
                                } else {
                                    emptyList()
                                },

                                onCopyUrl = { onCopyUrl(task.noteUrl) },
                                onBrowseUrl = { onBrowseUrl(task.noteUrl) },
                                onRetry = { onRetryTask(task) },
                                onContinue = { onContinueTask(task) },
                                onWebCrawl = { onWebCrawlTask(task) },
                                onStop = { onStopTask(task) },
                                onDelete = { taskToDelete = task },
                                onMediaClick = onMediaClick
                            )
                        }
                    }
                }
            }
        }

        // 悬停在页面底部的下载按钮
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = navPadding + 16.dp)
                .fillMaxWidth()
                .clickable(enabled = !uiState.isDownloading) { onDownload() },
            cornerRadius = 18.dp,
            colors = CardDefaults.defaultColors(
                color = if (uiState.isDownloading) MiuixTheme.colorScheme.primaryVariant else MiuixTheme.colorScheme.primary
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (uiState.isDownloading) "下载中..." else "开始下载",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (uiState.isDownloading) (activeTask?.noteTitle ?: activeTask?.noteUrl ?: " ") else "点击读取剪贴板并下载",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // 剪贴板检测提示气泡（叠加层，靠近底部按钮）
        if (detectedXhsLink != null && !uiState.isDownloading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = navPadding + 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissPrompt() },
                    cornerRadius = 16.dp,
                    colors = CardDefaults.defaultColors(
                        color = Color(0xFFDDECDE)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Info,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "检测到小红书链接",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                text = detectedXhsLink,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "×",
                            fontSize = 18.sp,
                            color = Color.Gray
                        )
                    }
                }
                // 三角形指针（紧贴卡片底部）
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(24.dp, 14.dp)
                ) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2, size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFFDDECDE)
                    )
                }
            }
        }
    }
}

/**
 * 任务 Cell 组件
 */
@Composable
private fun TaskCell(
    task: com.neoruaa.xhsdn.data.DownloadTask,
    mediaItems: List<MediaItem> = emptyList(),
    onCopyUrl: () -> Unit,
    onBrowseUrl: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onWebCrawl: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onMediaClick: (MediaItem) -> Unit = {}
) {
    val statusColor = when (task.status) {
        com.neoruaa.xhsdn.data.TaskStatus.QUEUED -> Color(0xFF9E9E9E)       // 灰色
        com.neoruaa.xhsdn.data.TaskStatus.DOWNLOADING -> Color(0xFF2196F3)  // 蓝色
        com.neoruaa.xhsdn.data.TaskStatus.COMPLETED -> Color(0xFF4CAF50)    // 绿色
        com.neoruaa.xhsdn.data.TaskStatus.FAILED -> Color(0xFFF44336)       // 红色
        com.neoruaa.xhsdn.data.TaskStatus.WAITING_FOR_USER -> Color(0xFFFF9800) // 橙色
    }
    
    val statusText = when (task.status) {
        com.neoruaa.xhsdn.data.TaskStatus.QUEUED -> "排队中"
        com.neoruaa.xhsdn.data.TaskStatus.DOWNLOADING -> "下载中"
        com.neoruaa.xhsdn.data.TaskStatus.COMPLETED -> "已完成"
        com.neoruaa.xhsdn.data.TaskStatus.COMPLETED -> "已完成"
        com.neoruaa.xhsdn.data.TaskStatus.FAILED -> "下载失败"
        com.neoruaa.xhsdn.data.TaskStatus.WAITING_FOR_USER -> "等待选择"
    }
    
    val typeText = when (task.noteType) {
        com.neoruaa.xhsdn.data.NoteType.IMAGE -> "图文"
        com.neoruaa.xhsdn.data.NoteType.VIDEO -> "视频"
        com.neoruaa.xhsdn.data.NoteType.UNKNOWN -> "未知"
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {}, 
                onLongClick = onDelete
            )
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        // 顶部：时间 + 状态标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 创建时间
            Text(
                text = formatTime(task.createdAt),
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            // 状态标签
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 标题（最多两行）
        Text(
            text = task.noteTitle ?: task.noteUrl,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 类型 + 文件数量
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$typeText · ${task.totalFiles} 个文件",
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            if (task.failedFiles > 0) {
                Text(
                    text = " · ${task.failedFiles} 失败",
                    fontSize = 12.sp,
                    color = Color(0xFFF44336)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 进度条（仅下载中显示）
        if (task.totalFiles > 0 && task.status == com.neoruaa.xhsdn.data.TaskStatus.DOWNLOADING) {
            Column {
                // 进度文本
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${task.completedFiles}/${task.totalFiles}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "${(task.progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // 进度条
                LinearProgressIndicator(
                    progress = task.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        
        // 媒体预览网格（最后一个任务显示）
        if (mediaItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                mediaItems.forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMediaClick(item) }
                    ) {
                        val bitmap = rememberThumbnail(item)
                        bitmap?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isDownloading = task.status == com.neoruaa.xhsdn.data.TaskStatus.DOWNLOADING || 
                                task.status == com.neoruaa.xhsdn.data.TaskStatus.QUEUED
            
            if (isDownloading) {
                 Button(
                     onClick = onStop,
                     modifier = Modifier.weight(1f),
                     colors = ButtonDefaults.buttonColorsPrimary()
                 ) {
                     Text("停止", color = Color.White)
                 }
            } else {
                
                // 等待用户选择状态 (显示 坚持下载/网页爬取)
                if (task.status == com.neoruaa.xhsdn.data.TaskStatus.WAITING_FOR_USER) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 提示语
                        Text(
                            text = "因官方限制，仅能下载低清版本。如需高清，选网页爬取。",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onContinue,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text("坚持下载", color = Color.White)
                            }
                            Button(
                                onClick = onWebCrawl,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    MiuixTheme.colorScheme.surface,
                                    MiuixTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("网页爬取", color = MiuixTheme.colorScheme.onSurface)
                            }
                        }
                    }
                } else {
                    // 复制链接按钮
                    TextButton(
                        text = "复制",
                        onClick = onCopyUrl,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 爬取按钮（通过网页爬取功能打开）
                    TextButton(
                        text = "爬取",
                        onClick = onWebCrawl,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 重试按钮（仅失败任务显示）
                    if (task.status == com.neoruaa.xhsdn.data.TaskStatus.FAILED) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Text(
                                text = "重试",
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 格式化时间戳为可读字符串
 */
private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
private fun FilesPage(
    uiState: MainUiState,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val navPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Card(
        modifier = modifier,
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        SmallTitle(text = "已下载文件")
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (uiState.mediaItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "暂无已下载文件",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    verticalItemSpacing = 10.dp,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = navPadding + 60.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 4.dp)
                ) {
                    items(uiState.mediaItems) { item ->
                        MediaPreview(item = item, onClick = { onMediaClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreview(item: MediaItem, onClick: () -> Unit) {
    val bitmap = rememberThumbnail(item)
    val aspectRatio = rememberAspectRatio(item) ?: 0.75f
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.path,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(Color.Black)
            )
        } else {
            PlaceholderMedia(type = item.type, aspectRatio = aspectRatio)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fileName = File(item.path).name
            Text(
                text = fileName,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            if (item.type == MediaType.VIDEO) {
                Icon(
                    imageVector = MiuixIcons.Play,
                    contentDescription = "播放",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaceholderMedia(
    type: MediaType,
    aspectRatio: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(Color.Black.copy(alpha = 0.05f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (type == MediaType.VIDEO) MiuixIcons.Play else MiuixIcons.Info,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = Color.Gray
        )
    }
}

@Composable
private fun rememberThumbnail(item: MediaItem): ImageBitmap? {
    // 先检查缓存
    val cachedBitmap = thumbnailCache.get(item.path)
    if (cachedBitmap != null) {
        return cachedBitmap
    }
    
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = item.path) {
        value = withContext(Dispatchers.IO) {
            // 再次检查缓存（可能在等待期间被其他协程加载）
            thumbnailCache.get(item.path)?.let { return@withContext it }
            
            val file = File(item.path)
            if (!file.exists()) return@withContext null
            val bitmap = runCatching {
                when (item.type) {
                    MediaType.IMAGE -> decodeSampledBitmap(file.path, 720, 720)?.asImageBitmap()
                    MediaType.VIDEO -> createVideoThumbnail(file)?.asImageBitmap()
                    MediaType.OTHER -> null
                }
            }.getOrNull()
            
            // 存入缓存
            bitmap?.let { thumbnailCache.put(item.path, it) }
            bitmap
        }
    }
    return state.value
}

@Composable
private fun rememberAspectRatio(item: MediaItem): Float? {
    return remember(item.path) {
        kotlin.runCatching {
            when (item.type) {
                MediaType.IMAGE -> {
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeFile(item.path, options)
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth.toFloat() / options.outHeight.toFloat()
                    } else {
                        null
                    }
                }

                MediaType.VIDEO -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(item.path)
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
                        if (width != null && height != null && height > 0f) width / height else null
                    } finally {
                        retriever.release()
                    }
                }

                MediaType.OTHER -> null
            }
        }.getOrNull()
    }
}

private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
    options.inJustDecodeBounds = false
    return android.graphics.BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

private fun createVideoThumbnail(file: File): android.graphics.Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        android.media.ThumbnailUtils.createVideoThumbnail(
            file,
            Size(640, 360),
            null
        )
    } else {
        @Suppress("DEPRECATION")
        android.media.ThumbnailUtils.createVideoThumbnail(
            file.path,
            android.provider.MediaStore.Video.Thumbnails.MINI_KIND
        )
    }
}

private fun detectMediaType(path: String): MediaType {
    val lowercasePath = path.lowercase(java.util.Locale.getDefault())
    return when {
        lowercasePath.endsWith(".mp4") -> MediaType.VIDEO
        lowercasePath.endsWith(".jpg") || lowercasePath.endsWith(".jpeg") || 
        lowercasePath.endsWith(".png") || lowercasePath.endsWith(".webp") -> MediaType.IMAGE
        else -> MediaType.OTHER
    }
}

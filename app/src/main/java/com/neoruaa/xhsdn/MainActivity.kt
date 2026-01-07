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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.neoruaa.xhsdn.BuildConfig
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Cancel
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.icon.icons.useful.NavigatorSwitch
import top.yukonga.miuix.kmp.icon.icons.useful.Play
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import android.util.Size
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import top.yukonga.miuix.kmp.icon.icons.basic.SearchCleanup
import top.yukonga.miuix.kmp.icon.icons.useful.Edit
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var switchToLogsTab: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val controller = ThemeController(ColorSchemeMode.System)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val topBarState = rememberTopAppBarState()
            val scrollBehavior = MiuixScrollBehavior(state = topBarState)
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }
            switchToLogsTab = { selectedTab = 1 }

            MiuixTheme(controller = controller) {
                MainScreen(
                    uiState = uiState,
                    onUrlChange = viewModel::updateUrl,
                    onDownload = {
                        ensureStoragePermission {
                            selectedTab = 1
                            viewModel.startDownload { showToast(it) }
                        }
                    },
                    onCopyText = { ensureStoragePermission { viewModel.copyDescription({ showToast("已复制文案") }, { showToast(it) }) } },
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenWeb = { openWebCrawl(uiState.urlInput) },
                    onContinueDownload = { viewModel.continueAfterVideoWarning() },
                    onMediaClick = { openFile(it) },
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    scrollBehavior = scrollBehavior,
                    versionLabel = "v${BuildConfig.VERSION_NAME}"
                )
            }
        }
    }

    private fun openWebCrawl(input: String) {
        val cleanUrl = extractFirstUrl(input)
        if (cleanUrl == null) {
            showToast("未找到有效链接，请重新输入")
            return
        }
        viewModel.resetWebCrawlFlag()
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("url", cleanUrl)
        startActivityForResult(intent, WEBVIEW_REQUEST_CODE)
    }

    private fun extractFirstUrl(text: String): String? {
        val regex = Regex("https?://[\\w\\-.]+(?:/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?")
        return regex.find(text)?.value
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
            if (urls.isNotEmpty()) {
                switchToLogsTab?.invoke()
                viewModel.onWebCrawlResult(urls, content)
            } else {
                showToast("未发现可下载的资源")
            }
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 3001
        private const val WEBVIEW_REQUEST_CODE = 3002
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(
    uiState: MainUiState,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCopyText: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWeb: () -> Unit,
    onContinueDownload: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    scrollBehavior: ScrollBehavior,
    versionLabel: String
    ) {
    val statusListState = rememberLazyListState()
    LaunchedEffect(uiState.status.size, selectedTab) {
        if (uiState.status.isNotEmpty() && selectedTab == 1) {
            statusListState.animateScrollToItem(uiState.status.lastIndex)
        }
    }
    val navItems = listOf(
        NavigationItem("操作", MiuixIcons.Useful.Edit),
        NavigationItem("日志", MiuixIcons.Useful.Info),
        NavigationItem("下载", MiuixIcons.Useful.Save)
    )
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                title = "小红书下载器",
                largeTitle = "小红书下载器",
                scrollBehavior = scrollBehavior,
                actions = {
                    Icon(
                        imageVector = MiuixIcons.Useful.Settings,
                        contentDescription = "设置",
                        modifier = Modifier
                            .padding(end = 26.dp)
//                            .size(24.dp)
                            .clickable { onOpenSettings() }
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar(
                items = navItems,
                selected = selectedTab,
                onClick = { onTabChange(it) }
            )
        }
    ) { padding ->
        when (selectedTab) {
            0 -> HomePage(
                uiState = uiState,
                onUrlChange = onUrlChange,
                onDownload = onDownload,
                onCopyText = onCopyText,
                onOpenWeb = onOpenWeb,
                scrollBehavior = scrollBehavior,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(padding)
            )

            1 -> LogPage(
                uiState = uiState,
                statusListState = statusListState,
                scrollBehavior = scrollBehavior,
                onOpenWeb = onOpenWeb,
                onContinueDownload = onContinueDownload,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(padding)
            )

            else -> DownloadsPage(
                uiState = uiState,
                onMediaClick = onMediaClick,
                scrollBehavior = scrollBehavior,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(
                        start = padding.calculateLeftPadding(layoutDirection),
                        end = padding.calculateRightPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        bottom = 0.dp
                    )
            )
        }
    }
}

@Composable
private fun HomePage(
    uiState: MainUiState,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCopyText: () -> Unit,
    onOpenWeb: () -> Unit,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surface
            )
        ) {
            SmallTitle(text = "输入链接")
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                        value = uiState.urlInput,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = "粘贴分享链接",
                        useLabelAsPlaceholder = true,
                    trailingIcon = {
                        if (uiState.urlInput.isNotEmpty()) {
                            Icon(
                                imageVector = MiuixIcons.Basic.SearchCleanup,
                                contentDescription = "清空",
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .size(20.dp)
                                    .clickable { onUrlChange("") }
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onDownload() })
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        text = "复制文案",
                        onClick = onCopyText,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isDownloading
                    )
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isDownloading,
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(
                            text = if (uiState.isDownloading) "下载中…" else "开始下载",
                            color = Color.White
                        )
                    }
                }
                if (uiState.showWebCrawl) {
                    Button(
                        onClick = onOpenWeb,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("JSON 解析失败？试试网页模式")
                    }
                } else {
                    TextButton(
                        text = "网页爬取模式",
                        onClick = onOpenWeb,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun LogPage(
    uiState: MainUiState,
    onOpenWeb: () -> Unit,
    onContinueDownload: () -> Unit,
    statusListState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxSize(),
        cornerRadius = 18.dp,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
        ) {
            SmallTitle(text = "日志状态")

            if (uiState.progressLabel.isNotEmpty() || uiState.isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "进度  ")
                                Text(text = uiState.progressLabel.ifEmpty { "--" }, color = Color.Gray)
                            }
                            Text(text = uiState.downloadProgressText, color = Color.Gray)
                        }
                        LinearProgressIndicator(progress = uiState.progress)
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
            ) {
                if (uiState.status.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text("暂无内容，开始下载后会显示日志")
                    }
                } else {
                    LazyColumn(
                        state = statusListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .padding(16.dp)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    ) {
                        itemsIndexed(uiState.status) { index, line ->
                            Text(text = line, color = if (index == uiState.status.lastIndex) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground)
                        }
                    }
                }
            }

            if (uiState.showWebCrawl) {
                Button(
                    onClick = onOpenWeb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("JSON 解析失败？试试网页模式")
                }
            }

            if (uiState.showVideoWarning) {
                Row(modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = "仍然下载",
                        onClick = onContinueDownload,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onOpenWeb,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(
                            text = "网页爬取模式",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadsPage(
    uiState: MainUiState,
    onMediaClick: (MediaItem) -> Unit,
    scrollBehavior: ScrollBehavior,
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
        SmallTitle(text = "已下载")
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
//            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.mediaItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "暂无内容，开始下载后会显示缩略图",
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
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
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
                    imageVector = MiuixIcons.Useful.Play,
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
            imageVector = if (type == MediaType.VIDEO) MiuixIcons.Useful.Play else MiuixIcons.Useful.Info,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = Color.Gray
        )
    }
}

@Composable
private fun rememberThumbnail(item: MediaItem): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = item.path) {
        value = withContext(Dispatchers.IO) {
            val file = File(item.path)
            if (!file.exists()) return@withContext null
            runCatching {
                when (item.type) {
                    MediaType.IMAGE -> decodeSampledBitmap(file.path, 720, 720)?.asImageBitmap()
                    MediaType.VIDEO -> createVideoThumbnail(file)?.asImageBitmap()
                    MediaType.OTHER -> null
                }
            }.getOrNull()
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

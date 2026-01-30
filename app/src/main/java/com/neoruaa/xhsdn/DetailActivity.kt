package com.neoruaa.xhsdn

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neoruaa.xhsdn.viewmodels.DetailViewModel
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalDensity
import com.neoruaa.xhsdn.MediaItem
import com.neoruaa.xhsdn.MediaType
import com.neoruaa.xhsdn.utils.detectMediaType
import com.neoruaa.xhsdn.utils.decodeSampledBitmap
import com.neoruaa.xhsdn.utils.calculateInSampleSize
import com.neoruaa.xhsdn.utils.createVideoThumbnail
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Play

data class DetailUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val taskTitle: String = "",
    val isDownloading: Boolean = false,
    val noteContent: String? = null
)

class DetailViewModel : ViewModel() {
    private val _state = androidx.compose.runtime.mutableStateOf(DetailUiState())
    val state: androidx.compose.runtime.State<DetailUiState> = _state

    fun updateState(newState: DetailUiState) {
        _state.value = newState
    }
}

class DetailActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_TITLE = "task_title"
        private const val EXTRA_FILE_PATHS = "file_paths"
        private const val EXTRA_NOTE_CONTENT = "note_content"

        fun newIntent(context: Context, taskId: String, taskTitle: String, filePaths: List<String>, noteContent: String? = null): Intent {
            return Intent(context, DetailActivity::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putStringArrayListExtra(EXTRA_FILE_PATHS, ArrayList(filePaths))
                putExtra(EXTRA_NOTE_CONTENT, noteContent)
            }
        }

        fun newIntent(context: Context, taskId: Long, taskTitle: String, filePaths: List<String>, noteContent: String? = null): Intent {
            return newIntent(context, taskId.toString(), taskTitle, filePaths, noteContent)
        }
    }

    private val viewModel: DetailViewModel by lazy {
        ViewModelProvider(this)[DetailViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = AndroidColor.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !isNightMode

        // 获取传递的数据
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: "0" // 默认值
        val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "详情"
        val filePaths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: arrayListOf()
        val noteContent = intent.getStringExtra(EXTRA_NOTE_CONTENT)

        // 构建媒体项列表
        val mediaItems = filePaths.map { path ->
            MediaItem(path, detectMediaType(path))
        }

        // 更新UI状态
        viewModel.updateState(
            DetailUiState(
                mediaItems = mediaItems,
                taskTitle = "下载详情",
                isDownloading = false,
                noteContent = noteContent
            )
        )

        setContent {
            val controller = ThemeController(ColorSchemeMode.System)
            val uiState by viewModel.state.collectAsStateWithLifecycle()
            val topBarState = rememberTopAppBarState()
            MiuixTheme(controller = controller) {
                DetailScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onMediaClick = { mediaItem ->
                        // 处理媒体项点击事件
                        val intent = Intent().apply {
                            action = Intent.ACTION_VIEW
                            setDataAndType(android.net.Uri.fromFile(File(mediaItem.path)), getMimeType(mediaItem.type))
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onDeleteMedia = { mediaItem ->
                        // 从UI状态中移除该项目
                        viewModel.removeMediaItem(mediaItem)
                    },
                    topBarState = topBarState
                )
            }
        }
    }

    private fun getMimeType(mediaType: MediaType): String {
        return when (mediaType) {
            MediaType.IMAGE -> "image/*"
            MediaType.VIDEO -> "video/*"
            MediaType.OTHER -> "*/*"
        }
    }
}

@Composable
private fun DetailScreen(
    uiState: DetailUiState,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onDeleteMedia: (MediaItem) -> Unit,
    topBarState: TopAppBarState
) {
    val scrollBehavior = MiuixScrollBehavior(state = topBarState)

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                title = uiState.taskTitle,
                navigationIcon = {
                    Icon(
                        imageVector = MiuixIcons.Back,
                        contentDescription = "返回",
                        modifier = Modifier
                            .padding(start = 26.dp)
                            .clickable { onBack() }
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        FilesPage(
            uiState = uiState,
            onMediaClick = onMediaClick,
            onDeleteMedia = onDeleteMedia,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding)
        )
    }
}

@Composable
private fun FilesPage(
    uiState: DetailUiState,
    onMediaClick: (MediaItem) -> Unit,
    onDeleteMedia: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val navPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        verticalItemSpacing = 10.dp,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(
            top = 16.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 20.dp + navPadding
        ),
        modifier = modifier
    ) {

        // ===== 笔记文案（单列 / 满行）=====
        if (uiState.noteContent != null) {
            item(span = StaggeredGridItemSpan.FullLine) {
                SmallTitle(
                    text = "笔记文案",
                    insideMargin = PaddingValues(12.dp, 0.dp)
                )
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    cornerRadius = 18.dp,
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.background
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = uiState.noteContent,
                                color = MiuixTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }

        // ===== 已下载文件标题（单列 / 满行）=====
        item(span = StaggeredGridItemSpan.FullLine) {
            SmallTitle(
                text = "已下载文件",
                insideMargin = PaddingValues(12.dp, 0.dp)
            )
        }

        // ===== 空态（单列 / 满行）=====
        if (uiState.mediaItems.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ContinuousRoundedRectangle(18.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = "暂无已下载文件",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            // ===== 真·瀑布流区域（双列）=====
            items(uiState.mediaItems) { item ->
                MediaPreview(
                    item = item,
                    onClick = { onMediaClick(item) },
                    onDelete = onDeleteMedia
                )
            }
        }
    }
}


@Composable
private fun MediaPreview(item: MediaItem, onClick: () -> Unit, onDelete: (MediaItem) -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val bitmap = rememberThumbnail(item)
    val aspectRatio = rememberAspectRatio(item) ?: 0.75f
    val fileName = remember(item.path) { File(item.path).name }

    Column(
        modifier = Modifier
            .clip(ContinuousRoundedRectangle(18.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
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
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (item.type == MediaType.VIDEO) {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = "播放",
                        modifier = Modifier.size(20.dp)
                    )
                }
                val fileSize = remember(item.path) {
                    val file = File(item.path)
                    if (file.exists()) {
                        val size = file.length()
                        when {
                            size > 1024 * 1024 * 1024 -> "%.2f GB".format(size / (1024.0 * 1024.0 * 1024.0))
                            size > 1024 * 1024 -> "%.1f MB".format(size / (1024.0 * 1024.0))
                            size > 1024 -> "%.1f KB".format(size / 1024.0)
                            else -> "$size B"
                        }
                    } else {
                        "--"
                    }
                }
                Text(
                    text = fileSize,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = "删除",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { showDeleteDialog = true }
            )
        }
    }

    if (showDeleteDialog) {
        SuperDialog(
            title = "删除文件",
            summary = "是否删除 \"$fileName\" 媒体文件？",
            show = remember { mutableStateOf(true) },
            onDismissRequest = { showDeleteDialog = false }
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteDialog = false },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = "删除",
                    onClick = {
                        // 删除文件
                        val file = File(item.path)
                        if (file.exists()) {
                            file.delete()
                        }
                        onDelete(item) // 调用删除回调
                        showDeleteDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
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

    val state = produceState<ImageBitmap?>(initialValue = null, item.path) {
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
        runCatching {
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
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(item.path)
                        val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
                        val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
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

// 缓存用于缩略图
private val thumbnailCache = object : LinkedHashMap<String, ImageBitmap>(100, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
        return size > 100
    }
}






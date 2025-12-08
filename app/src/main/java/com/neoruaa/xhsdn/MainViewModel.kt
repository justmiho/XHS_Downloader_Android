package com.neoruaa.xhsdn

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class MediaItem(val path: String, val type: MediaType)

enum class MediaType {
    IMAGE, VIDEO, OTHER
}

data class MainUiState(
    val urlInput: String = "",
    val status: List<String> = emptyList(),
    val mediaItems: List<MediaItem> = emptyList(),
    val isDownloading: Boolean = false,
    val progressLabel: String = "",
    val progress: Float = 0f,
    val showWebCrawl: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private var totalMediaCount = 0
    private var downloadedCount = 0
    private val displayedFiles = mutableSetOf<String>()
    private var currentUrl: String? = null

    fun updateUrl(value: String) {
        _uiState.update { it.copy(urlInput = value, showWebCrawl = false) }
    }

    fun startDownload(onError: (String) -> Unit) {
        val targetUrl = _uiState.value.urlInput.trim()
        if (targetUrl.isEmpty()) {
            onError("请输入链接")
            return
        }
        currentUrl = targetUrl
        downloadedCount = 0
        totalMediaCount = 0
        displayedFiles.clear()
        _uiState.update {
            it.copy(
                isDownloading = true,
                status = listOf("处理中：$targetUrl"),
                mediaItems = emptyList(),
                progressLabel = "",
                progress = 0f,
                showWebCrawl = false
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            totalMediaCount = runCatching { XHSDownloader(getApplication()).getMediaCount(targetUrl) }
                .getOrElse { 0 }
            updateProgress()

            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (displayedFiles.add(filePath)) {
                                addMedia(filePath)
                                downloadedCount++
                                updateProgress()
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        appendStatus(status)
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                        // 保留单个文件进度供后续扩展
                    }

                    override fun onDownloadError(status: String, originalUrl: String) {
                        appendStatus("错误：$status ($originalUrl)")
                        // 解析失败时给出网页爬取入口
                        if (status.contains("No media URLs found", true)
                            || status.contains("Failed to fetch post details", true)
                            || status.contains("Could not extract post ID", true)
                        ) {
                            _uiState.update { it.copy(showWebCrawl = true) }
                        }
                    }
                }
            )

            val success = downloader.downloadContent(targetUrl)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isDownloading = false) }
                if (!success) {
                    appendStatus("下载失败，请检查链接或网络")
                } else {
                    appendStatus("全部下载完成")
                }
            }
        }
    }

    fun copyDescription(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val targetUrl = _uiState.value.urlInput.trim()
        if (targetUrl.isEmpty()) {
            onError("请输入链接")
            return
        }
        appendStatus("获取笔记文案中…")
        viewModelScope.launch(Dispatchers.IO) {
            val desc = XHSDownloader(getApplication(), null).getNoteDescription(targetUrl)
            withContext(Dispatchers.Main) {
                if (!desc.isNullOrEmpty()) {
                    copyToClipboard(desc)
                    appendStatus("已复制文案：\n$desc")
                    onResult(desc)
                } else {
                    appendStatus("未获取到文案")
                    onError("未获取到文案")
                }
            }
        }
    }

    fun onWebCrawlResult(urls: List<String>, content: String?) {
        if (urls.isEmpty()) {
            appendStatus("网页未发现可下载的资源")
            return
        }
        appendStatus("网页模式发现 ${urls.size} 条资源，开始转存")
        content?.takeIf { it.isNotEmpty() }?.let {
            appendStatus("已复制网页文案")
            copyToClipboard(it)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (displayedFiles.add(filePath)) {
                                addMedia(filePath)
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        appendStatus(status)
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {}

                    override fun onDownloadError(status: String, originalUrl: String) {
                        appendStatus("错误：$status ($originalUrl)")
                    }
                }
            )
            val postId = currentUrl?.let { downloader.extractPostId(it) } ?: "image"
            urls.forEachIndexed { index, rawUrl ->
                val transformed = downloader.transformXhsCdnUrl(rawUrl).takeUnless { it.isNullOrEmpty() } ?: rawUrl
                val extension = determineFileExtension(transformed)
                val fileName = "${postId}_${index + 1}.$extension"
                downloader.downloadFile(transformed, fileName)
            }
            withContext(Dispatchers.Main) {
                appendStatus("网页转存完成")
                _uiState.update { it.copy(showWebCrawl = false) }
            }
        }
    }

    fun resetWebCrawlFlag() {
        _uiState.update { it.copy(showWebCrawl = false) }
    }

    fun notifyWebCrawlSuggestion() {
        _uiState.update { it.copy(showWebCrawl = true) }
    }

    private fun addMedia(filePath: String) {
        val type = detectMediaType(filePath)
        _uiState.update { state ->
            state.copy(mediaItems = state.mediaItems + MediaItem(filePath, type))
        }
    }

    private fun detectMediaType(filePath: String): MediaType {
        val lower = filePath.lowercase(Locale.getDefault())
        return when {
            lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") ||
                lower.endsWith(".mkv") || lower.contains("sns-video") || lower.contains("video") -> MediaType.VIDEO

            lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".gif") || lower.endsWith(".webp") -> MediaType.IMAGE

            else -> MediaType.OTHER
        }
    }

    private fun determineFileExtension(url: String?): String {
        val lower = url?.lowercase(Locale.getDefault()) ?: return "jpg"
        return when {
            lower.contains(".mp4") -> "mp4"
            lower.contains(".mov") -> "mov"
            lower.contains(".avi") -> "avi"
            lower.contains(".mkv") -> "mkv"
            lower.contains(".webm") -> "webm"
            lower.contains(".png") -> "png"
            lower.contains(".gif") -> "gif"
            lower.contains(".webp") -> "webp"
            lower.contains("video") || lower.contains("sns-video") -> "mp4"
            else -> "jpg"
        }
    }

    private fun updateProgress() {
        val label = if (totalMediaCount > 0) {
            "$downloadedCount/$totalMediaCount"
        } else {
            "$downloadedCount/?"
        }
        val fraction = if (totalMediaCount > 0) {
            downloadedCount.toFloat() / totalMediaCount
        } else {
            0f
        }
        _uiState.update { it.copy(progressLabel = label, progress = fraction) }
    }

    private fun appendStatus(message: String) {
        _uiState.update { it.copy(status = it.status + message) }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("xhsdn", text))
    }
}

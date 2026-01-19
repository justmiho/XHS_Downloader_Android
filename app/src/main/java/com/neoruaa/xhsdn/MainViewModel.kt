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
    val downloadProgressText: String = "0%｜0kb/s", // Format: "XX%｜XXXkb/s"
    val showWebCrawl: Boolean = false,
    val showVideoWarning: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    private var totalMediaCount = 0
    private var downloadedCount = 0
    private val displayedFiles = mutableSetOf<String>()
    private var currentUrl: String? = null
    private var hasUserContinuedAfterVideoWarning = false
    private var downloadJob: kotlinx.coroutines.Job? = null

    // Track individual file progress for more accurate overall progress
    private val fileProgressMap = mutableMapOf<String, Float>() // Maps file path to progress (0.0 to 1.0)
    private var currentFileProgress = 0f // Progress of the currently downloading file (0.0 to 1.0)
    private var lastOverallProgress = 0f // Track the last overall progress to prevent regression

    // Fields to track download progress and speed for the first callback
    private var currentDownloadStartTime: Long = 0
    private var currentDownloadStartBytes: Long = 0
    private var currentDownloadTotalBytes: Long = 0
    private var currentDownloadedBytes: Long = 0
    private var lastSpeedCalculationTime: Long = 0
    private var lastCalculatedSpeed = "0kb/s"

    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> { // >= 1 MB/s
                val mbps = bytesPerSecond / (1024 * 1024)
                "${String.format("%.2f", mbps)}MB/s"
            }
            bytesPerSecond >= 1024 -> { // >= 1 KB/s
                val kbps = bytesPerSecond / 1024
                "${String.format("%.1f", kbps)}KB/s"
            }
            else -> {
                "${bytesPerSecond.toInt()}B/s"
            }
        }
    }

    private fun resetDownloadTracking() {
        currentDownloadStartTime = System.currentTimeMillis()
        currentDownloadStartBytes = 0
        currentDownloadTotalBytes = 0
        currentDownloadedBytes = 0
        lastSpeedCalculationTime = 0
        lastCalculatedSpeed = "0KB/s"
        lastOverallProgress = 0f // Reset the last overall progress when starting a new download

        // Update UI to show initial state
        _uiState.update { currentState ->
            currentState.copy(downloadProgressText = "0.0%｜0KB/s")
        }
    }

    fun updateUrl(value: String) {
        _uiState.update { it.copy(urlInput = value, showWebCrawl = false) }
    }

    fun startDownload(onError: (String) -> Unit) {
        val targetUrl = _uiState.value.urlInput.trim()
        if (targetUrl.isEmpty()) {
            onError("请输入链接")
            return
        }
        // Cancel any existing download job
        downloadJob?.cancel()
        currentUrl = targetUrl
        downloadedCount = 0
        totalMediaCount = 0
        displayedFiles.clear()

        // Reset download tracking variables
        resetDownloadTracking()

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

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
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
                                currentFileProgress = 0f // Reset current file progress when file is completed
                                updateProgress()
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        appendStatus(status)
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                        // Calculate progress percentage - always update this
                        val progressPercent = if (total > 0) {
                            (downloaded.toDouble() / total.toDouble() * 100).toFloat()
                        } else 0f

                        // Calculate individual file progress (0.0 to 1.0)
                        currentFileProgress = if (total > 0) {
                            downloaded.toFloat() / total.toFloat()
                        } else {
                            0f
                        }

                        // Calculate download speed more responsively
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = currentTime - lastSpeedCalculationTime

                        // Update speed calculation more frequently for better responsiveness
                        if (deltaTime > 500) { // Update every 0.5 seconds instead of 1 second
                            // Prevent negative deltaBytes by ensuring downloaded is greater than or equal to currentDownloadedBytes
                            val deltaBytes = if (downloaded >= currentDownloadedBytes) {
                                downloaded - currentDownloadedBytes
                            } else {
                                // If downloaded is less than currentDownloadedBytes, it means we're tracking a different file
                                // In this case, just use the current downloaded amount as the basis
                                downloaded
                            }

                            val deltaTimeSec = deltaTime.toDouble() / 1000.0 // Convert to seconds

                            val speedBps = if (deltaTimeSec > 0) {
                                deltaBytes.toDouble() / deltaTimeSec
                            } else 0.0

                            // Format speed with appropriate units (KB/s or MB/s)
                            lastCalculatedSpeed = formatSpeed(speedBps)
                            lastSpeedCalculationTime = currentTime
                        }

                        // Update the current download stats
                        currentDownloadedBytes = downloaded
                        currentDownloadTotalBytes = total

                        // Update overall progress
                        updateProgress()

                        // Always update UI state with current progress and speed
                        val progressText = "${String.format("%.1f", progressPercent)}%｜$lastCalculatedSpeed"
                        _uiState.update { currentState ->
                            currentState.copy(downloadProgressText = progressText)
                        }
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

                    override fun onVideoDetected() {
                        // Only show the warning and stop the download if the user hasn't already chosen to continue
                        if (!hasUserContinuedAfterVideoWarning) {
                            _uiState.update { it.copy(showVideoWarning = true) }

                            // Cancel the download job to stop the download immediately
                            downloadJob?.cancel()
                            appendStatus("下载因检测到视频而停止")

                            // Update isDownloading to false when download is stopped
                            _uiState.update { it.copy(isDownloading = false) }

                            appendStatus("提示：由于小红书平台升级了接口，普通爬取暂不支持视频原文件获取，点击“仍然下载”以继续以 720P 下载视频，点击“网页爬取模式”以尝试从web端获取视频原文件")
                        }
                        // If user has already continued, just log that video was detected but don't stop
                        else {
                            appendStatus("检测到视频文件，继续下载...")
                        }
                    }
                }
            )

            // If user has continued after video warning, don't stop on video detection
            if (hasUserContinuedAfterVideoWarning) {
                downloader.setShouldStopOnVideo(false)
            } else {
                downloader.setShouldStopOnVideo(true)
            }
            // Run download with cancellation check
            val success = runDownloadWithCancellationCheck(downloader, targetUrl)
            withContext(Dispatchers.Main) {
                val jobStillActive = downloadJob?.isActive == true
                if (jobStillActive) {
                    // Reset download tracking when download completes
                    resetDownloadTracking()
                    _uiState.update { it.copy(isDownloading = false) }
                    if (!success) {
                        appendStatus("下载失败，请检查链接或网络")
                    } else {
                        appendStatus("全部下载完成")
                    }
                }
                // Reset the flag after download completes (whether successful or not)
                hasUserContinuedAfterVideoWarning = false
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
        downloadedCount = 0
        totalMediaCount = urls.size

        // Reset download tracking variables for web crawl
        resetDownloadTracking()

        _uiState.update {
            it.copy(
                isDownloading = true,
                progressLabel = "",
                progress = 0f,
                showWebCrawl = false,
                showVideoWarning = false
            )
        }
        updateProgress()
        content?.takeIf { it.isNotEmpty() }?.let {
            appendStatus("已复制网页文案")
            copyToClipboard(it)
        }
        appendStatus("网页模式发现 ${urls.size} 条资源，正在转存...")
        viewModelScope.launch(Dispatchers.IO) {
            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (displayedFiles.add(filePath)) {
                                addMedia(filePath)
                                downloadedCount++
                                currentFileProgress = 0f // Reset current file progress when file is completed
                                updateProgress()
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        appendStatus(status)
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                        // Calculate progress percentage - always update this
                        val progressPercent = if (total > 0) {
                            (downloaded.toDouble() / total.toDouble() * 100).toFloat()
                        } else 0f

                        // Calculate individual file progress (0.0 to 1.0)
                        currentFileProgress = if (total > 0) {
                            downloaded.toFloat() / total.toFloat()
                        } else {
                            0f
                        }

                        // Calculate download speed more responsively
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = currentTime - lastSpeedCalculationTime

                        // Update speed calculation more frequently for better responsiveness
                        if (deltaTime > 500) { // Update every 0.5 seconds instead of 1 second
                            // Prevent negative deltaBytes by ensuring downloaded is greater than or equal to currentDownloadedBytes
                            val deltaBytes = if (downloaded >= currentDownloadedBytes) {
                                downloaded - currentDownloadedBytes
                            } else {
                                // If downloaded is less than currentDownloadedBytes, it means we're tracking a different file
                                // In this case, just use the current downloaded amount as the basis
                                downloaded
                            }

                            val deltaTimeSec = deltaTime.toDouble() / 1000.0 // Convert to seconds

                            val speedBps = if (deltaTimeSec > 0) {
                                deltaBytes.toDouble() / deltaTimeSec
                            } else 0.0

                            // Format speed with appropriate units (KB/s or MB/s)
                            lastCalculatedSpeed = formatSpeed(speedBps)
                            lastSpeedCalculationTime = currentTime
                        }

                        // Update the current download stats
                        currentDownloadedBytes = downloaded
                        currentDownloadTotalBytes = total

                        // Update overall progress
                        updateProgress()

                        // Always update UI state with current progress and speed
                        val progressText = "${String.format("%.1f", progressPercent)}%｜$lastCalculatedSpeed"
                        _uiState.update { currentState ->
                            currentState.copy(downloadProgressText = progressText)
                        }
                    }

                    override fun onDownloadError(status: String, originalUrl: String) {
                        appendStatus("错误：$status ($originalUrl)")
                    }

                    override fun onVideoDetected() {
                        // For web crawl, we don't show the video warning since it's already in web mode
                        // Just update the state to reflect that videos were detected
                        _uiState.update { it.copy(showVideoWarning = true) }
                    }
                }
            )

            // If user has continued after video warning, don't stop on video detection
            if (hasUserContinuedAfterVideoWarning) {
                downloader.setShouldStopOnVideo(false)
            } else {
                downloader.setShouldStopOnVideo(true)
            }
            // Reset the stop flag for new download
            downloader.resetStopDownload()
            // Extract all valid XHS URLs from the input
            val url: List<String>? = downloader.extractLinks(currentUrl)
            val postIdTemp: String = url?.let { downloader.extractPostId(it.firstOrNull()) } ?: currentDownloadStartTime.toString()
            val postId = "webview_$postIdTemp"
            urls.forEachIndexed { index, rawUrl ->
                val transformed = downloader.transformXhsCdnUrl(rawUrl).takeUnless { it.isNullOrEmpty() } ?: rawUrl
                val extension = determineFileExtension(transformed)
                val fileName = "${postId}_${index + 1}.$extension"
                downloader.downloadFile(transformed, fileName)
            }
            withContext(Dispatchers.Main) {
                updateProgress()
                appendStatus("网页转存完成")
                // Reset download tracking when download completes
                resetDownloadTracking()
                _uiState.update { it.copy(showWebCrawl = false, isDownloading = false) }
                // Reset the flag after download completes (whether successful or not)
                hasUserContinuedAfterVideoWarning = false
            }
        }
    }

    fun resetWebCrawlFlag() {
        _uiState.update { it.copy(showWebCrawl = false) }
    }

    fun notifyWebCrawlSuggestion() {
        _uiState.update { it.copy(showWebCrawl = true) }
    }

    fun continueAfterVideoWarning() {
        hasUserContinuedAfterVideoWarning = true
        _uiState.update { it.copy(showVideoWarning = false) }
        // Restart the download with the same URL to continue after video warning
        currentUrl?.let { url ->
            startDownload { msg -> appendStatus("错误: $msg") }
        }
    }

    fun resetVideoWarning() {
        _uiState.update { it.copy(showVideoWarning = false) }
        hasUserContinuedAfterVideoWarning = false
    }

    private fun addMedia(filePath: String) {
        val type = detectMediaType(filePath)
        _uiState.update { state ->
            state.copy(mediaItems = state.mediaItems + MediaItem(filePath, type))
        }
    }

    fun removeMediaItem(mediaItem: MediaItem) {
        _uiState.update { state ->
            state.copy(mediaItems = state.mediaItems.filter { it.path != mediaItem.path })
        }
    }

    private fun runDownloadWithCancellationCheck(downloader: XHSDownloader, targetUrl: String): Boolean {
        // Create a thread to run the download
        var result = false
        val thread = Thread {
            result = downloader.downloadContent(targetUrl)
        }
        thread.start()

        // Periodically check if the job was cancelled
        while (thread.isAlive) {
            if (downloadJob?.isActive == false) {
                // Interrupt the thread
                thread.interrupt()
                // Wait a bit for the thread to respond to interruption
                Thread.sleep(100)
                // Don't use thread.stop() as it's deprecated and unsafe
                // Instead, rely on the interrupt mechanism and XHSDownloader's checks
                break
            }
            Thread.sleep(100) // Check every 100ms
        }

        // Wait for thread to finish gracefully
        try {
            thread.join(1000) // Wait up to 1 second for graceful shutdown
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        return result
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
        val calculatedProgress = if (totalMediaCount > 0) {
            // Calculate progress as (completed files + current file progress) / total files
            (downloadedCount + currentFileProgress) / totalMediaCount.toFloat()
        } else {
            0f
        }

        // Ensure progress doesn't regress (go backwards)
        val overallProgress = maxOf(calculatedProgress, lastOverallProgress)
        lastOverallProgress = overallProgress

        _uiState.update { it.copy(progressLabel = label, progress = overallProgress) }
    }

    private fun appendStatus(message: String) {
        _uiState.update { it.copy(status = it.status + message) }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("xhsdn", text))
    }
}

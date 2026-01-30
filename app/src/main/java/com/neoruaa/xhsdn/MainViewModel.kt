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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import com.neoruaa.xhsdn.data.TaskManager
import com.neoruaa.xhsdn.data.TaskStatus
import com.neoruaa.xhsdn.data.NoteType
import kotlinx.coroutines.CancellationException
import android.util.Log


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
    
    // Task tracking for history
    var currentTaskId: Long = 0
        private set
    private var taskCompletedFiles: Int = 0
    private var taskFailedFiles: Int = 0

    fun cancelCurrentDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            _uiState.update { it.copy(isDownloading = false, progressLabel = "已取消") }
            if (currentTaskId > 0) {
                TaskManager.completeTask(currentTaskId, false, "用户取消")
            }
        }
    }

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

    fun pasteLinkFromClipboard() {
        viewModelScope.launch {
            try {
                val clipboardManager = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val clipboardText = clipData.getItemAt(0).text?.toString() ?: ""
                    if (clipboardText.isNotEmpty()) {
                        _uiState.update { it.copy(urlInput = clipboardText) }
                        appendStatus("已从剪贴板粘贴链接")
                    } else {
                        appendStatus("剪贴板内容为空")
                    }
                } else {
                    appendStatus("剪贴板无内容")
                }
            } catch (e: Exception) {
                appendStatus("读取剪贴板失败: ${e.message}")
            }
        }
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
            
            // Create task in TaskManager
            // Default to IMAGE, update later if video detected
            val noteType = NoteType.IMAGE 
            val taskId = TaskManager.createTask(
                noteUrl = targetUrl,
                noteTitle = null, 
                noteType = noteType,
                totalFiles = if (totalMediaCount > 0) totalMediaCount else 1
            )
            TaskManager.startTask(taskId)
            currentTaskId = taskId
            val myTaskId = taskId // Capture taskId locally for this coroutine

            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (displayedFiles.add(filePath)) {
                                addMedia(filePath)
                                downloadedCount++
                                taskCompletedFiles++
                                currentFileProgress = 0f 
                                updateProgress()
                                // Update task progress using local ID
                                TaskManager.updateProgress(myTaskId, taskCompletedFiles, taskFailedFiles)
                                TaskManager.addFilePath(myTaskId, filePath)
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
                        // Correctly mark as VIDEO type in DB
                        // Note: Live Photos don't trigger this, so they stay as IMAGE (correct)
                        TaskManager.updateTaskType(myTaskId, NoteType.VIDEO)

                        // Only show the warning and stop the download if the user hasn't already chosen to continue
                        if (!hasUserContinuedAfterVideoWarning) {
                            _uiState.update { it.copy(showVideoWarning = true) }

                            // Update DB status to WAITING_FOR_USER so UI can show choice buttons
                            TaskManager.updateTaskStatus(myTaskId, TaskStatus.WAITING_FOR_USER, "检测到视频，请选择下载方式")

                            // Cancel the download job to stop the download immediately
                            // We use a specific flag to know this was an intentional stop for user input
                            downloadJob?.cancel(CancellationException("WAITING_FOR_USER"))
                            
                            appendStatus("下载因检测到视频而停止")

                            // Update isDownloading to false when download is stopped
                            _uiState.update { it.copy(isDownloading = false) }

                            appendStatus("提示：检测到视频，请选择坚持下载(720P)或网页爬取")
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
            // Run download with cancellation check, passing THIS job
            val currentJob = coroutineContext[Job]
            try {
                val success = runDownloadWithCancellationCheck(downloader, targetUrl, currentJob)
                
                withContext(Dispatchers.Main) {
                    // Only proceed if this coroutine is still active (not cancelled)
                    if (isActive) {
                        appendStatus("✅ 下载完成")
                        _uiState.update { it.copy(isDownloading = false) }
                        
                         if (!success) {
                        // If failed, but it was due to waiting for user code, do NOT mark as failed
                        // Check if current task status is WAITING_FOR_USER (race condition check)
                         // Actually, we handle this in the catch block better.
                         // Only mark failed if we are NOT waiting for user.
                         // But here success=false means downloader returned false.
                         // If cancelled, we go to catch block.
                         
                         // If downloader simply returned false (rare if cancelled?), complete as failed.
                         TaskManager.completeTask(myTaskId, false, "下载过程出错")
                         appendStatus("下载失败")
                    } else {
                         TaskManager.completeTask(myTaskId, true)
                         appendStatus("下载完成")
                    }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                     // Check if this cancellation was for WAITING_FOR_USER
                     if (e.message == "WAITING_FOR_USER") {
                         Log.d("MainViewModel", "Download cancelled for user input")
                         // Do NOT complete task as failed. Leave it as WAITING_FOR_USER.
                     } else {
                        appendStatus("⏹️ 下载已取消")
                        TaskManager.completeTask(myTaskId, false, "下载已取消")
                     }
                } else {
                    appendStatus("❌ 下载出错: ${e.message}")
                    TaskManager.completeTask(myTaskId, false, e.message ?: "未知错误")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Reset download tracking when download completes (success or failure)
                    resetDownloadTracking()
                    // Reset global currentTaskId only if it matches ours
                    if (currentTaskId == myTaskId) {
                        currentTaskId = 0
                    }
                    taskCompletedFiles = 0
                    taskFailedFiles = 0
                    // Reset the flag after download completes (whether successful or not)
                    hasUserContinuedAfterVideoWarning = false
                }
            }
        }
    }

    /**
     * 重试失败的任务（复用已有 taskId）
     */
    fun retryTask(task: com.neoruaa.xhsdn.data.DownloadTask, onError: (String) -> Unit) {
        // 重置任务状态
        TaskManager.resetTask(task.id)
        currentTaskId = task.id
        
        val targetUrl = task.noteUrl
        currentUrl = targetUrl
        updateUrl(targetUrl)
        
        // Reset tracking
        downloadedCount = 0
        totalMediaCount = 0
        taskCompletedFiles = 0
        taskFailedFiles = 0
        displayedFiles.clear()
        resetDownloadTracking()
        
        _uiState.update {
            it.copy(
                isDownloading = true,
                progressLabel = "",
                progress = 0f,
                showWebCrawl = false,
                showVideoWarning = false,
                mediaItems = emptyList()
            )
        }
        
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            totalMediaCount = runCatching { XHSDownloader(getApplication()).getMediaCount(targetUrl) }
                .getOrElse { 0 }
            updateProgress()
            
            val myTaskId = task.id
            
            val downloader = XHSDownloader(
                getApplication(),
                object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                        viewModelScope.launch(Dispatchers.Main) {
                            if (displayedFiles.add(filePath)) {
                                addMedia(filePath)
                                downloadedCount++
                                taskCompletedFiles++
                                currentFileProgress = 0f 
                                updateProgress()
                                TaskManager.updateProgress(myTaskId, taskCompletedFiles, taskFailedFiles)
                                TaskManager.addFilePath(myTaskId, filePath)
                            }
                        }
                    }

                    override fun onDownloadProgress(status: String) {
                        appendStatus(status)
                    }

                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {
                        val progressPercent = if (total > 0) {
                            (downloaded.toDouble() / total.toDouble() * 100).toFloat()
                        } else 0f
                        currentFileProgress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                        updateProgress()
                        val progressText = "${String.format("%.1f", progressPercent)}%｜$lastCalculatedSpeed"
                        _uiState.update { currentState ->
                            currentState.copy(downloadProgressText = progressText)
                        }
                    }

                    override fun onDownloadError(status: String, originalUrl: String) {
                        appendStatus("错误：$status ($originalUrl)")
                        taskFailedFiles++
                    }

                    override fun onVideoDetected() {
                        _uiState.update { it.copy(showVideoWarning = true) }
                        TaskManager.updateTaskType(myTaskId, NoteType.VIDEO)
                    }
                }
            )

            downloader.setShouldStopOnVideo(true)
            downloader.resetStopDownload()

            try {
                downloader.downloadContent(targetUrl)
                withContext(Dispatchers.Main) {
                    if (taskCompletedFiles > 0 || taskFailedFiles == 0) {
                        TaskManager.completeTask(myTaskId, true)
                    } else {
                        TaskManager.completeTask(myTaskId, false, "下载过程出错")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                withContext(Dispatchers.Main) {
                    if (taskCompletedFiles == 0) {
                        TaskManager.completeTask(myTaskId, false, "下载已取消")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    TaskManager.completeTask(myTaskId, false, e.message ?: "未知错误")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isDownloading = false, showVideoWarning = false) }
                    resetDownloadTracking()
                    if (currentTaskId == myTaskId) {
                        currentTaskId = 0
                    }
                    taskCompletedFiles = 0
                    taskFailedFiles = 0
                    hasUserContinuedAfterVideoWarning = false
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

    fun onWebCrawlResult(urls: List<String>, content: String?, taskId: Long? = null) {
        if (urls.isEmpty()) {
            appendStatus("网页未发现可下载的资源")
            return
        }

        // Filter duplicate videos logic
        // 1. Separate videos and images
        // Helper to check if URL is a video
        fun isVideo(url: String): Boolean {
            return url.contains(".mp4") || 
                   url.contains("sns-video") || 
                   url.contains("blob:")
        }

        val (videoUrls, imageUrls) = urls.partition { isVideo(it) }
        
        // 2. Deduplicate videos (Prioritize HD from sns-video-bd.xhscdn.com)
        // Since xhs_extractor.js pushes the main video (originVideoKey) first,
        // we can safely prioritize the first video that matches our quality criteria.
        val finalVideoUrls = if (videoUrls.size > 1) {
            val hdVideos = videoUrls.filter { it.contains("sns-video") } // Broadened check
            if (hdVideos.isNotEmpty()) {
                // Determine valid HD videos
                val distinctHd = hdVideos.distinct()
                
                // User requirement: Keep only the highest quality one.
                // Assuming the first one (from originVideoKey) is the best.
                listOf(distinctHd.first()) 
            } else {
                // No HD videos found, keep the first available video to avoid duplicates
                listOf(videoUrls.distinct().first())
            }
        } else {
            videoUrls // 0 or 1 video, just keep it
        }

        // 3. Combine and deduplicate everything
        val finalUrls = (imageUrls + finalVideoUrls).distinct()
        
        if (finalUrls.isEmpty()) {
             appendStatus("过滤后未发现有效资源")
             return
        }

        downloadedCount = 0
        totalMediaCount = finalUrls.size

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
        appendStatus("开始爬取，请等待任务完成")
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
                                // If we have a taskId, add the file path to that task
                                taskId?.let { id ->
                                    TaskManager.addFilePath(id, filePath)
                                }
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
            // Extract all valid XHS URLs from the input - prefer task URL if taskId is provided
            val sourceUrl = taskId?.let { TaskManager.getTaskById(it)?.noteUrl } ?: currentUrl
            val url: List<String> = sourceUrl?.let { downloader.extractLinks(it) }.orEmpty()
            val postIdTemp: String =
                if (url.isNotEmpty()) downloader.extractPostId(url.firstOrNull()) ?: currentDownloadStartTime.toString()
                else currentDownloadStartTime.toString()
            val postId = "webview_$postIdTemp"
            try {
                finalUrls.forEachIndexed { index, rawUrl ->
                    val transformed = downloader.transformXhsCdnUrl(rawUrl).takeUnless { it.isNullOrEmpty() } ?: rawUrl
                    val extension = determineFileExtension(transformed)
                    val fileName = "${postId}_${index + 1}.$extension"
                    downloader.downloadFile(transformed, fileName)
                }
                withContext(Dispatchers.Main) {
                    updateProgress()
                    appendStatus("网页转存完成")
                    // Mark task as complete if taskId was provided
                    taskId?.let { id ->
                        TaskManager.completeTask(id, true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendStatus("网页转存出错: ${e.message}")
                    // Mark task as failed if taskId was provided
                    taskId?.let { id ->
                        TaskManager.completeTask(id, false, "网页转存出错: ${e.message}")
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    // Reset download tracking when download completes (success or failure)
                    resetDownloadTracking()
                    _uiState.update { it.copy(showWebCrawl = false, isDownloading = false) }
                    // Reset the flag after download completes (whether successful or not)
                    hasUserContinuedAfterVideoWarning = false
                }
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

    fun continueTask(task: com.neoruaa.xhsdn.data.DownloadTask) {
        updateUrl(task.noteUrl)
        continueAfterVideoWarning()
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

    private fun runDownloadWithCancellationCheck(downloader: XHSDownloader, targetUrl: String, job: Job?): Boolean {
        // Create a thread to run the download
        var result = false
        val thread = Thread {
            result = downloader.downloadContent(targetUrl)
        }
        thread.start()

        // Periodically check if the SPECIFIC job was cancelled
        while (thread.isAlive) {
            if (job?.isActive == false) {
                // Interrupt the thread
                thread.interrupt()
                // Wait a bit for the thread to respond to interruption
                Thread.sleep(100)
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

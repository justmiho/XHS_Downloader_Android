package com.neoruaa.xhsdn.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.neoruaa.xhsdn.DownloadCallback
import com.neoruaa.xhsdn.MainActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import com.neoruaa.xhsdn.XHSApplication
import com.neoruaa.xhsdn.R
import com.neoruaa.xhsdn.XHSDownloader
import com.neoruaa.xhsdn.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job

import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException

object BackgroundDownloadManager {
    private const val TAG = "BackgroundDownload"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val activeUrls = ConcurrentHashMap.newKeySet<String>()
    private const val CHANNEL_ID = "xhs_download_channel_v2"
    private const val BASE_NOTIFICATION_ID = 1000

    fun startDownload(context: Context, url: String, title: String? = null) {
        Log.d(TAG, "startDownload: $url, title: $title")
        val appContext = context.applicationContext
        
        // Ensure TaskManager is initialized
        TaskManager.init(appContext)

        // 背景下载允许在任何情况下尝试，去重逻辑由 activeUrls 承担
        Log.d(TAG, "startDownload attempt for: $url")
        
        // Check for duplicates - atomically add to set
        if (!activeUrls.add(url)) {
            Log.d(TAG, "startDownload: Task already active, skipping duplicate. URL: $url")
            NotificationHelper.showDiagnosticNotification(appContext, "任务忽略", "链接正在下载中，请勿重复复制")
            return
        }
        
        // Also check if TaskManager has it recently (redundant but safe)
        if (TaskManager.hasRecentTask(url)) {
            Log.d(TAG, "startDownload: Task matches recent task in DB, skipping. URL: $url")
            activeUrls.remove(url)
            NotificationHelper.showDiagnosticNotification(appContext, "任务忽略", "该链接最近已下载过，请在历史页查看")
            return
        }

        // Check for storage permissions
        if (!hasStoragePermission(appContext)) {
            Log.e(TAG, "startDownload: Missing storage permissions!")
            activeUrls.remove(url)
            NotificationHelper.showDownloadNotification(
                appContext, 
                url.hashCode(), 
                "无法自动下载", 
                "缺少存储权限，请打开 App 授予权限", 
                false
            )
            return
        }

        scope.launch {
            val prepId = url.hashCode()
            var taskId: Long = -1 // Initialize with invalid ID
            try {
                NotificationHelper.showDownloadNotification(appContext, prepId, "正在准备下载...", url, true, showProgress = false)

                // 1. Get info
                // We run this inside runCatching because getMediaCount might throw or do network ops
                val mediaCount = runCatching { XHSDownloader(appContext).getMediaCount(url) }.getOrElse { 0 }
                // Default to IMAGE, update later if video detected
                val noteType = NoteType.IMAGE
                
                // 2. Create Task
                taskId = TaskManager.createTask(url, title, noteType, if (mediaCount > 0) mediaCount else 1)
                
                // Store job for cancellation
                activeJobs[taskId] = coroutineContext[Job]!!
                
                TaskManager.startTask(taskId)
                
                // 取消准备阶段的通知，替换为带 ID 的正式任务通知
                NotificationHelper.cancelNotification(appContext, prepId)
                NotificationHelper.showDownloadNotification(appContext, taskId.toInt(), "正在下载...", "共 $mediaCount 个文件", true)

                val completedFiles = java.util.concurrent.atomic.AtomicInteger(0)
                val failedFiles = java.util.concurrent.atomic.AtomicInteger(0)

                // 3. Setup Downloader
                val downloader = XHSDownloader(appContext, object : DownloadCallback {
                    override fun onFileDownloaded(filePath: String) {
                         val completed = completedFiles.incrementAndGet()
                         TaskManager.updateProgress(taskId, completed, failedFiles.get())
                         TaskManager.addFilePath(taskId, filePath)
                         // Update notification if needed
                    }

                    override fun onDownloadError(status: String, originalUrl: String) {
                        // Individual file error? 
                         // Not explicitly counted in current MainViewModel logic as "failed file" usually
                         // unless we track total vs completed. 
                         // For simplicity, we just log it.
                    }

                    override fun onDownloadProgress(status: String) {}
                    override fun onDownloadProgressUpdate(downloaded: Long, total: Long) {}
                    override fun onVideoDetected() {
                         // Update task type to VIDEO as we found real video content
                         TaskManager.updateTaskType(taskId, NoteType.VIDEO)
                    }
                })
                
                downloader.setShouldStopOnVideo(false)
                
                // 4. Download
                val success = downloader.downloadContent(url)
                
                // 5. Complete
                if (success) {
                    val completed = completedFiles.get()
                    // If mediaCount was 0 (unknown), use completed as total
                    val finalTotal = if (mediaCount == 0) completed else mediaCount
                    
                    if (completed > 0) {
                        TaskManager.completeTask(taskId, true)
                        NotificationHelper.showDownloadNotification(appContext, taskId.toInt(), "下载完成", "成功下载 $completed 个文件", false)
                    } else {
                        TaskManager.completeTask(taskId, false, "未下载任何文件")
                        NotificationHelper.showDownloadNotification(appContext, taskId.toInt(), "下载失败", "未能下载文件", false)
                    }
                } else {
                    TaskManager.completeTask(taskId, false, "下载过程出错")
                    NotificationHelper.showDownloadNotification(appContext, taskId.toInt(), "下载失败", "请检查网络或链接", false)
                }

            } catch (e: Exception) {
                if (e is CancellationException) {
                    Log.d(TAG, "Download cancelled for task $taskId")
                    // If task was created, mark it as failed due to cancellation
                    if (taskId != -1L) {
                        TaskManager.completeTask(taskId, false, "下载已取消")
                    }
                    NotificationHelper.showDownloadNotification(appContext, if (taskId != -1L) taskId.toInt() else prepId, "下载已取消", "任务已被用户停止", false)
                } else {
                    Log.e(TAG, "Download error for task $taskId", e)
                    // If task was created, fail it
                    if (taskId != -1L) {
                        TaskManager.completeTask(taskId, false, e.message ?: "未知错误")
                    }
                    NotificationHelper.showDownloadNotification(appContext, if (taskId != -1L) taskId.toInt() else prepId, "下载出错", e.message ?: "未知错误", false)
                }
            } finally {
               // Remove job from activeJobs map regardless of success or failure
                if (taskId != -1L) {
                    activeJobs.remove(taskId)
                }
                activeUrls.remove(url)
            }
        }
    }


    fun stopTask(taskId: Long) {
        val job = activeJobs.remove(taskId)
        if (job != null) {
            job.cancel()
            TaskManager.completeTask(taskId, false, "用户手动停止")
            Log.d(TAG, "Task $taskId stopped by user")
        }
    }

    private fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}

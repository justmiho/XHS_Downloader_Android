package com.neoruaa.xhsdn.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.neoruaa.xhsdn.MainActivity
import com.neoruaa.xhsdn.R

object NotificationHelper {
    private const val CHANNEL_ID = "xhs_download_channel_v2"
    private const val DIAGNOSTIC_CHANNEL_ID = "xhs_diagnostic_channel_v2"
    private const val DOWNLOAD_GROUP = "com.neoruaa.xhsdn.DOWNLOAD_GROUP"
    const val MONITOR_STATUS_ID = 1001 // 固定 ID，确保所有监控状态通知使用同一个槽位

    fun showDiagnosticNotification(context: Context, title: String, content: String, id: Int = MONITOR_STATUS_ID) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences("XHSDownloaderPrefs", Context.MODE_PRIVATE)
        val isDebugEnabled = prefs.getBoolean("debug_notification_enabled", false)

        if (!isDebugEnabled) {
            return
        }

        createDiagnosticChannel(appContext)
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(appContext, DIAGNOSTIC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("diagnostic")
            .setOnlyAlertOnce(false)
            .setDefaults(NotificationCompat.DEFAULT_ALL) // 确保使用系统默认的声音/震动

        // 强制 Pop-up 关键：先取消旧的，再发新的，强制系统视为新通知
        notificationManager.cancel(id)
        notificationManager.notify(id, builder.build())
    }

    fun showDownloadNotification(context: Context, id: Int, title: String, content: String, ongoing: Boolean, showProgress: Boolean = true) {
        val appContext = context.applicationContext
        createDownloadChannel(appContext)
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setGroup(DOWNLOAD_GROUP)

        if (ongoing && showProgress) {
            builder.setProgress(0, 0, true)
        }

        notificationManager.notify(id, builder.build())
    }

    fun cancelNotification(context: Context, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }

    private fun createDownloadChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "下载状态"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createDiagnosticChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "诊断调试"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(DIAGNOSTIC_CHANNEL_ID, name, importance)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

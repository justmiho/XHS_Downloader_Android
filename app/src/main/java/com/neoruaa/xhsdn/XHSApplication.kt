package com.neoruaa.xhsdn

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.accessibility.AccessibilityManager
import com.neoruaa.xhsdn.utils.NotificationHelper
import android.content.Context
import android.util.Log

class XHSApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initial setup only

    }

    companion object {
        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }
}

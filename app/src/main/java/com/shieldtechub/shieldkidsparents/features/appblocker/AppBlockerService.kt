package com.shieldtechub.shieldkidsparents.features.appblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

class AppBlockerService : AccessibilityService() {
    private val PREFS_NAME = "blocked_apps_prefs"
    private val BLOCKED_APPS_KEY = "blocked_apps"

    private fun getBlockedApps(): Set<String> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getStringSet(BLOCKED_APPS_KEY, emptySet()) ?: emptySet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (getBlockedApps().contains(packageName)) {
                // Block the app: show a toast and redirect to home
                Toast.makeText(this, "This app is blocked!", Toast.LENGTH_SHORT).show()
                val intent = Intent(Intent.ACTION_MAIN)
                intent.addCategory(Intent.CATEGORY_HOME)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {
        // Handle service interruption
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }
} 
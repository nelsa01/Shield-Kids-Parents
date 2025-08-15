package com.shieldtechhub.shieldkids.common.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

class SystemEventReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SystemEventReceiver"
        
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                // App installation/removal events
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
                
                // Screen events
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                
                // Boot events
                addAction(Intent.ACTION_BOOT_COMPLETED)
                addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
                
                // Time/date changes
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                handleAppInstalled(context, intent)
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                handleAppRemoved(context, intent)
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                handleAppUpdated(context, intent)
            }
            Intent.ACTION_SCREEN_ON -> {
                handleScreenOn(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                handleScreenOff(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                handleUserPresent(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                handleBootCompleted(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                handleAppUpdated(context, intent)
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                handleTimeChanged(context)
            }
        }
    }
    
    private fun handleAppInstalled(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        Log.d(TAG, "App installed: $packageName")
        
        // TODO: Notify app management service about new app
        // This would trigger re-scanning of installed apps
        packageName?.let {
            notifyAppInventoryChange(context, it, "INSTALLED")
        }
    }
    
    private fun handleAppRemoved(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        Log.d(TAG, "App removed: $packageName")
        
        packageName?.let {
            notifyAppInventoryChange(context, it, "REMOVED")
        }
    }
    
    private fun handleAppUpdated(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: context.packageName
        Log.d(TAG, "App updated: $packageName")
        
        if (packageName == context.packageName) {
            // Our app was updated, restart monitoring if needed
            restartMonitoringIfNeeded(context)
        } else {
            notifyAppInventoryChange(context, packageName, "UPDATED")
        }
    }
    
    private fun handleScreenOn(context: Context) {
        Log.d(TAG, "Screen turned on")
        // TODO: Record screen on event for screen time tracking
        recordScreenEvent(context, "SCREEN_ON")
    }
    
    private fun handleScreenOff(context: Context) {
        Log.d(TAG, "Screen turned off")
        // TODO: Record screen off event for screen time tracking
        recordScreenEvent(context, "SCREEN_OFF")
    }
    
    private fun handleUserPresent(context: Context) {
        Log.d(TAG, "User unlocked device")
        // TODO: Record unlock event and check for any pending restrictions
        recordScreenEvent(context, "USER_PRESENT")
    }
    
    private fun handleBootCompleted(context: Context) {
        Log.d(TAG, "Device boot completed")
        // TODO: Restart monitoring service and check device admin status
        restartMonitoringIfNeeded(context)
    }
    
    private fun handleTimeChanged(context: Context) {
        Log.d(TAG, "Time/date changed")
        // TODO: Update any time-based restrictions or schedules
        notifyTimeChanged(context)
    }
    
    private fun notifyAppInventoryChange(context: Context, packageName: String, action: String) {
        val intent = Intent("com.shieldtechhub.shieldkids.APP_INVENTORY_CHANGED").apply {
            putExtra("package_name", packageName)
            putExtra("action", action)
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }
    
    private fun recordScreenEvent(context: Context, event: String) {
        val intent = Intent("com.shieldtechhub.shieldkids.SCREEN_EVENT").apply {
            putExtra("event", event)
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }
    
    private fun restartMonitoringIfNeeded(context: Context) {
        val serviceManager = ServiceManager(context)
        if (!serviceManager.isServiceRunning(ShieldMonitoringService::class.java)) {
            serviceManager.startMonitoringService()
        }
    }
    
    private fun notifyTimeChanged(context: Context) {
        val intent = Intent("com.shieldtechhub.shieldkids.TIME_CHANGED").apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }
}
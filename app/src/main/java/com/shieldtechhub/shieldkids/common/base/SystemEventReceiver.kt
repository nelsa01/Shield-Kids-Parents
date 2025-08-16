package com.shieldtechhub.shieldkids.common.base

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.model.ViolationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        
        packageName?.let { pkg ->
            // Check if this is a system app installation or user app
            val isSystemApp = isSystemApp(context, pkg)
            
            if (!isSystemApp) {
                Log.i(TAG, "User app installed: $pkg")
                
                // Check if installation should be blocked
                val policyManager = PolicyEnforcementManager.getInstance(context)
                if (!policyManager.canInstallApp(pkg)) {
                    Log.w(TAG, "Installation blocked by policy: $pkg")
                    policyManager.blockAppInstallation(pkg, "Installation blocked by parental controls")
                    
                    // Attempt to uninstall immediately if possible
                    attemptUninstallBlockedApp(context, pkg)
                } else {
                    Log.d(TAG, "Installation allowed: $pkg")
                }
                
                // Send installation event to backend
                sendInstallationEventToBackend(context, pkg, "INSTALLED", isSystemApp)
            } else {
                Log.d(TAG, "System app installed/updated: $pkg")
            }
            
            // Always notify inventory change for tracking
            notifyAppInventoryChange(context, pkg, "INSTALLED")
        }
    }
    
    private fun handleAppRemoved(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        Log.d(TAG, "App removed: $packageName")
        
        packageName?.let { pkg ->
            // Check if someone tried to uninstall Shield Kids
            if (pkg == context.packageName) {
                Log.w(TAG, "Attempt to uninstall Shield Kids detected!")
                val policyManager = PolicyEnforcementManager.getInstance(context)
                policyManager.reportViolation(pkg, ViolationType.UNINSTALL_ATTEMPTED, 
                    "Attempt to uninstall Shield Kids parental control app")
                return@let
            }
            
            Log.i(TAG, "App removed: $pkg")
            
            // Send removal event to backend
            sendInstallationEventToBackend(context, pkg, "REMOVED", false)
            
            // Notify inventory change
            notifyAppInventoryChange(context, pkg, "REMOVED")
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
    
    // Helper methods for app installation monitoring
    private fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $packageName", e)
            false
        }
    }
    
    private fun attemptUninstallBlockedApp(context: Context, packageName: String) {
        try {
            Log.w(TAG, "Attempting to uninstall blocked app: $packageName")
            
            // This would require device admin privileges or system app status
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = android.net.Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            // Note: This will only work if the app has appropriate permissions
            context.startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall blocked app: $packageName", e)
            
            // Alternative: Show blocking overlay when app is launched
            val policyManager = PolicyEnforcementManager.getInstance(context)
            policyManager.reportViolation(packageName, ViolationType.INSTALLATION_BLOCKED,
                "Blocked app installation could not be removed automatically")
        }
    }
    
    private fun sendInstallationEventToBackend(context: Context, packageName: String, action: String, isSystemApp: Boolean) {
        // Use coroutine to send data to Firebase without blocking
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Sending installation event to backend: $packageName - $action")
                
                // Get app info for additional metadata
                val appInfo = getAppMetadata(context, packageName)
                
                val eventData = mapOf(
                    "packageName" to packageName,
                    "action" to action,
                    "isSystemApp" to isSystemApp,
                    "timestamp" to System.currentTimeMillis(),
                    "deviceId" to android.os.Build.ID,
                    "appName" to (appInfo?.get("name") ?: "Unknown"),
                    "appVersion" to (appInfo?.get("version") ?: "Unknown"),
                    "category" to (appInfo?.get("category") ?: "OTHER")
                )
                
                // Send to Firebase (placeholder implementation)
                sendEventToFirebase(eventData)
                
                // Also store locally for offline sync
                storeEventLocally(context, eventData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send installation event to backend", e)
            }
        }
    }
    
    private fun getAppMetadata(context: Context, packageName: String): Map<String, String>? {
        return try {
            val appInventoryManager = AppInventoryManager(context)
            val appInfo = appInventoryManager.getAppInfo(packageName)
            
            appInfo?.let {
                mapOf(
                    "name" to it.name,
                    "version" to it.version,
                    "category" to it.category.name
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get app metadata for: $packageName", e)
            null
        }
    }
    
    private fun sendEventToFirebase(eventData: Map<String, Any>) {
        // Placeholder for Firebase implementation
        Log.d(TAG, "Sending event to Firebase: $eventData")
        
        // This would integrate with Firebase Firestore to store the event
        // Example:
        // FirebaseFirestore.getInstance()
        //     .collection("installation_events")
        //     .add(eventData)
    }
    
    private fun storeEventLocally(context: Context, eventData: Map<String, Any>) {
        val prefs = context.getSharedPreferences("installation_events", Context.MODE_PRIVATE)
        val eventId = "event_${System.currentTimeMillis()}"
        
        // Store as JSON string
        val jsonString = org.json.JSONObject(eventData).toString()
        prefs.edit()
            .putString(eventId, jsonString)
            .apply()
        
        Log.d(TAG, "Stored installation event locally: $eventId")
    }
}
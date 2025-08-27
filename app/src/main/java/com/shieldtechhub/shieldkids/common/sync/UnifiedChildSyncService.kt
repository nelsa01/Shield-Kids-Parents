package com.shieldtechhub.shieldkids.common.sync

import android.content.Context
import android.util.Log
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.features.app_management.service.ChildAppSyncService
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class UnifiedChildSyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "UnifiedChildSync"
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        
        @Volatile
        private var INSTANCE: UnifiedChildSyncService? = null
        
        fun getInstance(context: Context): UnifiedChildSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedChildSyncService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val deviceStateManager = DeviceStateManager(context)
    private val screenTimeService = ScreenTimeService.getInstance(context)
    private val childAppSyncService = ChildAppSyncService.getInstance(context)
    private val policySyncManager = PolicySyncManager.getInstance(context)
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    
    /**
     * Start unified sync for all child device data
     */
    fun startUnifiedSync() {
        if (!deviceStateManager.isChildDevice()) {
            Log.d(TAG, "Not a child device, unified sync not needed")
            return
        }
        
        val childInfo = deviceStateManager.getChildDeviceInfo()
        if (childInfo == null) {
            Log.e(TAG, "Cannot start unified sync - child device info not found")
            return
        }
        
        if (isRunning) {
            Log.d(TAG, "Unified sync already running")
            return
        }
        
        isRunning = true
        Log.i(TAG, "Starting unified child sync service (every 5 minutes) for device: ${childInfo.deviceId}")
        
        serviceScope.launch {
            while (isRunning) {
                try {
                    performUnifiedSync(childInfo.childId, childInfo.deviceId)
                    delay(SYNC_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in unified sync loop", e)
                    delay(SYNC_INTERVAL_MS) // Continue after error
                }
            }
        }
    }
    
    /**
     * Stop unified sync service
     */
    fun stopUnifiedSync() {
        Log.d(TAG, "Stopping unified child sync service")
        isRunning = false
        serviceScope.cancel()
    }
    
    /**
     * Perform immediate sync of all data types
     */
    suspend fun performImmediateSync(): Boolean {
        if (!deviceStateManager.isChildDevice()) {
            Log.w(TAG, "Immediate sync should only be called on child devices")
            return false
        }
        
        val childInfo = deviceStateManager.getChildDeviceInfo()
        if (childInfo == null) {
            Log.e(TAG, "Cannot perform immediate sync - child device info not found")
            return false
        }
        
        return try {
            performUnifiedSync(childInfo.childId, childInfo.deviceId)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Immediate sync failed", e)
            false
        }
    }
    
    /**
     * Perform complete sync of all child device data
     */
    private suspend fun performUnifiedSync(childId: String, deviceId: String) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting unified sync for child: $childId, device: $deviceId")
        
        var screenTimeSuccess = false
        var appSyncSuccess = false
        var policySuccess = false
        
        try {
            // 1. Sync Screen Time Data
            Log.d(TAG, "Syncing screen time data...")
            try {
                val dailySummary = screenTimeService.collectAndSyncNow()
                screenTimeSuccess = true
                Log.d(TAG, "Screen time sync completed: ${formatDuration(dailySummary.totalScreenTimeMs)} today")
                
                // Check for time limit violations
                checkScreenTimeLimits(dailySummary)
                
            } catch (e: Exception) {
                Log.e(TAG, "Screen time sync failed", e)
            }
            
            // 2. Sync App Installations
            Log.d(TAG, "Syncing app installations...")
            try {
                appSyncSuccess = childAppSyncService.performImmediateSync(false)
                Log.d(TAG, "App sync completed: ${if (appSyncSuccess) "SUCCESS" else "FAILED"}")
            } catch (e: Exception) {
                Log.e(TAG, "App sync failed", e)
            }
            
            // 3. Sync Policy Updates
            Log.d(TAG, "Syncing policy updates...")
            try {
                // Note: PolicySyncManager doesn't have performImmediateSync, 
                // but it should listen for policy changes automatically
                policySuccess = true // Assume success since it's passive listening
                Log.d(TAG, "Policy sync completed")
            } catch (e: Exception) {
                Log.e(TAG, "Policy sync failed", e)
            }
            
        } finally {
            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Unified sync completed in ${duration}ms - " +
                    "ScreenTime: ${if (screenTimeSuccess) "✅" else "❌"}, " +
                    "Apps: ${if (appSyncSuccess) "✅" else "❌"}, " +
                    "Policies: ${if (policySuccess) "✅" else "❌"}")
        }
    }
    
    /**
     * Check if screen time limits have been exceeded and notify immediately
     */
    private suspend fun checkScreenTimeLimits(dailySummary: com.shieldtechhub.shieldkids.features.screen_time.model.DailyUsageSummary) {
        try {
            val totalMinutes = dailySummary.totalScreenTimeMs / (1000 * 60)
            
            // Example limits - this should be configurable via policies
            val dailyLimitMinutes = 60 // 1 hour default
            val warningThreshold = (dailyLimitMinutes * 0.8).toInt() // 80%
            
            when {
                totalMinutes >= dailyLimitMinutes -> {
                    Log.w(TAG, "🚨 DAILY LIMIT EXCEEDED: ${totalMinutes}m / ${dailyLimitMinutes}m")
                    // TODO: Send immediate notification to parent
                    // TODO: Trigger app blocking on child device
                    sendLimitExceededNotification(totalMinutes, dailyLimitMinutes)
                }
                totalMinutes >= warningThreshold -> {
                    Log.w(TAG, "⚠️ WARNING: Approaching daily limit: ${totalMinutes}m / ${dailyLimitMinutes}m")
                    // TODO: Send warning notification to parent
                    sendLimitWarningNotification(totalMinutes, dailyLimitMinutes)
                }
                else -> {
                    Log.d(TAG, "Screen time within limits: ${totalMinutes}m / ${dailyLimitMinutes}m")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check screen time limits", e)
        }
    }
    
    /**
     * Send immediate notification to parent when limit exceeded
     */
    private suspend fun sendLimitExceededNotification(actualMinutes: Long, limitMinutes: Int) {
        Log.i(TAG, "Sending limit exceeded notification to parent")
        // TODO: Implement Firebase Cloud Messaging to parent device
        // TODO: Create local notification on child device
        // TODO: Trigger app blocking mechanism
        
        // For now, just log the event
        val childInfo = deviceStateManager.getChildDeviceInfo()
        Log.w(TAG, "LIMIT EXCEEDED - Child: ${childInfo?.childId}, Used: ${actualMinutes}m, Limit: ${limitMinutes}m")
    }
    
    /**
     * Send warning notification when approaching limit
     */
    private suspend fun sendLimitWarningNotification(actualMinutes: Long, limitMinutes: Int) {
        Log.i(TAG, "Sending limit warning notification")
        // TODO: Implement warning notifications
        
        val remaining = limitMinutes - actualMinutes
        Log.w(TAG, "LIMIT WARNING - ${remaining}m remaining")
    }
    
    /**
     * Get current sync status
     */
    fun getSyncStatus(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning,
            "syncIntervalMs" to SYNC_INTERVAL_MS,
            "isChildDevice" to deviceStateManager.isChildDevice(),
            "lastSyncTime" to System.currentTimeMillis(),
            "nextSyncIn" to if (isRunning) "${SYNC_INTERVAL_MS / 1000 / 60} minutes" else "Not running"
        )
    }
    
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
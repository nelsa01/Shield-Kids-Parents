package com.shieldtechhub.shieldkids.features.app_management.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChildAppSyncService(private val context: Context) {
    
    companion object {
        private const val TAG = "ChildAppSyncService"
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        
        @Volatile
        private var INSTANCE: ChildAppSyncService? = null
        
        fun getInstance(context: Context): ChildAppSyncService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChildAppSyncService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val db = FirebaseFirestore.getInstance()
    private val appInventoryManager = AppInventoryManager(context)
    private val deviceStateManager = DeviceStateManager(context)
    private val screenTimeCollector = ScreenTimeCollector.getInstance(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var currentSyncStatus = SyncStatus.initial()
    private var lastInventoryFingerprint: InventoryFingerprint? = null
    
    // Sync status listeners
    private val syncStatusListeners = mutableListOf<(SyncStatus) -> Unit>()
    
    /**
     * Start periodic app inventory sync for child device
     */
    fun startSync() {
        if (isRunning) return
        
        // Only run on child devices
        if (!deviceStateManager.isChildDevice()) {
            Log.w(TAG, "App sync service should only run on child devices")
            return
        }
        
        val childInfo = deviceStateManager.getChildDeviceInfo()
        if (childInfo == null) {
            Log.e(TAG, "Cannot start sync - child device info not found")
            return
        }
        
        isRunning = true
        Log.i(TAG, "Starting child app sync service for device: ${childInfo.deviceId}")
        
        serviceScope.launch {
            while (isRunning) {
                try {
                    syncAppsToFirebase(childInfo.childId, childInfo.deviceId, false)
                    delay(SYNC_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in app sync loop", e)
                    delay(SYNC_INTERVAL_MS) // Continue after error
                }
            }
        }
    }
    
    /**
     * Stop the sync service
     */
    fun stopSync() {
        isRunning = false
        Log.i(TAG, "Stopping child app sync service")
    }
    
    /**
     * Add a sync status listener
     */
    fun addSyncStatusListener(listener: (SyncStatus) -> Unit) {
        syncStatusListeners.add(listener)
        // Immediately notify with current status
        listener(currentSyncStatus)
    }
    
    /**
     * Remove a sync status listener
     */
    fun removeSyncStatusListener(listener: (SyncStatus) -> Unit) {
        syncStatusListeners.remove(listener)
    }
    
    /**
     * Get current sync status
     */
    fun getCurrentSyncStatus(): SyncStatus = currentSyncStatus
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val network = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Update sync status and notify listeners
     */
    private fun updateSyncStatus(newStatus: SyncStatus) {
        currentSyncStatus = newStatus
        syncStatusListeners.forEach { listener ->
            try {
                listener(newStatus)
            } catch (e: Exception) {
                Log.w(TAG, "Error notifying sync status listener", e)
            }
        }
    }
    
    /**
     * Perform one-time sync (useful for immediate sync)
     */
    suspend fun performImmediateSync(forceFullSync: Boolean = false): Boolean {
        if (!deviceStateManager.isChildDevice()) {
            Log.w(TAG, "Immediate sync should only be called on child devices")
            return false
        }
        
        val childInfo = deviceStateManager.getChildDeviceInfo() ?: return false
        
        return try {
            syncAppsToFirebase(childInfo.childId, childInfo.deviceId, forceFullSync)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed immediate sync", e)
            false
        }
    }
    
    /**
     * Force a full sync, ignoring any cached state
     */
    suspend fun forceFullSync(): Boolean {
        Log.i(TAG, "Forcing full sync...")
        lastInventoryFingerprint = null // Clear cached state
        return performImmediateSync(forceFullSync = true)
    }
    
    /**
     * Sync current app inventory to Firebase
     */
    private suspend fun syncAppsToFirebase(childId: String, deviceId: String, forceFullSync: Boolean = false) {
        val syncId = UUID.randomUUID().toString()
        try {
            Log.d(TAG, "Starting app inventory and screen time sync for child: $childId, device: $deviceId")
            
            // Check network connectivity
            if (!isNetworkAvailable()) {
                updateSyncStatus(currentSyncStatus.withNetworkUnavailable())
                Log.w(TAG, "No network connection available for sync")
                return
            }
            
            // Get current app inventory
            val apps = appInventoryManager.getAllInstalledApps()
            val appInventoryResult = appInventoryManager.refreshAppInventory()
            
            // Generate current inventory fingerprint
            val currentFingerprint = AppInventoryHashUtil.generateInventoryFingerprint(apps)
            
            // Check if sync is needed (unless forced)
            if (!forceFullSync && lastInventoryFingerprint != null) {
                val lastFingerprint = lastInventoryFingerprint!!
                if (currentFingerprint.matches(lastFingerprint)) {
                    Log.d(TAG, "No changes detected, skipping sync")
                    updateSyncStatus(currentSyncStatus.withSyncSuccess(currentFingerprint.fullHash, apps.size))
                    return
                }
                Log.i(TAG, "Changes detected: ${currentFingerprint.getChangeSummary(lastFingerprint)}")
            }
            
            // Start sync process
            updateSyncStatus(
                currentSyncStatus.copy(syncId = syncId)
                    .withSyncStarted(isFullSync = forceFullSync || lastInventoryFingerprint == null, totalApps = apps.size)
            )
            
            // Get current screen time data
            val todayUsage = screenTimeCollector.collectDailyUsageData()
            val yesterdayUsage = screenTimeCollector.collectDailyUsageData(
                java.util.Calendar.getInstance().apply { 
                    add(java.util.Calendar.DAY_OF_MONTH, -1) 
                }.time
            )
            
            // Update progress
            updateSyncStatus(currentSyncStatus.withProgress(apps.size / 4)) // 25% progress
            
            // Create sync data
            val syncData = mapOf(
                "apps" to apps.map { app ->
                    mapOf(
                        "packageName" to app.packageName,
                        "name" to app.name,
                        "version" to app.version,
                        "versionCode" to app.versionCode,
                        "category" to app.category.name,
                        "isSystemApp" to app.isSystemApp,
                        "isEnabled" to app.isEnabled,
                        "installTime" to app.installTime,
                        "lastUpdateTime" to app.lastUpdateTime,
                        "targetSdkVersion" to app.targetSdkVersion,
                        "permissions" to app.permissions
                    )
                },
                "summary" to mapOf(
                    "totalApps" to appInventoryResult.totalApps,
                    "userApps" to appInventoryResult.userApps,
                    "systemApps" to appInventoryResult.systemApps,
                    "categoryBreakdown" to appInventoryResult.categories.mapKeys { (key, _) -> key.name },
                    "lastSyncTime" to System.currentTimeMillis(),
                    "scanTimeMs" to appInventoryResult.scanTimeMs
                ),
                "inventoryFingerprint" to currentFingerprint.toFirebaseMap(),
                "syncStatus" to currentSyncStatus.copy(syncId = syncId).toFirebaseMap(),
                "screenTime" to mapOf(
                    "today" to mapOf(
                        "date" to todayUsage.date.time,
                        "totalScreenTimeMs" to todayUsage.totalScreenTimeMs,
                        "totalForegroundTimeMs" to todayUsage.totalForegroundTimeMs,
                        "screenUnlocks" to todayUsage.screenUnlocks,
                        "firstUsageTime" to todayUsage.firstUsageTime,
                        "lastUsageTime" to todayUsage.lastUsageTime,
                        "topApps" to todayUsage.getTopApps(5).map { appUsage ->
                            mapOf(
                                "packageName" to appUsage.packageName,
                                "appName" to appUsage.appName,
                                "category" to appUsage.category,
                                "totalTimeMs" to appUsage.totalTimeMs,
                                "launchCount" to appUsage.launchCount
                            )
                        },
                        "categoryBreakdown" to todayUsage.getUsageByCategory()
                    ),
                    "yesterday" to mapOf(
                        "date" to yesterdayUsage.date.time,
                        "totalScreenTimeMs" to yesterdayUsage.totalScreenTimeMs,
                        "totalForegroundTimeMs" to yesterdayUsage.totalForegroundTimeMs,
                        "topApps" to yesterdayUsage.getTopApps(3).map { appUsage ->
                            mapOf(
                                "packageName" to appUsage.packageName,
                                "appName" to appUsage.appName,
                                "totalTimeMs" to appUsage.totalTimeMs
                            )
                        }
                    ),
                    "lastSyncTime" to System.currentTimeMillis()
                )
            )
            
            // Update progress
            updateSyncStatus(currentSyncStatus.withProgress(apps.size * 3 / 4)) // 75% progress
            
            // Upload to Firebase using device document ID - ensure device_ prefix
            val deviceDocId = if (deviceId.startsWith("device_")) deviceId else "device_$deviceId"
            val docRef = db.collection("children")
                .document(childId)
                .collection("devices")
                .document(deviceDocId)
                .collection("data")
                .document("appInventory")
            
            docRef.set(syncData).await()
            
            Log.i(TAG, "Successfully synced ${apps.size} apps and screen time data to Firebase")
            
            // Update progress to completion
            updateSyncStatus(currentSyncStatus.withProgress(apps.size))
            
            // Mark sync as successful
            val successStatus = currentSyncStatus.withSyncSuccess(currentFingerprint.fullHash, apps.size)
            updateSyncStatus(successStatus)
            
            // Update cached fingerprint
            lastInventoryFingerprint = currentFingerprint
            
            // Update last sync timestamp in device info
            updateLastSyncTimestamp(childId, deviceId, successStatus)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync apps to Firebase", e)
            
            // Handle sync failure with retry logic
            val failedStatus = currentSyncStatus.withSyncFailure(e.message ?: "Unknown error")
            
            if (failedStatus.canRetry()) {
                val retryStatus = failedStatus.withIncrementedRetry()
                updateSyncStatus(retryStatus)
                Log.i(TAG, "Scheduling sync retry (${retryStatus.retryAttempts}/${retryStatus.maxRetryAttempts})")
                
                // Schedule retry after delay
                delay(30000L * retryStatus.retryAttempts) // Exponential backoff
                syncAppsToFirebase(childId, deviceId, forceFullSync)
            } else {
                updateSyncStatus(failedStatus)
                Log.e(TAG, "Max retry attempts reached, giving up on sync")
            }
            
            throw e
        }
    }
    
    /**
     * Update the last sync timestamp in device info
     */
    private suspend fun updateLastSyncTimestamp(childId: String, deviceId: String, syncStatus: SyncStatus) {
        try {
            val deviceDocId = if (deviceId.startsWith("device_")) deviceId else "device_$deviceId"
            val deviceRef = db.collection("children")
                .document(childId)
                .collection("devices")
                .document(deviceDocId)
            
            deviceRef.update(
                mapOf(
                    "lastAppSyncTime" to syncStatus.lastSuccessfulSyncTime,
                    "appSyncStatus" to syncStatus.status.name,
                    "syncDurationMs" to syncStatus.syncDurationMs,
                    "totalAppsCount" to syncStatus.totalAppsCount,
                    "appInventoryHash" to syncStatus.appInventoryHash,
                    "lastFullSyncTime" to if (syncStatus.isFullSync) syncStatus.lastSuccessfulSyncTime else null
                )
            ).await()
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update sync timestamp", e)
        }
    }
    
    /**
     * Handle app installation/uninstallation events
     */
    fun onAppChanged(packageName: String, action: String) {
        Log.d(TAG, "App changed: $packageName, action: $action")
        
        // Trigger immediate sync for app changes
        serviceScope.launch {
            try {
                delay(2000) // Wait a bit for system to settle
                performImmediateSync()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync after app change", e)
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        stopSync()
        serviceScope.cancel()
        INSTANCE = null
    }
}
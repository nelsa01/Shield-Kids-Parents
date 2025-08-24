package com.shieldtechhub.shieldkids.features.app_management.service

import java.util.Date

/**
 * Represents the sync status of app inventory between child device and Firebase
 */
data class SyncStatus(
    val syncId: String = "",
    val status: SyncState = SyncState.IDLE,
    val lastSyncStartTime: Long = 0L,
    val lastSyncCompleteTime: Long = 0L,
    val lastSuccessfulSyncTime: Long = 0L,
    val appInventoryHash: String = "",
    val totalAppsCount: Int = 0,
    val syncedAppsCount: Int = 0,
    val errorMessage: String? = null,
    val retryAttempts: Int = 0,
    val maxRetryAttempts: Int = 3,
    val syncDurationMs: Long = 0L,
    val networkConnected: Boolean = true,
    val isFullSync: Boolean = true
) {
    
    /**
     * Check if sync is currently in progress
     */
    fun isInProgress(): Boolean = status == SyncState.IN_PROGRESS
    
    /**
     * Check if sync completed successfully
     */
    fun isSuccessful(): Boolean = status == SyncState.SUCCESS
    
    /**
     * Check if sync failed
     */
    fun hasFailed(): Boolean = status == SyncState.FAILED
    
    /**
     * Check if sync data is stale (older than threshold)
     */
    fun isStale(thresholdMinutes: Int = 10): Boolean {
        val thresholdMs = thresholdMinutes * 60 * 1000L
        return System.currentTimeMillis() - lastSuccessfulSyncTime > thresholdMs
    }
    
    /**
     * Check if all apps are synced
     */
    fun isFullySynced(): Boolean = 
        isSuccessful() && totalAppsCount > 0 && syncedAppsCount == totalAppsCount
    
    /**
     * Get sync progress percentage (0-100)
     */
    fun getProgressPercentage(): Int {
        return if (totalAppsCount == 0) 0
        else ((syncedAppsCount.toFloat() / totalAppsCount) * 100).toInt()
    }
    
    /**
     * Get human readable status message
     */
    fun getStatusMessage(): String {
        return when (status) {
            SyncState.IDLE -> "Ready to sync"
            SyncState.IN_PROGRESS -> "Syncing apps... ($syncedAppsCount/$totalAppsCount)"
            SyncState.SUCCESS -> "All apps synced successfully"
            SyncState.FAILED -> errorMessage ?: "Sync failed"
            SyncState.RETRYING -> "Retrying sync... (${retryAttempts}/$maxRetryAttempts)"
            SyncState.NO_NETWORK -> "Waiting for network connection"
        }
    }
    
    /**
     * Get last sync time as Date object
     */
    fun getLastSyncDate(): Date? {
        return if (lastSuccessfulSyncTime > 0) Date(lastSuccessfulSyncTime) else null
    }
    
    /**
     * Check if retry is possible
     */
    fun canRetry(): Boolean = retryAttempts < maxRetryAttempts && hasFailed()
    
    /**
     * Create a copy with updated retry attempt
     */
    fun withIncrementedRetry(): SyncStatus = 
        copy(retryAttempts = retryAttempts + 1, status = SyncState.RETRYING)
    
    /**
     * Create a copy marking sync as started
     */
    fun withSyncStarted(isFullSync: Boolean = true, totalApps: Int = 0): SyncStatus = copy(
        status = SyncState.IN_PROGRESS,
        lastSyncStartTime = System.currentTimeMillis(),
        isFullSync = isFullSync,
        totalAppsCount = totalApps,
        syncedAppsCount = 0,
        errorMessage = null
    )
    
    /**
     * Create a copy with progress update
     */
    fun withProgress(syncedApps: Int): SyncStatus = copy(
        syncedAppsCount = syncedApps
    )
    
    /**
     * Create a copy marking sync as successful
     */
    fun withSyncSuccess(appHash: String, totalApps: Int): SyncStatus = copy(
        status = SyncState.SUCCESS,
        lastSyncCompleteTime = System.currentTimeMillis(),
        lastSuccessfulSyncTime = System.currentTimeMillis(),
        appInventoryHash = appHash,
        totalAppsCount = totalApps,
        syncedAppsCount = totalApps,
        syncDurationMs = System.currentTimeMillis() - lastSyncStartTime,
        errorMessage = null,
        retryAttempts = 0
    )
    
    /**
     * Create a copy marking sync as failed
     */
    fun withSyncFailure(error: String): SyncStatus = copy(
        status = SyncState.FAILED,
        lastSyncCompleteTime = System.currentTimeMillis(),
        syncDurationMs = System.currentTimeMillis() - lastSyncStartTime,
        errorMessage = error
    )
    
    /**
     * Create a copy marking network unavailable
     */
    fun withNetworkUnavailable(): SyncStatus = copy(
        status = SyncState.NO_NETWORK,
        networkConnected = false
    )
    
    /**
     * Convert to Map for Firebase storage
     */
    fun toFirebaseMap(): Map<String, Any> = mapOf(
        "syncId" to syncId,
        "status" to status.name,
        "lastSyncStartTime" to lastSyncStartTime,
        "lastSyncCompleteTime" to lastSyncCompleteTime,
        "lastSuccessfulSyncTime" to lastSuccessfulSyncTime,
        "appInventoryHash" to appInventoryHash,
        "totalAppsCount" to totalAppsCount,
        "syncedAppsCount" to syncedAppsCount,
        "errorMessage" to (errorMessage ?: ""),
        "retryAttempts" to retryAttempts,
        "maxRetryAttempts" to maxRetryAttempts,
        "syncDurationMs" to syncDurationMs,
        "networkConnected" to networkConnected,
        "isFullSync" to isFullSync
    )
    
    companion object {
        /**
         * Create SyncStatus from Firebase Map
         */
        fun fromFirebaseMap(data: Map<String, Any>): SyncStatus = SyncStatus(
            syncId = data["syncId"] as? String ?: "",
            status = try { 
                SyncState.valueOf(data["status"] as? String ?: "IDLE") 
            } catch (e: Exception) { SyncState.IDLE },
            lastSyncStartTime = (data["lastSyncStartTime"] as? Number)?.toLong() ?: 0L,
            lastSyncCompleteTime = (data["lastSyncCompleteTime"] as? Number)?.toLong() ?: 0L,
            lastSuccessfulSyncTime = (data["lastSuccessfulSyncTime"] as? Number)?.toLong() ?: 0L,
            appInventoryHash = data["appInventoryHash"] as? String ?: "",
            totalAppsCount = (data["totalAppsCount"] as? Number)?.toInt() ?: 0,
            syncedAppsCount = (data["syncedAppsCount"] as? Number)?.toInt() ?: 0,
            errorMessage = (data["errorMessage"] as? String)?.takeIf { it.isNotEmpty() },
            retryAttempts = (data["retryAttempts"] as? Number)?.toInt() ?: 0,
            maxRetryAttempts = (data["maxRetryAttempts"] as? Number)?.toInt() ?: 3,
            syncDurationMs = (data["syncDurationMs"] as? Number)?.toLong() ?: 0L,
            networkConnected = data["networkConnected"] as? Boolean ?: true,
            isFullSync = data["isFullSync"] as? Boolean ?: true
        )
        
        /**
         * Create initial/default sync status
         */
        fun initial(): SyncStatus = SyncStatus(
            syncId = java.util.UUID.randomUUID().toString(),
            status = SyncState.IDLE
        )
    }
}

/**
 * Enum representing different sync states
 */
enum class SyncState {
    IDLE,           // Not syncing, ready to start
    IN_PROGRESS,    // Currently syncing
    SUCCESS,        // Last sync completed successfully
    FAILED,         // Last sync failed
    RETRYING,       // Retrying after failure
    NO_NETWORK      // Waiting for network connection
}
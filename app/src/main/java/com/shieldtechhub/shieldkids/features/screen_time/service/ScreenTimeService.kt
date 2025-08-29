package com.shieldtechhub.shieldkids.features.screen_time.service

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.features.screen_time.model.DailyUsageSummary
import com.shieldtechhub.shieldkids.features.screen_time.model.WeeklyUsageSummary
import com.shieldtechhub.shieldkids.features.screen_time.workers.ScreenTimeCollectionWorker
import kotlinx.coroutines.tasks.await
import java.util.*

class ScreenTimeService(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenTimeService"
        private const val COLLECTION_CHILDREN = "children"
        private const val COLLECTION_DEVICES = "devices"
        private const val COLLECTION_DATA = "data"
        
        @Volatile
        private var INSTANCE: ScreenTimeService? = null
        
        fun getInstance(context: Context): ScreenTimeService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenTimeService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val db = FirebaseFirestore.getInstance()
    private val deviceStateManager = DeviceStateManager(context)
    private val screenTimeCollector = ScreenTimeCollector.getInstance(context)
    
    /**
     * Start collecting screen time data in the background
     */
    fun startScreenTimeCollection() {
        Log.d(TAG, "⚠️ DEPRECATED: startScreenTimeCollection() - Use UnifiedChildSyncService instead")
        
        if (!deviceStateManager.isChildDevice()) {
            Log.d(TAG, "Not a child device, screen time collection not needed")
            return
        }
        
        // NOTE: This method is now deprecated in favor of UnifiedChildSyncService
        // which handles all child data sync every 5 minutes instead of 6 hours
        Log.w(TAG, "Screen time collection is now handled by UnifiedChildSyncService")
    }
    
    /**
     * Stop collecting screen time data
     */
    fun stopScreenTimeCollection() {
        Log.d(TAG, "Stopping screen time collection service")
        ScreenTimeCollectionWorker.stopPeriodicCollection(context)
    }
    
    /**
     * Collect and sync current usage data immediately
     */
    suspend fun collectAndSyncNow(): DailyUsageSummary {
        Log.d(TAG, "Collecting and syncing screen time data immediately")
        
        val today = Date()
        val dailySummary = screenTimeCollector.collectDailyUsageData(today)
        
        // Send to backend
        sendDailySummaryToFirebase(dailySummary)
        
        return dailySummary
    }
    
    /**
     * Send daily usage summary to Firebase
     */
    suspend fun sendDailySummaryToFirebase(summary: DailyUsageSummary): Boolean {
        return try {
            val childInfo = deviceStateManager.getChildDeviceInfo()
                ?: return false.also { Log.w(TAG, "No child device info found") }
            
            val documentId = "screen_time_${formatDateKey(summary.date)}"
            
            // Use DeviceStateManager's device ID format for consistency with app inventory
            val deviceStateManager = DeviceStateManager(context)
            val standardizedDeviceId = deviceStateManager.getDeviceId()
            
            val data = mapOf(
                "type" to "screen_time",
                "date" to summary.date.time,
                "deviceId" to standardizedDeviceId,
                "childId" to childInfo.childId,
                "totalScreenTimeMs" to summary.totalScreenTimeMs,
                "totalForegroundTimeMs" to summary.totalForegroundTimeMs,
                "screenUnlocks" to summary.screenUnlocks,
                "firstUsageTime" to summary.firstUsageTime,
                "lastUsageTime" to summary.lastUsageTime,
                "appCount" to summary.appUsageData.size,
                "topApps" to summary.getTopApps(5).map { app ->
                    mapOf(
                        "packageName" to app.packageName,
                        "appName" to app.appName,
                        "category" to app.category,
                        "totalTimeMs" to app.totalTimeMs,
                        "launchCount" to app.launchCount,
                        "averageSessionMs" to app.averageSessionDuration
                    )
                },
                "allAppsData" to summary.appUsageData.map { app ->
                    mapOf(
                        "packageName" to app.packageName,
                        "appName" to app.appName,
                        "category" to app.category,
                        "totalTimeMs" to app.totalTimeMs,
                        "foregroundTimeMs" to app.foregroundTimeMs,
                        "launchCount" to app.launchCount,
                        "lastUsedTime" to app.lastUsedTime,
                        "firstUsedTime" to app.firstUsedTime,
                        "averageSessionMs" to app.averageSessionDuration,
                        "sessionCount" to app.sessions.size
                    )
                },
                "longestSession" to summary.longestSession?.let { session ->
                    mapOf(
                        "packageName" to session.packageName,
                        "startTime" to session.startTime,
                        "endTime" to session.endTime,
                        "durationMs" to session.durationMs
                    )
                },
                "categoryBreakdown" to summary.getUsageByCategory(),
                "createdAt" to System.currentTimeMillis(),
                "version" to "1.0"
            )
            
            db.collection(COLLECTION_CHILDREN)
                .document(childInfo.childId)
                .collection(COLLECTION_DEVICES)
                .document(standardizedDeviceId)
                .collection(COLLECTION_DATA)
                .document(documentId)
                .set(data)
                .await()
            
            Log.d(TAG, "Successfully sent daily summary to Firebase: ${formatDateKey(summary.date)}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send daily summary to Firebase", e)
            false
        }
    }
    
    /**
     * Send weekly usage summary to Firebase
     */
    suspend fun sendWeeklySummaryToFirebase(summary: WeeklyUsageSummary): Boolean {
        return try {
            val childInfo = deviceStateManager.getChildDeviceInfo()
                ?: return false.also { Log.w(TAG, "No child device info found") }
            
            val documentId = "screen_time_weekly_${formatWeekKey(summary.weekStartDate)}"
            
            // Use DeviceStateManager's device ID format for consistency with app inventory
            val deviceStateManager = DeviceStateManager(context)
            val standardizedDeviceId = deviceStateManager.getDeviceId()
            
            val data = mapOf(
                "type" to "screen_time_weekly",
                "weekStartDate" to summary.weekStartDate.time,
                "deviceId" to standardizedDeviceId,
                "childId" to childInfo.childId,
                "totalWeekScreenTimeMs" to summary.totalWeekScreenTime,
                "averageDailyScreenTimeMs" to summary.averageDailyScreenTime,
                "daysWithData" to summary.dailySummaries.size,
                "topAppsOfWeek" to summary.topAppsOfWeek.map { app ->
                    mapOf(
                        "packageName" to app.packageName,
                        "appName" to app.appName,
                        "category" to app.category,
                        "totalTimeMs" to app.totalTimeMs,
                        "launchCount" to app.launchCount
                    )
                },
                "weeklyUsageByCategory" to summary.getWeeklyUsageByCategory(),
                "mostActiveDay" to summary.mostActiveDay?.let { day ->
                    mapOf(
                        "date" to day.date.time,
                        "totalScreenTimeMs" to day.totalScreenTimeMs
                    )
                },
                "leastActiveDay" to summary.leastActiveDay?.let { day ->
                    mapOf(
                        "date" to day.date.time,
                        "totalScreenTimeMs" to day.totalScreenTimeMs
                    )
                },
                "dailyBreakdown" to summary.dailySummaries.map { daily ->
                    mapOf(
                        "date" to daily.date.time,
                        "totalScreenTimeMs" to daily.totalScreenTimeMs,
                        "appCount" to daily.appUsageData.size,
                        "screenUnlocks" to daily.screenUnlocks
                    )
                },
                "weekRange" to summary.getWeekRange(),
                "createdAt" to System.currentTimeMillis(),
                "version" to "1.0"
            )
            
            db.collection(COLLECTION_CHILDREN)
                .document(childInfo.childId)
                .collection(COLLECTION_DEVICES)
                .document(standardizedDeviceId)
                .collection(COLLECTION_DATA)
                .document(documentId)
                .set(data)
                .await()
            
            Log.d(TAG, "Successfully sent weekly summary to Firebase: ${formatWeekKey(summary.weekStartDate)}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send weekly summary to Firebase", e)
            false
        }
    }
    
    /**
     * Ensure device ID has proper format for Firebase queries
     */
    private fun normalizeDeviceId(deviceId: String): String {
        return if (deviceId.startsWith("device_")) {
            deviceId // Already has prefix
        } else {
            "device_$deviceId" // Add prefix
        }
    }

    /**
     * Retrieve screen time data from app inventory document (current system)
     */
    suspend fun getScreenTimeFromAppInventory(childId: String, deviceId: String): Map<String, Any>? {
        return try {
            val normalizedDeviceId = normalizeDeviceId(deviceId)
            Log.d(TAG, "🔍 Retrieving screen time from app inventory:")
            Log.d(TAG, "   📍 Original device ID: '$deviceId'")
            Log.d(TAG, "   📍 Normalized device ID: '$normalizedDeviceId'")
            Log.d(TAG, "   📍 Path: children/$childId/devices/$normalizedDeviceId/data/appInventory")
            
            val snapshot = db.collection(COLLECTION_CHILDREN)
                .document(childId)
                .collection(COLLECTION_DEVICES)
                .document(normalizedDeviceId)
                .collection(COLLECTION_DATA)
                .document("appInventory")
                .get()
                .await()
            
            if (snapshot.exists()) {
                val data = snapshot.data
                val screenTimeData = data?.get("screenTime") as? Map<String, Any>
                val todayData = screenTimeData?.get("today") as? Map<String, Any>
                
                if (todayData != null) {
                    Log.d(TAG, "✅ Found screen time data in app inventory")
                    Log.d(TAG, "   🕐 Total screen time: ${todayData["totalScreenTimeMs"]} ms")
                    Log.d(TAG, "   📱 Screen unlocks: ${todayData["screenUnlocks"]}")
                    Log.d(TAG, "   📄 Top apps: ${(todayData["topApps"] as? List<*>)?.size}")
                    todayData
                } else {
                    Log.w(TAG, "❌ No screen time data found in app inventory")
                    null
                }
            } else {
                Log.w(TAG, "❌ App inventory document not found")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to retrieve screen time from app inventory", e)
            null
        }
    }

    /**
     * Retrieve daily usage data from Firebase for a specific child and device (for parents)
     */
    suspend fun getDailyUsageFromFirebase(date: Date, childId: String, deviceId: String): Map<String, Any>? {
        val normalizedDeviceId = normalizeDeviceId(deviceId)
        return try {
            val documentId = "screen_time_${formatDateKey(date)}"
            val fullPath = "children/$childId/devices/$normalizedDeviceId/data/$documentId"
            
            Log.d(TAG, "🔍 Attempting to retrieve screen time data from Firebase:")
            Log.d(TAG, "   📍 Original device ID: '$deviceId'")
            Log.d(TAG, "   📍 Normalized device ID: '$normalizedDeviceId'")
            Log.d(TAG, "   📍 Full path: $fullPath")
            Log.d(TAG, "   📅 Date: ${formatDateKey(date)}")
            Log.d(TAG, "   👶 Child ID: $childId")
            
            val snapshot = db.collection(COLLECTION_CHILDREN)
                .document(childId)
                .collection(COLLECTION_DEVICES)
                .document(normalizedDeviceId)
                .collection(COLLECTION_DATA)
                .document(documentId)
                .get()
                .await()
            
            if (snapshot.exists()) {
                val data = snapshot.data
                Log.d(TAG, "✅ Successfully retrieved screen time data from Firebase")
                Log.d(TAG, "   📊 Data keys: ${data?.keys}")
                Log.d(TAG, "   🕐 Total screen time: ${data?.get("totalScreenTimeMs")} ms")
                Log.d(TAG, "   📱 Screen unlocks: ${data?.get("screenUnlocks")}")
                Log.d(TAG, "   📲 App count: ${data?.get("appCount")}")
                data
            } else {
                Log.w(TAG, "❌ No screen time document found at path: $fullPath")
                
                // Let's also check if the device document exists
                val deviceSnapshot = db.collection(COLLECTION_CHILDREN)
                    .document(childId)
                    .collection(COLLECTION_DEVICES)
                    .document(normalizedDeviceId)
                    .get()
                    .await()
                    
                if (deviceSnapshot.exists()) {
                    Log.w(TAG, "📱 Device document exists, but no screen time data for today")
                    
                    // List all data documents to see what's available
                    val dataCollectionSnapshot = db.collection(COLLECTION_CHILDREN)
                        .document(childId)
                        .collection(COLLECTION_DEVICES)
                        .document(normalizedDeviceId)
                        .collection(COLLECTION_DATA)
                        .get()
                        .await()
                        
                    val availableDocs = dataCollectionSnapshot.documents.map { it.id }
                    Log.w(TAG, "📄 Available data documents: $availableDocs")
                } else {
                    Log.w(TAG, "❌ Device document does not exist at: children/$childId/devices/$normalizedDeviceId")
                }
                
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "💥 Failed to retrieve screen time data from Firebase", e)
            Log.e(TAG, "   📍 Attempted path: children/$childId/devices/$normalizedDeviceId/data/screen_time_${formatDateKey(date)}")
            null
        }
    }

    /**
     * Retrieve daily usage data from Firebase for a specific date (for child devices)
     */
    suspend fun getDailyUsageFromFirebase(date: Date, deviceId: String): Map<String, Any>? {
        return try {
            val childInfo = deviceStateManager.getChildDeviceInfo()
                ?: return null.also { Log.w(TAG, "No child device info found") }
            
            val documentId = "screen_time_${formatDateKey(date)}"
            
            val snapshot = db.collection(COLLECTION_CHILDREN)
                .document(childInfo.childId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_DATA)
                .document(documentId)
                .get()
                .await()
            
            if (snapshot.exists()) {
                Log.d(TAG, "Retrieved daily usage data from Firebase: ${formatDateKey(date)}")
                snapshot.data
            } else {
                Log.d(TAG, "No daily usage data found in Firebase for: ${formatDateKey(date)}")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve daily usage data from Firebase", e)
            null
        }
    }
    
    /**
     * Retrieve weekly usage data from Firebase
     */
    suspend fun getWeeklyUsageFromFirebase(weekStartDate: Date, deviceId: String): Map<String, Any>? {
        return try {
            val childInfo = deviceStateManager.getChildDeviceInfo()
                ?: return null.also { Log.w(TAG, "No child device info found") }
            
            val documentId = "screen_time_weekly_${formatWeekKey(weekStartDate)}"
            
            val snapshot = db.collection(COLLECTION_CHILDREN)
                .document(childInfo.childId)
                .collection(COLLECTION_DEVICES)
                .document(deviceId)
                .collection(COLLECTION_DATA)
                .document(documentId)
                .get()
                .await()
            
            if (snapshot.exists()) {
                Log.d(TAG, "Retrieved weekly usage data from Firebase: ${formatWeekKey(weekStartDate)}")
                snapshot.data
            } else {
                Log.d(TAG, "No weekly usage data found in Firebase for: ${formatWeekKey(weekStartDate)}")
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve weekly usage data from Firebase", e)
            null
        }
    }
    
    /**
     * Get usage statistics for parent dashboard
     */
    suspend fun getUsageStatsForParent(childId: String, deviceId: String, days: Int = 7): List<Map<String, Any>> {
        return try {
            val usageStats = mutableListOf<Map<String, Any>>()
            val calendar = Calendar.getInstance()
            
            repeat(days) { dayOffset ->
                calendar.add(Calendar.DAY_OF_MONTH, -dayOffset)
                val date = calendar.time
                
                val documentId = "screen_time_${formatDateKey(date)}"
                
                val snapshot = db.collection(COLLECTION_CHILDREN)
                    .document(childId)
                    .collection(COLLECTION_DEVICES)
                    .document(deviceId)
                    .collection(COLLECTION_DATA)
                    .document(documentId)
                    .get()
                    .await()
                
                if (snapshot.exists()) {
                    snapshot.data?.let { usageStats.add(it) }
                }
                
                calendar.time = Date() // Reset to today
            }
            
            Log.d(TAG, "Retrieved ${usageStats.size} days of usage stats for parent")
            usageStats.sortedByDescending { it["date"] as Long }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get usage stats for parent", e)
            emptyList()
        }
    }
    
    private fun formatDateKey(date: Date): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(date)
    }
    
    private fun formatWeekKey(weekStartDate: Date): String {
        val formatter = java.text.SimpleDateFormat("yyyy-'W'ww", Locale.getDefault())
        return formatter.format(weekStartDate)
    }
}
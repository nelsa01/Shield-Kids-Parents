package com.shieldtechhub.shieldkids.features.screen_time.service

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.screen_time.model.AppUsageData
import com.shieldtechhub.shieldkids.features.screen_time.model.DailyUsageSummary
import com.shieldtechhub.shieldkids.features.screen_time.model.ScreenTimeSession
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

class ScreenTimeCollector(private val context: Context) {
    
    companion object {
        private const val TAG = "ScreenTimeCollector"
        
        @Volatile
        private var INSTANCE: ScreenTimeCollector? = null
        
        fun getInstance(context: Context): ScreenTimeCollector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenTimeCollector(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val appInventoryManager = AppInventoryManager(context)
    private val deviceStateManager = DeviceStateManager(context)
    private val prefs = context.getSharedPreferences("screen_time_data", Context.MODE_PRIVATE)
    
    // Main collection methods
    suspend fun collectDailyUsageData(date: Date = Date()): DailyUsageSummary = withContext(Dispatchers.IO) {
        Log.d(TAG, "Collecting daily usage data for: ${formatDate(date)}")
        
        val startOfDay = getStartOfDay(date)
        val endOfDay = getEndOfDay(date)
        
        try {
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfDay,
                endOfDay
            )
            
            val appUsageList = mutableListOf<AppUsageData>()
            var totalScreenTime = 0L
            var totalForegroundTime = 0L
            
            usageStats?.forEach { usageStat ->
                if (usageStat.totalTimeInForeground > 0) {
                    val appUsage = createAppUsageData(usageStat, startOfDay, endOfDay)
                    appUsageList.add(appUsage)
                    
                    totalScreenTime += appUsage.totalTimeMs
                    totalForegroundTime += appUsage.foregroundTimeMs
                }
            }
            
            // Sort by usage time
            appUsageList.sortByDescending { it.totalTimeMs }
            
            val summary = DailyUsageSummary(
                date = date,
                totalScreenTimeMs = totalScreenTime,
                totalForegroundTimeMs = totalForegroundTime,
                appUsageData = appUsageList,
                screenUnlocks = getScreenUnlockCount(startOfDay, endOfDay),
                firstUsageTime = getFirstUsageTime(startOfDay, endOfDay),
                lastUsageTime = getLastUsageTime(startOfDay, endOfDay),
                longestSession = getLongestSession(startOfDay, endOfDay),
                deviceId = deviceStateManager.getDeviceId()
            )
            
            // Store summary locally
            storeDailySummary(summary)
            
            Log.d(TAG, "Collected usage data: ${appUsageList.size} apps, ${formatDuration(totalScreenTime)} total time")
            summary
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to collect daily usage data", e)
            
            // Return empty summary on error
            DailyUsageSummary(
                date = date,
                totalScreenTimeMs = 0,
                totalForegroundTimeMs = 0,
                appUsageData = emptyList(),
                deviceId = deviceStateManager.getDeviceId()
            )
        }
    }
    
    suspend fun collectWeeklyUsageData(weekStartDate: Date): List<DailyUsageSummary> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Collecting weekly usage data starting: ${formatDate(weekStartDate)}")
        
        val dailySummaries = mutableListOf<DailyUsageSummary>()
        val calendar = Calendar.getInstance().apply { time = weekStartDate }
        
        for (i in 0 until 7) {
            val dailySummary = collectDailyUsageData(calendar.time)
            dailySummaries.add(dailySummary)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        dailySummaries
    }
    
    private fun createAppUsageData(usageStat: UsageStats, startTime: Long, endTime: Long): AppUsageData {
        val packageName = usageStat.packageName
        val appInfo = appInventoryManager.getAppInfo(packageName)
        
        // Get detailed session data
        val sessions = getAppSessions(packageName, startTime, endTime)
        
        return AppUsageData(
            packageName = packageName,
            appName = appInfo?.name ?: packageName,
            category = appInfo?.category?.name ?: "OTHER",
            totalTimeMs = usageStat.totalTimeInForeground,
            foregroundTimeMs = usageStat.totalTimeInForeground,
            backgroundTimeMs = 0, // UsageStats doesn't track background time separately
            launchCount = getAppLaunchCount(packageName, startTime, endTime),
            lastUsedTime = usageStat.lastTimeUsed,
            firstUsedTime = getFirstUsedTime(packageName, startTime, endTime),
            sessions = sessions,
            averageSessionDuration = if (sessions.isNotEmpty()) {
                sessions.map { it.durationMs }.average().toLong()
            } else 0
        )
    }
    
    private fun getAppSessions(packageName: String, startTime: Long, endTime: Long): List<ScreenTimeSession> {
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val sessions = mutableListOf<ScreenTimeSession>()
            var sessionStart: Long? = null
            
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                
                if (event.packageName == packageName) {
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                            sessionStart = event.timeStamp
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                            sessionStart?.let { start ->
                                val duration = event.timeStamp - start
                                if (duration > 1000) { // Only count sessions longer than 1 second
                                    sessions.add(ScreenTimeSession(
                                        packageName = packageName,
                                        startTime = start,
                                        endTime = event.timeStamp,
                                        durationMs = duration
                                    ))
                                }
                                sessionStart = null
                            }
                        }
                    }
                }
            }
            
            // Handle ongoing session
            sessionStart?.let { start ->
                sessions.add(ScreenTimeSession(
                    packageName = packageName,
                    startTime = start,
                    endTime = System.currentTimeMillis(),
                    durationMs = System.currentTimeMillis() - start
                ))
            }
            
            return sessions
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get app sessions for $packageName", e)
            return emptyList()
        }
    }
    
    private fun getAppLaunchCount(packageName: String, startTime: Long, endTime: Long): Int {
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            var launchCount = 0
            
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                
                if (event.packageName == packageName && 
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    launchCount++
                }
            }
            
            return launchCount
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get launch count for $packageName", e)
            return 0
        }
    }
    
    private fun getFirstUsedTime(packageName: String, startTime: Long, endTime: Long): Long {
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            var firstTime = Long.MAX_VALUE
            
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                
                if (event.packageName == packageName && 
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    firstTime = minOf(firstTime, event.timeStamp)
                }
            }
            
            return if (firstTime == Long.MAX_VALUE) 0 else firstTime
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get first used time for $packageName", e)
            return 0
        }
    }
    
    private fun getScreenUnlockCount(startTime: Long, endTime: Long): Int {
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            var unlockCount = 0
            
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                
                if (event.eventType == android.app.usage.UsageEvents.Event.KEYGUARD_HIDDEN ||
                    event.eventType == android.app.usage.UsageEvents.Event.USER_INTERACTION) {
                    unlockCount++
                }
            }
            
            return unlockCount
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get screen unlock count", e)
            return 0
        }
    }
    
    private fun getFirstUsageTime(startTime: Long, endTime: Long): Long {
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            var firstTime = Long.MAX_VALUE
            
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    firstTime = minOf(firstTime, event.timeStamp)
                }
            }
            
            return if (firstTime == Long.MAX_VALUE) 0 else firstTime
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get first usage time", e)
            return 0
        }
    }
    
    private fun getLastUsageTime(startTime: Long, endTime: Long): Long {
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            var lastTime = 0L
            
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED) {
                    lastTime = maxOf(lastTime, event.timeStamp)
                }
            }
            
            return lastTime
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get last usage time", e)
            return 0
        }
    }
    
    private fun getLongestSession(startTime: Long, endTime: Long): ScreenTimeSession? {
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val sessions = mutableListOf<ScreenTimeSession>()
            var sessionStart: Long? = null
            var currentPackage: String? = null
            
            while (events.hasNextEvent()) {
                val event = android.app.usage.UsageEvents.Event()
                events.getNextEvent(event)
                
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                        sessionStart = event.timeStamp
                        currentPackage = event.packageName
                    }
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                        sessionStart?.let { start ->
                            currentPackage?.let { pkg ->
                                val duration = event.timeStamp - start
                                if (duration > 5000) { // Only count sessions longer than 5 seconds
                                    sessions.add(ScreenTimeSession(
                                        packageName = pkg,
                                        startTime = start,
                                        endTime = event.timeStamp,
                                        durationMs = duration
                                    ))
                                }
                            }
                        }
                        sessionStart = null
                        currentPackage = null
                    }
                }
            }
            
            return sessions.maxByOrNull { it.durationMs }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get longest session", e)
            return null
        }
    }
    
    // Data persistence
    private fun storeDailySummary(summary: DailyUsageSummary) {
        try {
            val dateKey = formatDateKey(summary.date)
            val jsonString = summary.toJson()
            
            prefs.edit()
                .putString("daily_$dateKey", jsonString)
                .putLong("last_updated_$dateKey", System.currentTimeMillis())
                .apply()
                
            Log.d(TAG, "Stored daily summary for: $dateKey")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store daily summary", e)
        }
    }
    
    fun getStoredDailySummary(date: Date): DailyUsageSummary? {
        return try {
            val dateKey = formatDateKey(date)
            val jsonString = prefs.getString("daily_$dateKey", null)
            
            jsonString?.let { DailyUsageSummary.fromJson(it) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stored daily summary", e)
            null
        }
    }
    
    // Backend sync
    suspend fun syncUsageDataToBackend() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Syncing usage data to backend")
            
            // Get all stored daily summaries
            val summaries = getAllStoredSummaries()
            val screenTimeService = ScreenTimeService.getInstance(context)
            
            var syncCount = 0
            summaries.forEach { summary ->
                try {
                    val success = screenTimeService.sendDailySummaryToFirebase(summary)
                    if (success) {
                        syncCount++
                        // Mark as synced
                        markSummaryAsSynced(summary)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync summary for ${formatDateKey(summary.date)}", e)
                }
            }
            
            Log.d(TAG, "Successfully synced $syncCount of ${summaries.size} daily summaries to backend")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync usage data to backend", e)
        }
    }
    
    private fun getAllStoredSummaries(): List<DailyUsageSummary> {
        val summaries = mutableListOf<DailyUsageSummary>()
        
        prefs.all.keys.filter { it.startsWith("daily_") }.forEach { key ->
            prefs.getString(key, null)?.let { jsonString ->
                try {
                    summaries.add(DailyUsageSummary.fromJson(jsonString))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse stored summary: $key", e)
                }
            }
        }
        
        return summaries.sortedByDescending { it.date }
    }
    
    private fun markSummaryAsSynced(summary: DailyUsageSummary) {
        try {
            val dateKey = formatDateKey(summary.date)
            prefs.edit()
                .putBoolean("synced_$dateKey", true)
                .putLong("sync_time_$dateKey", System.currentTimeMillis())
                .apply()
                
            Log.d(TAG, "Marked summary as synced: $dateKey")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark summary as synced", e)
        }
    }
    
    private fun isSummarySynced(summary: DailyUsageSummary): Boolean {
        val dateKey = formatDateKey(summary.date)
        return prefs.getBoolean("synced_$dateKey", false)
    }
    
    // Utility functions
    private fun getStartOfDay(date: Date): Long {
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
    
    private fun getEndOfDay(date: Date): Long {
        val calendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return calendar.timeInMillis
    }
    
    private fun formatDate(date: Date): String {
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return formatter.format(date)
    }
    
    private fun formatDateKey(date: Date): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(date)
    }
    
    private fun formatDuration(durationMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    // Real-time monitoring
    fun getCurrentAppUsage(packageName: String): Long {
        return try {
            val endTime = System.currentTimeMillis()
            val startTime = getStartOfDay(Date())
            
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            
            usageStats?.find { it.packageName == packageName }?.totalTimeInForeground ?: 0
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get current app usage for $packageName", e)
            0
        }
    }
    
    fun getTodaysTotalScreenTime(): Long {
        return try {
            val today = Date()
            val summary = getStoredDailySummary(today)
            summary?.totalScreenTimeMs ?: 0
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get today's total screen time", e)
            0
        }
    }
}
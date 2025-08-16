package com.shieldtechhub.shieldkids.features.screen_time.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class DailyUsageSummary(
    val date: Date,
    val totalScreenTimeMs: Long,
    val totalForegroundTimeMs: Long,
    val appUsageData: List<AppUsageData>,
    val screenUnlocks: Int = 0,
    val firstUsageTime: Long = 0,
    val lastUsageTime: Long = 0,
    val longestSession: ScreenTimeSession? = null,
    val deviceId: String = android.os.Build.ID
) {
    
    fun toJson(): String {
        val json = JSONObject().apply {
            put("date", date.time)
            put("totalScreenTimeMs", totalScreenTimeMs)
            put("totalForegroundTimeMs", totalForegroundTimeMs)
            put("screenUnlocks", screenUnlocks)
            put("firstUsageTime", firstUsageTime)
            put("lastUsageTime", lastUsageTime)
            put("deviceId", deviceId)
            
            // App usage data
            put("appUsageData", JSONArray().apply {
                appUsageData.forEach { appUsage ->
                    put(JSONObject(appUsage.toJson()))
                }
            })
            
            // Longest session
            longestSession?.let { session ->
                put("longestSession", JSONObject(session.toJson()))
            }
        }
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): DailyUsageSummary {
            val json = JSONObject(jsonString)
            
            // Parse app usage data
            val appUsageList = mutableListOf<AppUsageData>()
            if (json.has("appUsageData")) {
                val appUsageArray = json.getJSONArray("appUsageData")
                for (i in 0 until appUsageArray.length()) {
                    val appUsageJson = appUsageArray.getJSONObject(i).toString()
                    appUsageList.add(AppUsageData.fromJson(appUsageJson))
                }
            }
            
            // Parse longest session
            val longestSession = if (json.has("longestSession")) {
                val sessionJson = json.getJSONObject("longestSession").toString()
                ScreenTimeSession.fromJson(sessionJson)
            } else null
            
            return DailyUsageSummary(
                date = Date(json.getLong("date")),
                totalScreenTimeMs = json.getLong("totalScreenTimeMs"),
                totalForegroundTimeMs = json.getLong("totalForegroundTimeMs"),
                appUsageData = appUsageList,
                screenUnlocks = json.optInt("screenUnlocks", 0),
                firstUsageTime = json.optLong("firstUsageTime", 0),
                lastUsageTime = json.optLong("lastUsageTime", 0),
                longestSession = longestSession,
                deviceId = json.optString("deviceId", android.os.Build.ID)
            )
        }
    }
    
    // Utility methods
    fun getTopApps(limit: Int = 5): List<AppUsageData> {
        return appUsageData.sortedByDescending { it.totalTimeMs }.take(limit)
    }
    
    fun getUsageByCategory(): Map<String, Long> {
        return appUsageData.groupBy { it.category }
            .mapValues { (_, apps) -> apps.sumOf { it.totalTimeMs } }
    }
    
    fun getFormattedTotalTime(): String {
        return formatDuration(totalScreenTimeMs)
    }
    
    fun getFormattedDate(): String {
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        return formatter.format(date)
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

data class AppUsageData(
    val packageName: String,
    val appName: String,
    val category: String,
    val totalTimeMs: Long,
    val foregroundTimeMs: Long,
    val backgroundTimeMs: Long = 0,
    val launchCount: Int = 0,
    val lastUsedTime: Long = 0,
    val firstUsedTime: Long = 0,
    val sessions: List<ScreenTimeSession> = emptyList(),
    val averageSessionDuration: Long = 0
) {
    
    fun toJson(): String {
        val json = JSONObject().apply {
            put("packageName", packageName)
            put("appName", appName)
            put("category", category)
            put("totalTimeMs", totalTimeMs)
            put("foregroundTimeMs", foregroundTimeMs)
            put("backgroundTimeMs", backgroundTimeMs)
            put("launchCount", launchCount)
            put("lastUsedTime", lastUsedTime)
            put("firstUsedTime", firstUsedTime)
            put("averageSessionDuration", averageSessionDuration)
            
            // Sessions
            put("sessions", JSONArray().apply {
                sessions.forEach { session ->
                    put(JSONObject(session.toJson()))
                }
            })
        }
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): AppUsageData {
            val json = JSONObject(jsonString)
            
            // Parse sessions
            val sessionsList = mutableListOf<ScreenTimeSession>()
            if (json.has("sessions")) {
                val sessionsArray = json.getJSONArray("sessions")
                for (i in 0 until sessionsArray.length()) {
                    val sessionJson = sessionsArray.getJSONObject(i).toString()
                    sessionsList.add(ScreenTimeSession.fromJson(sessionJson))
                }
            }
            
            return AppUsageData(
                packageName = json.getString("packageName"),
                appName = json.getString("appName"),
                category = json.getString("category"),
                totalTimeMs = json.getLong("totalTimeMs"),
                foregroundTimeMs = json.getLong("foregroundTimeMs"),
                backgroundTimeMs = json.optLong("backgroundTimeMs", 0),
                launchCount = json.optInt("launchCount", 0),
                lastUsedTime = json.optLong("lastUsedTime", 0),
                firstUsedTime = json.optLong("firstUsedTime", 0),
                sessions = sessionsList,
                averageSessionDuration = json.optLong("averageSessionDuration", 0)
            )
        }
    }
    
    // Utility methods
    fun getFormattedTotalTime(): String {
        return formatDuration(totalTimeMs)
    }
    
    fun getFormattedAverageSession(): String {
        return formatDuration(averageSessionDuration)
    }
    
    fun getUsagePercentage(totalDayTime: Long): Double {
        return if (totalDayTime > 0) {
            (totalTimeMs.toDouble() / totalDayTime.toDouble()) * 100
        } else 0.0
    }
    
    fun getLongestSession(): ScreenTimeSession? {
        return sessions.maxByOrNull { it.durationMs }
    }
    
    fun getSessionsCount(): Int = sessions.size
    
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

data class ScreenTimeSession(
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long = endTime - startTime,
    val sessionId: String = generateSessionId(packageName, startTime)
) {
    
    fun toJson(): String {
        val json = JSONObject().apply {
            put("packageName", packageName)
            put("startTime", startTime)
            put("endTime", endTime)
            put("durationMs", durationMs)
            put("sessionId", sessionId)
        }
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): ScreenTimeSession {
            val json = JSONObject(jsonString)
            
            return ScreenTimeSession(
                packageName = json.getString("packageName"),
                startTime = json.getLong("startTime"),
                endTime = json.getLong("endTime"),
                durationMs = json.getLong("durationMs"),
                sessionId = json.optString("sessionId", 
                    generateSessionId(json.getString("packageName"), json.getLong("startTime")))
            )
        }
        
        private fun generateSessionId(packageName: String, startTime: Long): String {
            return "${packageName}_${startTime}"
        }
    }
    
    // Utility methods
    fun getFormattedDuration(): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (durationMs % (1000 * 60)) / 1000
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
    
    fun getFormattedStartTime(): String {
        val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return formatter.format(Date(startTime))
    }
    
    fun getFormattedEndTime(): String {
        val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return formatter.format(Date(endTime))
    }
    
    fun getFormattedTimeRange(): String {
        return "${getFormattedStartTime()} - ${getFormattedEndTime()}"
    }
    
    fun isLongSession(thresholdMinutes: Int = 30): Boolean {
        return durationMs >= (thresholdMinutes * 60 * 1000)
    }
    
    fun isShortSession(thresholdMinutes: Int = 2): Boolean {
        return durationMs <= (thresholdMinutes * 60 * 1000)
    }
}

data class WeeklyUsageSummary(
    val weekStartDate: Date,
    val dailySummaries: List<DailyUsageSummary>,
    val totalWeekScreenTime: Long = dailySummaries.sumOf { it.totalScreenTimeMs },
    val averageDailyScreenTime: Long = if (dailySummaries.isNotEmpty()) totalWeekScreenTime / dailySummaries.size else 0,
    val topAppsOfWeek: List<AppUsageData> = getTopAppsOfWeek(dailySummaries),
    val mostActiveDay: DailyUsageSummary? = dailySummaries.maxByOrNull { it.totalScreenTimeMs },
    val leastActiveDay: DailyUsageSummary? = dailySummaries.minByOrNull { it.totalScreenTimeMs }
) {
    
    companion object {
        private fun getTopAppsOfWeek(dailySummaries: List<DailyUsageSummary>): List<AppUsageData> {
            val appTotals = mutableMapOf<String, MutableMap<String, Any>>()
            
            dailySummaries.forEach { dailySummary ->
                dailySummary.appUsageData.forEach { appUsage ->
                    val existing = appTotals.getOrPut(appUsage.packageName) {
                        mutableMapOf(
                            "appName" to appUsage.appName,
                            "category" to appUsage.category,
                            "totalTimeMs" to 0L,
                            "launchCount" to 0
                        )
                    }
                    
                    existing["totalTimeMs"] = (existing["totalTimeMs"] as Long) + appUsage.totalTimeMs
                    existing["launchCount"] = (existing["launchCount"] as Int) + appUsage.launchCount
                }
            }
            
            return appTotals.map { (packageName, data) ->
                AppUsageData(
                    packageName = packageName,
                    appName = data["appName"] as String,
                    category = data["category"] as String,
                    totalTimeMs = data["totalTimeMs"] as Long,
                    foregroundTimeMs = data["totalTimeMs"] as Long,
                    launchCount = data["launchCount"] as Int
                )
            }.sortedByDescending { it.totalTimeMs }.take(10)
        }
    }
    
    fun getFormattedTotalTime(): String {
        val hours = totalWeekScreenTime / (1000 * 60 * 60)
        val minutes = (totalWeekScreenTime % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    fun getFormattedAverageDaily(): String {
        val hours = averageDailyScreenTime / (1000 * 60 * 60)
        val minutes = (averageDailyScreenTime % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    fun getWeeklyUsageByCategory(): Map<String, Long> {
        val categoryTotals = mutableMapOf<String, Long>()
        
        dailySummaries.forEach { dailySummary ->
            dailySummary.getUsageByCategory().forEach { (category, time) ->
                categoryTotals[category] = (categoryTotals[category] ?: 0) + time
            }
        }
        
        return categoryTotals
    }
    
    fun getWeekRange(): String {
        val endDate = Calendar.getInstance().apply {
            time = weekStartDate
            add(Calendar.DAY_OF_MONTH, 6)
        }.time
        
        val formatter = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
        return "${formatter.format(weekStartDate)} - ${formatter.format(endDate)}"
    }
}
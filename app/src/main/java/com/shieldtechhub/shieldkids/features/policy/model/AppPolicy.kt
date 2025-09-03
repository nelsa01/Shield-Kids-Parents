package com.shieldtechhub.shieldkids.features.policy.model

import org.json.JSONObject

data class AppPolicy(
    val packageName: String,
    val action: Action,
    val reason: String? = null,
    val timeLimit: TimeLimit? = null,
    val scheduleRestriction: ScheduleRestriction? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    
    enum class Action {
        ALLOW,      // App is allowed without restrictions
        BLOCK,      // App is completely blocked
        TIME_LIMIT, // App has time-based restrictions
        SCHEDULE    // App has schedule-based restrictions
    }
    
    data class TimeLimit(
        val dailyLimitMinutes: Long,
        val weeklyLimitMinutes: Long = dailyLimitMinutes * 7,
        val allowedStartTime: String? = null, // HH:MM format
        val allowedEndTime: String? = null,   // HH:MM format
        val breakDuration: Long = 0,          // Minutes of break required after time limit
        val warningAtMinutes: Long = 5        // Warning before time limit
    )
    
    data class ScheduleRestriction(
        val allowedDays: List<DayOfWeek>,
        val allowedTimeRanges: List<TimeRange>,
        val blockedDays: List<DayOfWeek> = emptyList()
    ) {
        
        enum class DayOfWeek {
            MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
        }
        
        data class TimeRange(
            val startTime: String, // HH:MM format
            val endTime: String    // HH:MM format
        )
    }
    
    fun toJson(): String {
        val json = JSONObject().apply {
            put("packageName", packageName)
            put("action", action.name)
            put("reason", reason)
            put("isActive", isActive)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            
            // Time limit
            timeLimit?.let { limit ->
                put("timeLimit", JSONObject().apply {
                    put("dailyLimitMinutes", limit.dailyLimitMinutes)
                    put("weeklyLimitMinutes", limit.weeklyLimitMinutes)
                    put("allowedStartTime", limit.allowedStartTime)
                    put("allowedEndTime", limit.allowedEndTime)
                    put("breakDuration", limit.breakDuration)
                    put("warningAtMinutes", limit.warningAtMinutes)
                })
            }
            
            // Schedule restriction
            scheduleRestriction?.let { schedule ->
                put("scheduleRestriction", JSONObject().apply {
                    put("allowedDays", org.json.JSONArray().apply {
                        schedule.allowedDays.forEach { day ->
                            put(day.name)
                        }
                    })
                    put("blockedDays", org.json.JSONArray().apply {
                        schedule.blockedDays.forEach { day ->
                            put(day.name)
                        }
                    })
                    put("allowedTimeRanges", org.json.JSONArray().apply {
                        schedule.allowedTimeRanges.forEach { range ->
                            put(JSONObject().apply {
                                put("startTime", range.startTime)
                                put("endTime", range.endTime)
                            })
                        }
                    })
                })
            }
        }
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): AppPolicy {
            val json = JSONObject(jsonString)
            
            // Parse time limit
            val timeLimit = if (json.has("timeLimit")) {
                val limitJson = json.getJSONObject("timeLimit")
                TimeLimit(
                    dailyLimitMinutes = limitJson.getLong("dailyLimitMinutes"),
                    weeklyLimitMinutes = limitJson.optLong("weeklyLimitMinutes", 
                        limitJson.getLong("dailyLimitMinutes") * 7),
                    allowedStartTime = limitJson.optString("allowedStartTime").takeIf { it.isNotEmpty() },
                    allowedEndTime = limitJson.optString("allowedEndTime").takeIf { it.isNotEmpty() },
                    breakDuration = limitJson.optLong("breakDuration", 0),
                    warningAtMinutes = limitJson.optLong("warningAtMinutes", 5)
                )
            } else null
            
            // Parse schedule restriction
            val scheduleRestriction = if (json.has("scheduleRestriction")) {
                val scheduleJson = json.getJSONObject("scheduleRestriction")
                
                val allowedDays = mutableListOf<ScheduleRestriction.DayOfWeek>()
                if (scheduleJson.has("allowedDays")) {
                    val allowedDaysArray = scheduleJson.getJSONArray("allowedDays")
                    for (i in 0 until allowedDaysArray.length()) {
                        allowedDays.add(ScheduleRestriction.DayOfWeek.valueOf(allowedDaysArray.getString(i)))
                    }
                }
                
                val blockedDays = mutableListOf<ScheduleRestriction.DayOfWeek>()
                if (scheduleJson.has("blockedDays")) {
                    val blockedDaysArray = scheduleJson.getJSONArray("blockedDays")
                    for (i in 0 until blockedDaysArray.length()) {
                        blockedDays.add(ScheduleRestriction.DayOfWeek.valueOf(blockedDaysArray.getString(i)))
                    }
                }
                
                val allowedTimeRanges = mutableListOf<ScheduleRestriction.TimeRange>()
                if (scheduleJson.has("allowedTimeRanges")) {
                    val rangesArray = scheduleJson.getJSONArray("allowedTimeRanges")
                    for (i in 0 until rangesArray.length()) {
                        val rangeJson = rangesArray.getJSONObject(i)
                        allowedTimeRanges.add(ScheduleRestriction.TimeRange(
                            startTime = rangeJson.getString("startTime"),
                            endTime = rangeJson.getString("endTime")
                        ))
                    }
                }
                
                ScheduleRestriction(
                    allowedDays = allowedDays,
                    allowedTimeRanges = allowedTimeRanges,
                    blockedDays = blockedDays
                )
            } else null
            
            return AppPolicy(
                packageName = json.getString("packageName"),
                action = Action.valueOf(json.getString("action")),
                reason = json.optString("reason").takeIf { it.isNotEmpty() },
                timeLimit = timeLimit,
                scheduleRestriction = scheduleRestriction,
                isActive = json.optBoolean("isActive", true),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
        
        // Convenience factory methods
        fun block(packageName: String, reason: String = "Blocked by parent"): AppPolicy {
            return AppPolicy(
                packageName = packageName,
                action = Action.BLOCK,
                reason = reason
            )
        }
        
        fun timeLimit(packageName: String, dailyMinutes: Long, startTime: String? = null, endTime: String? = null): AppPolicy {
            return AppPolicy(
                packageName = packageName,
                action = Action.TIME_LIMIT,
                timeLimit = TimeLimit(
                    dailyLimitMinutes = dailyMinutes,
                    allowedStartTime = startTime,
                    allowedEndTime = endTime
                )
            )
        }
        
        fun allow(packageName: String): AppPolicy {
            return AppPolicy(
                packageName = packageName,
                action = Action.ALLOW
            )
        }
        
        fun scheduleRestriction(
            packageName: String, 
            allowedDays: List<ScheduleRestriction.DayOfWeek>,
            timeRanges: List<ScheduleRestriction.TimeRange>
        ): AppPolicy {
            return AppPolicy(
                packageName = packageName,
                action = Action.SCHEDULE,
                scheduleRestriction = ScheduleRestriction(
                    allowedDays = allowedDays,
                    allowedTimeRanges = timeRanges
                )
            )
        }
    }
    
    // Utility functions
    fun isCurrentlyAllowed(context: android.content.Context): Boolean {
        if (!isActive) return true
        
        return when (action) {
            Action.ALLOW -> true
            Action.BLOCK -> false
            Action.TIME_LIMIT -> isWithinTimeWindow() && !hasExceededTimeLimit(context)
            Action.SCHEDULE -> isWithinSchedule()
        }
    }
    
    @Deprecated("Use isCurrentlyAllowed(context) instead")
    fun isCurrentlyAllowed(): Boolean {
        if (!isActive) return true
        
        return when (action) {
            Action.ALLOW -> true
            Action.BLOCK -> false
            Action.TIME_LIMIT -> isWithinTimeWindow() // Skip time limit check without context
            Action.SCHEDULE -> isWithinSchedule()
        }
    }
    
    private fun isWithinTimeWindow(): Boolean {
        val timeLimit = this.timeLimit ?: return true
        
        if (timeLimit.allowedStartTime == null || timeLimit.allowedEndTime == null) {
            return true
        }
        
        val currentTime = getCurrentTimeString()
        val startTime = timeLimit.allowedStartTime
        val endTime = timeLimit.allowedEndTime
        
        // Handle overnight time window (e.g., 20:00 to 08:00)
        return if (startTime > endTime) {
            currentTime >= startTime || currentTime <= endTime
        } else {
            currentTime >= startTime && currentTime <= endTime
        }
    }
    
    private fun hasExceededTimeLimit(context: android.content.Context): Boolean {
        val timeLimit = this.timeLimit ?: return false
        
        try {
            val screenTimeCollector = com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector.getInstance(context)
            val currentUsage = screenTimeCollector.getCurrentAppUsage(packageName)
            val dailyLimitMs = timeLimit.dailyLimitMinutes * 60 * 1000
            
            return currentUsage >= dailyLimitMs
        } catch (e: Exception) {
            android.util.Log.e("AppPolicy", "Failed to check time limit for $packageName", e)
            return false
        }
    }
    
    private fun isWithinSchedule(): Boolean {
        val schedule = this.scheduleRestriction ?: return true
        
        val currentDay = getCurrentDayOfWeek()
        val currentTime = getCurrentTimeString()
        
        // Check if current day is blocked
        if (schedule.blockedDays.contains(currentDay)) {
            return false
        }
        
        // Check if current day is in allowed days
        if (schedule.allowedDays.isNotEmpty() && !schedule.allowedDays.contains(currentDay)) {
            return false
        }
        
        // Check if current time is within allowed time ranges
        if (schedule.allowedTimeRanges.isEmpty()) {
            return true
        }
        
        return schedule.allowedTimeRanges.any { range ->
            isTimeInRange(currentTime, range.startTime, range.endTime)
        }
    }
    
    private fun isTimeInRange(currentTime: String, startTime: String, endTime: String): Boolean {
        return if (startTime > endTime) {
            // Overnight range
            currentTime >= startTime || currentTime <= endTime
        } else {
            currentTime >= startTime && currentTime <= endTime
        }
    }
    
    private fun getCurrentTimeString(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
    
    private fun getCurrentDayOfWeek(): ScheduleRestriction.DayOfWeek {
        val calendar = java.util.Calendar.getInstance()
        return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> ScheduleRestriction.DayOfWeek.MONDAY
            java.util.Calendar.TUESDAY -> ScheduleRestriction.DayOfWeek.TUESDAY
            java.util.Calendar.WEDNESDAY -> ScheduleRestriction.DayOfWeek.WEDNESDAY
            java.util.Calendar.THURSDAY -> ScheduleRestriction.DayOfWeek.THURSDAY
            java.util.Calendar.FRIDAY -> ScheduleRestriction.DayOfWeek.FRIDAY
            java.util.Calendar.SATURDAY -> ScheduleRestriction.DayOfWeek.SATURDAY
            java.util.Calendar.SUNDAY -> ScheduleRestriction.DayOfWeek.SUNDAY
            else -> ScheduleRestriction.DayOfWeek.MONDAY
        }
    }
    
    fun getRemainingTimeToday(context: android.content.Context): Long {
        val timeLimit = this.timeLimit ?: return Long.MAX_VALUE
        
        try {
            val screenTimeCollector = com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector.getInstance(context)
            val currentUsageMs = screenTimeCollector.getCurrentAppUsage(packageName)
            val dailyLimitMs = timeLimit.dailyLimitMinutes * 60 * 1000
            
            val remainingMs = dailyLimitMs - currentUsageMs
            return if (remainingMs > 0) remainingMs / (60 * 1000) else 0L // Return minutes
        } catch (e: Exception) {
            android.util.Log.e("AppPolicy", "Failed to get remaining time for $packageName", e)
            return timeLimit.dailyLimitMinutes // Fallback to full limit
        }
    }
    
    @Deprecated("Use getRemainingTimeToday(context) instead")
    fun getRemainingTimeToday(): Long {
        // Fallback for backward compatibility
        return timeLimit?.dailyLimitMinutes ?: 0L
    }
    
    fun getWarningTimeRemaining(context: android.content.Context): Long {
        val timeLimit = this.timeLimit ?: return 0L
        val remaining = getRemainingTimeToday(context)
        return if (remaining <= timeLimit.warningAtMinutes) remaining else 0L
    }
    
    fun shouldShowWarning(context: android.content.Context): Boolean {
        return getWarningTimeRemaining(context) > 0
    }
    
    @Deprecated("Use getWarningTimeRemaining(context) instead")
    fun getWarningTimeRemaining(): Long {
        val timeLimit = this.timeLimit ?: return 0L
        return timeLimit.warningAtMinutes // Fallback
    }
    
    @Deprecated("Use shouldShowWarning(context) instead")
    fun shouldShowWarning(): Boolean {
        return false // Fallback
    }
}
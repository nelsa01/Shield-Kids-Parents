package com.shieldtechhub.shieldkids.features.app_management.model

import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo

data class AppWithUsage(
    val appInfo: AppInfo,
    val usageTimeMs: Long = 0L,
    val launchCount: Int = 0,
    val lastUsedTime: Long = 0L,
    val hasUsageData: Boolean = false
) {
    fun getFormattedUsageTime(): String {
        if (!hasUsageData || usageTimeMs <= 0) return "No usage"
        
        val minutes = usageTimeMs / (1000 * 60)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        
        return when {
            hours > 0 -> "${hours}h ${remainingMinutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    fun getUsageColor(): String {
        if (!hasUsageData || usageTimeMs <= 0) return "#9E9E9E" // gray
        
        val minutes = usageTimeMs / (1000 * 60)
        return when {
            minutes >= 120 -> "#F44336" // red - very high usage (2+ hours)
            minutes >= 60 -> "#FF9800"  // orange - high usage (1+ hour)
            minutes >= 30 -> "#FFC107"  // amber - medium usage (30+ min)
            else -> "#4CAF50"           // green - low usage
        }
    }
}
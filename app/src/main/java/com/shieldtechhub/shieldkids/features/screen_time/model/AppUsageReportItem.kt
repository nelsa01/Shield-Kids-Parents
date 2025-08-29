package com.shieldtechhub.shieldkids.features.screen_time.model

data class AppUsageReportItem(
    val rank: Int,
    val appName: String,
    val packageName: String,
    val category: String,
    val usageTime: Long, // in milliseconds
    val launchCount: Long
) {
    fun getFormattedUsageTime(): String {
        val hours = usageTime / (1000 * 60 * 60)
        val minutes = (usageTime % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    fun getUsageIntensityColor(): String {
        return when {
            usageTime >= 2 * 60 * 60 * 1000 -> "#F44336" // Red - Heavy usage
            usageTime >= 30 * 60 * 1000 -> "#FF9800" // Orange - Moderate usage
            usageTime >= 5 * 60 * 1000 -> "#FFC107" // Yellow - Light usage
            else -> "#4CAF50" // Green - Minimal usage
        }
    }
    
    fun getUsageIntensityText(): String {
        return when {
            usageTime >= 2 * 60 * 60 * 1000 -> "Heavy"
            usageTime >= 30 * 60 * 1000 -> "Moderate"
            usageTime >= 5 * 60 * 1000 -> "Light"
            else -> "Minimal"
        }
    }
}
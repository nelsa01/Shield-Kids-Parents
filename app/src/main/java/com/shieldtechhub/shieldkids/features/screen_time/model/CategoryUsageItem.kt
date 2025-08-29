package com.shieldtechhub.shieldkids.features.screen_time.model

data class CategoryUsageItem(
    val category: String,
    val usageTime: Long, // in milliseconds
    val percentage: Int // percentage of total usage
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
    
    fun getDisplayName(): String {
        return category.lowercase().replaceFirstChar { it.uppercase() }
    }
    
    fun getCategoryColor(): String {
        return when (category.uppercase()) {
            "SOCIAL" -> "#E91E63" // Pink
            "GAMES" -> "#9C27B0" // Purple
            "EDUCATIONAL" -> "#4CAF50" // Green
            "ENTERTAINMENT" -> "#FF5722" // Deep Orange
            "BROWSERS" -> "#2196F3" // Blue
            "SHOPPING" -> "#FF9800" // Orange
            "COMMUNICATION" -> "#00BCD4" // Cyan
            "PRODUCTIVITY" -> "#795548" // Brown
            else -> "#607D8B" // Blue Grey
        }
    }
}
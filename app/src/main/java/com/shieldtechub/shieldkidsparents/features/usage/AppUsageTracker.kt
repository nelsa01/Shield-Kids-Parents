package com.shieldtechub.shieldkidsparents.features.usage

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

class AppUsageTracker(private val context: Context) {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getUsageStatsList(startTime: Long, endTime: Long): List<UsageStats> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
    }

    // Example: Get most used apps in a time range
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun getMostUsedApps(startTime: Long, endTime: Long, topN: Int = 5): List<UsageStats> {
        val stats = getUsageStatsList(startTime, endTime)
        return stats.sortedByDescending { it.totalTimeInForeground }.take(topN)
    }
} 
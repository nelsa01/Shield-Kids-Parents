package com.shieldtechhub.shieldkids.features.screen_time.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

class ScreenTimeCollectionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ScreenTimeWorker"
        private const val WORK_NAME = "screen_time_collection"
        
        fun startPeriodicCollection(context: Context) {
            Log.w(TAG, "⚠️ DEPRECATED: ScreenTimeCollectionWorker - Use UnifiedChildSyncService instead")
            Log.w(TAG, "This WorkManager task has been replaced by UnifiedChildSyncService (5-minute sync)")
            
            // Do not start the worker - it's been replaced by UnifiedChildSyncService
            // The old 6-hour/15-minute sync is no longer needed
            return
        }
        
        fun stopPeriodicCollection(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Stopped periodic screen time collection")
        }
        
        private fun createInputData(): Data {
            return Data.Builder()
                .putLong("collection_time", System.currentTimeMillis())
                .build()
        }
    }

    private val screenTimeCollector = ScreenTimeCollector.getInstance(applicationContext)
    private val deviceStateManager = DeviceStateManager(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Starting screen time collection work")
            
            // Only collect data on child devices
            if (!deviceStateManager.isChildDevice()) {
                Log.d(TAG, "Not a child device, skipping screen time collection")
                return@withContext Result.success()
            }
            
            // Collect today's usage data
            val today = Date()
            val dailySummary = screenTimeCollector.collectDailyUsageData(today)
            
            Log.d(TAG, "Collected usage data: ${dailySummary.appUsageData.size} apps, " +
                    "${formatDuration(dailySummary.totalScreenTimeMs)} total time")
            
            // Sync to backend
            screenTimeCollector.syncUsageDataToBackend()
            
            // Set up next collection
            val outputData = Data.Builder()
                .putLong("last_collection_time", System.currentTimeMillis())
                .putInt("apps_tracked", dailySummary.appUsageData.size)
                .putLong("total_screen_time_ms", dailySummary.totalScreenTimeMs)
                .build()
            
            Log.d(TAG, "Screen time collection completed successfully")
            Result.success(outputData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Screen time collection failed", e)
            
            // Return retry on failure
            if (runAttemptCount < 3) {
                Log.d(TAG, "Retrying screen time collection (attempt ${runAttemptCount + 1})")
                Result.retry()
            } else {
                Result.failure()
            }
        }
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
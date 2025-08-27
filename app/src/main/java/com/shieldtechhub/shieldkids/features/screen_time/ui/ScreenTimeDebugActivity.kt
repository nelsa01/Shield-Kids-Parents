package com.shieldtechhub.shieldkids.features.screen_time.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shieldtechhub.shieldkids.databinding.ActivityScreenTimeDebugBinding
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import kotlinx.coroutines.launch
import java.util.*

class ScreenTimeDebugActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ScreenTimeDebug"
    }
    
    private lateinit var binding: ActivityScreenTimeDebugBinding
    private lateinit var screenTimeService: ScreenTimeService
    private lateinit var screenTimeCollector: ScreenTimeCollector
    private lateinit var deviceStateManager: DeviceStateManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenTimeDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        initializeServices()
        loadDebugInfo()
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.btnCollectNow.setOnClickListener {
            collectUsageNow()
        }
        
        binding.btnSyncNow.setOnClickListener {
            syncToFirebase()
        }
        
        binding.btnRefresh.setOnClickListener {
            loadDebugInfo()
        }
    }
    
    private fun initializeServices() {
        screenTimeService = ScreenTimeService.getInstance(this)
        screenTimeCollector = ScreenTimeCollector.getInstance(this)
        deviceStateManager = DeviceStateManager(this)
    }
    
    private fun loadDebugInfo() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val debugInfo = StringBuilder()
                
                // Device Information
                debugInfo.appendLine("=== DEVICE INFORMATION ===")
                debugInfo.appendLine("Device ID: ${android.os.Build.ID}")
                debugInfo.appendLine("Device Model: ${android.os.Build.MODEL}")
                debugInfo.appendLine("Device Type: ${deviceStateManager.getDeviceType()}")
                debugInfo.appendLine("Is Child Device: ${deviceStateManager.isChildDevice()}")
                debugInfo.appendLine()
                
                if (deviceStateManager.isChildDevice()) {
                    val childInfo = deviceStateManager.getChildDeviceInfo()
                    debugInfo.appendLine("=== CHILD DEVICE INFO ===")
                    debugInfo.appendLine("Child ID: ${childInfo?.childId ?: "Not found"}")
                    debugInfo.appendLine("Device ID in Child Info: ${childInfo?.deviceId ?: "Not found"}")
                    debugInfo.appendLine()
                }
                
                // Today's Usage Data (Local)
                debugInfo.appendLine("=== LOCAL USAGE DATA ===")
                val todayUsage = screenTimeCollector.collectDailyUsageData()
                debugInfo.appendLine("Date: ${Date()}")
                debugInfo.appendLine("Total Screen Time: ${formatDuration(todayUsage.totalScreenTimeMs)}")
                debugInfo.appendLine("Apps Tracked: ${todayUsage.appUsageData.size}")
                debugInfo.appendLine("Screen Unlocks: ${todayUsage.screenUnlocks}")
                
                if (todayUsage.appUsageData.isNotEmpty()) {
                    debugInfo.appendLine("Top 3 Apps:")
                    todayUsage.getTopApps(3).forEach { app ->
                        debugInfo.appendLine("  • ${app.appName}: ${formatDuration(app.totalTimeMs)}")
                    }
                }
                debugInfo.appendLine()
                
                // Firebase Sync Status
                debugInfo.appendLine("=== FIREBASE SYNC STATUS ===")
                val storedSummaries = getStoredSummaries()
                debugInfo.appendLine("Stored Daily Summaries: ${storedSummaries.size}")
                
                if (deviceStateManager.isChildDevice()) {
                    val childInfo = deviceStateManager.getChildDeviceInfo()
                    if (childInfo != null) {
                        debugInfo.appendLine("Attempting to sync today's data...")
                        val success = screenTimeService.sendDailySummaryToFirebase(todayUsage)
                        debugInfo.appendLine("Sync Status: ${if (success) "✅ SUCCESS" else "❌ FAILED"}")
                        
                        // Try to read back from Firebase
                        val firebaseData = screenTimeService.getDailyUsageFromFirebase(Date(), android.os.Build.ID)
                        debugInfo.appendLine("Data in Firebase: ${if (firebaseData != null) "✅ FOUND" else "❌ NOT FOUND"}")
                        
                        if (firebaseData != null) {
                            val fbScreenTime = firebaseData["totalScreenTimeMs"] as? Long ?: 0L
                            val fbAppCount = firebaseData["appCount"] as? Int ?: 0
                            debugInfo.appendLine("Firebase Data:")
                            debugInfo.appendLine("  Screen Time: ${formatDuration(fbScreenTime)}")
                            debugInfo.appendLine("  App Count: $fbAppCount")
                        }
                    } else {
                        debugInfo.appendLine("❌ No child device info found - cannot sync")
                    }
                } else {
                    debugInfo.appendLine("ℹ️ Parent device - no sync needed")
                }
                
                binding.tvDebugInfo.text = debugInfo.toString()
                
            } catch (e: Exception) {
                binding.tvDebugInfo.text = "Error loading debug info: ${e.message}\n\n${e.stackTraceToString()}"
                Log.e(TAG, "Error loading debug info", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun collectUsageNow() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Manually collecting usage data...")
                val usage = screenTimeCollector.collectDailyUsageData()
                Log.d(TAG, "Collected usage for ${usage.appUsageData.size} apps")
                
                loadDebugInfo() // Refresh display
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to collect usage data", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun syncToFirebase() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Manually syncing to Firebase...")
                screenTimeCollector.syncUsageDataToBackend()
                Log.d(TAG, "Sync completed")
                
                loadDebugInfo() // Refresh display
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync to Firebase", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun getStoredSummaries(): List<String> {
        val prefs = getSharedPreferences("screen_time_data", MODE_PRIVATE)
        return prefs.all.keys.filter { it.startsWith("daily_") }.toList()
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
package com.shieldtechhub.shieldkids.common.sync

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shieldtechhub.shieldkids.databinding.ActivityUnifiedSyncDebugBinding
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UnifiedSyncDebugActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "UnifiedSyncDebug"
    }
    
    private lateinit var binding: ActivityUnifiedSyncDebugBinding
    private lateinit var unifiedSyncService: UnifiedChildSyncService
    private lateinit var deviceStateManager: DeviceStateManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnifiedSyncDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        initializeServices()
        loadDebugInfo()
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.btnSyncNow.setOnClickListener {
            performImmediateSync()
        }
        
        binding.btnStartSync.setOnClickListener {
            startUnifiedSync()
        }
        
        binding.btnStopSync.setOnClickListener {
            stopUnifiedSync()
        }
        
        binding.btnRefresh.setOnClickListener {
            loadDebugInfo()
        }
    }
    
    private fun initializeServices() {
        unifiedSyncService = UnifiedChildSyncService.getInstance(this)
        deviceStateManager = DeviceStateManager(this)
    }
    
    private fun loadDebugInfo() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val debugInfo = StringBuilder()
                
                // Device Information
                debugInfo.appendLine("=== UNIFIED SYNC DEBUG ===")
                debugInfo.appendLine("Time: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
                debugInfo.appendLine()
                
                // Device Status
                debugInfo.appendLine("=== DEVICE STATUS ===")
                debugInfo.appendLine("Device ID: ${android.os.Build.ID}")
                debugInfo.appendLine("Device Type: ${deviceStateManager.getDeviceType()}")
                debugInfo.appendLine("Is Child Device: ${deviceStateManager.isChildDevice()}")
                
                if (deviceStateManager.isChildDevice()) {
                    val childInfo = deviceStateManager.getChildDeviceInfo()
                    debugInfo.appendLine("Child ID: ${childInfo?.childId ?: "Not found"}")
                    debugInfo.appendLine("Device ID in Child Info: ${childInfo?.deviceId ?: "Not found"}")
                } else {
                    debugInfo.appendLine("⚠️ NOT A CHILD DEVICE - Unified sync will not run")
                }
                debugInfo.appendLine()
                
                // Sync Service Status
                debugInfo.appendLine("=== SYNC SERVICE STATUS ===")
                val syncStatus = unifiedSyncService.getSyncStatus()
                syncStatus.forEach { (key, value) ->
                    debugInfo.appendLine("$key: $value")
                }
                debugInfo.appendLine()
                
                // Sync Frequency Explanation
                debugInfo.appendLine("=== SYNC FREQUENCY ===")
                debugInfo.appendLine("Unified Sync: Every 5 minutes")
                debugInfo.appendLine("Data Types Synced:")
                debugInfo.appendLine("  • Screen Time Usage")
                debugInfo.appendLine("  • App Installations")
                debugInfo.appendLine("  • Policy Updates")
                debugInfo.appendLine("  • Time Limit Monitoring")
                debugInfo.appendLine()
                
                // Previous Sync Methods (Deprecated)
                debugInfo.appendLine("=== DEPRECATED METHODS ===")
                debugInfo.appendLine("❌ ScreenTimeCollectionWorker: 6 hours → REPLACED")
                debugInfo.appendLine("❌ ChildAppSyncService: 5 minutes → INTEGRATED")
                debugInfo.appendLine("✅ UnifiedChildSyncService: 5 minutes → ACTIVE")
                debugInfo.appendLine()
                
                // Benefits
                debugInfo.appendLine("=== BENEFITS ===")
                debugInfo.appendLine("✅ Real-time limit monitoring")
                debugInfo.appendLine("✅ Immediate parent notifications")
                debugInfo.appendLine("✅ Faster policy enforcement")
                debugInfo.appendLine("✅ Consolidated resource usage")
                debugInfo.appendLine("✅ Better battery optimization")
                
                binding.tvDebugInfo.text = debugInfo.toString()
                
            } catch (e: Exception) {
                binding.tvDebugInfo.text = "Error loading debug info: ${e.message}\n\n${e.stackTraceToString()}"
                Log.e(TAG, "Error loading debug info", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun performImmediateSync() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Manually triggering immediate sync...")
                val success = unifiedSyncService.performImmediateSync()
                
                Log.d(TAG, "Immediate sync result: ${if (success) "SUCCESS" else "FAILED"}")
                loadDebugInfo() // Refresh display
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to perform immediate sync", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun startUnifiedSync() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Manually starting unified sync service...")
                unifiedSyncService.startUnifiedSync()
                
                loadDebugInfo() // Refresh display
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start unified sync", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun stopUnifiedSync() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Manually stopping unified sync service...")
                unifiedSyncService.stopUnifiedSync()
                
                loadDebugInfo() // Refresh display
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop unified sync", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
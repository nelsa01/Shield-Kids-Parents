package com.shieldtechhub.shieldkids.features.app_management.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.WidgetSyncStatusBinding
import com.shieldtechhub.shieldkids.features.app_management.service.InventoryFingerprint
import com.shieldtechhub.shieldkids.features.app_management.service.SyncState
import com.shieldtechhub.shieldkids.features.app_management.service.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom widget to display comprehensive app sync status
 * Shows real-time sync progress, health indicators, and manual sync controls
 */
class SyncStatusWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {
    
    private val binding: WidgetSyncStatusBinding
    private val db = FirebaseFirestore.getInstance()
    private val widgetScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var childId: String = ""
    private var deviceId: String = ""
    private var syncStatusListener: ListenerRegistration? = null
    private var onManualSyncRequested: (() -> Unit)? = null
    private var rotationAnimation: Animation? = null
    
    // Current sync state
    private var currentSyncStatus: SyncStatus = SyncStatus.initial()
    private var currentFingerprint: InventoryFingerprint? = null
    private var isOnline: Boolean = true
    
    init {
        // Inflate the layout
        binding = WidgetSyncStatusBinding.inflate(LayoutInflater.from(context), this, true)
        
        // Initialize animations
        rotationAnimation = AnimationUtils.loadAnimation(context, R.anim.rotation_continuous)
        
        // Setup click listeners
        setupClickListeners()
        
        // Initial state
        updateUI()
    }
    
    /**
     * Normalize device ID to ensure it has the device_ prefix
     */
    private fun normalizeDeviceId(deviceId: String): String {
        return if (deviceId.startsWith("device_")) {
            deviceId // Already has prefix
        } else {
            "device_$deviceId" // Add prefix
        }
    }
    
    /**
     * Configure the widget for specific child and device
     */
    fun configure(childId: String, deviceId: String, onManualSyncRequested: (() -> Unit)? = null) {
        this.childId = childId
        this.deviceId = deviceId
        this.onManualSyncRequested = onManualSyncRequested
        
        // Start listening for sync status updates
        startSyncStatusListener()
    }
    
    /**
     * Update online/offline status
     */
    fun updateNetworkStatus(isOnline: Boolean) {
        this.isOnline = isOnline
        updateNetworkIndicator()
    }
    
    /**
     * Manually update sync status (for local sync operations)
     */
    fun updateSyncStatus(syncStatus: SyncStatus) {
        currentSyncStatus = syncStatus
        updateUI()
    }
    
    /**
     * Set up click listeners
     */
    private fun setupClickListeners() {
        binding.btnRefreshSync.setOnClickListener {
            if (!currentSyncStatus.isInProgress()) {
                onManualSyncRequested?.invoke()
                
                // Show loading animation briefly
                binding.ivSyncIcon.startAnimation(rotationAnimation)
                binding.btnRefreshSync.isEnabled = false
                
                // Re-enable after delay
                postDelayed({
                    binding.ivSyncIcon.clearAnimation()
                    binding.btnRefreshSync.isEnabled = true
                }, 2000)
            }
        }
        
        // Make the entire widget clickable for expanded details
        setOnClickListener {
            showSyncDetails()
        }
    }
    
    /**
     * Start listening for sync status updates from Firebase
     */
    private fun startSyncStatusListener() {
        if (childId.isEmpty() || deviceId.isEmpty()) return
        
        // Stop previous listener
        syncStatusListener?.remove()
        
        // Normalize device ID to ensure it has the device_ prefix
        val normalizedDeviceId = normalizeDeviceId(deviceId)
        
        android.util.Log.d("SyncStatusWidget", "Starting listener for path: children/$childId/devices/$normalizedDeviceId/data/appInventory")
        android.util.Log.d("SyncStatusWidget", "Original deviceId: '$deviceId', normalized: '$normalizedDeviceId'")
        
        val appInventoryRef = db.collection("children")
            .document(childId)
            .collection("devices")
            .document(normalizedDeviceId)
            .collection("data")
            .document("appInventory")
        
        syncStatusListener = appInventoryRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // Handle error
                updateSyncStatus(currentSyncStatus.withSyncFailure(error.message ?: "Firebase error"))
                return@addSnapshotListener
            }
            
            if (snapshot?.exists() == true) {
                android.util.Log.d("SyncStatusWidget", "✅ AppInventory document found with keys: ${snapshot.data?.keys}")
                
                widgetScope.launch {
                    try {
                        // Extract sync status from document
                        val syncStatusData = snapshot.get("syncStatus") as? Map<String, Any>
                        val fingerprintData = snapshot.get("inventoryFingerprint") as? Map<String, Any>
                        val summaryData = snapshot.get("summary") as? Map<String, Any>
                        val lastSyncTime = snapshot.getLong("lastSyncTime") ?: 0L
                        
                        android.util.Log.d("SyncStatusWidget", "Data found - syncStatus: ${syncStatusData != null}, summary: ${summaryData != null}, lastSync: $lastSyncTime")
                        
                        // Update sync status - create success status from available data
                        if (syncStatusData != null) {
                            val syncStatus = SyncStatus.fromFirebaseMap(syncStatusData)
                            currentSyncStatus = syncStatus
                        } else if (lastSyncTime > 0) {
                            // Create basic success status from timestamp
                            currentSyncStatus = SyncStatus.successful(lastSyncTime)
                        }
                        
                        // Update fingerprint
                        if (fingerprintData != null) {
                            currentFingerprint = InventoryFingerprint.fromFirebaseMap(fingerprintData)
                        }
                        
                        // Extract app count from summary
                        if (summaryData != null) {
                            val totalApps = (summaryData["totalApps"] as? Number)?.toInt() ?: 0
                            currentSyncStatus = currentSyncStatus.copy(totalAppsCount = totalApps)
                            android.util.Log.d("SyncStatusWidget", "Found $totalApps total apps in summary")
                        }
                        
                        updateUI()
                        
                    } catch (e: Exception) {
                        android.util.Log.e("SyncStatusWidget", "Error parsing document data", e)
                        updateSyncStatus(currentSyncStatus.withSyncFailure("Data parsing error: ${e.message}"))
                    }
                }
            } else {
                android.util.Log.w("SyncStatusWidget", "❌ AppInventory document does not exist at expected path")
                updateSyncStatus(currentSyncStatus.withSyncFailure("No app inventory data found. Child device may not be synced."))
            }
        }
    }
    
    /**
     * Update all UI elements based on current sync status
     */
    private fun updateUI() {
        updateStatusIndicator()
        updateProgressBar()
        updateTimestamps()
        updateHealthIndicators()
        updateNetworkIndicator()
        updateErrorMessage()
    }
    
    /**
     * Update the main status indicator and text
     */
    private fun updateStatusIndicator() {
        val status = currentSyncStatus.status
        val statusColor = when (status) {
            SyncState.SUCCESS -> ContextCompat.getColor(context, R.color.status_success)
            SyncState.IN_PROGRESS, SyncState.RETRYING -> ContextCompat.getColor(context, R.color.status_warning)
            SyncState.FAILED -> ContextCompat.getColor(context, R.color.status_error)
            SyncState.NO_NETWORK -> ContextCompat.getColor(context, R.color.status_warning)
            SyncState.IDLE -> ContextCompat.getColor(context, R.color.text_secondary)
        }
        
        binding.viewStatusIndicator.backgroundTintList = 
            ContextCompat.getColorStateList(context, android.R.color.transparent)
        binding.viewStatusIndicator.setBackgroundColor(statusColor)
        
        binding.tvSyncStatus.text = currentSyncStatus.getStatusMessage()
        
        // Update app count
        if (currentSyncStatus.totalAppsCount > 0) {
            binding.tvAppCount.text = "${currentSyncStatus.totalAppsCount} apps"
            binding.tvAppCount.visibility = View.VISIBLE
        } else {
            binding.tvAppCount.visibility = View.GONE
        }
        
        // Update icon based on status
        when (status) {
            SyncState.IN_PROGRESS, SyncState.RETRYING -> {
                binding.ivSyncIcon.setImageResource(R.drawable.ic_sync)
                if (binding.ivSyncIcon.animation == null) {
                    binding.ivSyncIcon.startAnimation(rotationAnimation)
                }
            }
            SyncState.SUCCESS -> {
                binding.ivSyncIcon.clearAnimation()
                binding.ivSyncIcon.setImageResource(R.drawable.ic_check_circle)
            }
            SyncState.FAILED -> {
                binding.ivSyncIcon.clearAnimation()
                binding.ivSyncIcon.setImageResource(R.drawable.ic_error)
            }
            else -> {
                binding.ivSyncIcon.clearAnimation()
                binding.ivSyncIcon.setImageResource(R.drawable.ic_sync)
            }
        }
    }
    
    /**
     * Update progress bar visibility and progress
     */
    private fun updateProgressBar() {
        if (currentSyncStatus.isInProgress()) {
            binding.progressBarSync.visibility = View.VISIBLE
            binding.tvProgressText.visibility = View.VISIBLE
            
            val progress = currentSyncStatus.getProgressPercentage()
            binding.progressBarSync.progress = progress
            
            binding.tvProgressText.text = "Syncing apps... (${currentSyncStatus.syncedAppsCount}/${currentSyncStatus.totalAppsCount})"
        } else {
            binding.progressBarSync.visibility = View.GONE
            binding.tvProgressText.visibility = View.GONE
        }
    }
    
    /**
     * Update timestamp displays
     */
    private fun updateTimestamps() {
        val lastSyncDate = currentSyncStatus.getLastSyncDate()
        if (lastSyncDate != null) {
            val timeAgo = formatTimeAgo(lastSyncDate)
            binding.tvLastSyncTime.text = timeAgo
        } else {
            binding.tvLastSyncTime.text = "Never"
        }
    }
    
    /**
     * Update health indicators
     */
    private fun updateHealthIndicators() {
        // Sync health
        val isHealthy = currentSyncStatus.isSuccessful() && !currentSyncStatus.isStale()
        
        if (isHealthy) {
            binding.ivHealthIndicator.setImageResource(R.drawable.ic_check_circle)
            binding.ivHealthIndicator.imageTintList = 
                ContextCompat.getColorStateList(context, R.color.status_success)
            binding.tvSyncHealth.text = "Healthy"
        } else if (currentSyncStatus.isStale()) {
            binding.ivHealthIndicator.setImageResource(R.drawable.ic_warning)
            binding.ivHealthIndicator.imageTintList = 
                ContextCompat.getColorStateList(context, R.color.status_warning)
            binding.tvSyncHealth.text = "Stale"
        } else {
            binding.ivHealthIndicator.setImageResource(R.drawable.ic_error)
            binding.ivHealthIndicator.imageTintList = 
                ContextCompat.getColorStateList(context, R.color.status_error)
            binding.tvSyncHealth.text = "Issues"
        }
    }
    
    /**
     * Update network status indicator
     */
    private fun updateNetworkIndicator() {
        if (isOnline && currentSyncStatus.networkConnected) {
            binding.ivNetworkIndicator.setImageResource(R.drawable.ic_wifi)
            binding.ivNetworkIndicator.imageTintList = 
                ContextCompat.getColorStateList(context, R.color.status_success)
            binding.tvNetworkStatus.text = "Online"
        } else {
            binding.ivNetworkIndicator.setImageResource(R.drawable.ic_wifi_off)
            binding.ivNetworkIndicator.imageTintList = 
                ContextCompat.getColorStateList(context, R.color.status_error)
            binding.tvNetworkStatus.text = "Offline"
        }
    }
    
    /**
     * Update error message visibility and content
     */
    private fun updateErrorMessage() {
        if (currentSyncStatus.hasFailed() && !currentSyncStatus.errorMessage.isNullOrEmpty()) {
            binding.tvErrorMessage.text = currentSyncStatus.errorMessage
            binding.tvErrorMessage.visibility = View.VISIBLE
        } else if (currentSyncStatus.isStale(10)) {
            binding.tvErrorMessage.text = "Data may be outdated. Last sync was ${formatTimeAgo(currentSyncStatus.getLastSyncDate())}"
            binding.tvErrorMessage.visibility = View.VISIBLE
        } else {
            binding.tvErrorMessage.visibility = View.GONE
        }
    }
    
    /**
     * Show detailed sync information dialog
     */
    private fun showSyncDetails() {
        val details = buildString {
            appendLine("Sync Status Details")
            appendLine()
            appendLine("Status: ${currentSyncStatus.status}")
            appendLine("Total Apps: ${currentSyncStatus.totalAppsCount}")
            appendLine("Synced Apps: ${currentSyncStatus.syncedAppsCount}")
            
            if (currentSyncStatus.lastSuccessfulSyncTime > 0) {
                appendLine("Last Success: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(currentSyncStatus.lastSuccessfulSyncTime))}")
            }
            
            if (currentSyncStatus.syncDurationMs > 0) {
                appendLine("Duration: ${currentSyncStatus.syncDurationMs}ms")
            }
            
            if (!currentSyncStatus.appInventoryHash.isEmpty()) {
                appendLine("Hash: ${currentSyncStatus.appInventoryHash.take(8)}...")
            }
            
            if (currentSyncStatus.retryAttempts > 0) {
                appendLine("Retries: ${currentSyncStatus.retryAttempts}/${currentSyncStatus.maxRetryAttempts}")
            }
        }
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Sync Status")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Format time ago string
     */
    private fun formatTimeAgo(date: Date?): String {
        if (date == null) return "Never"
        
        val now = System.currentTimeMillis()
        val diff = now - date.time
        
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        syncStatusListener?.remove()
        widgetScope.cancel()
        binding.ivSyncIcon.clearAnimation()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }
}
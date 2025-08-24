package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.databinding.ActivityChildModeBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.app_management.service.ChildAppSyncService
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ChildModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChildModeBinding
    private lateinit var deviceStateManager: DeviceStateManager
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var policySyncManager: PolicySyncManager
    private lateinit var screenTimeCollector: ScreenTimeCollector
    private lateinit var appInventoryManager: AppInventoryManager
    private lateinit var childAppSyncService: ChildAppSyncService
    
    // Device status monitoring
    private val db = FirebaseFirestore.getInstance()
    private val statusHandler = Handler(Looper.getMainLooper())
    private var statusCheckRunnable: Runnable? = null
    private var isStatusMonitoringActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        deviceStateManager = DeviceStateManager(this)
        policyManager = PolicyEnforcementManager.getInstance(this)
        policySyncManager = PolicySyncManager.getInstance(this)
        screenTimeCollector = ScreenTimeCollector.getInstance(this)
        appInventoryManager = AppInventoryManager(this)
        childAppSyncService = ChildAppSyncService.getInstance(this)

        // Verify this is actually a child device
        if (!deviceStateManager.isChildDevice()) {
            // This shouldn't happen, but if it does, go back to splash
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }

        setupUI()
        loadChildInfo()
        setupClickListeners()
        startMonitoringServices()
    }

    private fun setupUI() {
        // Hide action bar for child mode
        supportActionBar?.hide()
        
        // Set welcome message
        val childInfo = deviceStateManager.getChildDeviceInfo()
        binding.tvWelcome.text = "Hi ${childInfo?.childName ?: "there"}!"
        binding.tvSubtitle.text = "Your device is protected by Shield Kids"
    }

    private fun loadChildInfo() {
        val childInfo = deviceStateManager.getChildDeviceInfo() ?: return

        // Display child information
        binding.tvChildName.text = childInfo.childName
        binding.tvParentEmail.text = "Managed by: ${childInfo.parentEmail}"
        
        // Show link timestamp
        val linkDate = DateFormat.getDateTimeInstance().format(Date(childInfo.linkTimestamp))
        binding.tvLinkDate.text = "Protected since: $linkDate"

        // Load screen time information
        loadScreenTimeInfo()
        
        // Load app statistics
        loadAppStats()
    }

    private fun loadScreenTimeInfo() {
        lifecycleScope.launch {
            try {
                val todayUsage = screenTimeCollector.collectDailyUsageData()
                val totalMinutes = todayUsage.totalScreenTimeMs / (1000 * 60)
                val hours = totalMinutes / 60
                val minutes = totalMinutes % 60
                
                binding.tvScreenTime.text = if (hours > 0) {
                    "${hours}h ${minutes}m today"
                } else {
                    "${minutes}m today"
                }
                
                // Show most used app
                val topApp = todayUsage.appUsageData.maxByOrNull { it.totalTimeMs }
                if (topApp != null) {
                    val appMinutes = topApp.totalTimeMs / (1000 * 60)
                    binding.tvTopApp.text = "Most used: ${topApp.appName} (${appMinutes}m)"
                } else {
                    binding.tvTopApp.text = "No app usage today"
                }
                
            } catch (e: Exception) {
                binding.tvScreenTime.text = "Screen time unavailable"
                binding.tvTopApp.text = ""
            }
        }
    }

    private fun loadAppStats() {
        lifecycleScope.launch {
            try {
                val result = appInventoryManager.refreshAppInventory()
                
                binding.tvTotalApps.text = "${result.totalApps} apps installed"
                binding.tvUserApps.text = "${result.userApps} user apps"
                
                // Show blocked apps count (placeholder for now)
                // This would integrate with PolicyEnforcementManager to count blocked apps
                binding.tvBlockedApps.text = "Protection active"
                
            } catch (e: Exception) {
                binding.tvTotalApps.text = "Apps unavailable"
                binding.tvUserApps.text = ""
                binding.tvBlockedApps.text = ""
            }
        }
    }

    private fun setupClickListeners() {
        // Emergency contact button
        binding.btnEmergencyContact.setOnClickListener {
            showEmergencyContactDialog()
        }

        // Request permission button (for requesting app access, etc.)
        binding.btnRequestPermission.setOnClickListener {
            showRequestPermissionDialog()
        }

        // Device info button
        binding.btnDeviceInfo.setOnClickListener {
            showDeviceInfoDialog()
        }

        // Help button
        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }

        // Settings button (limited child settings)
        binding.btnChildSettings.setOnClickListener {
            showChildSettingsDialog()
        }
    }

    private fun showEmergencyContactDialog() {
        val childInfo = deviceStateManager.getChildDeviceInfo()
        
        AlertDialog.Builder(this)
            .setTitle("Emergency Contact")
            .setMessage("Need help? You can contact your parent at:\n\n${childInfo?.parentEmail}\n\nFor real emergencies, always call emergency services.")
            .setPositiveButton("OK", null)
            .setNeutralButton("Call Emergency", null) // Could implement emergency calling
            .show()
    }

    private fun showRequestPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Request Permission")
            .setMessage("What would you like to request permission for?")
            .setPositiveButton("Install App") { _, _ ->
                // TODO: Implement app installation request
                Toast.makeText(this, "App installation request sent to parent", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("More Screen Time") { _, _ ->
                // TODO: Implement screen time extension request
                Toast.makeText(this, "Screen time extension request sent to parent", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeviceInfoDialog() {
        val deviceInfo = deviceStateManager.getDeviceStateSummary()
        
        AlertDialog.Builder(this)
            .setTitle("Device Information")
            .setMessage(deviceInfo)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHelpDialog() {
        val helpText = """
            This device is protected by Shield Kids parental controls.
            
            What this means:
            • Some apps may be blocked or time-limited
            • Your screen time is monitored for your safety
            • Your parent can see your app usage
            • Emergency features are always available
            
            If you need help or have questions, contact your parent.
        """.trimIndent()
        
        AlertDialog.Builder(this)
            .setTitle("About Shield Kids")
            .setMessage(helpText)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showChildSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setMessage("What would you like to do?")
            .setPositiveButton("Check Permissions") { _, _ ->
                // Open system permissions
                startActivity(Intent(this, PermissionRequestActivity::class.java))
            }
            .setNeutralButton("View Apps") { _, _ ->
                // Open app list (read-only for children)
                startActivity(Intent(this, AppListActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startMonitoringServices() {
        // Start policy sync listener to receive policies from parent
        policySyncManager.startPolicySync()
        android.util.Log.i("ChildModeActivity", "Started policy sync listener")
        
        // Start app inventory sync to send child's apps to Firebase
        childAppSyncService.startSync()
        android.util.Log.i("ChildModeActivity", "Started child app sync service")
        
        // Ensure policy enforcement is active
        if (!policyManager.validatePolicyIntegrity()) {
            // Policy integrity check failed - notify parent
            Toast.makeText(this, "Protection system needs attention", Toast.LENGTH_LONG).show()
        }
        
        // Start screen time collection (collection happens automatically when called)
        lifecycleScope.launch {
            try {
                // Trigger initial collection
                screenTimeCollector.collectDailyUsageData()
                
                // Perform immediate app sync on startup
                val syncSuccess = childAppSyncService.performImmediateSync()
                if (syncSuccess) {
                    android.util.Log.i("ChildModeActivity", "Initial app sync completed successfully")
                } else {
                    android.util.Log.w("ChildModeActivity", "Initial app sync failed")
                }
                
            } catch (e: Exception) {
                // Screen time collection failed - continue but log
                android.util.Log.w("ChildModeActivity", "Failed to start screen time collection", e)
            }
        }
        
        // Start device status monitoring
        startDeviceStatusMonitoring()
    }

    override fun onResume() {
        super.onResume()
        
        // Refresh data when returning to app
        loadChildInfo()
        
        // Verify device is still linked
        if (!deviceStateManager.isChildDevice()) {
            // Device was unlinked, return to splash
            startActivity(Intent(this, SplashActivity::class.java))
            finish()
            return
        }
        
        // Resume monitoring when app comes to foreground
        if (!isStatusMonitoringActive) {
            startDeviceStatusMonitoring()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop monitoring when app goes to background to save battery
        stopDeviceStatusMonitoring()
    }

    override fun onBackPressed() {
        // Prevent going back from child mode
        // Children should use home button to exit app
        moveTaskToBack(true)
    }

    // Hide the device unlinking option from children
    // Only parents should be able to unlink devices
    private fun showUnlinkWarning() {
        AlertDialog.Builder(this)
            .setTitle("Device Protection")
            .setMessage("This device is protected by your parent. Only your parent can change these settings.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    /**
     * Start monitoring device status to detect if parent removes this device
     */
    private fun startDeviceStatusMonitoring() {
        isStatusMonitoringActive = true
        
        statusCheckRunnable = object : Runnable {
            override fun run() {
                if (isStatusMonitoringActive) {
                    checkDeviceStillExists()
                    // Check every 30 seconds
                    statusHandler.postDelayed(this, 30000)
                }
            }
        }
        
        // Start immediate check
        statusCheckRunnable?.let { statusHandler.post(it) }
        
        android.util.Log.d("ChildModeActivity", "Device status monitoring started")
    }
    
    /**
     * Stop device status monitoring
     */
    private fun stopDeviceStatusMonitoring() {
        isStatusMonitoringActive = false
        statusCheckRunnable?.let { statusHandler.removeCallbacks(it) }
        android.util.Log.d("ChildModeActivity", "Device status monitoring stopped")
    }
    
    /**
     * Check if this device still exists in the parent's child document
     */
    private fun checkDeviceStillExists() {
        val childInfo = deviceStateManager.getChildDeviceInfo() ?: return
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        
        db.collection("children").document(childInfo.childId)
            .get()
            .addOnSuccessListener { childDoc ->
                if (childDoc.exists()) {
                    val devices = childDoc.get("devices") as? HashMap<String, Any> ?: HashMap()
                    
                    // Check if our device ID still exists in the devices map
                    var deviceExists = false
                    for ((key, value) in devices) {
                        when (value) {
                            is String -> {
                                // Legacy format - device ID is the key
                                if (key == deviceId) {
                                    deviceExists = true
                                    break
                                }
                            }
                            is Map<*, *> -> {
                                // New format - check deviceId inside the map
                                val deviceInfo = value as Map<String, Any>
                                if (deviceInfo["deviceId"] == deviceId) {
                                    deviceExists = true
                                    break
                                }
                            }
                        }
                    }
                    
                    if (!deviceExists) {
                        // Device has been removed by parent - auto logout
                        handleDeviceRemoved()
                    }
                } else {
                    // Child document doesn't exist - likely deleted
                    handleDeviceRemoved()
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.w("ChildModeActivity", "Failed to check device status", e)
                // Don't logout on network errors - could be temporary
            }
    }
    
    /**
     * Handle when device is removed by parent
     */
    private fun handleDeviceRemoved() {
        android.util.Log.i("ChildModeActivity", "Device was removed by parent - logging out")
        
        // Stop monitoring
        stopDeviceStatusMonitoring()
        
        // Show notification to child
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Device Unlinked")
                .setMessage("Your parent has removed this device from protection. The app will now restart.")
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    performAutoLogout()
                }
                .show()
        }
    }
    
    /**
     * Perform automatic logout and reset device state
     */
    private fun performAutoLogout() {
        // Reset device state to unlinked
        deviceStateManager.resetDeviceState()
        
        // Clear any stored user data
        getSharedPreferences("screen_time_data", MODE_PRIVATE).edit().clear().apply()
        
        // Stop any running services
        try {
            val serviceManager = com.shieldtechhub.shieldkids.common.base.ServiceManager(this)
            serviceManager.stopMonitoringService()
        } catch (e: Exception) {
            android.util.Log.w("ChildModeActivity", "Failed to stop monitoring service", e)
        }
        
        // Navigate back to splash/login
        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Stop all sync services
        try {
            policySyncManager.stopPolicySync()
            childAppSyncService.stopSync()
            android.util.Log.i("ChildModeActivity", "Stopped policy and app sync services")
        } catch (e: Exception) {
            android.util.Log.w("ChildModeActivity", "Error stopping sync services", e)
        }
        
        // Stop monitoring when activity is destroyed
        stopDeviceStatusMonitoring()
    }
}
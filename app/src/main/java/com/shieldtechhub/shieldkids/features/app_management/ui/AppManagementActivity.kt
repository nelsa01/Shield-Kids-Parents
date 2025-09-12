package com.shieldtechhub.shieldkids.features.app_management.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityAppManagementBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import kotlinx.coroutines.launch
import com.shieldtechhub.shieldkids.features.screen_time.ui.DeviceScreenTimeActivity
import com.shieldtechhub.shieldkids.features.policy.ui.CategoryPoliciesActivity
import com.shieldtechhub.shieldkids.features.policy.ui.CategoryPolicyActivity
import com.shieldtechhub.shieldkids.features.device_setup.ui.DeviceAdminSetupActivity
import kotlinx.coroutines.tasks.await

class AppManagementActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAppManagementBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var policySyncManager: PolicySyncManager
    private lateinit var appInventoryManager: AppInventoryManager
    
    private var deviceId: String = ""
    private var childId: String = ""
    private var devicePolicy: DevicePolicy? = null
    private var childApps: List<AppInfo> = emptyList() // Child's apps from Firebase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        childId = intent.getStringExtra("childId") ?: ""
        
        Log.d("AppManagementActivity", "Received deviceId: '$deviceId', childId: '$childId'")
        
        if (deviceId.isEmpty() || childId.isEmpty()) {
            Log.e("AppManagementActivity", "Missing required parameters - deviceId: '$deviceId', childId: '$childId'")
            Toast.makeText(this, "Missing device or child information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize managers
        policyManager = PolicyEnforcementManager.getInstance(this)
        policySyncManager = PolicySyncManager.getInstance(this)
        appInventoryManager = AppInventoryManager(this)
        
        setupUI()
        checkDeviceAdminStatus()
        loadChildAppsFromFirebase()
        loadCurrentPolicy()
    }
    
    private fun setupUI() {
        // Toolbar
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        
        // App monitoring toggle
        binding.switchAppMonitoring.setOnCheckedChangeListener { _, isChecked ->
            updateAppMonitoring(isChecked)
        }
        
        // Age restrictions section
        binding.layoutAgeRestrictions.setOnClickListener {
            Toast.makeText(this, "Age restrictions configuration coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        // Screen Time Limits section
        binding.layoutScreenTime.setOnClickListener {
            val intent = Intent(this, DeviceScreenTimeActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            intent.putExtra("childId", childId)
            startActivity(intent)
        }
        
        // Apps exclusions section
        binding.layoutAppExclusions.setOnClickListener {
            val intent = Intent(this, AppExclusionsActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            intent.putExtra("childId", childId)
            startActivity(intent)
        }
        
        // Category policies section
        binding.layoutCategoryPolicies.setOnClickListener {
            val intent = Intent(this, CategoryPoliciesActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            intent.putExtra("childId", childId)
            startActivity(intent)
        }
        
        // View All Apps section
        binding.layoutViewAllApps.setOnClickListener {
            val intent = Intent(this, com.shieldtechhub.shieldkids.features.app_management.AllAppsActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            intent.putExtra("childId", childId)
            startActivity(intent)
        }
        
        // Category sections
        setupCategoryClickListeners()
    }
    
    private fun setupCategoryClickListeners() {
        val categoryClickListener = { category: AppCategory ->
            val intent = Intent(this, CategoryPolicyActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            intent.putExtra("childId", childId)
            intent.putExtra("category", category.name)
            startActivity(intent)
        }
        
        // Category sections removed - all functionality moved to unified Manage Apps screen
        
        // Temporarily disabled duplicate browser sections to fix compilation
        // binding.layoutBrowsers3.setOnClickListener { categoryClickListener(AppCategory.BROWSERS) }
        // binding.layoutBrowsers4.setOnClickListener { categoryClickListener(AppCategory.BROWSERS) }
        // binding.layoutBrowsers5.setOnClickListener { categoryClickListener(AppCategory.BROWSERS) }
    }
    
    private fun loadCurrentPolicy() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Load current device policy
                val activePolicies = policyManager.activePolicies.value
                Log.d("AppManagement", "🔍 Policy lookup - deviceId: '$deviceId'")
                Log.d("AppManagement", "🔍 Available policy keys: ${activePolicies.keys}")
                
                // Try both formats for device ID lookup
                val loadedPolicy = activePolicies[deviceId] ?: activePolicies["device_$deviceId"]
                
                Log.d("AppManagement", "🔍 Found policy: ${loadedPolicy != null}")
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    devicePolicy = loadedPolicy
                    
                    // Update UI with current policy state
                    updateUIWithPolicy(devicePolicy)
                    
                    // Update category policies status
                    updateCategoryPoliciesStatus()
                    
                    // Load app counts for each category
                    updateCategoryCounts()
                }
                
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@AppManagementActivity, "Failed to load policy: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateScreenTimeStatus() {
        val policy = devicePolicy
        val status = if (policy != null) {
            val weekdayHours = policy.weekdayScreenTime / 60
            val weekendHours = policy.weekendScreenTime / 60
            
            val weekdayText = if (policy.weekdayScreenTime == 0L) "No limit" else "${weekdayHours}h"
            val weekendText = if (policy.weekendScreenTime == 0L) "No limit" else "${weekendHours}h"
            
            when {
                policy.weekdayScreenTime > 0 && policy.weekendScreenTime > 0 -> 
                    "$weekdayText weekday, $weekendText weekend"
                policy.weekdayScreenTime > 0 -> "Weekdays: $weekdayText"
                policy.weekendScreenTime > 0 -> "Weekends: $weekendText"
                policy.bedtimeStart != null -> "Bedtime: ${policy.bedtimeStart}-${policy.bedtimeEnd}"
                else -> "No limits set"
            }
        } else {
            "Not configured"
        }
        
        binding.tvScreenTimeStatus.text = status
    }
    
    private fun updateCategoryPoliciesStatus() {
        val policy = devicePolicy
        val blockedCategories = policy?.blockedCategories ?: emptyList()
        val totalCategories = AppCategory.entries.filter { it != AppCategory.OTHER }.size
        
        val status = when {
            blockedCategories.isEmpty() -> "All allowed"
            blockedCategories.size == totalCategories -> "All blocked"
            else -> "${blockedCategories.size} blocked"
        }
        
        binding.tvCategoryPoliciesStatus.text = status
        
        Log.d("AppManagement", "Category policies status: $status (${blockedCategories.size}/$totalCategories)")
    }

    private fun updateUIWithPolicy(policy: DevicePolicy?) {
        Log.d("AppManagement", "📱 updateUIWithPolicy - policy exists: ${policy != null}")
        
        // Update screen time status
        updateScreenTimeStatus()
        
        // Temporarily remove listener to prevent unwanted toast messages
        binding.switchAppMonitoring.setOnCheckedChangeListener(null)
        
        if (policy != null) {
            // App monitoring is enabled if we have ANY restrictions active
            val hasAppPolicies = policy.appPolicies.isNotEmpty()
            val hasBlockedCategories = policy.blockedCategories.isNotEmpty()
            val hasScreenTimeLimits = policy.weekdayScreenTime > 0 || policy.weekendScreenTime > 0
            val hasBedtimeRestrictions = !policy.bedtimeStart.isNullOrEmpty() && !policy.bedtimeEnd.isNullOrEmpty()
            val hasDeviceRestrictions = policy.cameraDisabled || policy.installationsBlocked || policy.keyguardRestrictions > 0
            
            val isMonitoringActive = hasAppPolicies || hasBlockedCategories || hasScreenTimeLimits || hasBedtimeRestrictions || hasDeviceRestrictions
            
            Log.d("AppManagement", "📱 Policy restrictions - Apps: $hasAppPolicies, Categories: $hasBlockedCategories, ScreenTime: $hasScreenTimeLimits, Bedtime: $hasBedtimeRestrictions, Device: $hasDeviceRestrictions")
            Log.d("AppManagement", "📱 Monitoring active: $isMonitoringActive")
            
            binding.switchAppMonitoring.isChecked = isMonitoringActive
            
            // Update exclusions count
            val excludedApps = policy.appPolicies.count { it.action == AppPolicy.Action.ALLOW }
            binding.tvAppExclusionsCount.text = excludedApps.toString()
        } else {
            // Default state - monitoring off, no exclusions
            Log.d("AppManagement", "📱 No policy found, setting monitoring switch to OFF")
            binding.switchAppMonitoring.isChecked = false
            binding.tvAppExclusionsCount.text = "0"
        }
        
        // Restore listener after UI update
        binding.switchAppMonitoring.setOnCheckedChangeListener { _, isChecked ->
            updateAppMonitoring(isChecked)
        }
    }
    
    /**
     * Load child's app inventory from Firebase
     */
    private fun loadChildAppsFromFirebase() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                Log.d("AppManagement", "Loading apps for child: $childId, device: $deviceId")
                
                // Use simplified device ID format: device_{deviceId} (no timestamp)
                val expectedDeviceDocId = if (deviceId.startsWith("device_")) deviceId else "device_$deviceId"
                
                val appInventoryRef = db.collection("children")
                    .document(childId)
                    .collection("devices")
                    .document(expectedDeviceDocId)
                    .collection("data")
                    .document("appInventory")
                
                Log.d("AppManagement", "Using path: children/$childId/devices/$expectedDeviceDocId/data/appInventory")
                
                val snapshot = appInventoryRef.get().await()
                
                Log.d("AppManagement", "App inventory document exists: ${snapshot.exists()}")
                
                if (!snapshot.exists()) {
                    Log.w("AppManagement", "App inventory document not found")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        showChildAppsNotAvailable()
                    }
                    return@launch
                }
                
                val appsData = snapshot.get("apps") as? List<Map<String, Any>>
                
                if (appsData != null) {
                    val loadedApps = appsData.mapNotNull { appData ->
                        try {
                            AppInfo(
                                packageName = appData["packageName"] as? String ?: "",
                                name = appData["name"] as? String ?: "Unknown",
                                version = appData["version"] as? String ?: "1.0",
                                versionCode = (appData["versionCode"] as? Number)?.toLong() ?: 1L,
                                category = try { 
                                    AppCategory.valueOf(appData["category"] as? String ?: "OTHER") 
                                } catch (e: Exception) { AppCategory.OTHER },
                                isSystemApp = appData["isSystemApp"] as? Boolean ?: false,
                                isEnabled = appData["isEnabled"] as? Boolean ?: true,
                                installTime = (appData["installTime"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                lastUpdateTime = (appData["lastUpdateTime"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                targetSdkVersion = (appData["targetSdkVersion"] as? Number)?.toInt() ?: 1,
                                permissions = (appData["permissions"] as? List<String>) ?: emptyList(),
                                icon = null, // No icon from Firebase
                                dataDir = "" // No dataDir from Firebase
                            )
                        } catch (e: Exception) {
                            Log.w("AppManagement", "Failed to parse app data: $appData", e)
                            null
                        }
                    }
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        childApps = loadedApps
                        Log.i("AppManagement", "Loaded ${childApps.size} apps from child device")
                        updateCategoryCounts()
                    }
                    
                    // Load screen time data if available
                    loadChildScreenTimeData(snapshot)
                    
                } else {
                    Log.w("AppManagement", "No apps data found in Firebase")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        childApps = emptyList()
                        showChildAppsNotAvailable()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("AppManagement", "Failed to load child apps from Firebase", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@AppManagementActivity, "Failed to load child's apps: ${e.message}", Toast.LENGTH_SHORT).show()
                    childApps = emptyList()
                }
            }
        }
    }
    
    /**
     * Show message when child apps are not available
     */
    private fun showChildAppsNotAvailable() {
        Toast.makeText(this, "Child's app list not yet synced. Please ensure the child device is online and try again.", Toast.LENGTH_LONG).show()
        
        // Category counts removed - all functionality moved to unified Manage Apps screen
    }
    
    /**
     * Load and display child's screen time data from Firebase
     */
    private fun loadChildScreenTimeData(snapshot: com.google.firebase.firestore.DocumentSnapshot) {
        try {
            val screenTimeData = snapshot.get("screenTime") as? Map<String, Any>
            
            if (screenTimeData != null) {
                val todayData = screenTimeData["today"] as? Map<String, Any>
                val yesterdayData = screenTimeData["yesterday"] as? Map<String, Any>
                
                // Display today's screen time
                if (todayData != null) {
                    val totalTimeMs = todayData["totalScreenTimeMs"] as? Long ?: 0L
                    val screenUnlocks = todayData["screenUnlocks"] as? Long ?: 0L
                    val topApps = todayData["topApps"] as? List<Map<String, Any>> ?: emptyList()
                    
                    runOnUiThread {
                        displayScreenTimeData(totalTimeMs, screenUnlocks.toInt(), topApps)
                    }
                    
                    Log.d("AppManagement", "Loaded screen time: ${formatDuration(totalTimeMs)}, ${screenUnlocks} unlocks, ${topApps.size} top apps")
                } else {
                    Log.d("AppManagement", "No screen time data available for today")
                }
            } else {
                Log.d("AppManagement", "No screen time data found in Firebase document")
            }
            
        } catch (e: Exception) {
            Log.e("AppManagement", "Failed to parse screen time data", e)
        }
    }
    
    /**
     * Display screen time data in UI
     */
    private fun displayScreenTimeData(totalTimeMs: Long, screenUnlocks: Int, topApps: List<Map<String, Any>>) {
        // Update screen time display if we have these UI elements
        // For now, just log the data - UI elements would need to be added to layout
        val formattedTime = formatDuration(totalTimeMs)
        val topAppName = if (topApps.isNotEmpty()) {
            topApps[0]["appName"] as? String ?: "Unknown"
        } else "No apps used"
        
        Log.i("AppManagement", "Child's screen time today: $formattedTime ($screenUnlocks unlocks, most used: $topAppName)")
        
        // TODO: Update actual UI elements when screen time display is added to layout
        // Example: binding.tvScreenTimeToday.text = formattedTime
        // Example: binding.tvScreenUnlocks.text = "$screenUnlocks unlocks today"
        // Example: binding.tvMostUsedApp.text = "Most used: $topAppName"
    }
    
    /**
     * Format duration from milliseconds to readable string
     */
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    private fun updateCategoryCounts() {
        try {
            // Count CHILD's apps by category (not parent's apps)
            val browserCount = childApps.count { it.category == AppCategory.BROWSERS }
            val shoppingCount = childApps.count { it.category == AppCategory.SHOPPING }
            val gamesCount = childApps.count { it.category == AppCategory.GAMES }
            val socialCount = childApps.count { it.category == AppCategory.SOCIAL }
            val educationalCount = childApps.count { it.category == AppCategory.EDUCATIONAL }
            val otherCount = childApps.count { it.category == AppCategory.OTHER }
            
            Log.d("AppManagement", "Category counts - Browsers: $browserCount, Shopping: $shoppingCount, Games: $gamesCount, Social: $socialCount, Other: $otherCount")
            
            // Update UI on main thread
            runOnUiThread {
                // Update total apps count for "Manage Apps" section
                binding.tvAllAppsCount.text = childApps.size.toString()
            }
            
        } catch (e: Exception) {
            Log.e("AppManagement", "Failed to update category counts", e)
            runOnUiThread {
                Toast.makeText(this@AppManagementActivity, "Failed to load app counts", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateAppMonitoring(enabled: Boolean) {
        Log.d("AppManagement", "🔄 updateAppMonitoring called with enabled=$enabled")
        lifecycleScope.launch {
            try {
                if (enabled) {
                    // Show immediate feedback
                    Toast.makeText(this@AppManagementActivity, "Enabling app monitoring...", Toast.LENGTH_SHORT).show()
                    
                    // Perform heavy operations on background thread
                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // Preserve existing policy restrictions or create new one with defaults
                            val policy = devicePolicy ?: DevicePolicy.createDefault(deviceId)
                            
                            // Apply policy locally first
                            val localSuccess = policyManager.applyDevicePolicy(deviceId, policy)
                            if (localSuccess) {
                                // Then sync to Firebase for child device to receive
                                val firebaseSuccess = policySyncManager.savePolicyToFirebase(childId, deviceId, policy)
                                
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (firebaseSuccess) {
                                        devicePolicy = policy
                                        Toast.makeText(this@AppManagementActivity, "App monitoring enabled", Toast.LENGTH_SHORT).show()
                                        Log.i("AppManagement", "Policy applied locally and sent to Firebase for child: $childId, device: $deviceId")
                                    } else {
                                        Toast.makeText(this@AppManagementActivity, "Settings saved locally but failed to sync to child device", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    binding.switchAppMonitoring.isChecked = false
                                    Toast.makeText(this@AppManagementActivity, "Failed to apply policy locally", Toast.LENGTH_SHORT).show()
                                }
                            }
                            
                        } catch (e: Exception) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                binding.switchAppMonitoring.isChecked = false
                                Toast.makeText(this@AppManagementActivity, "Failed to update monitoring: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    // Disable app monitoring by clearing policies
                    policyManager.clearAllPolicies()
                    devicePolicy = null
                    Toast.makeText(this@AppManagementActivity, "App monitoring disabled", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.switchAppMonitoring.isChecked = !enabled
                Toast.makeText(this@AppManagementActivity, "Failed to update monitoring: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning from other screens
        checkDeviceAdminStatus()
        loadCurrentPolicy()
    }
    
    private fun checkDeviceAdminStatus() {
        val deviceAdminManager = com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager(this)
        if (!deviceAdminManager.isDeviceAdminActive()) {
            // Temporarily remove listener to prevent unwanted toast
            val currentListener = binding.switchAppMonitoring.hasOnClickListeners()
            binding.switchAppMonitoring.setOnCheckedChangeListener(null)
            
            // Disable app monitoring switch if device admin is not active
            binding.switchAppMonitoring.isEnabled = false
            binding.switchAppMonitoring.isChecked = false
            
            // Restore listener if it was set
            binding.switchAppMonitoring.setOnCheckedChangeListener { _, isChecked ->
                updateAppMonitoring(isChecked)
            }
            
            // Show status text
            binding.tvDeviceAdminStatus.visibility = android.view.View.VISIBLE
            binding.tvDeviceAdminStatus.text = "⚠️ Device admin permissions required. Tap here to enable."
            binding.tvDeviceAdminStatus.setOnClickListener {
                showDeviceAdminRequiredDialog()
            }
        } else {
            binding.switchAppMonitoring.isEnabled = true
            binding.tvDeviceAdminStatus.visibility = android.view.View.GONE
        }
    }
    
    private fun showDeviceAdminRequiredDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Device Admin Required")
            .setMessage("App monitoring requires device administrator permissions to function properly.\n\nThis allows Shield Kids to:\n• Block harmful apps\n• Monitor app usage\n• Enforce time limits\n• Protect your child's device\n\nWould you like to enable device admin now?")
            .setPositiveButton("Enable Now") { _, _ ->
                val intent = Intent(this, DeviceAdminSetupActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "App monitoring cannot function without device admin permissions", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }
}
package com.shieldtechhub.shieldkids.features.policy.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shieldtechhub.shieldkids.adapters.CategoryAppAdapter
import com.shieldtechhub.shieldkids.databinding.ActivityCategoryPolicyBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy.TimeLimit
import kotlinx.coroutines.launch
import com.shieldtechhub.shieldkids.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.util.Log

class CategoryPolicyActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCategoryPolicyBinding
    private lateinit var appInventoryManager: AppInventoryManager
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var adapter: CategoryAppAdapter
    private val db = FirebaseFirestore.getInstance()
    
    private var deviceId: String = ""
    private var childId: String = ""
    private var category: AppCategory = AppCategory.OTHER
    private var categoryApps = listOf<AppInfo>()
    private var devicePolicy: DevicePolicy? = null
    private var currentPolicyAction: AppPolicy.Action = AppPolicy.Action.ALLOW
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        childId = intent.getStringExtra("childId") ?: ""
        val categoryName = intent.getStringExtra("category") ?: ""
        
        if (deviceId.isEmpty() || childId.isEmpty() || categoryName.isEmpty()) {
            Toast.makeText(this, "Missing required information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        try {
            category = AppCategory.valueOf(categoryName)
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid category: $categoryName", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize managers
        appInventoryManager = AppInventoryManager(this)
        policyManager = PolicyEnforcementManager.getInstance(this)
        
        setupUI()
        loadCategoryApps()
    }
    
    private fun setupUI() {
        // Set toolbar title based on category
        val categoryDisplayName = category.name.lowercase().replaceFirstChar { it.uppercase() }
        binding.toolbar.title = categoryDisplayName
        binding.tvCategoryName.text = categoryDisplayName
        
        // Toolbar navigation
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // Setup RecyclerView
        adapter = CategoryAppAdapter { appInfo, action ->
            updateIndividualAppPolicy(appInfo, action)
        }
        
        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = adapter
        
        // Policy action buttons
        binding.btnAllow.setOnClickListener {
            setCategoryPolicy(AppPolicy.Action.ALLOW)
        }
        
        binding.btnBlock.setOnClickListener {
            setCategoryPolicy(AppPolicy.Action.BLOCK)
        }
        
        binding.btnSchedule.setOnClickListener {
            Toast.makeText(this, "Schedule configuration coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnTimeLimit.setOnClickListener {
            showTimeLimitDialog()
        }
    }
    
    private fun loadCategoryApps() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // Load child's apps from Firebase instead of parent's device
                val allApps = loadChildAppsFromFirebase()
                categoryApps = allApps.filter { it.category == category }
                    .sortedBy { it.name.lowercase() }
                
                Log.i("CategoryPolicy", "Loaded ${categoryApps.size} child apps in category ${category.name} from Firebase")
                
                // Load current device policy
                val activePolicies = policyManager.activePolicies.value
                devicePolicy = activePolicies[deviceId]
                
                // Determine current category policy
                determineCategoryPolicy()
                
                // Update UI
                updatePolicyUI()
                updateAppsWithPolicyStatus()
                updateStatsUI()
                
            } catch (e: Exception) {
                Toast.makeText(this@CategoryPolicyActivity, "Failed to load apps: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    /**
     * Load child's app inventory from Firebase
     */
    private suspend fun loadChildAppsFromFirebase(): List<AppInfo> {
        try {
            Log.d("CategoryPolicy", "Loading apps for child: $childId, device: $deviceId")
            
            // Use simplified device ID format: device_{deviceId} (no timestamp)
            val expectedDeviceDocId = "device_$deviceId"
            
            val appInventoryRef = db.collection("children")
                .document(childId)
                .collection("devices")
                .document(expectedDeviceDocId)
                .collection("data")
                .document("appInventory")
            
            val snapshot = appInventoryRef.get().await()
            
            if (snapshot.exists()) {
                val appsData = snapshot.get("apps") as? List<Map<String, Any>>
                
                if (appsData != null) {
                    return appsData.mapNotNull { appData ->
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
                            Log.w("CategoryPolicy", "Failed to parse app data: $appData", e)
                            null
                        }
                    }
                } else {
                    Log.w("CategoryPolicy", "No apps data found in Firebase")
                    return emptyList()
                }
            } else {
                Log.w("CategoryPolicy", "App inventory document not found in Firebase")
                return emptyList()
            }
            
        } catch (e: Exception) {
            Log.e("CategoryPolicy", "Failed to load child apps from Firebase", e)
            throw e
        }
    }
    
    private fun determineCategoryPolicy() {
        val categoryPolicies = devicePolicy?.appPolicies
            ?.filter { policy ->
                categoryApps.any { app -> app.packageName == policy.packageName }
            } ?: emptyList()
        
        if (categoryPolicies.isEmpty()) {
            currentPolicyAction = AppPolicy.Action.ALLOW // Default to allow
        } else {
            // Determine the most common action
            val actionCounts = categoryPolicies.groupingBy { it.action }.eachCount()
            currentPolicyAction = actionCounts.maxByOrNull { it.value }?.key ?: AppPolicy.Action.ALLOW
        }
    }
    
    private fun updatePolicyUI() {
        // Reset button states
        binding.btnAllow.setBackgroundResource(R.drawable.button_secondary)
        binding.btnBlock.setBackgroundResource(R.drawable.button_secondary)
        binding.btnAllow.setTextColor(ContextCompat.getColor(this, R.color.gray_600))
        binding.btnBlock.setTextColor(ContextCompat.getColor(this, R.color.gray_600))
        
        // Update time limit button status
        updateTimeLimitButtonStatus()
        
        // Highlight current policy
        when (currentPolicyAction) {
            AppPolicy.Action.ALLOW -> {
                binding.btnAllow.setBackgroundResource(R.drawable.button_primary)
                binding.btnAllow.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvPolicyStatus.text = "Category is allowed"
                binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(this, R.color.green_600))
            }
            AppPolicy.Action.BLOCK -> {
                binding.btnBlock.setBackgroundResource(R.drawable.button_danger)
                binding.btnBlock.setTextColor(ContextCompat.getColor(this, R.color.white))
                binding.tvPolicyStatus.text = "Category is blocked"
                binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(this, R.color.red_600))
            }
            AppPolicy.Action.TIME_LIMIT -> {
                binding.tvPolicyStatus.text = "Time limits applied"
                binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(this, R.color.orange_600))
            }
            else -> {
                binding.tvPolicyStatus.text = "Mixed policies applied"
                binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(this, R.color.orange_600))
            }
        }
    }
    
    private fun updateTimeLimitButtonStatus() {
        // Check if any apps in this category have time limits
        val categoryAppsWithTimeLimit = devicePolicy?.appPolicies?.filter { policy ->
            policy.action == AppPolicy.Action.TIME_LIMIT &&
            categoryApps.any { it.packageName == policy.packageName }
        } ?: emptyList()
        
        if (categoryAppsWithTimeLimit.isNotEmpty()) {
            // Show time limit is active
            binding.btnTimeLimit.setBackgroundResource(R.drawable.button_teal_rounded)
            binding.btnTimeLimit.setTextColor(ContextCompat.getColor(this, R.color.white))
            
            // Show summary of time limits
            val samplePolicy = categoryAppsWithTimeLimit.first()
            val timeLimit = samplePolicy.timeLimit
            val limitText = buildString {
                if (timeLimit?.dailyLimitMinutes != null && timeLimit.dailyLimitMinutes < Long.MAX_VALUE) {
                    val hours = timeLimit.dailyLimitMinutes / 60
                    val minutes = timeLimit.dailyLimitMinutes % 60
                    if (hours > 0) {
                        append("${hours}h")
                        if (minutes > 0) append(" ${minutes}m")
                    } else {
                        append("${minutes}m")
                    }
                }
                if (timeLimit?.allowedStartTime != null && timeLimit.allowedEndTime != null) {
                    if (isNotEmpty()) append(" • ")
                    append("${timeLimit.allowedStartTime}-${timeLimit.allowedEndTime}")
                }
            }
            binding.btnTimeLimit.text = if (limitText.isNotEmpty()) "Time Limit\n$limitText" else "Time Limit\nActive"
        } else {
            // Show time limit is not set
            binding.btnTimeLimit.setBackgroundResource(R.drawable.button_secondary)
            binding.btnTimeLimit.setTextColor(ContextCompat.getColor(this, R.color.gray_600))
            binding.btnTimeLimit.text = "Time Limit"
        }
    }
    
    private fun updateAppsWithPolicyStatus() {
        val appPolicies = devicePolicy?.appPolicies?.associateBy { it.packageName } ?: emptyMap()
        
        Log.d("CategoryPolicy", "🎯 Updating ${categoryApps.size} apps with policy status")
        Log.d("CategoryPolicy", "📋 Device policy exists: ${devicePolicy != null}")
        Log.d("CategoryPolicy", "📋 App policies count: ${appPolicies.size}")
        Log.d("CategoryPolicy", "📋 Current category action: $currentPolicyAction")
        
        val appsWithStatus = categoryApps.map { app ->
            val policy = appPolicies[app.packageName]
            val currentAction = policy?.action ?: currentPolicyAction
            
            Log.d("CategoryPolicy", "📱 ${app.name}: action=$currentAction, hasPolicy=${policy != null}")
            
            CategoryAppAdapter.CategoryAppItem(
                appInfo = app,
                currentAction = currentAction,
                hasIndividualPolicy = policy != null
            )
        }
        
        adapter.updateApps(appsWithStatus)
        Log.d("CategoryPolicy", "✅ Updated adapter with ${appsWithStatus.size} app items")
    }
    
    private fun updateStatsUI() {
        val totalApps = categoryApps.size
        val blockedApps = devicePolicy?.appPolicies
            ?.count { it.action == AppPolicy.Action.BLOCK && categoryApps.any { app -> app.packageName == it.packageName } } ?: 0
        val allowedApps = totalApps - blockedApps
        
        binding.tvStats.text = "Total: $totalApps • Allowed: $allowedApps • Blocked: $blockedApps"
    }
    
    private fun setCategoryPolicy(action: AppPolicy.Action) {
        if (currentPolicyAction == action) return
        
        lifecycleScope.launch {
            try {
                val currentPolicy = devicePolicy ?: createDefaultPolicy()
                val updatedAppPolicies = currentPolicy.appPolicies.toMutableList()
                
                // Remove existing policies for all apps in this category
                updatedAppPolicies.removeAll { policy ->
                    categoryApps.any { app -> app.packageName == policy.packageName }
                }
                
                // Add new policies for all apps in this category
                categoryApps.forEach { app ->
                    val appPolicy = AppPolicy(
                        packageName = app.packageName,
                        action = action,
                        reason = "Category policy: ${category.name}"
                    )
                    updatedAppPolicies.add(appPolicy)
                }
                
                // Create updated device policy
                val updatedPolicy = currentPolicy.copy(
                    appPolicies = updatedAppPolicies,
                    updatedAt = System.currentTimeMillis()
                )
                
                // Apply the policy
                val success = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                if (success) {
                    devicePolicy = updatedPolicy
                    currentPolicyAction = action
                    updatePolicyUI()
                    updateAppsWithPolicyStatus()
                    updateStatsUI()
                    
                    val actionText = if (action == AppPolicy.Action.ALLOW) "allowed" else "blocked"
                    Toast.makeText(this@CategoryPolicyActivity, "Category $actionText", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CategoryPolicyActivity, "Failed to apply category policy", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@CategoryPolicyActivity, "Error applying policy: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateIndividualAppPolicy(appInfo: AppInfo, action: AppPolicy.Action) {
        lifecycleScope.launch {
            try {
                val currentPolicy = devicePolicy ?: createDefaultPolicy()
                val updatedAppPolicies = currentPolicy.appPolicies.toMutableList()
                
                // Remove existing policy for this app
                updatedAppPolicies.removeAll { it.packageName == appInfo.packageName }
                
                // Add new policy if different from category default
                if (action != currentPolicyAction) {
                    val appPolicy = AppPolicy(
                        packageName = appInfo.packageName,
                        action = action,
                        reason = "Individual override for ${appInfo.name}"
                    )
                    updatedAppPolicies.add(appPolicy)
                }
                
                // Create updated device policy
                val updatedPolicy = currentPolicy.copy(
                    appPolicies = updatedAppPolicies,
                    updatedAt = System.currentTimeMillis()
                )
                
                // Apply the policy
                val success = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                if (success) {
                    devicePolicy = updatedPolicy
                    updateAppsWithPolicyStatus()
                    updateStatsUI()
                    
                    val actionText = if (action == AppPolicy.Action.ALLOW) "allowed" else "blocked"
                    Toast.makeText(this@CategoryPolicyActivity, "${appInfo.name} $actionText", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CategoryPolicyActivity, "Failed to update app policy", Toast.LENGTH_SHORT).show()
                    // Revert UI changes
                    updateAppsWithPolicyStatus()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@CategoryPolicyActivity, "Error updating policy: ${e.message}", Toast.LENGTH_SHORT).show()
                updateAppsWithPolicyStatus()
            }
        }
    }
    
    private fun createDefaultPolicy(): DevicePolicy {
        return DevicePolicy(
            id = "policy_$deviceId",
            name = "Default Policy",
            appPolicies = listOf(),
            cameraDisabled = false,
            installationsBlocked = false,
            keyguardRestrictions = 0,
            passwordPolicy = null
        )
    }
    
    private fun showTimeLimitDialog() {
        // Create custom dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_limit, null)
        
        // Get UI elements
        val timePicker = dialogView.findViewById<TimePicker>(R.id.timePicker)
        val switchDailyLimit = dialogView.findViewById<Switch>(R.id.switchDailyLimit)
        val layoutTimeWindow = dialogView.findViewById<LinearLayout>(R.id.layoutTimeWindow)
        val switchTimeWindow = dialogView.findViewById<Switch>(R.id.switchTimeWindow)
        val tvStartTime = dialogView.findViewById<TextView>(R.id.tvStartTime)
        val tvEndTime = dialogView.findViewById<TextView>(R.id.tvEndTime)
        val tvCategoryName = dialogView.findViewById<TextView>(R.id.tvCategoryName)
        val btnRemoveTimeLimit = dialogView.findViewById<Button>(R.id.btnRemoveTimeLimit)
        
        // Set category name
        tvCategoryName.text = "Set time limits for $category apps"
        timePicker.setIs24HourView(true)
        
        // Load existing time limits if any
        val existingTimeLimit = getExistingTimeLimitForCategory()
        if (existingTimeLimit != null) {
            // Pre-populate with existing values
            if (existingTimeLimit.dailyLimitMinutes < Long.MAX_VALUE) {
                switchDailyLimit.isChecked = true
                val hours = (existingTimeLimit.dailyLimitMinutes / 60).toInt()
                val minutes = (existingTimeLimit.dailyLimitMinutes % 60).toInt()
                timePicker.hour = hours
                timePicker.minute = minutes
            } else {
                switchDailyLimit.isChecked = false
                timePicker.hour = 1  // Default: 1 hour
                timePicker.minute = 0
            }
            
            if (existingTimeLimit.allowedStartTime != null && existingTimeLimit.allowedEndTime != null) {
                switchTimeWindow.isChecked = true
                tvStartTime.text = existingTimeLimit.allowedStartTime
                tvEndTime.text = existingTimeLimit.allowedEndTime
                layoutTimeWindow.visibility = View.VISIBLE
            } else {
                switchTimeWindow.isChecked = false
                tvStartTime.text = "09:00"
                tvEndTime.text = "18:00"
                layoutTimeWindow.visibility = View.GONE
            }
            
            // Show remove button for existing time limits
            btnRemoveTimeLimit.visibility = View.VISIBLE
        } else {
            // Set defaults for new time limit
            switchDailyLimit.isChecked = true
            timePicker.hour = 1  // Default: 1 hour
            timePicker.minute = 0
            switchTimeWindow.isChecked = false
            tvStartTime.text = "09:00"
            tvEndTime.text = "18:00"
            layoutTimeWindow.visibility = View.GONE
            
            // Hide remove button for new time limits
            btnRemoveTimeLimit.visibility = View.GONE
        }
        
        // Handle time window visibility
        switchTimeWindow.setOnCheckedChangeListener { _, isChecked ->
            layoutTimeWindow.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Handle time selection for allowed window
        tvStartTime.setOnClickListener {
            showTimePickerDialog("Start Time") { hour, minute ->
                tvStartTime.text = String.format("%02d:%02d", hour, minute)
            }
        }
        
        tvEndTime.setOnClickListener {
            showTimePickerDialog("End Time") { hour, minute ->
                tvEndTime.text = String.format("%02d:%02d", hour, minute)
            }
        }
        
        // Handle remove time limit button
        btnRemoveTimeLimit.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Remove Time Limit")
                .setMessage("Are you sure you want to remove all time limits for $category apps?")
                .setPositiveButton("Remove") { dialog, _ ->
                    removeTimeLimitForCategory()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        // Create and show dialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Time Limit Configuration")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val dailyMinutes = if (switchDailyLimit.isChecked) {
                    (timePicker.hour * 60) + timePicker.minute
                } else 0
                
                // Validate inputs
                if (switchDailyLimit.isChecked && dailyMinutes <= 0) {
                    Toast.makeText(this, "Please set a valid daily time limit", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (switchTimeWindow.isChecked) {
                    val startTime = tvStartTime.text.toString()
                    val endTime = tvEndTime.text.toString()
                    if (startTime >= endTime) {
                        Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }
                
                if (!switchDailyLimit.isChecked && !switchTimeWindow.isChecked) {
                    Toast.makeText(this, "Please enable at least one time restriction", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                applyTimeLimitPolicy(
                    dailyLimitEnabled = switchDailyLimit.isChecked,
                    dailyLimitMinutes = dailyMinutes,
                    timeWindowEnabled = switchTimeWindow.isChecked,
                    startTime = if (switchTimeWindow.isChecked) tvStartTime.text.toString() else null,
                    endTime = if (switchTimeWindow.isChecked) tvEndTime.text.toString() else null
                )
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
    }
    
    private fun showTimePickerDialog(title: String, onTimeSelected: (hour: Int, minute: Int) -> Unit) {
        val timePicker = TimePicker(this)
        timePicker.setIs24HourView(true)
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(timePicker)
            .setPositiveButton("OK") { _, _ ->
                onTimeSelected(timePicker.hour, timePicker.minute)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun applyTimeLimitPolicy(
        dailyLimitEnabled: Boolean,
        dailyLimitMinutes: Int,
        timeWindowEnabled: Boolean,
        startTime: String?,
        endTime: String?
    ) {
        lifecycleScope.launch {
            try {
                val currentPolicy = devicePolicy ?: createDefaultPolicy()
                val categoryApps = getCategoryApps()
                
                val updatedAppPolicies = currentPolicy.appPolicies.toMutableList()
                
                // Remove existing time limit policies for this category
                updatedAppPolicies.removeAll { policy ->
                    categoryApps.any { app -> app.packageName == policy.packageName } &&
                    policy.action == AppPolicy.Action.TIME_LIMIT
                }
                
                // Add new time limit policies if enabled
                if (dailyLimitEnabled || timeWindowEnabled) {
                    categoryApps.forEach { app ->
                        val timeLimit = TimeLimit(
                            dailyLimitMinutes = if (dailyLimitEnabled) dailyLimitMinutes.toLong() else Long.MAX_VALUE,
                            allowedStartTime = if (timeWindowEnabled) startTime else null,
                            allowedEndTime = if (timeWindowEnabled) endTime else null,
                            warningAtMinutes = 5
                        )
                        
                        val timeLimitPolicy = AppPolicy.timeLimit(
                            packageName = app.packageName,
                            dailyMinutes = timeLimit.dailyLimitMinutes,
                            startTime = timeLimit.allowedStartTime,
                            endTime = timeLimit.allowedEndTime
                        ).copy(reason = "Time limit for $category apps")
                        
                        updatedAppPolicies.add(timeLimitPolicy)
                    }
                }
                
                // Create updated device policy
                val updatedPolicy = currentPolicy.copy(
                    appPolicies = updatedAppPolicies,
                    updatedAt = System.currentTimeMillis()
                )
                
                // Apply the policy
                val success = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                if (success) {
                    devicePolicy = updatedPolicy
                    updateAppsWithPolicyStatus()
                    updateStatsUI()
                    
                    val limitText = when {
                        dailyLimitEnabled && timeWindowEnabled -> 
                            "${dailyLimitMinutes} minutes daily, allowed $startTime-$endTime"
                        dailyLimitEnabled -> 
                            "${dailyLimitMinutes} minutes daily"
                        timeWindowEnabled -> 
                            "allowed $startTime-$endTime"
                        else -> "removed"
                    }
                    
                    Toast.makeText(this@CategoryPolicyActivity, 
                        "Time limits applied: $limitText", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@CategoryPolicyActivity, 
                        "Failed to apply time limit policy", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@CategoryPolicyActivity, 
                    "Error applying time limits: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getCategoryApps(): List<AppInfo> {
        return categoryApps.filter { it.category == category }
    }
    
    private fun getExistingTimeLimitForCategory(): TimeLimit? {
        val categoryAppsWithTimeLimit = devicePolicy?.appPolicies?.filter { policy ->
            policy.action == AppPolicy.Action.TIME_LIMIT &&
            categoryApps.any { it.packageName == policy.packageName }
        } ?: emptyList()
        
        return categoryAppsWithTimeLimit.firstOrNull()?.timeLimit
    }
    
    private fun removeTimeLimitForCategory() {
        lifecycleScope.launch {
            try {
                val currentPolicy = devicePolicy ?: return@launch
                val categoryApps = getCategoryApps()
                
                val updatedAppPolicies = currentPolicy.appPolicies.toMutableList()
                
                // Remove all time limit policies for this category
                updatedAppPolicies.removeAll { policy ->
                    categoryApps.any { app -> app.packageName == policy.packageName } &&
                    policy.action == AppPolicy.Action.TIME_LIMIT
                }
                
                // Create updated device policy
                val updatedPolicy = currentPolicy.copy(
                    appPolicies = updatedAppPolicies,
                    updatedAt = System.currentTimeMillis()
                )
                
                // Apply the policy
                val success = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                if (success) {
                    devicePolicy = updatedPolicy
                    updateAppsWithPolicyStatus()
                    updateStatsUI()
                    
                    // Re-determine category policy after removal
                    determineCategoryPolicy()
                    updatePolicyUI()
                    
                    Toast.makeText(this@CategoryPolicyActivity, 
                        "Time limits removed for $category apps", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CategoryPolicyActivity, 
                        "Failed to remove time limits", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@CategoryPolicyActivity, 
                    "Error removing time limits: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning from other screens
        loadCategoryApps()
    }
}
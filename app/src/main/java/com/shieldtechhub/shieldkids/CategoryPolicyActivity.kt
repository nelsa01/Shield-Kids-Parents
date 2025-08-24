package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.view.View
import android.widget.Toast
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
import kotlinx.coroutines.launch
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
            Toast.makeText(this, "Time limit configuration coming soon!", Toast.LENGTH_SHORT).show()
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
            
            val appInventoryRef = db.collection("children")
                .document(childId)
                .collection("devices")
                .document(deviceId)
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
            else -> {
                binding.tvPolicyStatus.text = "Mixed policies applied"
                binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(this, R.color.orange_600))
            }
        }
    }
    
    private fun updateAppsWithPolicyStatus() {
        val appPolicies = devicePolicy?.appPolicies?.associateBy { it.packageName } ?: emptyMap()
        
        val appsWithStatus = categoryApps.map { app ->
            val policy = appPolicies[app.packageName]
            val currentAction = policy?.action ?: currentPolicyAction
            
            CategoryAppAdapter.CategoryAppItem(
                appInfo = app,
                currentAction = currentAction,
                hasIndividualPolicy = policy != null
            )
        }
        
        adapter.updateApps(appsWithStatus)
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
    
    override fun onResume() {
        super.onResume()
        // Refresh data when returning from other screens
        loadCategoryApps()
    }
}
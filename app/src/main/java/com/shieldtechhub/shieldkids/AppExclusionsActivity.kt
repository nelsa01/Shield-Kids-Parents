package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shieldtechhub.shieldkids.adapters.AppExclusionAdapter
import com.shieldtechhub.shieldkids.databinding.ActivityAppExclusionsBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log

class AppExclusionsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAppExclusionsBinding
    private lateinit var appInventoryManager: AppInventoryManager
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var adapter: AppExclusionAdapter
    private val db = FirebaseFirestore.getInstance()
    
    private var deviceId: String = ""
    private var childId: String = ""
    private var allApps = listOf<AppInfo>()
    private var devicePolicy: DevicePolicy? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppExclusionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        childId = intent.getStringExtra("childId") ?: ""
        
        if (deviceId.isEmpty() || childId.isEmpty()) {
            Toast.makeText(this, "Missing device or child information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize managers
        appInventoryManager = AppInventoryManager(this)
        policyManager = PolicyEnforcementManager.getInstance(this)
        
        setupUI()
        loadAppsAndPolicy()
    }
    
    private fun setupUI() {
        // Toolbar
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // Setup RecyclerView
        adapter = AppExclusionAdapter { appInfo, isExcluded ->
            updateAppExclusion(appInfo, isExcluded)
        }
        
        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = adapter
        
        // Search functionality
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterApps(query ?: "")
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText ?: "")
                return true
            }
        })
        
        // Refresh functionality can be added via toolbar menu if needed
    }
    
    private fun loadAppsAndPolicy() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // Load child's apps from Firebase instead of parent's device
                allApps = loadChildAppsFromFirebase()
                    .filter { !it.isSystemApp } // Only show user apps for exclusions
                    .sortedBy { it.name.lowercase() }
                
                Log.i("AppExclusions", "Loaded ${allApps.size} child apps from Firebase for restrictions")
                
                // Load current device policy
                val activePolicies = policyManager.activePolicies.value
                devicePolicy = activePolicies[deviceId]
                
                // Update UI
                updateAppsWithExclusionStatus()
                updateStatsUI()
                
            } catch (e: Exception) {
                Log.e("AppExclusions", "Failed to load child apps from Firebase", e)
                Toast.makeText(this@AppExclusionsActivity, "Failed to load child's apps: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Log.d("AppExclusions", "Loading apps for child: $childId, device: $deviceId")
            
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
                            Log.w("AppExclusions", "Failed to parse app data: $appData", e)
                            null
                        }
                    }
                } else {
                    Log.w("AppExclusions", "No apps data found in Firebase")
                    return emptyList()
                }
            } else {
                Log.w("AppExclusions", "App inventory document not found in Firebase")
                return emptyList()
            }
            
        } catch (e: Exception) {
            Log.e("AppExclusions", "Failed to load child apps from Firebase", e)
            throw e
        }
    }
    
    /**
     * Refresh child's apps from Firebase
     */
    private fun refreshChildApps() {
        lifecycleScope.launch {
            try {
                // Load fresh data from Firebase
                allApps = loadChildAppsFromFirebase()
                    .filter { !it.isSystemApp }
                    .sortedBy { it.name.lowercase() }
                
                Log.i("AppExclusions", "Refreshed ${allApps.size} child apps from Firebase")
                
                // Update UI
                updateAppsWithExclusionStatus()
                updateStatsUI()
                
                Toast.makeText(this@AppExclusionsActivity, "Child's app list refreshed", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e("AppExclusions", "Failed to refresh child apps", e)
                Toast.makeText(this@AppExclusionsActivity, "Failed to refresh apps: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                // Refresh completed
            }
        }
    }
    
    private fun updateAppsWithExclusionStatus() {
        val excludedPackageNames = devicePolicy?.appPolicies
            ?.filter { it.action == AppPolicy.Action.ALLOW }
            ?.map { it.packageName }
            ?.toSet() ?: emptySet()
        
        val appsWithStatus = allApps.map { app ->
            AppExclusionAdapter.AppExclusionItem(
                appInfo = app,
                isExcluded = excludedPackageNames.contains(app.packageName)
            )
        }
        
        adapter.updateApps(appsWithStatus)
        filterApps("") // Apply any current search filter
    }
    
    private fun filterApps(query: String) {
        val currentApps = adapter.getCurrentApps()
        val filteredApps = if (query.isBlank()) {
            currentApps
        } else {
            currentApps.filter { item ->
                item.appInfo.name.contains(query, ignoreCase = true) ||
                item.appInfo.packageName.contains(query, ignoreCase = true)
            }
        }
        
        adapter.updateApps(filteredApps)
        updateResultsCount(filteredApps.size, query.isNotBlank())
    }
    
    private fun updateResultsCount(count: Int, isSearching: Boolean) {
        if (isSearching) {
            binding.tvResultsCount.text = "$count apps found"
            binding.tvResultsCount.visibility = View.VISIBLE
        } else {
            binding.tvResultsCount.visibility = View.GONE
        }
    }
    
    private fun updateStatsUI() {
        val totalApps = allApps.size
        val excludedCount = devicePolicy?.appPolicies
            ?.count { it.action == AppPolicy.Action.ALLOW } ?: 0
        val blockedCount = totalApps - excludedCount
        
        binding.tvStatsTitle.text = "App Exclusions"
        binding.tvStats.text = "Total: $totalApps • Excluded: $excludedCount • Blocked: $blockedCount"
    }
    
    private fun updateAppExclusion(appInfo: AppInfo, isExcluded: Boolean) {
        lifecycleScope.launch {
            try {
                val currentPolicy = devicePolicy ?: createDefaultPolicy()
                val updatedAppPolicies = currentPolicy.appPolicies.toMutableList()
                
                // Remove any existing policy for this app
                updatedAppPolicies.removeAll { it.packageName == appInfo.packageName }
                
                if (isExcluded) {
                    // Add exclusion policy (ALLOW action means excluded from blocking)
                    val exclusionPolicy = AppPolicy(
                        packageName = appInfo.packageName,
                        action = AppPolicy.Action.ALLOW,
                        reason = "Excluded by parent"
                    )
                    updatedAppPolicies.add(exclusionPolicy)
                } else {
                    // Add blocking policy
                    val blockingPolicy = AppPolicy(
                        packageName = appInfo.packageName,
                        action = AppPolicy.Action.BLOCK,
                        reason = "Blocked by parent"
                    )
                    updatedAppPolicies.add(blockingPolicy)
                }
                
                // Create updated policy
                val updatedPolicy = currentPolicy.copy(
                    appPolicies = updatedAppPolicies,
                    updatedAt = System.currentTimeMillis()
                )
                
                // Apply the updated policy
                val success = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                if (success) {
                    devicePolicy = updatedPolicy
                    updateStatsUI()
                    Toast.makeText(
                        this@AppExclusionsActivity,
                        if (isExcluded) "App excluded" else "App blocked",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@AppExclusionsActivity, "Failed to update app policy", Toast.LENGTH_SHORT).show()
                    // Revert the UI change
                    updateAppsWithExclusionStatus()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@AppExclusionsActivity, "Error updating policy: ${e.message}", Toast.LENGTH_SHORT).show()
                // Revert the UI change
                updateAppsWithExclusionStatus()
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
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_app_exclusions, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshChildApps()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
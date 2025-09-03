package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.adapters.AllAppsAdapter
import com.shieldtechhub.shieldkids.databinding.ActivityAllAppsBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AllAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllAppsBinding
    private lateinit var adapter: AllAppsAdapter
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var policySyncManager: PolicySyncManager
    private val db = FirebaseFirestore.getInstance()
    
    private var deviceId: String = ""
    private var childId: String = ""
    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAllAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        childId = intent.getStringExtra("childId") ?: ""
        
        if (deviceId.isEmpty() || childId.isEmpty()) {
            Toast.makeText(this, "Missing device or child information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize policy managers
        policyManager = PolicyEnforcementManager.getInstance(this)
        policySyncManager = PolicySyncManager.getInstance(this)
        
        setupUI()
        loadChildApps()
    }
    
    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Apps"
        
        // Setup RecyclerView
        adapter = AllAppsAdapter(
            onAppClick = { appInfo ->
                onAppClicked(appInfo)
            },
            onToggleBlock = { appInfo, isBlocked ->
                onAppBlockToggled(appInfo, isBlocked)
            }
        )
        
        binding.recyclerViewAllApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAllApps.adapter = adapter
        
        // Setup empty state
        showEmptyState(true)
    }
    
    private fun loadChildApps() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                Log.d("AllAppsActivity", "Loading apps for child: $childId, device: $deviceId")
                
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
                        allApps = appsData.mapNotNull { appData ->
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
                                    dataDir = "", // No dataDir from Firebase
                                    isBlocked = appData["isBlocked"] as? Boolean ?: false
                                )
                            } catch (e: Exception) {
                                Log.w("AllAppsActivity", "Failed to parse app data: $appData", e)
                                null
                            }
                        }
                        
                        // Sort apps alphabetically
                        allApps = allApps.sortedBy { it.name.lowercase() }
                        filteredApps = allApps
                        
                        Log.i("AllAppsActivity", "Loaded ${allApps.size} apps from child device")
                        
                        updateUI()
                        
                    } else {
                        Log.w("AllAppsActivity", "No apps data found in Firebase")
                        showNoAppsMessage()
                    }
                } else {
                    Log.w("AllAppsActivity", "App inventory document not found in Firebase")
                    showNoAppsMessage()
                }
                
            } catch (e: Exception) {
                Log.e("AllAppsActivity", "Failed to load child apps from Firebase", e)
                Toast.makeText(this@AllAppsActivity, "Failed to load apps: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun updateUI() {
        if (filteredApps.isEmpty()) {
            showEmptyState(true)
        } else {
            showEmptyState(false)
            adapter.updateApps(filteredApps)
            
            // Update subtitle
            val userApps = filteredApps.count { !it.isSystemApp }
            val systemApps = filteredApps.size - userApps
            supportActionBar?.subtitle = "${filteredApps.size} apps ($userApps user, $systemApps system)"
        }
    }
    
    private fun showNoAppsMessage() {
        showEmptyState(true)
        binding.tvEmptyMessage.text = "Child's app list not yet synced.\nPlease ensure the child device is online and try again."
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerViewAllApps.visibility = if (show) android.view.View.GONE else android.view.View.VISIBLE
    }
    
    private fun showEmptyState(show: Boolean) {
        binding.layoutEmpty.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerViewAllApps.visibility = if (show) android.view.View.GONE else android.view.View.VISIBLE
    }
    
    private fun onAppClicked(appInfo: AppInfo) {
        // Show app details or policy options
        val message = buildString {
            appendLine("App: ${appInfo.name}")
            appendLine("Package: ${appInfo.packageName}")
            appendLine("Version: ${appInfo.version}")
            appendLine("Category: ${appInfo.category}")
            appendLine("System app: ${if (appInfo.isSystemApp) "Yes" else "No"}")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("App Information")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("Block App") { _, _ ->
                // TODO: Implement app blocking
                Toast.makeText(this, "App blocking coming soon", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun onAppBlockToggled(appInfo: AppInfo, isBlocked: Boolean) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Get current device policy
                val currentPolicy = policyManager.activePolicies.value[deviceId] ?: DevicePolicy.createDefault(deviceId)
                
                // Update app policies list
                val updatedAppPolicies = currentPolicy.appPolicies.toMutableList()
                val existingIndex = updatedAppPolicies.indexOfFirst { it.packageName == appInfo.packageName }
                
                if (isBlocked) {
                    // Add or update block policy
                    val blockPolicy = AppPolicy(
                        packageName = appInfo.packageName,
                        action = AppPolicy.Action.BLOCK,
                        isActive = true
                    )
                    
                    if (existingIndex != -1) {
                        updatedAppPolicies[existingIndex] = blockPolicy
                    } else {
                        updatedAppPolicies.add(blockPolicy)
                    }
                } else {
                    // Remove block policy
                    if (existingIndex != -1) {
                        updatedAppPolicies.removeAt(existingIndex)
                    }
                }
                
                // Create updated policy
                val updatedPolicy = currentPolicy.copy(
                    appPolicies = updatedAppPolicies,
                    updatedAt = System.currentTimeMillis()
                )
                
                // Apply policy locally first
                val localSuccess = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                if (localSuccess) {
                    // Then sync to Firebase
                    val firebaseSuccess = policySyncManager.savePolicyToFirebase(childId, deviceId, updatedPolicy)
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val action = if (isBlocked) "blocked" else "unblocked"
                        if (firebaseSuccess) {
                            Toast.makeText(this@AllAppsActivity, "${appInfo.name} has been $action", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@AllAppsActivity, "${appInfo.name} $action locally but failed to sync to child device", Toast.LENGTH_LONG).show()
                        }
                        
                        // Update local UI state
                        val updatedApp = appInfo.copy(isBlocked = isBlocked)
                        val appIndex = allApps.indexOfFirst { it.packageName == appInfo.packageName }
                        if (appIndex != -1) {
                            allApps = allApps.toMutableList().apply { set(appIndex, updatedApp) }
                            filterApps("") // Refresh the filtered list
                        }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@AllAppsActivity, "Failed to apply app blocking policy", Toast.LENGTH_SHORT).show()
                    }
                }
                
            } catch (e: Exception) {
                Log.e("AllAppsActivity", "Failed to toggle app block", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@AllAppsActivity, "Error updating app policy: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun filterApps(query: String) {
        filteredApps = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.name.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true) ||
                app.category.name.contains(query, ignoreCase = true)
            }
        }
        updateUI()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_all_apps, menu)
        
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText ?: "")
                return true
            }
        })
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh -> {
                loadChildApps()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
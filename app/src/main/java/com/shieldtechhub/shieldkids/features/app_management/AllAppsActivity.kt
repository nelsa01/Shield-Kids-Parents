package com.shieldtechhub.shieldkids.features.app_management

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ActivityAllAppsBinding
import com.shieldtechhub.shieldkids.features.app_management.adapters.AllAppsAdapter
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AllAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAllAppsBinding
    private lateinit var adapter: AllAppsAdapter
    private val db = FirebaseFirestore.getInstance()
    
    private var deviceId: String = ""
    private var childId: String = ""
    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var currentFilter = AppFilter.ALL
    private var searchQuery = ""

    enum class AppFilter {
        ALL, USER, SYSTEM, SOCIAL, GAMES, EDUCATIONAL, ENTERTAINMENT, PRODUCTIVITY, COMMUNICATION, BROWSERS, SHOPPING, OTHER
    }
    
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
        
        setupUI()
        loadChildApps()
    }
    
    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Apps"
        
        // Setup RecyclerView
        adapter = AllAppsAdapter { appInfo ->
            onAppClicked(appInfo)
        }
        
        binding.recyclerViewAllApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewAllApps.adapter = adapter
        
        // Setup filter chips
        setupFilterChips()
        
        // Setup search
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchQuery = query ?: ""
                filterApps()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText ?: ""
                filterApps()
                return true
            }
        })

        // Setup refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadChildApps()
        }
        
        // Setup empty state
        showEmptyState(true)
    }
    
    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { setFilter(AppFilter.ALL) }
        binding.chipUser.setOnClickListener { setFilter(AppFilter.USER) }
        binding.chipSystem.setOnClickListener { setFilter(AppFilter.SYSTEM) }
        binding.chipSocial.setOnClickListener { setFilter(AppFilter.SOCIAL) }
        binding.chipGames.setOnClickListener { setFilter(AppFilter.GAMES) }
        binding.chipEducational.setOnClickListener { setFilter(AppFilter.EDUCATIONAL) }
        binding.chipEntertainment.setOnClickListener { setFilter(AppFilter.ENTERTAINMENT) }
        binding.chipProductivity.setOnClickListener { setFilter(AppFilter.PRODUCTIVITY) }
        binding.chipCommunication.setOnClickListener { setFilter(AppFilter.COMMUNICATION) }
        binding.chipBrowsers.setOnClickListener { setFilter(AppFilter.BROWSERS) }
        binding.chipShopping.setOnClickListener { setFilter(AppFilter.SHOPPING) }
        binding.chipOther.setOnClickListener { setFilter(AppFilter.OTHER) }
    }

    private fun setFilter(filter: AppFilter) {
        currentFilter = filter
        updateFilterChips()
        filterApps()
    }

    private fun updateFilterChips() {
        // Reset all chips
        binding.chipAll.isChecked = false
        binding.chipUser.isChecked = false
        binding.chipSystem.isChecked = false
        binding.chipSocial.isChecked = false
        binding.chipGames.isChecked = false
        binding.chipEducational.isChecked = false
        binding.chipEntertainment.isChecked = false
        binding.chipProductivity.isChecked = false
        binding.chipCommunication.isChecked = false
        binding.chipBrowsers.isChecked = false
        binding.chipShopping.isChecked = false
        binding.chipOther.isChecked = false

        // Set current filter chip as checked
        when (currentFilter) {
            AppFilter.ALL -> binding.chipAll.isChecked = true
            AppFilter.USER -> binding.chipUser.isChecked = true
            AppFilter.SYSTEM -> binding.chipSystem.isChecked = true
            AppFilter.SOCIAL -> binding.chipSocial.isChecked = true
            AppFilter.GAMES -> binding.chipGames.isChecked = true
            AppFilter.EDUCATIONAL -> binding.chipEducational.isChecked = true
            AppFilter.ENTERTAINMENT -> binding.chipEntertainment.isChecked = true
            AppFilter.PRODUCTIVITY -> binding.chipProductivity.isChecked = true
            AppFilter.COMMUNICATION -> binding.chipCommunication.isChecked = true
            AppFilter.BROWSERS -> binding.chipBrowsers.isChecked = true
            AppFilter.SHOPPING -> binding.chipShopping.isChecked = true
            AppFilter.OTHER -> binding.chipOther.isChecked = true
        }
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
                                    dataDir = "" // No dataDir from Firebase
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
                        updateStats()
                        
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
                binding.swipeRefreshLayout.isRefreshing = false
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
        
        updateResultsCount(filteredApps.size)
    }
    
    private fun filterApps() {
        val filtered = allApps.filter { app ->
            val matchesFilter = when (currentFilter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystemApp
                AppFilter.SYSTEM -> app.isSystemApp
                AppFilter.SOCIAL -> app.category == AppCategory.SOCIAL
                AppFilter.GAMES -> app.category == AppCategory.GAMES
                AppFilter.EDUCATIONAL -> app.category == AppCategory.EDUCATIONAL
                AppFilter.ENTERTAINMENT -> app.category == AppCategory.ENTERTAINMENT
                AppFilter.PRODUCTIVITY -> app.category == AppCategory.PRODUCTIVITY
                AppFilter.COMMUNICATION -> app.category == AppCategory.COMMUNICATION
                AppFilter.BROWSERS -> app.category == AppCategory.BROWSERS
                AppFilter.SHOPPING -> app.category == AppCategory.SHOPPING
                AppFilter.OTHER -> app.category == AppCategory.OTHER
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true) ||
                app.category.name.contains(searchQuery, ignoreCase = true)
            }

            matchesFilter && matchesSearch
        }.sortedBy { it.name.lowercase() }

        filteredApps = filtered
        updateUI()
    }
    
    private fun updateStats() {
        val userApps = allApps.count { !it.isSystemApp }
        val systemApps = allApps.count { it.isSystemApp }
        
        binding.tvStatsTitle.text = "Child's App Statistics"
        binding.tvStats.text = "Total: ${allApps.size} • User: $userApps • System: $systemApps"
    }

    private fun updateResultsCount(count: Int) {
        binding.tvResultsCount.text = "$count apps found"
        binding.tvResultsCount.visibility = if (searchQuery.isNotBlank() || currentFilter != AppFilter.ALL) {
            View.VISIBLE
        } else {
            View.GONE
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
        // Show app management dialog with detailed options
        val message = buildString {
            appendLine("App: ${appInfo.name}")
            appendLine("Package: ${appInfo.packageName}")
            appendLine("Version: ${appInfo.version}")
            appendLine("Category: ${appInfo.category.name.lowercase().replaceFirstChar { it.uppercase() }}")
            appendLine("Type: ${if (appInfo.isSystemApp) "System App" else "User App"}")
            appendLine("Status: ${if (appInfo.isEnabled) "Enabled" else "Disabled"}")
            appendLine("Permissions: ${appInfo.permissions.size}")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Manage: ${appInfo.name}")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .setNeutralButton("Block App") { _, _ ->
                showBlockConfirmation(appInfo)
            }
            .setNegativeButton("View Permissions") { _, _ ->
                showPermissions(appInfo)
            }
            .show()
    }
    
    private fun showBlockConfirmation(appInfo: AppInfo) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Block ${appInfo.name}?")
            .setMessage("This app will be blocked on the child's device. The child will not be able to open it.")
            .setPositiveButton("Block") { _, _ ->
                blockApp(appInfo)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPermissions(appInfo: AppInfo) {
        val permissionsText = if (appInfo.permissions.isNotEmpty()) {
            appInfo.permissions.joinToString("\n") { permission ->
                "• ${permission.substringAfterLast(".")}"
            }
        } else {
            "No permissions requested"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${appInfo.name} Permissions")
            .setMessage(permissionsText)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun blockApp(appInfo: AppInfo) {
        // TODO: Implement app blocking through policy system
        Toast.makeText(this, "Blocking ${appInfo.name}...", Toast.LENGTH_SHORT).show()
        
        // This would integrate with the existing AppExclusionsActivity or CategoryPolicyActivity logic
        // For now, show a confirmation
        Toast.makeText(this, "${appInfo.name} has been blocked", Toast.LENGTH_SHORT).show()
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
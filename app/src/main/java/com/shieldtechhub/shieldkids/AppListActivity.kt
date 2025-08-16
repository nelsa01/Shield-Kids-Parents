package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shieldtechhub.shieldkids.adapters.AppListAdapter
import com.shieldtechhub.shieldkids.databinding.ActivityAppListBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import kotlinx.coroutines.launch

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var appInventoryManager: AppInventoryManager
    private lateinit var adapter: AppListAdapter
    
    private var allApps = listOf<AppInfo>()
    private var currentFilter = AppFilter.ALL
    private var searchQuery = ""

    enum class AppFilter {
        ALL, USER, SYSTEM, SOCIAL, GAMES, EDUCATIONAL, ENTERTAINMENT, PRODUCTIVITY, COMMUNICATION, OTHER
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appInventoryManager = AppInventoryManager(this)
        setupUI()
        loadApps()
    }

    private fun setupUI() {
        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Setup RecyclerView
        adapter = AppListAdapter { appInfo ->
            showAppDetails(appInfo)
        }
        binding.recyclerViewApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewApps.adapter = adapter

        // Setup filter chips
        setupFilterChips()
        
        // Setup search
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
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
            loadApps()
        }
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
            AppFilter.OTHER -> binding.chipOther.isChecked = true
        }
    }

    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefreshLayout.isRefreshing = true
        
        lifecycleScope.launch {
            try {
                allApps = appInventoryManager.getAllInstalledApps()
                filterApps()
                updateStats()
            } catch (e: Exception) {
                Toast.makeText(this@AppListActivity, "Failed to load apps: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun filterApps() {
        val filteredApps = allApps.filter { app ->
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
                AppFilter.OTHER -> app.category == AppCategory.OTHER
            }

            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                app.name.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            }

            matchesFilter && matchesSearch
        }.sortedBy { it.name.lowercase() }

        adapter.updateApps(filteredApps)
        updateResultsCount(filteredApps.size)
    }

    private fun updateStats() {
        val userApps = allApps.count { !it.isSystemApp }
        val systemApps = allApps.count { it.isSystemApp }
        
        binding.tvStatsTitle.text = "App Statistics"
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

    private fun showAppDetails(appInfo: AppInfo) {
        val details = buildString {
            appendLine("Package: ${appInfo.packageName}")
            appendLine("Version: ${appInfo.version} (${appInfo.versionCode})")
            appendLine("Category: ${appInfo.category.name}")
            appendLine("Type: ${if (appInfo.isSystemApp) "System" else "User"} App")
            appendLine("Enabled: ${if (appInfo.isEnabled) "Yes" else "No"}")
            appendLine("Target SDK: ${appInfo.targetSdkVersion}")
            appendLine("Install Date: ${java.text.DateFormat.getDateInstance().format(java.util.Date(appInfo.installTime))}")
            appendLine("Last Update: ${java.text.DateFormat.getDateInstance().format(java.util.Date(appInfo.lastUpdateTime))}")
            if (appInfo.permissions.isNotEmpty()) {
                appendLine("Permissions: ${appInfo.permissions.size}")
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(appInfo.name)
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("App Info") { _, _ ->
                // Open app info in system settings
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = android.net.Uri.parse("package:${appInfo.packageName}")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open app info", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
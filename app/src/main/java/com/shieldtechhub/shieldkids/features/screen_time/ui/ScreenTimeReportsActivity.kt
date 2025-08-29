package com.shieldtechhub.shieldkids.features.screen_time.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ActivityScreenTimeReportsBinding
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import com.shieldtechhub.shieldkids.features.screen_time.adapters.TopAppsReportAdapter
import com.shieldtechhub.shieldkids.features.screen_time.adapters.CategoryUsageAdapter
import com.shieldtechhub.shieldkids.features.screen_time.model.AppUsageReportItem
import com.shieldtechhub.shieldkids.features.screen_time.model.CategoryUsageItem
import com.shieldtechhub.shieldkids.debug.ScreenTimeDebugHelper
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

class ScreenTimeReportsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScreenTimeReportsBinding
    private lateinit var screenTimeService: ScreenTimeService
    private lateinit var topAppsAdapter: TopAppsReportAdapter
    private lateinit var categoryUsageAdapter: CategoryUsageAdapter
    
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var childId: String = ""
    
    companion object {
        private const val TAG = "ScreenTimeReportsActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenTimeReportsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        deviceName = intent.getStringExtra("deviceName") ?: "Unknown Device"
        childId = intent.getStringExtra("childId") ?: ""
        
        if (deviceId.isEmpty() || childId.isEmpty()) {
            Toast.makeText(this, "Missing device or child information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize services
        screenTimeService = ScreenTimeService.getInstance(this)
        
        setupUI()
        setupAdapters()
        setupClickListeners()
        loadScreenTimeReports()
    }
    
    private fun setupUI() {
        // Setup toolbar
        binding.toolbarTitle.text = "Screen Time Reports"
        binding.toolbarSubtitle.text = deviceName
        
        // Add debug functionality - long click on title for debug
        binding.toolbarTitle.setOnLongClickListener {
            Log.d(TAG, "🐛 MANUAL DEBUG TRIGGER - Long click on reports title")
            lifecycleScope.launch {
                val debugResults = ScreenTimeDebugHelper.debugFirebaseScreenTimeAccess(childId, deviceId)
                Log.d(TAG, "🐛 MANUAL DEBUG RESULTS:\n$debugResults")
                Toast.makeText(this@ScreenTimeReportsActivity, "Debug logged - check LogCat for 'ScreenTimeDebug'", Toast.LENGTH_LONG).show()
            }
            true
        }
    }
    
    private fun setupAdapters() {
        // Top apps adapter
        topAppsAdapter = TopAppsReportAdapter()
        binding.recyclerTopApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerTopApps.adapter = topAppsAdapter
        
        // Category usage adapter  
        categoryUsageAdapter = CategoryUsageAdapter()
        binding.recyclerCategoryUsage.layoutManager = LinearLayoutManager(this)
        binding.recyclerCategoryUsage.adapter = categoryUsageAdapter
    }
    
    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Time period tabs
        binding.btnToday.setOnClickListener {
            selectTimePeriod(TimePeriod.TODAY)
        }
        
        binding.btnWeek.setOnClickListener {
            selectTimePeriod(TimePeriod.WEEK)
        }
        
        binding.btnMonth.setOnClickListener {
            selectTimePeriod(TimePeriod.MONTH)
        }
    }
    
    private fun selectTimePeriod(period: TimePeriod) {
        // Update tab selection UI
        binding.btnToday.isSelected = period == TimePeriod.TODAY
        binding.btnWeek.isSelected = period == TimePeriod.WEEK
        binding.btnMonth.isSelected = period == TimePeriod.MONTH
        
        // Load data for selected period
        when (period) {
            TimePeriod.TODAY -> loadTodayReport()
            TimePeriod.WEEK -> loadWeeklyReport()
            TimePeriod.MONTH -> loadMonthlyReport()
        }
    }
    
    private fun loadScreenTimeReports() {
        // Default to today's report
        selectTimePeriod(TimePeriod.TODAY)
    }
    
    private fun loadTodayReport() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🚀 Starting loadTodayReport() debug")
                Log.d(TAG, "📍 ScreenTimeReportsActivity - Device Usage Reports")
                
                // Run comprehensive debug
                val debugResults = ScreenTimeDebugHelper.debugFirebaseScreenTimeAccess(childId, deviceId)
                Log.d(TAG, "📋 Debug Results:\n$debugResults")
                
                val today = Date()
                Log.d(TAG, "Loading data for: childId=$childId, deviceId=$deviceId, date=$today")
                // Use the new method that reads from app inventory document
                val usageData = screenTimeService.getScreenTimeFromAppInventory(childId, deviceId)
                
                if (usageData != null) {
                    Log.d(TAG, "Found usage data: ${usageData.keys}")
                    displayTodayReport(usageData)
                } else {
                    Log.w(TAG, "No usage data found in Firebase for today")
                    showEmptyState("No screen time data available for today")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load today's report", e)
                showError("Failed to load screen time data")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun loadWeeklyReport() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val weeklyData = mutableListOf<Map<String, Any>>()
                val calendar = Calendar.getInstance()
                
                // Get last 7 days of data
                for (i in 0 until 7) {
                    val date = calendar.time
                    val dailyData = screenTimeService.getDailyUsageFromFirebase(date, childId, deviceId)
                    dailyData?.let { weeklyData.add(it) }
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                }
                
                if (weeklyData.isNotEmpty()) {
                    displayWeeklyReport(weeklyData)
                } else {
                    showEmptyState("No screen time data available for this week")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load weekly report", e)
                showError("Failed to load weekly screen time data")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun loadMonthlyReport() {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val monthlyData = mutableListOf<Map<String, Any>>()
                val calendar = Calendar.getInstance()
                
                // Get last 30 days of data
                for (i in 0 until 30) {
                    val date = calendar.time
                    val dailyData = screenTimeService.getDailyUsageFromFirebase(date, childId, deviceId)
                    dailyData?.let { monthlyData.add(it) }
                    calendar.add(Calendar.DAY_OF_MONTH, -1)
                }
                
                if (monthlyData.isNotEmpty()) {
                    displayMonthlyReport(monthlyData)
                } else {
                    showEmptyState("No screen time data available for this month")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load monthly report", e)
                showError("Failed to load monthly screen time data")
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun displayTodayReport(usageData: Map<String, Any>) {
        Log.d(TAG, "displayTodayReport called with data keys: ${usageData.keys}")
        
        // Update summary stats
        val totalScreenTime = usageData["totalScreenTimeMs"] as? Long ?: 0
        val screenUnlocks = usageData["screenUnlocks"] as? Long ?: 0
        val topApps = usageData["topApps"] as? List<Map<String, Any>> ?: emptyList()
        val appCount = topApps.size.toLong() // Use topApps count since appCount isn't in this format
        
        Log.d(TAG, "Summary stats - Time: ${formatDuration(totalScreenTime)}, Unlocks: $screenUnlocks, Apps: $appCount")
        
        binding.tvTotalTime.text = formatDuration(totalScreenTime)
        binding.tvScreenUnlocks.text = "$screenUnlocks unlocks"
        binding.tvAppsUsed.text = "$appCount apps used"
        
        // Display top apps (already extracted above)
        val topAppsReport = topApps.mapIndexed { index, app ->
            AppUsageReportItem(
                rank = index + 1,
                appName = app["appName"] as? String ?: "Unknown",
                packageName = app["packageName"] as? String ?: "",
                category = app["category"] as? String ?: "Other",
                usageTime = app["totalTimeMs"] as? Long ?: 0,
                launchCount = app["launchCount"] as? Long ?: 0
            )
        }
        topAppsAdapter.updateItems(topAppsReport)
        
        // Display category breakdown
        val categoryBreakdown = usageData["categoryBreakdown"] as? Map<String, Long> ?: emptyMap()
        val categoryItems = categoryBreakdown.map { (category, time) ->
            CategoryUsageItem(
                category = category,
                usageTime = time,
                percentage = if (totalScreenTime > 0) (time * 100 / totalScreenTime).toInt() else 0
            )
        }.sortedByDescending { it.usageTime }
        
        categoryUsageAdapter.updateItems(categoryItems)
        
        // Update period indicator
        binding.tvPeriodIndicator.text = "Today • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())}"
        
        showContent()
    }
    
    private fun displayWeeklyReport(weeklyData: List<Map<String, Any>>) {
        // Aggregate weekly stats
        var totalWeekTime = 0L
        var totalUnlocks = 0L
        var totalApps = 0L
        val appUsageMap = mutableMapOf<String, AppUsageReportItem>()
        val categoryUsageMap = mutableMapOf<String, Long>()
        
        weeklyData.forEach { dailyData ->
            totalWeekTime += dailyData["totalScreenTimeMs"] as? Long ?: 0
            totalUnlocks += dailyData["screenUnlocks"] as? Long ?: 0
            totalApps += dailyData["appCount"] as? Long ?: 0
            
            // Aggregate top apps
            val topApps = dailyData["topApps"] as? List<Map<String, Any>> ?: emptyList()
            topApps.forEach { app ->
                val packageName = app["packageName"] as? String ?: ""
                val existingApp = appUsageMap[packageName]
                
                if (existingApp != null) {
                    appUsageMap[packageName] = existingApp.copy(
                        usageTime = existingApp.usageTime + (app["totalTimeMs"] as? Long ?: 0),
                        launchCount = existingApp.launchCount + (app["launchCount"] as? Long ?: 0)
                    )
                } else {
                    appUsageMap[packageName] = AppUsageReportItem(
                        rank = 0,
                        appName = app["appName"] as? String ?: "Unknown",
                        packageName = packageName,
                        category = app["category"] as? String ?: "Other",
                        usageTime = app["totalTimeMs"] as? Long ?: 0,
                        launchCount = app["launchCount"] as? Long ?: 0
                    )
                }
            }
            
            // Aggregate categories
            val categoryBreakdown = dailyData["categoryBreakdown"] as? Map<String, Long> ?: emptyMap()
            categoryBreakdown.forEach { (category, time) ->
                categoryUsageMap[category] = (categoryUsageMap[category] ?: 0) + time
            }
        }
        
        // Update summary stats
        binding.tvTotalTime.text = formatDuration(totalWeekTime)
        binding.tvScreenUnlocks.text = "${totalUnlocks / weeklyData.size} avg unlocks/day"
        binding.tvAppsUsed.text = "${totalApps / weeklyData.size} avg apps/day"
        
        // Sort and rank apps
        val topAppsReport = appUsageMap.values
            .sortedByDescending { it.usageTime }
            .take(10)
            .mapIndexed { index, app -> app.copy(rank = index + 1) }
        
        topAppsAdapter.updateItems(topAppsReport)
        
        // Display category breakdown
        val categoryItems = categoryUsageMap.map { (category, time) ->
            CategoryUsageItem(
                category = category,
                usageTime = time,
                percentage = if (totalWeekTime > 0) (time * 100 / totalWeekTime).toInt() else 0
            )
        }.sortedByDescending { it.usageTime }
        
        categoryUsageAdapter.updateItems(categoryItems)
        
        // Update period indicator
        binding.tvPeriodIndicator.text = "Last 7 Days • ${weeklyData.size} days of data"
        
        showContent()
    }
    
    private fun displayMonthlyReport(monthlyData: List<Map<String, Any>>) {
        // Similar logic to weekly but for 30 days
        displayWeeklyReport(monthlyData) // Reuse logic for now
        binding.tvPeriodIndicator.text = "Last 30 Days • ${monthlyData.size} days of data"
    }
    
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.layoutContent.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showContent() {
        binding.layoutContent.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
    }
    
    private fun showEmptyState(message: String) {
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.layoutContent.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvEmptyMessage.text = message
    }
    
    private fun showError(message: String) {
        showEmptyState(message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    enum class TimePeriod {
        TODAY, WEEK, MONTH
    }
}
package com.shieldtechhub.shieldkids.features.screen_time.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ActivityScreenTimeDashboardBinding
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import com.shieldtechhub.shieldkids.features.screen_time.ui.adapters.DailyUsageAdapter
import kotlinx.coroutines.launch
import java.util.*

class ScreenTimeDashboardActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ScreenTimeDashboard"
        private const val EXTRA_CHILD_ID = "child_id"
        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_CHILD_NAME = "child_name"
        
        fun createIntent(context: Context, childId: String, deviceId: String, childName: String): Intent {
            return Intent(context, ScreenTimeDashboardActivity::class.java).apply {
                putExtra(EXTRA_CHILD_ID, childId)
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_CHILD_NAME, childName)
            }
        }
    }
    
    private lateinit var binding: ActivityScreenTimeDashboardBinding
    private lateinit var screenTimeService: ScreenTimeService
    private lateinit var dailyUsageAdapter: DailyUsageAdapter
    
    private lateinit var childId: String
    private lateinit var deviceId: String
    private lateinit var childName: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenTimeDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        childId = intent.getStringExtra(EXTRA_CHILD_ID) ?: return finish()
        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: return finish()
        childName = intent.getStringExtra(EXTRA_CHILD_NAME) ?: "Child"
        
        setupUI()
        initializeServices()
        loadScreenTimeData()
    }
    
    private fun setupUI() {
        // Setup toolbar
        binding.toolbar.title = "$childName's Screen Time"
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Setup RecyclerView
        dailyUsageAdapter = DailyUsageAdapter()
        binding.recyclerViewDailyUsage.apply {
            layoutManager = LinearLayoutManager(this@ScreenTimeDashboardActivity)
            adapter = dailyUsageAdapter
        }
        
        // Setup refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadScreenTimeData()
        }
        
        // Setup time period buttons
        binding.btnWeekView.setOnClickListener {
            showWeeklyView()
        }
        
        binding.btnMonthView.setOnClickListener {
            showMonthlyView()
        }
        
        binding.btnTodayView.setOnClickListener {
            showTodayView()
        }
        
        // Default to week view
        showWeeklyView()
    }
    
    private fun initializeServices() {
        screenTimeService = ScreenTimeService.getInstance(this)
    }
    
    private fun loadScreenTimeData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.swipeRefreshLayout.isRefreshing = false
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading screen time data for child: $childId, device: $deviceId")
                
                // ONLY get data from Firebase - never local collection
                val usageStats = screenTimeService.getUsageStatsForParent(childId, deviceId, 7)
                
                if (usageStats.isNotEmpty()) {
                    Log.d(TAG, "Found ${usageStats.size} days of usage data from Firebase")
                    displayUsageStats(usageStats)
                    binding.emptyStateLayout.visibility = View.GONE
                    binding.contentLayout.visibility = View.VISIBLE
                } else {
                    Log.d(TAG, "No usage data found in Firebase for child: $childId")
                    showEmptyState()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load screen time data", e)
                showErrorState()
                
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun displayUsageStats(usageStats: List<Map<String, Any>>) {
        // Update summary cards
        updateSummaryCards(usageStats)
        
        // Update daily usage list
        dailyUsageAdapter.updateData(usageStats)
        
        // Update charts/graphs (placeholder for future implementation)
        updateUsageCharts(usageStats)
    }
    
    private fun updateSummaryCards(usageStats: List<Map<String, Any>>) {
        try {
            var totalWeekTime = 0L
            var totalAppsCount = 0
            var totalUnlocks = 0
            var averageDailyTime = 0L
            
            usageStats.forEach { dayData ->
                totalWeekTime += (dayData["totalScreenTimeMs"] as? Long) ?: 0L
                totalAppsCount += (dayData["appCount"] as? Int) ?: 0
                totalUnlocks += (dayData["screenUnlocks"] as? Int) ?: 0
            }
            
            if (usageStats.isNotEmpty()) {
                averageDailyTime = totalWeekTime / usageStats.size
            }
            
            // Update UI
            binding.tvTotalWeekTime.text = formatDuration(totalWeekTime)
            binding.tvAverageDailyTime.text = formatDuration(averageDailyTime)
            binding.tvTotalApps.text = totalAppsCount.toString()
            binding.tvTotalUnlocks.text = totalUnlocks.toString()
            
            // Calculate and show week-over-week change
            if (usageStats.size >= 7) {
                val thisWeekTime = usageStats.take(7).sumOf { (it["totalScreenTimeMs"] as? Long) ?: 0L }
                // For now, we'll just show this week's time
                val changeText = formatDuration(thisWeekTime)
                binding.tvWeekChange.text = "This week: $changeText"
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error updating summary cards", e)
        }
    }
    
    private fun updateUsageCharts(usageStats: List<Map<String, Any>>) {
        // This would integrate with a charting library like MPAndroidChart
        // For now, we'll show a simple text-based representation
        
        try {
            val chartData = StringBuilder()
            chartData.appendLine("Daily Screen Time (Last 7 Days)")
            chartData.appendLine("─────────────────────────────")
            
            usageStats.take(7).forEachIndexed { index, dayData ->
                val date = Date((dayData["date"] as? Long) ?: System.currentTimeMillis())
                val screenTime = (dayData["totalScreenTimeMs"] as? Long) ?: 0L
                val dayName = getDayName(date)
                
                chartData.appendLine("$dayName: ${formatDuration(screenTime)}")
                
                // Simple bar representation
                val hours = screenTime / (1000 * 60 * 60)
                val barLength = minOf(hours.toInt(), 10)
                val bar = "█".repeat(barLength.toInt())
                chartData.appendLine("$bar")
                chartData.appendLine()
            }
            
            binding.tvUsageChart.text = chartData.toString()
            
        } catch (e: Exception) {
            Log.w(TAG, "Error updating usage charts", e)
            binding.tvUsageChart.text = "Chart data unavailable"
        }
    }
    
    private fun showWeeklyView() {
        binding.btnWeekView.isSelected = true
        binding.btnMonthView.isSelected = false
        binding.btnTodayView.isSelected = false
        
        binding.tvPeriodTitle.text = "Weekly Overview"
        loadScreenTimeData() // Load 7 days
    }
    
    private fun showMonthlyView() {
        binding.btnWeekView.isSelected = false
        binding.btnMonthView.isSelected = true
        binding.btnTodayView.isSelected = false
        
        binding.tvPeriodTitle.text = "Monthly Overview"
        
        // Load 30 days of data
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val monthlyStats = screenTimeService.getUsageStatsForParent(childId, deviceId, 30)
                displayUsageStats(monthlyStats)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load monthly data", e)
                showErrorState()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun showTodayView() {
        binding.btnWeekView.isSelected = false
        binding.btnMonthView.isSelected = false
        binding.btnTodayView.isSelected = true
        
        binding.tvPeriodTitle.text = "Today's Usage"
        
        // Load just today's data
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val todayStats = screenTimeService.getUsageStatsForParent(childId, deviceId, 1)
                displayUsageStats(todayStats)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load today's data", e)
                showErrorState()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun showEmptyState() {
        binding.contentLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = "No screen time data available for $childName"
        binding.tvEmptySubtitle.text = "Make sure the child device (ID: $deviceId) has:\n• Shield Kids app installed\n• Usage access permission granted\n• Internet connection for data sync"
    }
    
    private fun showErrorState() {
        binding.contentLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.tvEmptyMessage.text = "Unable to load screen time data"
        binding.tvEmptySubtitle.text = "Please check your internet connection and try again"
        
        binding.btnRetry.visibility = View.VISIBLE
        binding.btnRetry.setOnClickListener {
            binding.btnRetry.visibility = View.GONE
            loadScreenTimeData()
        }
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
    
    private fun getDayName(date: Date): String {
        val formatter = java.text.SimpleDateFormat("EEE MMM dd", Locale.getDefault())
        return formatter.format(date)
    }
}
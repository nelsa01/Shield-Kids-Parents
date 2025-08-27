package com.shieldtechhub.shieldkids.features.screen_time.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import java.text.SimpleDateFormat
import java.util.*

class DailyUsageAdapter : RecyclerView.Adapter<DailyUsageAdapter.UsageViewHolder>() {
    
    private var usageData: List<Map<String, Any>> = emptyList()
    private var onItemClickListener: ((Map<String, Any>) -> Unit)? = null
    
    fun updateData(newData: List<Map<String, Any>>) {
        usageData = newData.sortedByDescending { it["date"] as? Long ?: 0L }
        notifyDataSetChanged()
    }
    
    fun setOnItemClickListener(listener: (Map<String, Any>) -> Unit) {
        onItemClickListener = listener
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_daily_usage, parent, false)
        return UsageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: UsageViewHolder, position: Int) {
        val dayData = usageData[position]
        holder.bind(dayData, onItemClickListener)
    }
    
    override fun getItemCount(): Int = usageData.size
    
    class UsageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvDayName: TextView = itemView.findViewById(R.id.tvDayName)
        private val tvTotalTime: TextView = itemView.findViewById(R.id.tvTotalTime)
        private val tvAppCount: TextView = itemView.findViewById(R.id.tvAppCount)
        private val tvUnlocks: TextView = itemView.findViewById(R.id.tvUnlocks)
        private val tvTopApp: TextView = itemView.findViewById(R.id.tvTopApp)
        private val tvUsageBar: View = itemView.findViewById(R.id.vUsageBar)
        
        fun bind(dayData: Map<String, Any>, onClickListener: ((Map<String, Any>) -> Unit)?) {
            try {
                // Parse data
                val dateMs = dayData["date"] as? Long ?: System.currentTimeMillis()
                val date = Date(dateMs)
                val totalTimeMs = dayData["totalScreenTimeMs"] as? Long ?: 0L
                val appCount = dayData["appCount"] as? Int ?: 0
                val unlocks = dayData["screenUnlocks"] as? Int ?: 0
                
                // Format date
                val dayFormatter = SimpleDateFormat("EEE", Locale.getDefault())
                val dateFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())
                
                tvDayName.text = dayFormatter.format(date)
                tvDate.text = dateFormatter.format(date)
                
                // Format screen time
                tvTotalTime.text = formatDuration(totalTimeMs)
                
                // App count and unlocks
                tvAppCount.text = "$appCount apps"
                tvUnlocks.text = "$unlocks unlocks"
                
                // Top app
                val topApps = dayData["topApps"] as? List<Map<String, Any>>
                if (!topApps.isNullOrEmpty()) {
                    val topApp = topApps[0]
                    val appName = topApp["appName"] as? String ?: "Unknown"
                    val appTime = topApp["totalTimeMs"] as? Long ?: 0L
                    tvTopApp.text = "$appName (${formatDuration(appTime)})"
                } else {
                    tvTopApp.text = "No app data"
                }
                
                // Usage bar (visual representation of usage intensity)
                updateUsageBar(totalTimeMs)
                
                // Click listener
                itemView.setOnClickListener {
                    onClickListener?.invoke(dayData)
                }
                
                // Style based on usage level
                styleBasedOnUsage(totalTimeMs)
                
            } catch (e: Exception) {
                android.util.Log.w("DailyUsageAdapter", "Error binding data", e)
                // Set default values on error
                tvDayName.text = "---"
                tvDate.text = "---"
                tvTotalTime.text = "0m"
                tvAppCount.text = "0 apps"
                tvUnlocks.text = "0 unlocks"
                tvTopApp.text = "No data"
            }
        }
        
        private fun updateUsageBar(totalTimeMs: Long) {
            val hours = totalTimeMs / (1000 * 60 * 60)
            
            // Set bar width based on usage (0-8 hours scale)
            val maxWidth = 100 // dp
            val widthPercent = minOf(hours / 8.0, 1.0)
            val layoutParams = tvUsageBar.layoutParams
            
            // This is a simplified version - in a real app, you'd want to use proper dimension calculations
            val newWidth = (maxWidth * widthPercent).toInt()
            tvUsageBar.layoutParams = layoutParams.apply {
                width = if (newWidth > 0) newWidth else 4 // Minimum width for visibility
            }
            
            // Color based on usage intensity
            val color = when {
                hours < 1 -> R.color.usage_low
                hours < 3 -> R.color.usage_medium
                hours < 6 -> R.color.usage_high
                else -> R.color.usage_very_high
            }
            
            tvUsageBar.setBackgroundResource(color)
        }
        
        private fun styleBasedOnUsage(totalTimeMs: Long) {
            val hours = totalTimeMs / (1000 * 60 * 60)
            
            // Style the time text based on usage level
            val textColor = when {
                hours < 1 -> R.color.gray_600
                hours < 3 -> R.color.orange_600
                hours < 6 -> R.color.red_500
                else -> R.color.red_700
            }
            
            tvTotalTime.setTextColor(itemView.context.getColor(textColor))
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
    }
}
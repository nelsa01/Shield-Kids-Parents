package com.shieldtechhub.shieldkids.features.screen_time.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ItemTopAppReportBinding
import com.shieldtechhub.shieldkids.features.screen_time.model.AppUsageReportItem

class TopAppsReportAdapter : RecyclerView.Adapter<TopAppsReportAdapter.ViewHolder>() {
    
    private var items: List<AppUsageReportItem> = emptyList()
    
    fun updateItems(newItems: List<AppUsageReportItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopAppReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount(): Int = items.size
    
    inner class ViewHolder(private val binding: ItemTopAppReportBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: AppUsageReportItem) {
            binding.tvRank.text = "#${item.rank}"
            binding.tvAppName.text = item.appName
            binding.tvCategory.text = item.category.lowercase().replaceFirstChar { it.uppercase() }
            binding.tvUsageTime.text = item.getFormattedUsageTime()
            binding.tvLaunches.text = "${item.launchCount} opens"
            binding.tvIntensity.text = item.getUsageIntensityText()
            
            // Set rank background color
            val rankColor = when (item.rank) {
                1 -> "#FFD700" // Gold
                2 -> "#C0C0C0" // Silver  
                3 -> "#CD7F32" // Bronze
                else -> "#E0E0E0" // Default grey
            }
            binding.tvRank.setBackgroundColor(Color.parseColor(rankColor))
            
            // Set intensity color
            try {
                binding.tvIntensity.setTextColor(Color.parseColor(item.getUsageIntensityColor()))
            } catch (e: Exception) {
                binding.tvIntensity.setTextColor(Color.parseColor("#757575"))
            }
            
            // Set app icon placeholder
            binding.ivAppIcon.setImageResource(R.drawable.ic_apps)
        }
    }
}
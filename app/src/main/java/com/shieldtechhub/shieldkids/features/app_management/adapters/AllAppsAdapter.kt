package com.shieldtechhub.shieldkids.features.app_management.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ItemAppBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import com.shieldtechhub.shieldkids.features.app_management.model.AppWithUsage

class AllAppsAdapter(
    private val onAppClick: (AppWithUsage) -> Unit,
    private val onToggleBlock: (AppWithUsage, Boolean) -> Unit
) : RecyclerView.Adapter<AllAppsAdapter.AppViewHolder>() {

    private var apps: List<AppWithUsage> = emptyList()

    fun updateApps(newApps: List<AppInfo>) {
        // Convert AppInfo to AppWithUsage (without usage data)
        apps = newApps.map { AppWithUsage(it) }
        notifyDataSetChanged()
    }
    
    fun updateAppsWithUsage(newApps: List<AppWithUsage>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAppClick(apps[position])
                }
            }
            
            // Set up toggle switch listener
            binding.switchBlockApp.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onToggleBlock(apps[position], isChecked)
                }
            }
        }

        fun bind(appWithUsage: AppWithUsage) {
            val appInfo = appWithUsage.appInfo
            
            binding.tvAppName.text = appInfo.name
            
            // Show usage time if available, otherwise show "No usage data"
            if (appWithUsage.hasUsageData && appWithUsage.usageTimeMs > 0) {
                binding.tvUsageTime.text = appWithUsage.getFormattedUsageTime()
            } else {
                binding.tvUsageTime.text = "No usage data"
            }
            
            // Set toggle state based on app blocking status
            binding.switchBlockApp.isChecked = appInfo.isBlocked
        }
    }
}
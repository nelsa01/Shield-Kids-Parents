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
    private val onAppClick: (AppWithUsage) -> Unit
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
        }

        fun bind(appWithUsage: AppWithUsage) {
            val appInfo = appWithUsage.appInfo
            
            binding.tvAppName.text = appInfo.name
            binding.tvPackageName.text = appInfo.packageName
            binding.tvAppVersion.text = "v${appInfo.version}"
            binding.tvAppCategory.text = appInfo.category.name.lowercase().replaceFirstChar { it.uppercase() }
            
            // Set app type badge
            binding.tvAppType.text = if (appInfo.isSystemApp) "System" else "User"
            binding.tvAppType.setBackgroundResource(
                if (appInfo.isSystemApp) R.drawable.badge_background_orange 
                else R.drawable.badge_background_green
            )
            
            // Set category color indicator
            binding.viewCategoryIndicator.setBackgroundResource(
                when (appInfo.category) {
                    com.shieldtechhub.shieldkids.features.app_management.service.AppCategory.SOCIAL -> R.color.category_social
                    com.shieldtechhub.shieldkids.features.app_management.service.AppCategory.GAMES -> R.color.category_games
                    com.shieldtechhub.shieldkids.features.app_management.service.AppCategory.EDUCATIONAL -> R.color.category_educational
                    com.shieldtechhub.shieldkids.features.app_management.service.AppCategory.ENTERTAINMENT -> R.color.category_entertainment
                    com.shieldtechhub.shieldkids.features.app_management.service.AppCategory.BROWSERS -> R.color.category_browsers
                    com.shieldtechhub.shieldkids.features.app_management.service.AppCategory.SHOPPING -> R.color.category_shopping
                    else -> R.color.category_other
                }
            )
            
            // Show usage time if available
            if (appWithUsage.hasUsageData && appWithUsage.usageTimeMs > 0) {
                binding.tvUsageTime.visibility = View.VISIBLE
                binding.tvUsageTime.text = appWithUsage.getFormattedUsageTime()
                // Set usage time color based on usage intensity
                try {
                    binding.tvUsageTime.setTextColor(Color.parseColor(appWithUsage.getUsageColor()))
                } catch (e: Exception) {
                    // Fallback color if parsing fails
                    binding.tvUsageTime.setTextColor(Color.parseColor("#FF9800"))
                }
            } else {
                binding.tvUsageTime.visibility = View.GONE
            }
            
            // Show app icon placeholder
            binding.ivAppIcon.setImageResource(R.drawable.ic_apps)
        }
    }
}
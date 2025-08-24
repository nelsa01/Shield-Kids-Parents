package com.shieldtechhub.shieldkids.features.app_management.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ItemAppBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo

class AllAppsAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AllAppsAdapter.AppViewHolder>() {

    private var apps: List<AppInfo> = emptyList()

    fun updateApps(newApps: List<AppInfo>) {
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

        fun bind(appInfo: AppInfo) {
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
            
            // Show app icon placeholder
            binding.ivAppIcon.setImageResource(R.drawable.ic_apps)
        }
    }
}
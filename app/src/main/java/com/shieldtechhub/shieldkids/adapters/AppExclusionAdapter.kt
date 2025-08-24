package com.shieldtechhub.shieldkids.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.common.utils.ImageLoader
import com.shieldtechhub.shieldkids.databinding.ItemAppExclusionBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo

class AppExclusionAdapter(
    private val onExclusionChanged: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppExclusionAdapter.AppExclusionViewHolder>() {
    
    data class AppExclusionItem(
        val appInfo: AppInfo,
        val isExcluded: Boolean
    )
    
    private var apps = listOf<AppExclusionItem>()
    
    fun updateApps(newApps: List<AppExclusionItem>) {
        apps = newApps
        notifyDataSetChanged()
    }
    
    fun getCurrentApps(): List<AppExclusionItem> = apps
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppExclusionViewHolder {
        val binding = ItemAppExclusionBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return AppExclusionViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AppExclusionViewHolder, position: Int) {
        holder.bind(apps[position])
    }
    
    override fun getItemCount(): Int = apps.size
    
    inner class AppExclusionViewHolder(
        private val binding: ItemAppExclusionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: AppExclusionItem) {
            val appInfo = item.appInfo
            
            // Set app icon
            ImageLoader.loadInto(
                binding.root.context,
                binding.ivAppIcon,
                "", // AppInfo doesn't have icon URI, will use default
                R.drawable.ic_shield
            )
            
            // Try to load actual app icon if possible
            try {
                val packageManager = binding.root.context.packageManager
                val appIcon = packageManager.getApplicationIcon(appInfo.packageName)
                binding.ivAppIcon.setImageDrawable(appIcon)
            } catch (e: Exception) {
                // Keep default icon
            }
            
            // Set app name
            binding.tvAppName.text = appInfo.name
            
            // Set package name
            binding.tvPackageName.text = appInfo.packageName
            
            // Set app category
            binding.tvAppCategory.text = appInfo.category.name.lowercase().replaceFirstChar { it.uppercase() }
            
            // Set version info
            binding.tvAppVersion.text = "v${appInfo.version}"
            
            // Set exclusion status
            binding.switchExcluded.isChecked = item.isExcluded
            
            // Set exclusion status text
            binding.tvExclusionStatus.text = if (item.isExcluded) {
                "Excluded from blocking"
            } else {
                "Subject to blocking"
            }
            
            binding.tvExclusionStatus.setTextColor(
                binding.root.context.getColor(
                    if (item.isExcluded) R.color.green_500 else R.color.red_500
                )
            )
            
            // Handle switch changes
            binding.switchExcluded.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != item.isExcluded) {
                    onExclusionChanged(appInfo, isChecked)
                }
            }
            
            // Handle item click to toggle switch
            binding.root.setOnClickListener {
                binding.switchExcluded.isChecked = !binding.switchExcluded.isChecked
            }
        }
    }
}
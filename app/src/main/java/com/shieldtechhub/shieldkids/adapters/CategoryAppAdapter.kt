package com.shieldtechhub.shieldkids.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.common.utils.ImageLoader
import com.shieldtechhub.shieldkids.databinding.ItemCategoryAppBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy

class CategoryAppAdapter(
    private val onPolicyChanged: (AppInfo, AppPolicy.Action) -> Unit
) : RecyclerView.Adapter<CategoryAppAdapter.CategoryAppViewHolder>() {
    
    data class CategoryAppItem(
        val appInfo: AppInfo,
        val currentAction: AppPolicy.Action,
        val hasIndividualPolicy: Boolean
    )
    
    private var apps = listOf<CategoryAppItem>()
    
    fun updateApps(newApps: List<CategoryAppItem>) {
        apps = newApps
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryAppViewHolder {
        val binding = ItemCategoryAppBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return CategoryAppViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: CategoryAppViewHolder, position: Int) {
        holder.bind(apps[position])
    }
    
    override fun getItemCount(): Int = apps.size
    
    inner class CategoryAppViewHolder(
        private val binding: ItemCategoryAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: CategoryAppItem) {
            val appInfo = item.appInfo
            val context = binding.root.context
            
            // Set app icon
            ImageLoader.loadInto(
                context,
                binding.ivAppIcon,
                "", // AppInfo doesn't have icon URI, will use default
                R.drawable.ic_shield
            )
            
            // Try to load actual app icon if possible
            try {
                val packageManager = context.packageManager
                val appIcon = packageManager.getApplicationIcon(appInfo.packageName)
                binding.ivAppIcon.setImageDrawable(appIcon)
            } catch (e: Exception) {
                // Keep default icon
            }
            
            // Set app name
            binding.tvAppName.text = appInfo.name
            
            // Set package name
            binding.tvPackageName.text = appInfo.packageName
            
            // Set version info
            binding.tvAppVersion.text = "v${appInfo.version}"
            
            // Show individual policy indicator
            if (item.hasIndividualPolicy) {
                binding.tvIndividualPolicy.visibility = View.VISIBLE
                binding.tvIndividualPolicy.text = "Individual policy"
            } else {
                binding.tvIndividualPolicy.visibility = View.GONE
            }
            
            // Set policy status
            updatePolicyStatus(item.currentAction)
            
            // Set up policy buttons
            setupPolicyButtons(item)
            
            // Handle item click
            binding.root.setOnClickListener {
                togglePolicyActions()
            }
        }
        
        private fun updatePolicyStatus(action: AppPolicy.Action) {
            val context = binding.root.context
            
            when (action) {
                AppPolicy.Action.ALLOW -> {
                    binding.tvPolicyStatus.text = "Allowed"
                    binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(context, R.color.green_600))
                    binding.viewPolicyIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.green_500))
                }
                AppPolicy.Action.BLOCK -> {
                    binding.tvPolicyStatus.text = "Blocked"
                    binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(context, R.color.red_600))
                    binding.viewPolicyIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.red_500))
                }
                AppPolicy.Action.TIME_LIMIT -> {
                    binding.tvPolicyStatus.text = "Time Limited"
                    binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(context, R.color.orange_600))
                    binding.viewPolicyIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.orange_500))
                }
                AppPolicy.Action.SCHEDULE -> {
                    binding.tvPolicyStatus.text = "Scheduled"
                    binding.tvPolicyStatus.setTextColor(ContextCompat.getColor(context, R.color.blue_600))
                    binding.viewPolicyIndicator.setBackgroundColor(ContextCompat.getColor(context, R.color.blue_500))
                }
            }
        }
        
        private fun setupPolicyButtons(item: CategoryAppItem) {
            val context = binding.root.context
            
            // Reset button styles
            resetButtonStyles()
            
            // Highlight current action
            when (item.currentAction) {
                AppPolicy.Action.ALLOW -> {
                    binding.btnAllow.setBackgroundResource(R.drawable.button_primary)
                    binding.btnAllow.setTextColor(ContextCompat.getColor(context, R.color.white))
                }
                AppPolicy.Action.BLOCK -> {
                    binding.btnBlock.setBackgroundResource(R.drawable.button_danger)
                    binding.btnBlock.setTextColor(ContextCompat.getColor(context, R.color.white))
                }
                else -> {
                    // For TIME_LIMIT and SCHEDULE, no button highlighting yet
                }
            }
            
            // Set button click listeners
            binding.btnAllow.setOnClickListener {
                if (item.currentAction != AppPolicy.Action.ALLOW) {
                    onPolicyChanged(item.appInfo, AppPolicy.Action.ALLOW)
                }
                hidePolicyActions()
            }
            
            binding.btnBlock.setOnClickListener {
                if (item.currentAction != AppPolicy.Action.BLOCK) {
                    onPolicyChanged(item.appInfo, AppPolicy.Action.BLOCK)
                }
                hidePolicyActions()
            }
            
            binding.btnTimeLimit.setOnClickListener {
                // TODO: Implement time limit configuration
                hidePolicyActions()
            }
            
            binding.btnSchedule.setOnClickListener {
                // TODO: Implement schedule configuration
                hidePolicyActions()
            }
        }
        
        private fun resetButtonStyles() {
            val context = binding.root.context
            val grayColor = ContextCompat.getColor(context, R.color.gray_600)
            
            binding.btnAllow.setBackgroundResource(R.drawable.button_secondary)
            binding.btnAllow.setTextColor(grayColor)
            
            binding.btnBlock.setBackgroundResource(R.drawable.button_secondary)
            binding.btnBlock.setTextColor(grayColor)
            
            binding.btnTimeLimit.setBackgroundResource(R.drawable.button_secondary)
            binding.btnTimeLimit.setTextColor(grayColor)
            
            binding.btnSchedule.setBackgroundResource(R.drawable.button_secondary)
            binding.btnSchedule.setTextColor(grayColor)
        }
        
        private fun togglePolicyActions() {
            if (binding.layoutPolicyActions.visibility == View.VISIBLE) {
                hidePolicyActions()
            } else {
                showPolicyActions()
            }
        }
        
        private fun showPolicyActions() {
            binding.layoutPolicyActions.visibility = View.VISIBLE
            binding.ivExpandIcon.rotation = 180f
        }
        
        private fun hidePolicyActions() {
            binding.layoutPolicyActions.visibility = View.GONE
            binding.ivExpandIcon.rotation = 0f
        }
    }
}
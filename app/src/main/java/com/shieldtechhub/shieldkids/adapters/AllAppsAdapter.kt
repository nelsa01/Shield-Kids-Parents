package com.shieldtechhub.shieldkids.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ItemAppBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo

class AllAppsAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onToggleBlock: (AppInfo, Boolean) -> Unit
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
            
            // Set up toggle switch listener
            binding.switchBlockApp.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onToggleBlock(apps[position], isChecked)
                }
            }
        }

        fun bind(appInfo: AppInfo) {
            binding.tvAppName.text = appInfo.name
            
            // Set screen time based on actual usage data if available
            // For now, show "No usage data" since this adapter doesn't have usage info
            binding.tvUsageTime.text = "No usage data"
            
            // Set toggle state based on app blocking status
            binding.switchBlockApp.isChecked = appInfo.isBlocked
        }
    }
}
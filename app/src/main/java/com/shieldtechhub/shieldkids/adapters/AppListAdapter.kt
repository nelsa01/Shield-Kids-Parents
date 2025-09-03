package com.shieldtechhub.shieldkids.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ItemAppListBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.app_management.service.AppInfo
import java.text.DateFormat
import java.util.Date

class AppListAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var apps = mutableListOf<AppInfo>()

    fun updateApps(newApps: List<AppInfo>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    override fun getItemCount(): Int = apps.size

    inner class AppViewHolder(
        private val binding: ItemAppListBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            binding.apply {
                // App basic info
                tvAppName.text = app.name
                tvPackageName.text = app.packageName
                tvAppVersion.text = "v${app.version}"

                // App icon
                if (app.icon != null) {
                    ivAppIcon.setImageDrawable(app.icon)
                } else {
                    ivAppIcon.setImageResource(R.drawable.ic_apps)
                }

                // Category badge
                tvCategoryBadge.text = getCategoryDisplayName(app.category)
                tvCategoryBadge.setBackgroundResource(getCategoryBackgroundColor(app.category))
                tvCategoryBadge.setTextColor(ContextCompat.getColor(itemView.context, getCategoryTextColor(app.category)))

                // App type indicator
                tvAppType.text = if (app.isSystemApp) "System" else "User"
                tvAppType.setTextColor(ContextCompat.getColor(
                    itemView.context,
                    if (app.isSystemApp) R.color.orange else R.color.teal_500
                ))

                // Status indicator
                ivStatusIndicator.setImageResource(
                    if (app.isEnabled) R.drawable.ic_check_circle else R.drawable.ic_error
                )
                ivStatusIndicator.setColorFilter(ContextCompat.getColor(
                    itemView.context,
                    if (app.isEnabled) R.color.green_500 else R.color.red_500
                ))

                // Additional info
                tvInstallDate.text = "Installed: ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(app.installTime))}"
                tvTargetSdk.text = "Target SDK: ${app.targetSdkVersion}"

                // Permissions count
                tvPermissionsCount.text = "${app.permissions.size} permissions"

                // Click listener
                root.setOnClickListener { onAppClick(app) }
            }
        }

        private fun getCategoryDisplayName(category: AppCategory): String {
            return when (category) {
                AppCategory.SOCIAL -> "Social"
                AppCategory.GAMES -> "Games"
                AppCategory.EDUCATIONAL -> "Education"
                AppCategory.ENTERTAINMENT -> "Entertainment"
                AppCategory.PRODUCTIVITY -> "Productivity"
                AppCategory.COMMUNICATION -> "Communication"
                AppCategory.SYSTEM -> "System"
                AppCategory.OTHER -> "Other"
                AppCategory.BROWSERS -> "Browsers"
                AppCategory.SHOPPING -> "Shopping"
                AppCategory.NEWS -> "News"
                AppCategory.PHOTO_VIDEO -> "Photo & Video"
                AppCategory.MUSIC -> "Music"
                AppCategory.HEALTH -> "Health"
                AppCategory.TRAVEL -> "Travel"
                AppCategory.FINANCE -> "Finance"
            }
        }

        private fun getCategoryBackgroundColor(category: AppCategory): Int {
            return when (category) {
                AppCategory.SOCIAL -> R.drawable.badge_social
                AppCategory.GAMES -> R.drawable.badge_games
                AppCategory.EDUCATIONAL -> R.drawable.badge_educational
                AppCategory.ENTERTAINMENT -> R.drawable.badge_entertainment
                AppCategory.PRODUCTIVITY -> R.drawable.badge_productivity
                AppCategory.COMMUNICATION -> R.drawable.badge_communication
                AppCategory.SYSTEM -> R.drawable.badge_system
                AppCategory.OTHER -> R.drawable.badge_other
                AppCategory.BROWSERS -> R.drawable.badge_productivity // Reuse productivity badge
                AppCategory.SHOPPING -> R.drawable.badge_other // Reuse other badge
                AppCategory.NEWS -> R.drawable.badge_other
                AppCategory.PHOTO_VIDEO -> R.drawable.badge_entertainment
                AppCategory.MUSIC -> R.drawable.badge_entertainment
                AppCategory.HEALTH -> R.drawable.badge_educational
                AppCategory.TRAVEL -> R.drawable.badge_other
                AppCategory.FINANCE -> R.drawable.badge_productivity
            }
        }

        private fun getCategoryTextColor(category: AppCategory): Int {
            return when (category) {
                AppCategory.SOCIAL -> R.color.white
                AppCategory.GAMES -> R.color.white
                AppCategory.EDUCATIONAL -> R.color.white
                AppCategory.ENTERTAINMENT -> R.color.white
                AppCategory.PRODUCTIVITY -> R.color.white
                AppCategory.COMMUNICATION -> R.color.white
                AppCategory.SYSTEM -> R.color.white
                AppCategory.OTHER -> R.color.gray_600
                AppCategory.BROWSERS -> R.color.white
                AppCategory.SHOPPING -> R.color.gray_600
                AppCategory.NEWS -> R.color.gray_600
                AppCategory.PHOTO_VIDEO -> R.color.white
                AppCategory.MUSIC -> R.color.white
                AppCategory.HEALTH -> R.color.white
                AppCategory.TRAVEL -> R.color.gray_600
                AppCategory.FINANCE -> R.color.white
            }
        }
    }
}
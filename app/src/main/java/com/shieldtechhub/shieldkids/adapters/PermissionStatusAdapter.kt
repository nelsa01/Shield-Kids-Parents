package com.shieldtechhub.shieldkids.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.common.utils.PermissionStatus
import com.shieldtechhub.shieldkids.databinding.ItemPermissionStatusBinding

class PermissionStatusAdapter(
    private val onPermissionClick: (String) -> Unit
) : RecyclerView.Adapter<PermissionStatusAdapter.PermissionViewHolder>() {

    private var permissions = mutableListOf<PermissionItem>()

    data class PermissionItem(
        val permission: String,
        val status: PermissionStatus,
        val description: String,
        val isEssential: Boolean,
        val requiresSpecialHandling: Boolean
    )

    fun updatePermissions(newPermissions: List<PermissionItem>) {
        permissions.clear()
        permissions.addAll(newPermissions)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
        val binding = ItemPermissionStatusBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PermissionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) {
        holder.bind(permissions[position])
    }

    override fun getItemCount(): Int = permissions.size

    inner class PermissionViewHolder(
        private val binding: ItemPermissionStatusBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PermissionItem) {
            binding.apply {
                tvPermissionName.text = getPermissionDisplayName(item.permission)
                tvPermissionDescription.text = item.description
                
                // Show essential badge
                if (item.isEssential) {
                    tvEssentialBadge.visibility = View.VISIBLE
                    tvEssentialBadge.text = "ESSENTIAL"
                    tvEssentialBadge.setBackgroundResource(R.drawable.badge_essential)
                    tvEssentialBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.orange))
                } else {
                    tvEssentialBadge.visibility = View.VISIBLE
                    tvEssentialBadge.text = "OPTIONAL"
                    tvEssentialBadge.setBackgroundResource(R.drawable.badge_optional)
                    tvEssentialBadge.setTextColor(ContextCompat.getColor(itemView.context, R.color.gray_600))
                }

                // Update status indicator
                updateStatusIndicator(item.status)

                // Handle click
                root.setOnClickListener {
                    if (item.status != PermissionStatus.GRANTED) {
                        onPermissionClick(item.permission)
                    }
                }

                // Update clickability and appearance
                root.isClickable = item.status != PermissionStatus.GRANTED
                root.alpha = if (item.status == PermissionStatus.GRANTED) 0.7f else 1.0f
            }
        }

        private fun updateStatusIndicator(status: PermissionStatus) {
            binding.apply {
                when (status) {
                    PermissionStatus.GRANTED -> {
                        ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.green_500))
                        tvStatus.text = "Granted"
                        tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_500))
                        btnAction.visibility = View.GONE
                    }
                    PermissionStatus.DENIED -> {
                        ivStatusIcon.setImageResource(R.drawable.ic_warning)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.orange))
                        tvStatus.text = "Denied"
                        tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.orange))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Request"
                    }
                    PermissionStatus.PERMANENTLY_DENIED -> {
                        ivStatusIcon.setImageResource(R.drawable.ic_error)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.red_500))
                        tvStatus.text = "Permanently Denied"
                        tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_500))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Settings"
                    }
                    PermissionStatus.NOT_REQUESTED -> {
                        ivStatusIcon.setImageResource(R.drawable.ic_help)
                        ivStatusIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.gray_500))
                        tvStatus.text = "Not Requested"
                        tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.gray_500))
                        btnAction.visibility = View.VISIBLE
                        btnAction.text = "Request"
                    }
                }
            }
        }

        private fun getPermissionDisplayName(permission: String): String {
            return when {
                permission.contains("PACKAGE_USAGE_STATS") -> "App Usage Statistics"
                permission.contains("SYSTEM_ALERT_WINDOW") -> "Display Over Other Apps"
                permission.contains("ACCESS_FINE_LOCATION") -> "Precise Location"
                permission.contains("ACCESS_COARSE_LOCATION") -> "Approximate Location"
                permission.contains("QUERY_ALL_PACKAGES") -> "Installed Apps Access"
                permission.contains("CAMERA") -> "Camera"
                permission.contains("READ_EXTERNAL_STORAGE") -> "Read Storage"
                permission.contains("WRITE_EXTERNAL_STORAGE") -> "Write Storage"
                permission.contains("POST_NOTIFICATIONS") -> "Notifications"
                else -> permission.substringAfterLast(".").replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercaseChar() } }
            }
        }
    }
}
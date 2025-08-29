package com.shieldtechhub.shieldkids.features.screen_time.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.databinding.ItemCategoryUsageBinding
import com.shieldtechhub.shieldkids.features.screen_time.model.CategoryUsageItem

class CategoryUsageAdapter : RecyclerView.Adapter<CategoryUsageAdapter.ViewHolder>() {
    
    private var items: List<CategoryUsageItem> = emptyList()
    
    fun updateItems(newItems: List<CategoryUsageItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }
    
    override fun getItemCount(): Int = items.size
    
    inner class ViewHolder(private val binding: ItemCategoryUsageBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: CategoryUsageItem) {
            binding.tvCategoryName.text = item.getDisplayName()
            binding.tvUsageTime.text = item.getFormattedUsageTime()
            binding.tvPercentage.text = "${item.percentage}%"
            
            // Set progress bar
            binding.progressBar.progress = item.percentage
            
            // Set category color indicator
            try {
                val color = Color.parseColor(item.getCategoryColor())
                binding.viewColorIndicator.setBackgroundColor(color)
                binding.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(color)
            } catch (e: Exception) {
                // Fallback color
                val fallbackColor = Color.parseColor("#607D8B")
                binding.viewColorIndicator.setBackgroundColor(fallbackColor)
                binding.progressBar.progressTintList = android.content.res.ColorStateList.valueOf(fallbackColor)
            }
        }
    }
}
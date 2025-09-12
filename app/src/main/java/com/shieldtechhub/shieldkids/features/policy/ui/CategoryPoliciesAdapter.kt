package com.shieldtechhub.shieldkids.features.policy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.databinding.ItemCategoryPolicyBinding

class CategoryPoliciesAdapter(
    private val onCategoryToggle: (CategoryPoliciesActivity.CategoryPolicyItem) -> Unit
) : RecyclerView.Adapter<CategoryPoliciesAdapter.CategoryPolicyViewHolder>() {
    
    private var categoryPolicies = listOf<CategoryPoliciesActivity.CategoryPolicyItem>()
    
    fun updateData(newPolicies: List<CategoryPoliciesActivity.CategoryPolicyItem>) {
        categoryPolicies = newPolicies
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryPolicyViewHolder {
        val binding = ItemCategoryPolicyBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return CategoryPolicyViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: CategoryPolicyViewHolder, position: Int) {
        holder.bind(categoryPolicies[position])
    }
    
    override fun getItemCount() = categoryPolicies.size
    
    inner class CategoryPolicyViewHolder(
        private val binding: ItemCategoryPolicyBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(categoryPolicy: CategoryPoliciesActivity.CategoryPolicyItem) {
            binding.apply {
                // Category icon
                ivCategoryIcon.setImageResource(categoryPolicy.icon)
                
                // Category name
                tvCategoryName.text = categoryPolicy.category.displayName
                
                // Category description
                tvCategoryDescription.text = categoryPolicy.description
                
                // Block/Allow switch
                switchCategoryBlock.isChecked = categoryPolicy.isBlocked
                
                // Status text
                tvBlockStatus.text = if (categoryPolicy.isBlocked) "Blocked" else "Allowed"
                tvBlockStatus.setTextColor(
                    if (categoryPolicy.isBlocked) {
                        binding.root.context.getColor(R.color.red_600)
                    } else {
                        binding.root.context.getColor(R.color.green_600)
                    }
                )
                
                // Switch change listener
                switchCategoryBlock.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked != categoryPolicy.isBlocked) {
                        onCategoryToggle(categoryPolicy)
                    }
                }
                
                // Click listener for the entire item
                root.setOnClickListener {
                    switchCategoryBlock.isChecked = !switchCategoryBlock.isChecked
                }
            }
        }
    }
}
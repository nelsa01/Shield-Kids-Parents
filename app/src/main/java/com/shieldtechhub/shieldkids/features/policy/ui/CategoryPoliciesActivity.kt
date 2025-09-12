package com.shieldtechhub.shieldkids.features.policy.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shieldtechhub.shieldkids.databinding.ActivityCategoryPoliciesBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import kotlinx.coroutines.launch
import com.shieldtechhub.shieldkids.R

class CategoryPoliciesActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "CategoryPoliciesActivity"
    }
    
    private lateinit var binding: ActivityCategoryPoliciesBinding
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var policySyncManager: PolicySyncManager
    private lateinit var categoryPoliciesAdapter: CategoryPoliciesAdapter
    
    private var deviceId: String = ""
    private var childId: String = ""
    private var devicePolicy: DevicePolicy? = null
    
    private val categoryPolicies = mutableListOf<CategoryPolicyItem>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryPoliciesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        childId = intent.getStringExtra("childId") ?: ""
        
        Log.d(TAG, "Received deviceId: '$deviceId', childId: '$childId'")
        
        if (deviceId.isEmpty() || childId.isEmpty()) {
            Log.e(TAG, "Missing required parameters")
            Toast.makeText(this, "Missing device or child information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize managers
        policyManager = PolicyEnforcementManager.getInstance(this)
        policySyncManager = PolicySyncManager.getInstance(this)
        
        setupUI()
        loadCurrentPolicy()
        setupCategoryPolicies()
    }
    
    private fun setupUI() {
        // Toolbar
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // RecyclerView setup
        categoryPoliciesAdapter = CategoryPoliciesAdapter { categoryPolicy ->
            toggleCategoryPolicy(categoryPolicy)
        }
        
        binding.recyclerViewCategoryPolicies.apply {
            layoutManager = LinearLayoutManager(this@CategoryPoliciesActivity)
            adapter = categoryPoliciesAdapter
        }
    }
    
    private fun loadCurrentPolicy() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val activePolicies = policyManager.activePolicies.value
                val loadedPolicy = activePolicies[deviceId] ?: activePolicies["device_$deviceId"]
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    devicePolicy = loadedPolicy
                    Log.d(TAG, "Loaded policy: ${devicePolicy != null}")
                    updateCategoryPoliciesStatus()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load policy", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(this@CategoryPoliciesActivity, "Failed to load policy: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupCategoryPolicies() {
        // Initialize category policies with all available categories
        val allCategories = AppCategory.entries.filter { it != AppCategory.OTHER }
        
        categoryPolicies.clear()
        
        allCategories.forEach { category ->
            val isBlocked = devicePolicy?.blockedCategories?.contains(category.name) ?: false
            
            categoryPolicies.add(
                CategoryPolicyItem(
                    category = category,
                    isBlocked = isBlocked,
                    description = getCategoryDescription(category),
                    icon = getCategoryIcon(category)
                )
            )
        }
        
        categoryPoliciesAdapter.updateData(categoryPolicies)
        updateStatusText()
    }
    
    private fun toggleCategoryPolicy(categoryPolicy: CategoryPolicyItem) {
        lifecycleScope.launch {
            try {
                // Update UI immediately for responsiveness
                categoryPolicy.isBlocked = !categoryPolicy.isBlocked
                categoryPoliciesAdapter.notifyItemChanged(categoryPolicies.indexOf(categoryPolicy))
                updateStatusText()
                
                // Show immediate feedback
                val message = if (categoryPolicy.isBlocked) {
                    "${categoryPolicy.category.displayName} apps are now blocked"
                } else {
                    "${categoryPolicy.category.displayName} apps are now allowed"
                }
                Toast.makeText(this@CategoryPoliciesActivity, message, Toast.LENGTH_SHORT).show()
                
                // Perform heavy operations on background thread
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val currentPolicy = devicePolicy ?: DevicePolicy.createDefault(deviceId)
                        val currentBlockedCategories = currentPolicy.blockedCategories.toMutableList()
                        
                        if (categoryPolicy.isBlocked) {
                            currentBlockedCategories.add(categoryPolicy.category.name)
                        } else {
                            currentBlockedCategories.remove(categoryPolicy.category.name)
                        }
                        
                        val updatedPolicy = currentPolicy.copy(
                            blockedCategories = currentBlockedCategories
                        )
                        
                        // Apply policy on background thread
                        val success = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                        
                        if (success) {
                            devicePolicy = updatedPolicy
                            
                            // Sync to Firebase on background thread
                            policySyncManager.savePolicyToFirebase(childId, deviceId, updatedPolicy)
                            
                            Log.i(TAG, "Category policy updated successfully")
                        } else {
                            // Revert UI change on main thread
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                categoryPolicy.isBlocked = !categoryPolicy.isBlocked
                                categoryPoliciesAdapter.notifyItemChanged(categoryPolicies.indexOf(categoryPolicy))
                                updateStatusText()
                                
                                Toast.makeText(this@CategoryPoliciesActivity, 
                                    "Failed to update policy", Toast.LENGTH_SHORT).show()
                            }
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to apply category policy", e)
                        
                        // Revert UI change on main thread
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            categoryPolicy.isBlocked = !categoryPolicy.isBlocked
                            categoryPoliciesAdapter.notifyItemChanged(categoryPolicies.indexOf(categoryPolicy))
                            updateStatusText()
                            
                            Toast.makeText(this@CategoryPoliciesActivity, 
                                "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle category policy", e)
                Toast.makeText(this@CategoryPoliciesActivity, 
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                
                // Revert UI change
                categoryPolicy.isBlocked = !categoryPolicy.isBlocked
                categoryPoliciesAdapter.notifyItemChanged(categoryPolicies.indexOf(categoryPolicy))
                updateStatusText()
            }
        }
    }
    
    private fun updateCategoryPoliciesStatus() {
        // This will be called from AppManagementActivity when it loads policy
        updateStatusText()
    }
    
    private fun updateStatusText() {
        val blockedCount = categoryPolicies.count { it.isBlocked }
        val totalCount = categoryPolicies.size
        
        binding.tvCategoryPoliciesStatus.text = when (blockedCount) {
            0 -> "All allowed"
            totalCount -> "All blocked"
            else -> "$blockedCount blocked"
        }
    }
    
    private fun getCategoryDescription(category: AppCategory): String {
        return when (category) {
            AppCategory.SOCIAL -> "Social media and messaging apps like Facebook, Instagram, WhatsApp"
            AppCategory.GAMES -> "Gaming apps and mobile games"
            AppCategory.BROWSERS -> "Web browsers like Chrome, Firefox, Safari"
            AppCategory.ENTERTAINMENT -> "Video streaming and entertainment apps like YouTube, Netflix"
            AppCategory.EDUCATIONAL -> "Learning and educational apps"
            AppCategory.SHOPPING -> "Shopping and e-commerce apps"
            AppCategory.NEWS -> "News and information apps"
            AppCategory.PRODUCTIVITY -> "Work and productivity tools"
            AppCategory.COMMUNICATION -> "Communication and video calling apps"
            AppCategory.PHOTO_VIDEO -> "Photo editing and video apps"
            AppCategory.MUSIC -> "Music streaming and audio apps"
            AppCategory.HEALTH -> "Health and fitness apps"
            AppCategory.TRAVEL -> "Travel and navigation apps"
            AppCategory.FINANCE -> "Banking and financial apps"
            else -> "Other apps in this category"
        }
    }
    
    private fun getCategoryIcon(category: AppCategory): Int {
        return when (category) {
            AppCategory.SOCIAL -> R.drawable.ic_apps
            AppCategory.GAMES -> R.drawable.ic_apps
            AppCategory.BROWSERS -> R.drawable.ic_apps
            AppCategory.ENTERTAINMENT -> R.drawable.ic_apps
            AppCategory.EDUCATIONAL -> R.drawable.ic_apps
            AppCategory.SHOPPING -> R.drawable.ic_apps
            AppCategory.NEWS -> R.drawable.ic_apps
            AppCategory.PRODUCTIVITY -> R.drawable.ic_apps
            AppCategory.COMMUNICATION -> R.drawable.ic_apps
            AppCategory.PHOTO_VIDEO -> R.drawable.ic_apps
            AppCategory.MUSIC -> R.drawable.ic_apps
            AppCategory.HEALTH -> R.drawable.ic_apps
            AppCategory.TRAVEL -> R.drawable.ic_apps
            AppCategory.FINANCE -> R.drawable.ic_apps
            else -> R.drawable.ic_apps
        }
    }
    
    /**
     * Data class for category policy items
     */
    data class CategoryPolicyItem(
        val category: AppCategory,
        var isBlocked: Boolean,
        val description: String,
        val icon: Int
    )
}
package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shieldtechhub.shieldkids.databinding.ActivityPolicyStatusBinding
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.common.utils.ChildDeviceInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PolicyStatusActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PolicyStatusActivity"
    }
    
    private lateinit var binding: ActivityPolicyStatusBinding
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var policySyncManager: PolicySyncManager
    private lateinit var deviceStateManager: DeviceStateManager
    private lateinit var adapter: PolicyStatusAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPolicyStatusBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        policyManager = PolicyEnforcementManager.getInstance(this)
        policySyncManager = PolicySyncManager.getInstance(this)
        deviceStateManager = DeviceStateManager(this)
        
        setupUI()
        loadPolicyStatus()
    }
    
    private fun setupUI() {
        // Setup toolbar
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // Setup RecyclerView
        adapter = PolicyStatusAdapter()
        binding.recyclerViewPolicies.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPolicies.adapter = adapter
        
        // Setup refresh
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadPolicyStatus()
        }
        
        // Setup sync button
        binding.btnSyncPolicies.setOnClickListener {
            syncPoliciesManually()
        }
    }
    
    private fun loadPolicyStatus() {
        lifecycleScope.launch {
            try {
                binding.swipeRefreshLayout.isRefreshing = true
                
                val deviceInfo = deviceStateManager.getChildDeviceInfo()
                if (deviceInfo == null) {
                    showError("Device not registered as child device")
                    return@launch
                }
                
                Log.d(TAG, "Loading policy status for device: ${deviceInfo.deviceId}")
                
                // Get active policies
                val activePolicies = policyManager.activePolicies.value
                Log.d(TAG, "=== POLICY LOADING DEBUG ===")
                Log.d(TAG, "Device ID: ${deviceInfo.deviceId}")
                Log.d(TAG, "Available policy keys: ${activePolicies.keys}")
                
                var devicePolicy = activePolicies[deviceInfo.deviceId]
                
                // Try alternate device ID format if not found
                if (devicePolicy == null) {
                    val alternateDeviceId = "device_${deviceInfo.deviceId}"
                    Log.d(TAG, "Trying alternate device ID: $alternateDeviceId")
                    devicePolicy = activePolicies[alternateDeviceId]
                }
                
                Log.d(TAG, "Found policy: ${devicePolicy != null}")
                if (devicePolicy != null) {
                    Log.d(TAG, "Policy details will be logged in showPolicyDetails()")
                }
                Log.d(TAG, "=============================")
                
                // Get sync status
                val syncStatus = policySyncManager.getPolicySyncStatus(deviceInfo.deviceId)
                
                // Update UI
                updatePolicyStatusUI(deviceInfo, devicePolicy, syncStatus)
                
                Log.d(TAG, "Policy status loaded successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load policy status", e)
                showError("Failed to load policy status: ${e.message}")
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    
    private fun updatePolicyStatusUI(
        deviceInfo: ChildDeviceInfo,
        policy: DevicePolicy?,
        syncStatus: Map<String, Any>
    ) {
        // Device info
        binding.tvDeviceId.text = "Device ID: ${deviceInfo.deviceId}"
        binding.tvChildId.text = "Child ID: ${deviceInfo.childId}"
        
        // Sync status
        val isListening = syncStatus["isListening"] as? Boolean ?: false
        val currentVersion = syncStatus["currentVersion"] as? Long ?: 0L
        val lastAppliedAt = syncStatus["lastAppliedAt"] as? Long ?: 0L
        
        binding.tvSyncStatus.text = if (isListening) "Status: Listening for policy updates" else "Status: Not listening for updates"
        binding.tvPolicyVersion.text = "Policy Version: $currentVersion"
        
        if (lastAppliedAt > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            binding.tvLastUpdated.text = "Last Updated: ${dateFormat.format(Date(lastAppliedAt))}"
        } else {
            binding.tvLastUpdated.text = "Last Updated: Never"
        }
        
        // Policy details
        if (policy != null) {
            binding.layoutNoPolicies.visibility = android.view.View.GONE
            binding.layoutPolicyDetails.visibility = android.view.View.VISIBLE
            
            showPolicyDetails(policy)
        } else {
            binding.layoutNoPolicies.visibility = android.view.View.VISIBLE
            binding.layoutPolicyDetails.visibility = android.view.View.GONE
        }
    }
    
    private fun showPolicyDetails(policy: DevicePolicy) {
        val policyItems = mutableListOf<PolicyStatusItem>()
        
        // Debug logging
        Log.d(TAG, "=== POLICY DEBUG ===")
        Log.d(TAG, "Policy Name: ${policy.name}")
        Log.d(TAG, "Policy ID: ${policy.id}")
        Log.d(TAG, "Is Active: ${policy.isActive}")
        Log.d(TAG, "Weekday Screen Time: ${policy.weekdayScreenTime}")
        Log.d(TAG, "Weekend Screen Time: ${policy.weekendScreenTime}")
        Log.d(TAG, "Bedtime Start: ${policy.bedtimeStart}")
        Log.d(TAG, "Bedtime End: ${policy.bedtimeEnd}")
        Log.d(TAG, "Blocked Categories: ${policy.blockedCategories}")
        Log.d(TAG, "App Policies Count: ${policy.appPolicies.size}")
        policy.appPolicies.forEach { appPolicy ->
            Log.d(TAG, "  - App: ${appPolicy.packageName}, Action: ${appPolicy.action}")
        }
        Log.d(TAG, "Camera Disabled: ${policy.cameraDisabled}")
        Log.d(TAG, "Installations Blocked: ${policy.installationsBlocked}")
        Log.d(TAG, "Keyguard Restrictions: ${policy.keyguardRestrictions}")
        Log.d(TAG, "Emergency Mode: ${policy.emergencyMode}")
        Log.d(TAG, "==================")
        
        // Basic policy info
        policyItems.add(PolicyStatusItem.Header("Policy Information"))
        policyItems.add(PolicyStatusItem.Info("Policy Name", policy.name))
        policyItems.add(PolicyStatusItem.Info("Policy ID", policy.id))
        policyItems.add(PolicyStatusItem.Info("Status", if (policy.isActive) "Active" else "Inactive"))
        
        val createdDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(policy.createdAt))
        val updatedDate = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(policy.updatedAt))
        
        policyItems.add(PolicyStatusItem.Info("Created", createdDate))
        policyItems.add(PolicyStatusItem.Info("Updated", updatedDate))
        
        // Screen time restrictions
        if (policy.weekdayScreenTime > 0 || policy.weekendScreenTime > 0 || 
            !policy.bedtimeStart.isNullOrEmpty()) {
            
            policyItems.add(PolicyStatusItem.Header("Screen Time Restrictions"))
            
            if (policy.weekdayScreenTime > 0) {
                val hours = policy.weekdayScreenTime / 60
                val minutes = policy.weekdayScreenTime % 60
                val timeText = if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
                policyItems.add(PolicyStatusItem.Restriction(
                    "ACTIVE", 
                    "Weekday limit: $timeText per day"
                ))
            }
            
            if (policy.weekendScreenTime > 0) {
                val hours = policy.weekendScreenTime / 60
                val minutes = policy.weekendScreenTime % 60
                val timeText = if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
                policyItems.add(PolicyStatusItem.Restriction(
                    "ACTIVE", 
                    "Weekend limit: $timeText per day"
                ))
            }
            
            if (!policy.bedtimeStart.isNullOrEmpty() && !policy.bedtimeEnd.isNullOrEmpty()) {
                policyItems.add(PolicyStatusItem.Restriction(
                    "ACTIVE", 
                    "Bedtime: ${policy.bedtimeStart} - ${policy.bedtimeEnd}"
                ))
            }
        }
        
        // Category restrictions
        if (policy.blockedCategories.isNotEmpty()) {
            policyItems.add(PolicyStatusItem.Header("Blocked App Categories (${policy.blockedCategories.size})"))
            policy.blockedCategories.forEach { category ->
                val displayName = getCategoryDisplayName(category)
                policyItems.add(PolicyStatusItem.Restriction("BLOCKED", "$displayName ($category)"))
            }
        }
        
        // App-specific policies
        if (policy.appPolicies.isNotEmpty()) {
            val blockedApps = policy.appPolicies.filter { it.action == AppPolicy.Action.BLOCK }
            val allowedApps = policy.appPolicies.filter { it.action == AppPolicy.Action.ALLOW }
            val timeLimitApps = policy.appPolicies.filter { it.action == AppPolicy.Action.TIME_LIMIT }
            
            policyItems.add(PolicyStatusItem.Header("Individual App Restrictions (${policy.appPolicies.size})"))
            
            if (blockedApps.isNotEmpty()) {
                blockedApps.forEach { appPolicy ->
                    policyItems.add(PolicyStatusItem.Restriction(
                        "BLOCKED", 
                        appPolicy.packageName
                    ))
                }
            }
            
            if (allowedApps.isNotEmpty()) {
                allowedApps.forEach { appPolicy ->
                    policyItems.add(PolicyStatusItem.Restriction(
                        "ALLOWED", 
                        "${appPolicy.packageName} (Always accessible)"
                    ))
                }
            }
            
            if (timeLimitApps.isNotEmpty()) {
                timeLimitApps.forEach { appPolicy ->
                    val timeLimit = appPolicy.timeLimit
                    val limitText = if (timeLimit != null) {
                        "${timeLimit.dailyLimitMinutes} minutes per day"
                    } else "Time limited"
                    
                    policyItems.add(PolicyStatusItem.Restriction(
                        "TIME LIMITED", 
                        "${appPolicy.packageName} - $limitText"
                    ))
                }
            }
        }
        
        // Device restrictions
        val deviceRestrictions = mutableListOf<String>()
        if (policy.cameraDisabled) deviceRestrictions.add("Camera disabled")
        if (policy.installationsBlocked) deviceRestrictions.add("App installations blocked")
        if (policy.keyguardRestrictions > 0) deviceRestrictions.add("Lock screen restrictions")
        
        if (deviceRestrictions.isNotEmpty()) {
            policyItems.add(PolicyStatusItem.Header("Device Restrictions (${deviceRestrictions.size})"))
            deviceRestrictions.forEach { restriction ->
                policyItems.add(PolicyStatusItem.Restriction("ACTIVE", restriction))
            }
        }
        
        // Emergency settings
        if (policy.emergencyMode) {
            policyItems.add(PolicyStatusItem.Header("Emergency Mode"))
            policyItems.add(PolicyStatusItem.Info("Status", "ACTIVE - All restrictions bypassed"))
        }
        
        // Check if we have any restrictions at all
        val hasAnyRestrictions = policy.weekdayScreenTime > 0 || 
                                policy.weekendScreenTime > 0 || 
                                !policy.bedtimeStart.isNullOrEmpty() ||
                                policy.blockedCategories.isNotEmpty() ||
                                policy.appPolicies.isNotEmpty() ||
                                policy.cameraDisabled ||
                                policy.installationsBlocked ||
                                policy.keyguardRestrictions > 0 ||
                                policy.emergencyMode
        
        if (!hasAnyRestrictions) {
            policyItems.add(PolicyStatusItem.Header("No Active Restrictions"))
            policyItems.add(PolicyStatusItem.Info("Status", "Policy exists but contains no restrictions"))
            policyItems.add(PolicyStatusItem.Info("Issue", "Parent's restrictions are not being saved to policy"))
            policyItems.add(PolicyStatusItem.Info("Solution", "Parent should check App Management settings and re-apply restrictions"))
            policyItems.add(PolicyStatusItem.Info("Debug", "Screen time: ${policy.weekdayScreenTime}/${policy.weekendScreenTime}, Categories: ${policy.blockedCategories.size}, Apps: ${policy.appPolicies.size}"))
        }
        
        // Update adapter
        adapter.updateItems(policyItems)
    }
    
    private fun getCategoryDisplayName(category: String): String {
        return when (category.uppercase()) {
            "SOCIAL" -> "Social Media"
            "GAMES" -> "Games"  
            "EDUCATIONAL" -> "Educational"
            "ENTERTAINMENT" -> "Entertainment"
            "PRODUCTIVITY" -> "Productivity"
            "COMMUNICATION" -> "Communication"
            "SYSTEM" -> "System"
            "BROWSERS" -> "Web Browsers"
            "SHOPPING" -> "Shopping"
            "NEWS" -> "News & Information"
            "PHOTO_VIDEO" -> "Photo & Video"
            "MUSIC" -> "Music & Audio"
            "HEALTH" -> "Health & Fitness"
            "TRAVEL" -> "Travel & Navigation"
            "FINANCE" -> "Finance & Banking"
            else -> category
        }
    }
    
    private fun syncPoliciesManually() {
        lifecycleScope.launch {
            try {
                binding.btnSyncPolicies.isEnabled = false
                binding.btnSyncPolicies.text = "Syncing..."
                
                Log.d(TAG, "Manual sync initiated")
                
                // Start policy sync
                policySyncManager.startPolicySync()
                
                // Also try to fetch current policy from Firebase manually
                val deviceInfo = deviceStateManager.getChildDeviceInfo()
                if (deviceInfo != null) {
                    Log.d(TAG, "Attempting to fetch policy from Firebase for device: ${deviceInfo.deviceId}")
                    val firebasePolicy = policySyncManager.fetchCurrentPolicy(deviceInfo.deviceId)
                    Log.d(TAG, "Fetched policy from Firebase: ${firebasePolicy != null}")
                    if (firebasePolicy != null) {
                        Log.d(TAG, "Firebase policy details:")
                        Log.d(TAG, "  - Name: ${firebasePolicy.name}")
                        Log.d(TAG, "  - App Policies: ${firebasePolicy.appPolicies.size}")
                        Log.d(TAG, "  - Blocked Categories: ${firebasePolicy.blockedCategories}")
                        Log.d(TAG, "  - Screen Time: weekday=${firebasePolicy.weekdayScreenTime}, weekend=${firebasePolicy.weekendScreenTime}")
                    }
                }
                
                // Wait a moment then reload
                kotlinx.coroutines.delay(2000)
                loadPolicyStatus()
                
                android.widget.Toast.makeText(this@PolicyStatusActivity, 
                    "Policy sync initiated", android.widget.Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync policies", e)
                android.widget.Toast.makeText(this@PolicyStatusActivity, 
                    "Sync failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnSyncPolicies.isEnabled = true
                binding.btnSyncPolicies.text = "Sync Policies"
            }
        }
    }
    
    private fun showError(message: String) {
        binding.layoutNoPolicies.visibility = android.view.View.VISIBLE
        binding.layoutPolicyDetails.visibility = android.view.View.GONE
        binding.tvNoPoliciesMessage.text = message
        
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }
    
    override fun onResume() {
        super.onResume()
        loadPolicyStatus()
    }
}

// Data classes for policy status items
sealed class PolicyStatusItem {
    data class Header(val title: String) : PolicyStatusItem()
    data class Info(val label: String, val value: String) : PolicyStatusItem()
    data class Restriction(val type: String, val description: String) : PolicyStatusItem()
}
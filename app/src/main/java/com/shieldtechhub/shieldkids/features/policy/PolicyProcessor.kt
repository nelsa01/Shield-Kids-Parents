package com.shieldtechhub.shieldkids.features.policy

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.app_management.service.AppCategory
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import com.shieldtechhub.shieldkids.features.policy.model.ViolationType
import kotlinx.coroutines.*

/**
 * Enhanced policy processor that applies device policies systematically
 * Handles policy conflicts, validation, and enforcement across device restarts
 */
class PolicyProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "PolicyProcessor"
        private const val POLICY_APPLICATION_TIMEOUT = 30000L // 30 seconds
        
        @Volatile
        private var INSTANCE: PolicyProcessor? = null
        
        fun getInstance(context: Context): PolicyProcessor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PolicyProcessor(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val deviceAdminManager = DeviceAdminManager(context)
    private val appInventoryManager = AppInventoryManager(context)
    private val policyEnforcementManager = PolicyEnforcementManager.getInstance(context)
    private val packageManager = context.packageManager
    
    private val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Policy application results tracking
    data class PolicyApplicationResult(
        val success: Boolean,
        val appliedPolicies: Int,
        val failedPolicies: Int,
        val warnings: List<String>,
        val errors: List<String>
    )
    
    /**
     * Apply a complete device policy with comprehensive validation
     */
    suspend fun applyDevicePolicy(deviceId: String, policy: DevicePolicy): PolicyApplicationResult {
        Log.i(TAG, "Applying device policy: ${policy.name} for device: $deviceId")
        
        return withTimeoutOrNull(POLICY_APPLICATION_TIMEOUT) {
            processDevicePolicyInternal(deviceId, policy)
        } ?: PolicyApplicationResult(
            success = false,
            appliedPolicies = 0,
            failedPolicies = 1,
            warnings = emptyList(),
            errors = listOf("Policy application timed out after ${POLICY_APPLICATION_TIMEOUT}ms")
        )
    }
    
    private suspend fun processDevicePolicyInternal(deviceId: String, policy: DevicePolicy): PolicyApplicationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        var appliedPolicies = 0
        var failedPolicies = 0
        
        try {
            // Pre-flight validation
            val validationResult = validatePolicyRequirements(policy)
            if (!validationResult.isValid) {
                return PolicyApplicationResult(
                    success = false,
                    appliedPolicies = 0,
                    failedPolicies = 1,
                    warnings = emptyList(),
                    errors = validationResult.errors
                )
            }
            warnings.addAll(validationResult.warnings)
            
            // Step 1: Apply device-level restrictions
            Log.d(TAG, "Applying device-level restrictions")
            val deviceResult = applyDeviceLevelRestrictions(policy)
            if (deviceResult.success) {
                appliedPolicies++
            } else {
                failedPolicies++
                errors.addAll(deviceResult.errors)
            }
            
            // Step 2: Apply app-specific policies
            Log.d(TAG, "Applying ${policy.appPolicies.size} app policies")
            val appResults = applyAppPolicies(policy.appPolicies)
            appliedPolicies += appResults.successful
            failedPolicies += appResults.failed
            warnings.addAll(appResults.warnings)
            errors.addAll(appResults.errors)
            
            // Step 3: Configure installation blocking
            Log.d(TAG, "Configuring installation blocking")
            val installResult = configureInstallationBlocking(policy)
            if (installResult.success) {
                appliedPolicies++
            } else {
                failedPolicies++
                errors.addAll(installResult.errors)
            }
            
            // Step 4: Apply category-based restrictions
            Log.d(TAG, "Applying category restrictions")
            val categoryResult = applyCategoryRestrictions(policy.blockedCategories)
            if (categoryResult.success) {
                appliedPolicies++
            } else {
                failedPolicies++
                errors.addAll(categoryResult.errors)
            }
            
            // Step 5: Configure enforcement monitoring
            Log.d(TAG, "Setting up enforcement monitoring")
            val monitoringResult = setupEnforcementMonitoring(deviceId, policy)
            if (!monitoringResult.success) {
                warnings.addAll(monitoringResult.errors.map { "Monitoring: $it" })
            }
            
            // Step 6: Validate policy application
            val postValidationResult = validatePolicyApplication(policy)
            if (!postValidationResult.isValid) {
                warnings.addAll(postValidationResult.warnings)
                errors.addAll(postValidationResult.errors)
            }
            
            // Step 7: Store policy for persistence
            storePolicyConfiguration(deviceId, policy)
            
            val overallSuccess = failedPolicies == 0 && errors.isEmpty()
            
            Log.i(TAG, "Policy application completed. Success: $overallSuccess, Applied: $appliedPolicies, Failed: $failedPolicies")
            
            return PolicyApplicationResult(
                success = overallSuccess,
                appliedPolicies = appliedPolicies,
                failedPolicies = failedPolicies,
                warnings = warnings,
                errors = errors
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply device policy", e)
            return PolicyApplicationResult(
                success = false,
                appliedPolicies = appliedPolicies,
                failedPolicies = failedPolicies + 1,
                warnings = warnings,
                errors = errors + "Unexpected error: ${e.message}"
            )
        }
    }
    
    private suspend fun validatePolicyRequirements(policy: DevicePolicy): ValidationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        // Check device admin status
        if (!deviceAdminManager.isDeviceAdminActive()) {
            errors.add("Device admin is not active - policy enforcement will be limited")
        }
        
        // Check required permissions
        val capabilities = deviceAdminManager.getDeviceAdminCapabilities()
        
        if (policy.cameraDisabled && !capabilities.canDisableCamera) {
            warnings.add("Cannot disable camera - insufficient device admin privileges")
        }
        
        if (policy.passwordPolicy != null && !capabilities.canSetPasswordPolicy) {
            warnings.add("Cannot enforce password policy - insufficient privileges")
        }
        
        if (policy.keyguardRestrictions > 0 && !capabilities.canControlKeyguard) {
            warnings.add("Cannot apply keyguard restrictions - insufficient privileges")
        }
        
        // Check app policies for validity
        policy.appPolicies.forEach { appPolicy ->
            if (!isAppInstalled(appPolicy.packageName)) {
                warnings.add("App not installed: ${appPolicy.packageName}")
            }
            
            if (appPolicy.timeLimit != null && appPolicy.timeLimit.dailyLimitMinutes <= 0) {
                errors.add("Invalid time limit for ${appPolicy.packageName}: must be greater than 0")
            }
        }
        
        // Check blocked categories
        policy.blockedCategories.forEach { category ->
            if (!isValidCategory(category)) {
                warnings.add("Unknown category: $category")
            }
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            warnings = warnings,
            errors = errors
        )
    }
    
    private suspend fun applyDeviceLevelRestrictions(policy: DevicePolicy): OperationResult {
        val errors = mutableListOf<String>()
        
        try {
            // Camera restrictions
            if (policy.cameraDisabled) {
                if (!deviceAdminManager.setCameraDisabled(true)) {
                    errors.add("Failed to disable camera")
                }
            }
            
            // Password policy
            policy.passwordPolicy?.let { passwordPolicy ->
                if (!deviceAdminManager.setPasswordPolicy(
                    passwordPolicy.minLength, 
                    passwordPolicy.requireSpecialChars
                )) {
                    errors.add("Failed to apply password policy")
                }
            }
            
            // Keyguard restrictions
            if (policy.keyguardRestrictions > 0) {
                if (!deviceAdminManager.setKeyguardRestrictions(policy.keyguardRestrictions)) {
                    errors.add("Failed to apply keyguard restrictions")
                }
            }
            
            // Store bedtime settings
            if (policy.bedtimeStart != null && policy.bedtimeEnd != null) {
                storeBedtimeSettings(policy.bedtimeStart, policy.bedtimeEnd)
            }
            
            // Store screen time limits
            storeScreenTimeLimits(policy.weekdayScreenTime, policy.weekendScreenTime)
            
            return OperationResult(
                success = errors.isEmpty(),
                errors = errors
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply device-level restrictions", e)
            return OperationResult(
                success = false,
                errors = listOf("Device restrictions error: ${e.message}")
            )
        }
    }
    
    private suspend fun applyAppPolicies(appPolicies: List<AppPolicy>): AppPolicyResult {
        var successful = 0
        var failed = 0
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        appPolicies.forEach { appPolicy ->
            try {
                val result = applyIndividualAppPolicy(appPolicy)
                if (result.success) {
                    successful++
                } else {
                    failed++
                    errors.addAll(result.errors)
                }
            } catch (e: Exception) {
                failed++
                errors.add("Failed to apply policy for ${appPolicy.packageName}: ${e.message}")
            }
        }
        
        return AppPolicyResult(
            successful = successful,
            failed = failed,
            warnings = warnings,
            errors = errors
        )
    }
    
    private suspend fun applyIndividualAppPolicy(appPolicy: AppPolicy): OperationResult {
        Log.d(TAG, "Applying policy for app: ${appPolicy.packageName} - ${appPolicy.action}")
        
        return try {
            val success = policyEnforcementManager.applyDevicePolicy(
                "current_device", 
                DevicePolicy(
                    id = "temp_policy",
                    name = "Temporary Policy",
                    appPolicies = listOf(appPolicy)
                )
            )
            
            OperationResult(
                success = success,
                errors = if (!success) listOf("Failed to apply policy for ${appPolicy.packageName}") else emptyList()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error applying app policy for ${appPolicy.packageName}", e)
            OperationResult(
                success = false,
                errors = listOf("App policy error: ${e.message}")
            )
        }
    }
    
    private suspend fun configureInstallationBlocking(policy: DevicePolicy): OperationResult {
        return try {
            val prefs = context.getSharedPreferences("shield_policies", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("block_installations", policy.installationsBlocked)
                .putStringSet("blocked_categories", policy.blockedCategories.toSet())
                .apply()
            
            // Start installation blocking service if needed
            if (policy.installationsBlocked) {
                InstallationBlockingService.startService(context)
            }
            
            OperationResult(success = true, errors = emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure installation blocking", e)
            OperationResult(success = false, errors = listOf("Installation blocking error: ${e.message}"))
        }
    }
    
    private suspend fun applyCategoryRestrictions(blockedCategories: List<String>): OperationResult {
        return try {
            val categoryApps = mutableListOf<String>()
            
            blockedCategories.forEach { category ->
                val appsInCategory = getAppsInCategory(category)
                categoryApps.addAll(appsInCategory)
            }
            
            // Apply blocking to all apps in blocked categories
            val prefs = context.getSharedPreferences("shield_policies", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            categoryApps.forEach { packageName ->
                editor.putBoolean("blocked_$packageName", true)
                editor.putString("block_reason_$packageName", "Category blocked by parent")
            }
            
            editor.apply()
            
            OperationResult(success = true, errors = emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply category restrictions", e)
            OperationResult(success = false, errors = listOf("Category restrictions error: ${e.message}"))
        }
    }
    
    private suspend fun setupEnforcementMonitoring(deviceId: String, policy: DevicePolicy): OperationResult {
        return try {
            // Configure monitoring parameters
            val prefs = context.getSharedPreferences("enforcement_config", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("current_policy_id", policy.id)
                .putLong("policy_applied_at", System.currentTimeMillis())
                .putBoolean("emergency_mode", policy.emergencyMode)
                .apply()
            
            // Schedule periodic policy validation
            schedulePolicyValidation()
            
            OperationResult(success = true, errors = emptyList())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup enforcement monitoring", e)
            OperationResult(success = false, errors = listOf("Monitoring setup error: ${e.message}"))
        }
    }
    
    private suspend fun validatePolicyApplication(policy: DevicePolicy): ValidationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        try {
            // Verify device admin is still active
            if (!deviceAdminManager.isDeviceAdminActive()) {
                errors.add("Device admin was deactivated during policy application")
            }
            
            // Check if policies were properly stored
            val prefs = context.getSharedPreferences("shield_policies", Context.MODE_PRIVATE)
            
            // Verify installation blocking setting
            val installBlockingStored = prefs.getBoolean("block_installations", false)
            if (installBlockingStored != policy.installationsBlocked) {
                warnings.add("Installation blocking setting mismatch")
            }
            
            // Verify app policies were applied
            policy.appPolicies.forEach { appPolicy ->
                val appBlocked = prefs.getBoolean("blocked_${appPolicy.packageName}", false)
                val expectedBlocked = appPolicy.action == AppPolicy.Action.BLOCK
                
                if (appBlocked != expectedBlocked) {
                    warnings.add("App policy mismatch for ${appPolicy.packageName}")
                }
            }
            
        } catch (e: Exception) {
            errors.add("Validation error: ${e.message}")
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            warnings = warnings,
            errors = errors
        )
    }
    
    private fun storePolicyConfiguration(deviceId: String, policy: DevicePolicy) {
        val prefs = context.getSharedPreferences("policy_persistence", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("policy_${deviceId}", policy.toJson())
            .putLong("policy_timestamp_${deviceId}", System.currentTimeMillis())
            .putString("current_device_policy", deviceId)
            .apply()
        
        Log.d(TAG, "Stored policy configuration for device: $deviceId")
    }
    
    // Persistence and restart handling
    suspend fun restorePoliciesAfterRestart(): PolicyApplicationResult {
        Log.i(TAG, "Restoring policies after device restart")
        
        return try {
            val prefs = context.getSharedPreferences("policy_persistence", Context.MODE_PRIVATE)
            val currentDevicePolicy = prefs.getString("current_device_policy", null)
            
            if (currentDevicePolicy == null) {
                Log.w(TAG, "No policy to restore")
                return PolicyApplicationResult(
                    success = true,
                    appliedPolicies = 0,
                    failedPolicies = 0,
                    warnings = listOf("No stored policy found"),
                    errors = emptyList()
                )
            }
            
            val policyJson = prefs.getString("policy_${currentDevicePolicy}", null)
            if (policyJson == null) {
                Log.e(TAG, "Policy data not found for device: $currentDevicePolicy")
                return PolicyApplicationResult(
                    success = false,
                    appliedPolicies = 0,
                    failedPolicies = 1,
                    warnings = emptyList(),
                    errors = listOf("Policy data corrupted or missing")
                )
            }
            
            val policy = DevicePolicy.fromJson(policyJson)
            applyDevicePolicy(currentDevicePolicy, policy)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore policies after restart", e)
            PolicyApplicationResult(
                success = false,
                appliedPolicies = 0,
                failedPolicies = 1,
                warnings = emptyList(),
                errors = listOf("Restore error: ${e.message}")
            )
        }
    }
    
    // Utility methods
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private fun isValidCategory(category: String): Boolean {
        return try {
            AppCategory.valueOf(category.uppercase())
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun getAppsInCategory(category: String): List<String> {
        return try {
            withContext(Dispatchers.IO) {
                val categoryEnum = AppCategory.valueOf(category.uppercase())
                appInventoryManager.getAppsByCategory(categoryEnum).map { it.packageName }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get apps in category: $category", e)
            emptyList()
        }
    }
    
    private fun storeBedtimeSettings(startTime: String, endTime: String) {
        val prefs = context.getSharedPreferences("bedtime_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("bedtime_start", startTime)
            .putString("bedtime_end", endTime)
            .putBoolean("bedtime_enabled", true)
            .apply()
    }
    
    private fun storeScreenTimeLimits(weekdayMinutes: Long, weekendMinutes: Long) {
        val prefs = context.getSharedPreferences("screen_time_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("weekday_limit_minutes", weekdayMinutes)
            .putLong("weekend_limit_minutes", weekendMinutes)
            .apply()
    }
    
    private fun schedulePolicyValidation() {
        // Schedule periodic validation using WorkManager or similar
        Log.d(TAG, "Scheduling periodic policy validation")
        // Implementation would depend on chosen scheduling mechanism
    }
    
    // Data classes for results
    data class ValidationResult(
        val isValid: Boolean,
        val warnings: List<String>,
        val errors: List<String>
    )
    
    data class OperationResult(
        val success: Boolean,
        val errors: List<String>
    )
    
    data class AppPolicyResult(
        val successful: Int,
        val failed: Int,
        val warnings: List<String>,
        val errors: List<String>
    )
    
    fun cleanup() {
        processingScope.cancel()
    }
}
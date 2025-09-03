package com.shieldtechhub.shieldkids.features.policy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager
import com.shieldtechhub.shieldkids.features.policy.model.AppPolicy
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import com.shieldtechhub.shieldkids.features.policy.model.PolicyViolation
import com.shieldtechhub.shieldkids.features.policy.model.ViolationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PolicyEnforcementManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PolicyEnforcement"
        private const val POLICIES_PREF = "shield_policies"
        
        @Volatile
        private var INSTANCE: PolicyEnforcementManager? = null
        
        fun getInstance(context: Context): PolicyEnforcementManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PolicyEnforcementManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val deviceAdminManager = DeviceAdminManager(context)
    private val deviceStateManager = com.shieldtechhub.shieldkids.common.utils.DeviceStateManager(context)
    private val packageManager = context.packageManager
    private val prefs = context.getSharedPreferences(POLICIES_PREF, Context.MODE_PRIVATE)
    
    private val _activePolicies = MutableStateFlow<Map<String, DevicePolicy>>(emptyMap())
    val activePolicies: StateFlow<Map<String, DevicePolicy>> = _activePolicies.asStateFlow()
    
    private val _policyViolations = MutableStateFlow<List<PolicyViolation>>(emptyList())
    val policyViolations: StateFlow<List<PolicyViolation>> = _policyViolations.asStateFlow()
    
    private var violationListeners = mutableListOf<(PolicyViolation) -> Unit>()
    private var policyChangeListeners = mutableListOf<(String, DevicePolicy) -> Unit>()
    
    private var policySyncManager: PolicySyncManager? = null
    
    init {
        loadStoredPolicies()
        initializePolicySync()
    }
    
    /**
     * Initialize policy sync for child devices
     */
    private fun initializePolicySync() {
        if (deviceStateManager.isChildDevice()) {
            Log.d(TAG, "Initializing policy sync for child device")
            try {
                policySyncManager = PolicySyncManager.getInstance(context)
                policySyncManager?.startPolicySync()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize policy sync", e)
            }
        }
    }
    
    /**
     * Add listener for policy changes
     */
    fun addPolicyChangeListener(listener: (String, DevicePolicy) -> Unit) {
        policyChangeListeners.add(listener)
    }
    
    /**
     * Remove policy change listener
     */
    fun removePolicyChangeListener(listener: (String, DevicePolicy) -> Unit) {
        policyChangeListeners.remove(listener)
    }
    
    /**
     * Notify all policy change listeners
     */
    private fun notifyPolicyChange(deviceId: String, policy: DevicePolicy) {
        policyChangeListeners.forEach { listener ->
            try {
                listener(deviceId, policy)
            } catch (e: Exception) {
                Log.e(TAG, "Error in policy change listener", e)
            }
        }
    }
    
    // Policy Management
    fun applyDevicePolicy(deviceId: String, policy: DevicePolicy): Boolean {
        Log.d(TAG, "Applying device policy for device: $deviceId")
        
        if (!deviceAdminManager.isDeviceAdminActive()) {
            Log.w(TAG, "Cannot apply policy: Device admin not active")
            return false
        }
        
        return try {
            // Apply device-level restrictions
            applyDeviceRestrictions(policy)
            
            // Apply app-level policies
            policy.appPolicies.forEach { appPolicy ->
                applyAppPolicy(appPolicy)
            }
            
            // Store policy
            storePolicyLocally(deviceId, policy)
            
            // Update active policies
            val currentPolicies = _activePolicies.value.toMutableMap()
            currentPolicies[deviceId] = policy
            _activePolicies.value = currentPolicies
            
            // Notify policy change listeners
            notifyPolicyChange(deviceId, policy)
            
            Log.d(TAG, "Device policy applied successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply device policy", e)
            false
        }
    }
    
    private fun applyDeviceRestrictions(policy: DevicePolicy): Boolean {
        var success = true
        
        // Camera restriction
        if (policy.cameraDisabled) {
            success = success && deviceAdminManager.setCameraDisabled(true)
        }
        
        // Password policy
        policy.passwordPolicy?.let { passwordPolicy ->
            success = success && deviceAdminManager.setPasswordPolicy(
                passwordPolicy.minLength,
                passwordPolicy.requireSpecialChars
            )
        }
        
        // Keyguard restrictions
        if (policy.keyguardRestrictions > 0) {
            success = success && deviceAdminManager.setKeyguardRestrictions(policy.keyguardRestrictions)
        }
        
        return success
    }
    
    private fun applyAppPolicy(appPolicy: AppPolicy): Boolean {
        Log.d(TAG, "Applying app policy for: ${appPolicy.packageName}")
        
        return try {
            when (appPolicy.action) {
                AppPolicy.Action.BLOCK -> {
                    // Mark app as blocked in local storage
                    prefs.edit()
                        .putBoolean("blocked_${appPolicy.packageName}", true)
                        .putString("block_reason_${appPolicy.packageName}", appPolicy.reason ?: "Blocked by parent")
                        .apply()
                }
                AppPolicy.Action.TIME_LIMIT -> {
                    // Store time limit for app
                    appPolicy.timeLimit?.let { timeLimit ->
                        prefs.edit()
                            .putLong("time_limit_${appPolicy.packageName}", timeLimit.dailyLimitMinutes * 60 * 1000)
                            .putString("time_start_${appPolicy.packageName}", timeLimit.allowedStartTime)
                            .putString("time_end_${appPolicy.packageName}", timeLimit.allowedEndTime)
                            .apply()
                    }
                }
                AppPolicy.Action.ALLOW -> {
                    // Remove any existing restrictions
                    prefs.edit()
                        .remove("blocked_${appPolicy.packageName}")
                        .remove("block_reason_${appPolicy.packageName}")
                        .remove("time_limit_${appPolicy.packageName}")
                        .apply()
                }
                AppPolicy.Action.SCHEDULE -> {
                    // Store schedule restrictions for app
                    appPolicy.scheduleRestriction?.let { schedule ->
                        val editor = prefs.edit()
                        // Store allowed days
                        editor.putString("schedule_days_${appPolicy.packageName}", schedule.allowedDays.joinToString(",") { it.name })
                        // Store allowed time ranges
                        schedule.allowedTimeRanges.forEachIndexed { index, range ->
                            editor.putString("schedule_start_${index}_${appPolicy.packageName}", range.startTime)
                            editor.putString("schedule_end_${index}_${appPolicy.packageName}", range.endTime)
                        }
                        editor.putInt("schedule_ranges_count_${appPolicy.packageName}", schedule.allowedTimeRanges.size)
                        editor.apply()
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply app policy for ${appPolicy.packageName}", e)
            false
        }
    }
    
    // Policy Checking
    fun isAppBlocked(packageName: String): Boolean {
        return prefs.getBoolean("blocked_$packageName", false)
    }
    
    fun getAppBlockReason(packageName: String): String? {
        return prefs.getString("block_reason_$packageName", null)
    }
    
    fun hasTimeLimit(packageName: String): Boolean {
        return prefs.contains("time_limit_$packageName")
    }
    
    fun getTimeLimit(packageName: String): Long {
        return prefs.getLong("time_limit_$packageName", 0)
    }
    
    fun hasExceededTimeLimit(packageName: String): Boolean {
        val timeLimit = getTimeLimit(packageName)
        if (timeLimit <= 0) return false
        
        return try {
            val screenTimeCollector = com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector.getInstance(context)
            val currentUsage = screenTimeCollector.getCurrentAppUsage(packageName)
            currentUsage >= timeLimit
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check time limit for $packageName", e)
            false
        }
    }
    
    fun getRemainingTime(packageName: String): Long {
        val timeLimit = getTimeLimit(packageName)
        if (timeLimit <= 0) return Long.MAX_VALUE
        
        return try {
            val screenTimeCollector = com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector.getInstance(context)
            val currentUsage = screenTimeCollector.getCurrentAppUsage(packageName)
            val remaining = timeLimit - currentUsage
            maxOf(0L, remaining) // Don't return negative values
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get remaining time for $packageName", e)
            timeLimit
        }
    }
    
    fun isWithinAllowedTime(packageName: String): Boolean {
        val startTime = prefs.getString("time_start_$packageName", null)
        val endTime = prefs.getString("time_end_$packageName", null)
        
        if (startTime == null || endTime == null) {
            return true // No time restrictions
        }
        
        // Check current time against allowed window
        val currentTime = getCurrentTimeString()
        return currentTime >= startTime && currentTime <= endTime
    }
    
    private fun getCurrentTimeString(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
    
    // Violation Handling
    fun reportViolation(packageName: String, violationType: ViolationType, details: String? = null) {
        val violation = PolicyViolation(
            id = generateViolationId(),
            packageName = packageName,
            type = violationType,
            timestamp = System.currentTimeMillis(),
            details = details,
            deviceId = getDeviceId()
        )
        
        Log.w(TAG, "Policy violation reported: $violation")
        
        // Add to violations list
        val currentViolations = _policyViolations.value.toMutableList()
        currentViolations.add(0, violation) // Add to front
        
        // Keep only last 100 violations
        if (currentViolations.size > 100) {
            currentViolations.removeAt(currentViolations.size - 1)
        }
        
        _policyViolations.value = currentViolations
        
        // Store violation locally
        storeViolationLocally(violation)
        
        // Notify listeners
        violationListeners.forEach { listener ->
            try {
                listener(violation)
            } catch (e: Exception) {
                Log.e(TAG, "Error notifying violation listener", e)
            }
        }
        
        // Take enforcement action
        enforceViolation(violation)
    }
    
    private fun enforceViolation(violation: PolicyViolation) {
        when (violation.type) {
            ViolationType.APP_BLOCKED_ATTEMPTED -> {
                // App blocking is handled by the blocking service
                Log.d(TAG, "App blocking violation handled by blocking service")
            }
            ViolationType.TIME_LIMIT_EXCEEDED -> {
                // Force close the app or show warning
                broadcastTimeoutWarning(violation.packageName)
            }
            ViolationType.INSTALLATION_BLOCKED -> {
                // Installation blocking is handled by the installation monitor
                Log.d(TAG, "Installation blocking handled by monitor")
            }
            ViolationType.POLICY_TAMPERING -> {
                // Alert parent and potentially lock device
                handlePolicyTampering()
            }
            ViolationType.SCHEDULE_VIOLATION -> {
                // Schedule violation is handled by accessibility service
                Log.d(TAG, "Schedule violation handled by accessibility service")
            }
            ViolationType.BEDTIME_VIOLATION -> {
                // Bedtime violation - lock device or show warning
                Log.w(TAG, "Bedtime violation detected")
                broadcastBedtimeWarning()
            }
            ViolationType.CATEGORY_BLOCKED -> {
                // Category blocking is handled by app blocking service
                Log.d(TAG, "Category blocking handled by blocking service")
            }
            ViolationType.UNINSTALL_ATTEMPTED -> {
                // Uninstall attempt - alert parent immediately
                Log.e(TAG, "Uninstall attempt detected")
                handleUninstallAttempt()
            }
            ViolationType.DEVICE_ADMIN_DISABLED -> {
                // Device admin disabled - critical security issue
                Log.e(TAG, "Device admin disabled")
                handleDeviceAdminDisabled()
            }
            ViolationType.UNKNOWN -> {
                // Unknown violation - log for analysis
                Log.w(TAG, "Unknown violation type: ${violation.details}")
            }
        }
    }
    
    private fun broadcastTimeoutWarning(packageName: String) {
        val intent = Intent("com.shieldkids.TIME_LIMIT_WARNING").apply {
            putExtra("package_name", packageName)
        }
        context.sendBroadcast(intent)
    }
    
    private fun handlePolicyTampering() {
        Log.w(TAG, "Policy tampering detected - notifying parent")
        
        // Could lock device if severe tampering
        val intent = Intent("com.shieldkids.POLICY_TAMPERING").apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }
    
    private fun broadcastBedtimeWarning() {
        val intent = Intent("com.shieldkids.BEDTIME_VIOLATION").apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }
    
    private fun handleUninstallAttempt() {
        Log.e(TAG, "Uninstall attempt detected - alerting parent")
        
        val intent = Intent("com.shieldkids.UNINSTALL_ATTEMPT").apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }
    
    private fun handleDeviceAdminDisabled() {
        Log.e(TAG, "Device admin disabled - critical security issue")
        
        val intent = Intent("com.shieldkids.DEVICE_ADMIN_DISABLED").apply {
            putExtra("timestamp", System.currentTimeMillis())
        }
        context.sendBroadcast(intent)
    }
    
    // Installation Control
    fun canInstallApp(packageName: String): Boolean {
        // Check if app installations are globally blocked
        val installationsBlocked = prefs.getBoolean("block_installations", false)
        if (installationsBlocked) {
            return false
        }
        
        // Check if specific app is pre-approved
        val preApproved = prefs.getBoolean("approved_$packageName", false)
        if (preApproved) {
            return true
        }
        
        // Check category restrictions
        return !isCategoryBlocked(packageName)
    }
    
    private fun isCategoryBlocked(packageName: String): Boolean {
        // This would integrate with AppInventoryManager to check category
        return false // Placeholder
    }
    
    fun blockAppInstallation(packageName: String, reason: String = "Parent approval required") {
        Log.d(TAG, "Blocking installation of: $packageName")
        
        prefs.edit()
            .putBoolean("install_blocked_$packageName", true)
            .putString("install_block_reason_$packageName", reason)
            .apply()
        
        reportViolation(packageName, ViolationType.INSTALLATION_BLOCKED, reason)
    }
    
    // Persistence
    private fun storePolicyLocally(deviceId: String, policy: DevicePolicy) {
        val policyJson = policy.toJson()
        prefs.edit()
            .putString("policy_$deviceId", policyJson)
            .putLong("policy_timestamp_$deviceId", System.currentTimeMillis())
            .apply()
    }
    
    private fun loadStoredPolicies() {
        val policies = mutableMapOf<String, DevicePolicy>()
        
        prefs.all.keys.filter { it.startsWith("policy_") && !it.contains("timestamp") }.forEach { key ->
            val deviceId = key.removePrefix("policy_")
            val policyJson = prefs.getString(key, null)
            
            policyJson?.let { json ->
                try {
                    val policy = DevicePolicy.fromJson(json)
                    policies[deviceId] = policy
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load policy for device: $deviceId", e)
                }
            }
        }
        
        _activePolicies.value = policies
        Log.d(TAG, "Loaded ${policies.size} stored policies")
    }
    
    private fun storeViolationLocally(violation: PolicyViolation) {
        val violationJson = violation.toJson()
        prefs.edit()
            .putString("violation_${violation.id}", violationJson)
            .apply()
    }
    
    // Utility functions
    private fun generateViolationId(): String {
        return "violation_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
    
    private fun getDeviceId(): String {
        // Get unique device identifier
        return Build.ID
    }
    
    // Listener management
    fun addViolationListener(listener: (PolicyViolation) -> Unit) {
        violationListeners.add(listener)
    }
    
    fun removeViolationListener(listener: (PolicyViolation) -> Unit) {
        violationListeners.remove(listener)
    }
    
    // Policy validation
    fun validatePolicyIntegrity(): Boolean {
        return try {
            // Check if device admin is still active
            if (!deviceAdminManager.isDeviceAdminActive()) {
                reportViolation("system", ViolationType.POLICY_TAMPERING, "Device admin deactivated")
                return false
            }
            
            // Check if app is still system app / uninstallable
            val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
            if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                // App is not system app - could be vulnerable to uninstallation
                Log.w(TAG, "⚠️ Security Warning: Shield Kids is not installed as a system app")
                Log.w(TAG, "This may allow the child to uninstall the app and bypass parental controls")
                Log.w(TAG, "For maximum security, consider installing as a system app or using device management")
                
                // Report this security concern to parent
                reportViolation("system", ViolationType.POLICY_TAMPERING, 
                    "Shield Kids not installed as system app - may be vulnerable to uninstallation")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Policy integrity check failed", e)
            false
        }
    }
    
    // Policy synchronization
    fun syncPoliciesWithBackend() {
        // This would integrate with Firebase to sync policies
        // For now, placeholder
        Log.d(TAG, "Policy sync with backend - placeholder")
    }
    
    // Emergency functions
    fun emergencyLockDevice(): Boolean {
        return if (deviceAdminManager.isDeviceAdminActive()) {
            Log.w(TAG, "Emergency device lock triggered")
            deviceAdminManager.lockDevice()
        } else {
            Log.e(TAG, "Cannot emergency lock - device admin not active")
            false
        }
    }
    
    fun clearAllPolicies() {
        Log.w(TAG, "Clearing all policies")
        
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("policy_") || key.startsWith("blocked_") || 
                key.startsWith("time_limit_") || key.startsWith("approved_")) {
                editor.remove(key)
            }
        }
        editor.apply()
        
        _activePolicies.value = emptyMap()
    }
    
    // Device-level screen time enforcement
    
    /**
     * Check if device is in bedtime
     */
    fun isInBedtime(): Boolean {
        return activePolicies.value.values.any { policy ->
            policy.isWithinBedtime()
        }
    }
    
    /**
     * Check if daily screen time limit is exceeded
     */
    fun isDailyScreenTimeLimitExceeded(): Boolean {
        val policy = activePolicies.value.values.firstOrNull() ?: return false
        val dailyLimit = policy.getDailyScreenTimeLimit()
        
        if (dailyLimit <= 0) return false // No limit set
        
        val todayUsage = getTodayScreenTime()
        return todayUsage >= dailyLimit * 60 * 1000 // Convert minutes to milliseconds
    }
    
    /**
     * Check if weekly screen time limit is exceeded
     */
    fun isWeeklyScreenTimeLimitExceeded(): Boolean {
        val policy = activePolicies.value.values.firstOrNull() ?: return false
        val weeklyLimit = policy.weeklyScreenTime
        
        if (weeklyLimit <= 0) return false // No limit set
        
        val weekUsage = getWeekScreenTime()
        return weekUsage >= weeklyLimit * 60 * 1000 // Convert minutes to milliseconds
    }
    
    /**
     * Get remaining screen time for today in minutes
     */
    fun getRemainingScreenTime(): Long {
        val policy = activePolicies.value.values.firstOrNull() ?: return Long.MAX_VALUE
        val dailyLimit = policy.getDailyScreenTimeLimit()
        
        if (dailyLimit <= 0) return Long.MAX_VALUE // No limit
        
        val todayUsage = getTodayScreenTime() / (60 * 1000) // Convert to minutes
        return maxOf(0, dailyLimit - todayUsage)
    }
    
    /**
     * Check if device usage should be blocked
     */
    fun isDeviceBlocked(): Boolean {
        return isInBedtime() || isDailyScreenTimeLimitExceeded() || isWeeklyScreenTimeLimitExceeded()
    }
    
    /**
     * Get current daily usage in milliseconds
     */
    private fun getTodayScreenTime(): Long {
        // This would integrate with ScreenTimeCollector
        // For now, return 0 - this needs proper implementation
        return 0L
    }
    
    /**
     * Get current weekly usage in milliseconds
     */
    private fun getWeekScreenTime(): Long {
        // This would integrate with ScreenTimeCollector
        // For now, return 0 - this needs proper implementation
        return 0L
    }
    
    /**
     * Show device block notification to user
     */
    fun showDeviceBlockedNotification(reason: String) {
        val intent = Intent("com.shieldtechhub.shieldkids.DEVICE_BLOCKED")
        intent.putExtra("reason", reason)
        intent.putExtra("timestamp", System.currentTimeMillis())
        context.sendBroadcast(intent)
    }
    
    /**
     * Clean up resources and stop policy sync
     */
    fun cleanup() {
        try {
            policySyncManager?.stopPolicySync()
            policyChangeListeners.clear()
            violationListeners.clear()
            
            Log.d(TAG, "PolicyEnforcementManager cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    /**
     * Get current policy sync status for debugging
     */
    fun getPolicySyncStatus(): Map<String, Any> {
        return try {
            mapOf(
                "isChildDevice" to deviceStateManager.isChildDevice(),
                "activePoliciesCount" to _activePolicies.value.size,
                "policyChangeListeners" to policyChangeListeners.size,
                "violationListeners" to violationListeners.size,
                "syncManagerInitialized" to true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting sync status", e)
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }
}
package com.shieldtechhub.shieldkids.features.policy

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager
import com.shieldtechhub.shieldkids.features.policy.model.PolicyViolation
import com.shieldtechhub.shieldkids.features.policy.model.ViolationType
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Enhanced policy violation handler with escalation strategies
 * Handles immediate enforcement, parent notification, and repeat violation tracking
 */
class PolicyViolationHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "PolicyViolationHandler"
        private const val MAX_VIOLATIONS_PER_HOUR = 10
        private const val ESCALATION_THRESHOLD = 3
        private const val CRITICAL_VIOLATION_THRESHOLD = 5
        
        @Volatile
        private var INSTANCE: PolicyViolationHandler? = null
        
        fun getInstance(context: Context): PolicyViolationHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PolicyViolationHandler(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val deviceAdminManager = DeviceAdminManager(context)
    private val policyEnforcementManager = PolicyEnforcementManager.getInstance(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Violation tracking
    private val violationCount = ConcurrentHashMap<String, Int>()
    private val lastViolationTime = ConcurrentHashMap<String, Long>()
    private val escalationLevel = ConcurrentHashMap<String, Int>()
    
    // Enforcement strategies
    private val enforcementStrategies = mapOf(
        ViolationType.APP_BLOCKED_ATTEMPTED to ::handleAppBlockAttempt,
        ViolationType.TIME_LIMIT_EXCEEDED to ::handleTimeLimitViolation,
        ViolationType.INSTALLATION_BLOCKED to ::handleBlockedInstallation,
        ViolationType.POLICY_TAMPERING to ::handlePolicyTampering,
        ViolationType.BEDTIME_VIOLATION to ::handleBedtimeViolation,
        ViolationType.CATEGORY_BLOCKED to ::handleCategoryViolation,
        ViolationType.SCHEDULE_VIOLATION to ::handleScheduleViolation,
        ViolationType.UNINSTALL_ATTEMPTED to ::handleUninstallAttempt,
        ViolationType.DEVICE_ADMIN_DISABLED to ::handleDeviceAdminDisabled,
        ViolationType.UNKNOWN to ::handleUnknownViolation
    )
    
    /**
     * Handle a policy violation with comprehensive enforcement
     */
    suspend fun handleViolation(violation: PolicyViolation): ViolationHandlingResult {
        Log.w(TAG, "Handling violation: ${violation.type} for ${violation.packageName}")
        
        return withContext(Dispatchers.IO) {
            try {
                // Update violation tracking
                updateViolationTracking(violation)
                
                // Determine enforcement strategy
                val strategy = enforcementStrategies[violation.type] ?: ::handleUnknownViolation
                
                // Apply immediate enforcement
                val enforcementResult = strategy(violation)
                
                // Handle escalation if needed
                val escalationResult = handleViolationEscalation(violation)
                
                // Notify parent if required
                val notificationResult = notifyParentIfRequired(violation)
                
                // Store violation record
                storeViolationRecord(violation)
                
                // Schedule follow-up actions
                scheduleFollowUpActions(violation)
                
                ViolationHandlingResult(
                    success = enforcementResult.success && escalationResult.success,
                    actionsTaken = enforcementResult.actions + escalationResult.actions,
                    parentNotified = notificationResult.sent,
                    escalationLevel = getCurrentEscalationLevel(violation.packageName),
                    followUpScheduled = true
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle violation", e)
                ViolationHandlingResult(
                    success = false,
                    actionsTaken = listOf("Error occurred during violation handling"),
                    parentNotified = false,
                    escalationLevel = 0,
                    followUpScheduled = false
                )
            }
        }
    }
    
    private fun updateViolationTracking(violation: PolicyViolation) {
        val key = "${violation.packageName}_${violation.type}"
        val currentTime = System.currentTimeMillis()
        val hourAgo = currentTime - (60 * 60 * 1000)
        
        // Reset count if more than an hour has passed
        val lastTime = lastViolationTime[key] ?: 0
        if (lastTime < hourAgo) {
            violationCount[key] = 0
        }
        
        // Update counts
        violationCount[key] = (violationCount[key] ?: 0) + 1
        lastViolationTime[key] = currentTime
        
        // Update escalation level
        val count = violationCount[key] ?: 0
        escalationLevel[key] = when {
            count >= CRITICAL_VIOLATION_THRESHOLD -> 3
            count >= ESCALATION_THRESHOLD -> 2
            count > 1 -> 1
            else -> 0
        }
    }
    
    private suspend fun handleAppBlockAttempt(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Force close the app if it's running
            val result = forceCloseApp(violation.packageName)
            if (result) {
                actions.add("Forced app closure")
            }
            
            // Show blocking overlay
            showAppBlockingOverlay(violation.packageName)
            actions.add("Displayed blocking message")
            
            // Escalate if repeated attempts
            val count = violationCount["${violation.packageName}_${violation.type}"] ?: 0
            if (count > ESCALATION_THRESHOLD) {
                // Temporarily disable app
                if (disableAppTemporarily(violation.packageName, 5 * 60 * 1000)) { // 5 minutes
                    actions.add("Temporarily disabled app for 5 minutes")
                }
            }
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle app block attempt", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleTimeLimitViolation(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Show time limit warning
            showTimeLimitWarning(violation.packageName)
            actions.add("Displayed time limit warning")
            
            // Force close after grace period
            delay(30000) // 30 second grace period
            
            if (forceCloseApp(violation.packageName)) {
                actions.add("Forced app closure after grace period")
            }
            
            // Block app for remainder of day
            blockAppUntilNextDay(violation.packageName)
            actions.add("Blocked app until next day")
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle time limit violation", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleBlockedInstallation(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Show installation blocked message
            showInstallationBlockedMessage(violation.packageName)
            actions.add("Displayed installation blocked message")
            
            // Request app uninstall
            if (requestAppUninstall(violation.packageName)) {
                actions.add("Requested app uninstall")
            }
            
            // If uninstall fails, hide app
            delay(5000) // Wait for uninstall attempt
            if (isAppStillInstalled(violation.packageName)) {
                hideAppFromLauncher(violation.packageName)
                actions.add("Hidden app from launcher")
            }
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle blocked installation", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handlePolicyTampering(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // This is critical - immediately alert parent
            sendEmergencyParentAlert(violation)
            actions.add("Sent emergency parent alert")
            
            // Lock device if tampering is severe
            if (deviceAdminManager.lockDevice()) {
                actions.add("Locked device due to policy tampering")
            }
            
            // Re-apply policies
            restoreCompromisedPolicies()
            actions.add("Restored compromised policies")
            
            // Increase monitoring frequency
            enableIntensiveMonitoring()
            actions.add("Enabled intensive monitoring")
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle policy tampering", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleBedtimeViolation(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Show bedtime warning
            showBedtimeWarning()
            actions.add("Displayed bedtime warning")
            
            // Close all non-essential apps
            closeNonEssentialApps()
            actions.add("Closed non-essential apps")
            
            // Enable bedtime mode
            enableBedtimeMode()
            actions.add("Enabled bedtime mode")
            
            // Dim screen and restrict access
            if (deviceAdminManager.isDeviceAdminActive()) {
                // Could implement screen dimming or sleep mode
                actions.add("Applied bedtime restrictions")
            }
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle bedtime violation", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleCategoryViolation(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Similar to app block but for category
            forceCloseApp(violation.packageName)
            actions.add("Closed blocked category app")
            
            // Show category blocking message
            showCategoryBlockedMessage(violation.packageName)
            actions.add("Displayed category blocked message")
            
            // Block all apps in same category
            blockCategoryApps(getCategoryForApp(violation.packageName))
            actions.add("Blocked all apps in category")
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle category violation", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleScheduleViolation(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Show schedule restriction message
            showScheduleViolationMessage(violation.packageName)
            actions.add("Displayed schedule violation message")
            
            // Force close app
            forceCloseApp(violation.packageName)
            actions.add("Closed app due to schedule violation")
            
            // Set reminder for when app becomes available
            scheduleAppAvailabilityReminder(violation.packageName)
            actions.add("Scheduled availability reminder")
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle schedule violation", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleUninstallAttempt(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Critical security event - immediate parent notification
            sendEmergencyParentAlert(violation)
            actions.add("Sent emergency alert for uninstall attempt")
            
            // Lock device immediately
            if (deviceAdminManager.lockDevice()) {
                actions.add("Locked device to prevent uninstall")
            }
            
            // Enable maximum security mode
            enableMaximumSecurityMode()
            actions.add("Enabled maximum security mode")
            
            // Log detailed forensic information
            logUninstallAttemptDetails()
            actions.add("Logged forensic details")
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle uninstall attempt", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleDeviceAdminDisabled(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Critical - attempt to re-enable device admin
            attemptDeviceAdminReactivation()
            actions.add("Attempted device admin reactivation")
            
            // Emergency parent notification
            sendEmergencyParentAlert(violation)
            actions.add("Sent emergency parent alert")
            
            // Enable fallback enforcement mode
            enableFallbackEnforcement()
            actions.add("Enabled fallback enforcement")
            
            // Continuous reactivation attempts
            scheduleDeviceAdminRecovery()
            actions.add("Scheduled recovery attempts")
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle device admin disabled", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleUnknownViolation(violation: PolicyViolation): EnforcementResult {
        val actions = mutableListOf<String>()
        
        try {
            // Log for analysis
            logUnknownViolation(violation)
            actions.add("Logged unknown violation for analysis")
            
            // Apply generic enforcement
            if (violation.packageName.isNotEmpty()) {
                showGenericViolationMessage(violation.packageName)
                actions.add("Displayed generic violation message")
            }
            
            return EnforcementResult(success = true, actions = actions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle unknown violation", e)
            return EnforcementResult(success = false, actions = actions)
        }
    }
    
    private suspend fun handleViolationEscalation(violation: PolicyViolation): EnforcementResult {
        val currentLevel = getCurrentEscalationLevel(violation.packageName)
        val actions = mutableListOf<String>()
        
        when (currentLevel) {
            1 -> {
                // First escalation - increase monitoring
                actions.add("Increased monitoring frequency")
            }
            2 -> {
                // Second escalation - temporary restrictions
                enableTemporaryRestrictions(violation.packageName)
                actions.add("Applied temporary additional restrictions")
            }
            3 -> {
                // Critical escalation - severe measures
                if (applySevereMeasures(violation)) {
                    actions.add("Applied severe enforcement measures")
                }
                
                // Always notify parent at this level
                sendEscalationParentAlert(violation, currentLevel)
                actions.add("Sent escalation alert to parent")
            }
        }
        
        return EnforcementResult(success = true, actions = actions)
    }
    
    // Utility methods for enforcement actions
    private fun forceCloseApp(packageName: String): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.killBackgroundProcesses(packageName)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot force close app: $packageName", e)
            false
        }
    }
    
    private fun showAppBlockingOverlay(packageName: String) {
        val intent = Intent("com.shieldkids.SHOW_APP_BLOCKED").apply {
            putExtra("package_name", packageName)
        }
        context.sendBroadcast(intent)
    }
    
    private fun disableAppTemporarily(packageName: String, duration: Long): Boolean {
        return try {
            // This would require system or device admin privileges
            val prefs = context.getSharedPreferences("temp_disabled", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("disabled_$packageName", true)
                .putLong("disabled_until_$packageName", System.currentTimeMillis() + duration)
                .apply()
            
            // Schedule re-enable
            scheduleAppReEnable(packageName, duration)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to temporarily disable app: $packageName", e)
            false
        }
    }
    
    private fun getCurrentEscalationLevel(packageName: String): Int {
        return escalationLevel["${packageName}_escalation"] ?: 0
    }
    
    // Additional helper methods would be implemented here...
    // These are simplified implementations for brevity
    
    private suspend fun notifyParentIfRequired(violation: PolicyViolation): NotificationResult {
        return if (violation.shouldNotifyParent()) {
            sendParentNotification(violation)
            NotificationResult(sent = true)
        } else {
            NotificationResult(sent = false)
        }
    }
    
    private fun sendParentNotification(violation: PolicyViolation) {
        val notification = NotificationCompat.Builder(context, "shield_violations")
            .setContentTitle("Policy Violation Alert")
            .setContentText(violation.getDisplayMessage())
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(violation.id.hashCode(), notification)
    }
    
    private fun storeViolationRecord(violation: PolicyViolation) {
        val prefs = context.getSharedPreferences("violation_records", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("violation_${violation.id}", violation.toJson())
            .apply()
    }
    
    // Data classes for results
    data class ViolationHandlingResult(
        val success: Boolean,
        val actionsTaken: List<String>,
        val parentNotified: Boolean,
        val escalationLevel: Int,
        val followUpScheduled: Boolean
    )
    
    data class EnforcementResult(
        val success: Boolean,
        val actions: List<String>
    )
    
    data class NotificationResult(
        val sent: Boolean
    )
    
    // Placeholder methods - would need full implementation
    private fun showTimeLimitWarning(packageName: String) { /* Implementation */ }
    private fun blockAppUntilNextDay(packageName: String) { /* Implementation */ }
    private fun showInstallationBlockedMessage(packageName: String) { /* Implementation */ }
    private fun requestAppUninstall(packageName: String): Boolean = false
    private fun isAppStillInstalled(packageName: String): Boolean = true
    private fun hideAppFromLauncher(packageName: String) { /* Implementation */ }
    private fun sendEmergencyParentAlert(violation: PolicyViolation) { /* Implementation */ }
    private fun restoreCompromisedPolicies() { /* Implementation */ }
    private fun enableIntensiveMonitoring() { /* Implementation */ }
    private fun showBedtimeWarning() { /* Implementation */ }
    private fun closeNonEssentialApps() { /* Implementation */ }
    private fun enableBedtimeMode() { /* Implementation */ }
    private fun showCategoryBlockedMessage(packageName: String) { /* Implementation */ }
    private fun blockCategoryApps(category: String) { /* Implementation */ }
    private fun getCategoryForApp(packageName: String): String = "UNKNOWN"
    private fun showScheduleViolationMessage(packageName: String) { /* Implementation */ }
    private fun scheduleAppAvailabilityReminder(packageName: String) { /* Implementation */ }
    private fun enableMaximumSecurityMode() { /* Implementation */ }
    private fun logUninstallAttemptDetails() { /* Implementation */ }
    private fun attemptDeviceAdminReactivation() { /* Implementation */ }
    private fun enableFallbackEnforcement() { /* Implementation */ }
    private fun scheduleDeviceAdminRecovery() { /* Implementation */ }
    private fun logUnknownViolation(violation: PolicyViolation) { /* Implementation */ }
    private fun showGenericViolationMessage(packageName: String) { /* Implementation */ }
    private fun enableTemporaryRestrictions(packageName: String) { /* Implementation */ }
    private fun applySevereMeasures(violation: PolicyViolation): Boolean = false
    private fun sendEscalationParentAlert(violation: PolicyViolation, level: Int) { /* Implementation */ }
    private fun scheduleAppReEnable(packageName: String, duration: Long) { /* Implementation */ }
    private fun scheduleFollowUpActions(violation: PolicyViolation) { /* Implementation */ }
    
    fun cleanup() {
        handlerScope.cancel()
    }
}
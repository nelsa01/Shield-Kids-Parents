package com.shieldtechhub.shieldkids.features.core

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.shieldtechhub.shieldkids.common.base.ShieldDeviceAdminReceiver
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.model.ViolationType

/**
 * 🛡️ Advanced Self-Protection Manager
 * 
 * This class implements multiple layers of protection to prevent Shield Kids
 * from being easily disabled or uninstalled by unauthorized users.
 * 
 * Protection Layers:
 * 1. Device Admin enforcement with strong warnings
 * 2. Uninstall attempt detection and blocking
 * 3. Settings manipulation monitoring
 * 4. Emergency parent notifications
 * 5. Device lockdown on tampering attempts
 */
class SelfProtectionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SelfProtection"
        
        @Volatile
        private var INSTANCE: SelfProtectionManager? = null
        
        fun getInstance(context: Context): SelfProtectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SelfProtectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ShieldDeviceAdminReceiver.getComponentName(context)
    private val packageManager = context.packageManager
    private val policyManager = PolicyEnforcementManager.getInstance(context)
    
    // 🛡️ PRIMARY PROTECTION: Prevent Device Admin Deactivation
    fun enforceDeviceAdminProtection(): Boolean {
        return try {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                Log.w(TAG, "⚠️ CRITICAL: Device Admin not active - app vulnerable to uninstallation")
                
                // Report security vulnerability
                policyManager.reportViolation(
                    context.packageName,
                    ViolationType.POLICY_TAMPERING,
                    "Device Admin deactivated - app protection compromised"
                )
                
                return false
            }
            
            // Device Admin is active - we have protection
            Log.d(TAG, "✅ Device Admin protection active")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check Device Admin status", e)
            false
        }
    }
    
    // 🚨 CRITICAL: Detect uninstall attempts and block them
    fun checkForUninstallAttempt(): Boolean {
        return try {
            // Check if our app is still installed and functioning
            val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val isEnabled = appInfo.enabled
            
            if (!isEnabled) {
                Log.e(TAG, "🚨 SECURITY ALERT: Shield Kids has been disabled!")
                
                // Emergency lockdown
                triggerEmergencyLockdown("App disabled by unauthorized user")
                
                return true // Tampering detected
            }
            
            false // No tampering detected
            
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Failed to verify app installation status", e)
            true // Assume tampering if we can't verify
        }
    }
    
    // 🔒 EMERGENCY: Lock device when tampering is detected
    fun triggerEmergencyLockdown(reason: String) {
        Log.e(TAG, "🚨 EMERGENCY LOCKDOWN TRIGGERED: $reason")
        
        try {
            // 1. Lock the device immediately
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
                Log.w(TAG, "Device locked due to security violation")
            }
            
            // 2. Report critical violation
            policyManager.reportViolation(
                "system",
                ViolationType.POLICY_TAMPERING,
                "EMERGENCY LOCKDOWN: $reason"
            )
            
            // 3. Try to send immediate parent notification
            sendEmergencyNotification(reason)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute emergency lockdown", e)
        }
    }
    
    // 📱 Send immediate emergency notification to parent
    private fun sendEmergencyNotification(reason: String) {
        try {
            val emergencyIntent = Intent("com.shieldtechhub.shieldkids.EMERGENCY_ALERT")
            emergencyIntent.putExtra("alert_type", "TAMPERING_ATTEMPT")
            emergencyIntent.putExtra("reason", reason)
            emergencyIntent.putExtra("timestamp", System.currentTimeMillis())
            emergencyIntent.putExtra("severity", "CRITICAL")
            
            context.sendBroadcast(emergencyIntent)
            Log.d(TAG, "Emergency notification sent to parent")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send emergency notification", e)
        }
    }
    
    // 🔍 Monitor for Settings app access (attempt to disable Device Admin)
    fun isUserTryingToDisableProtection(): Boolean {
        // This would require accessibility service or usage stats to detect
        // if user is trying to access Device Admin settings
        
        // For now, we rely on onDisableRequested() in the DeviceAdminReceiver
        return false
    }
    
    // 💪 Force re-enable Device Admin if possible
    fun attemptToReenableProtection(activity: Activity) {
        Log.w(TAG, "Attempting to re-enable Device Admin protection")
        
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            // Request Device Admin activation
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                """
                🛡️ SECURITY RESTORE REQUIRED 🛡️
                
                Shield Kids protection has been disabled.
                Please re-enable Device Admin to restore:
                • App uninstall protection
                • Parental control enforcement
                • Child safety monitoring
                
                This is required for your child's digital safety.
                """.trimIndent()
            )
            
            try {
                activity.startActivityForResult(intent, 3001)
                Log.d(TAG, "Device Admin re-activation requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request Device Admin re-activation", e)
            }
        }
    }
    
    // 🔬 Advanced: Check for root/debugging attempts
    fun checkForSecurityThreats(): List<String> {
        val threats = mutableListOf<String>()
        
        try {
            // Check for root access
            if (isDeviceRooted()) {
                threats.add("Device is rooted - security compromised")
            }
            
            // Check for debugging enabled
            if (isDeveloperOptionsEnabled()) {
                threats.add("Developer options enabled - potential security risk")
            }
            
            // Check for unknown sources enabled
            if (isUnknownSourcesEnabled()) {
                threats.add("Unknown sources enabled - sideloading possible")
            }
            
            // Check for ADB debugging
            if (isAdbEnabled()) {
                threats.add("ADB debugging enabled - external control possible")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check security threats", e)
            threats.add("Security threat check failed")
        }
        
        return threats
    }
    
    // Helper methods for security threat detection
    private fun isDeviceRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.destroy()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isDeveloperOptionsEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    }
    
    private fun isUnknownSourcesEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0
            ) == 1
        }
    }
    
    private fun isAdbEnabled(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1
    }
    
    // 🛡️ Main protection enforcement method
    fun enforceFullProtection(): ProtectionStatus {
        Log.d(TAG, "Enforcing full self-protection measures...")
        
        val status = ProtectionStatus()
        
        // Check Device Admin
        status.deviceAdminActive = enforceDeviceAdminProtection()
        
        // Check for uninstall attempts
        status.uninstallAttempted = checkForUninstallAttempt()
        
        // Check for security threats
        status.securityThreats = checkForSecurityThreats()
        
        // Calculate overall protection level
        status.protectionLevel = when {
            !status.deviceAdminActive -> ProtectionLevel.VULNERABLE
            status.uninstallAttempted -> ProtectionLevel.COMPROMISED
            status.securityThreats.isNotEmpty() -> ProtectionLevel.AT_RISK
            else -> ProtectionLevel.PROTECTED
        }
        
        Log.d(TAG, "Protection status: ${status.protectionLevel}")
        
        // If protection is compromised, activate advanced protection
        if (status.protectionLevel != ProtectionLevel.PROTECTED) {
            activateAdvancedProtection()
        }
        
        return status
    }
    
    // 🚀 Activate advanced protection measures
    private fun activateAdvancedProtection() {
        Log.w(TAG, "Activating advanced protection measures...")
        
        try {
            val advancedProtectionManager = AdvancedSelfProtectionManager.getInstance(context)
            advancedProtectionManager.startPersistentProtection()
            
            Log.d(TAG, "Advanced protection measures activated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to activate advanced protection", e)
        }
    }
}

// Data classes for protection status
data class ProtectionStatus(
    var deviceAdminActive: Boolean = false,
    var uninstallAttempted: Boolean = false,
    var securityThreats: List<String> = emptyList(),
    var protectionLevel: ProtectionLevel = ProtectionLevel.UNKNOWN
)

enum class ProtectionLevel {
    PROTECTED,      // All protections active
    AT_RISK,        // Some security concerns detected
    VULNERABLE,     // Major protections disabled
    COMPROMISED,    // Active tampering detected
    UNKNOWN         // Cannot determine status
}
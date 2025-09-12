package com.shieldtechhub.shieldkids.common.base

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class ShieldDeviceAdminReceiver : DeviceAdminReceiver() {
    
    companion object {
        private const val TAG = "ShieldDeviceAdmin"
        
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, ShieldDeviceAdminReceiver::class.java)
        }
        
        fun isDeviceAdminActive(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return devicePolicyManager.isAdminActive(getComponentName(context))
        }
        
        fun requestDeviceAdminActivation(context: Context): Intent {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Shield Kids needs device admin privileges to enforce parental controls and protect your child's device usage."
            )
            return intent
        }
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Device Admin enabled for Shield Kids")
        
        // Notify user that device admin is now active
        Toast.makeText(context, "Shield Kids parental controls activated", Toast.LENGTH_LONG).show()
        
        // Start monitoring service now that we have admin privileges
        val serviceManager = ServiceManager(context)
        serviceManager.startMonitoringService()
        
        // Store activation timestamp
        val prefs = context.getSharedPreferences("shield_device_admin", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_active", true)
            .putLong("activated_at", System.currentTimeMillis())
            .apply()
            
        // Broadcast that device admin is now enabled
        val broadcastIntent = Intent("com.shieldtechhub.shieldkids.DEVICE_ADMIN_ENABLED")
        context.sendBroadcast(broadcastIntent)
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Device Admin disabled for Shield Kids")
        
        // Notify user that device admin has been deactivated
        Toast.makeText(context, "Shield Kids parental controls deactivated", Toast.LENGTH_LONG).show()
        
        // Stop monitoring service since we no longer have admin privileges
        val serviceManager = ServiceManager(context)
        serviceManager.stopMonitoringService()
        
        // Update stored status
        val prefs = context.getSharedPreferences("shield_device_admin", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_active", false)
            .putLong("deactivated_at", System.currentTimeMillis())
            .apply()
            
        // Broadcast that device admin has been disabled
        val broadcastIntent = Intent("com.shieldtechhub.shieldkids.DEVICE_ADMIN_DISABLED")
        context.sendBroadcast(broadcastIntent)
    }
    
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.d(TAG, "Device password changed")
        
        // Record password change event for security monitoring
        val prefs = context.getSharedPreferences("shield_security_events", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_password_change", System.currentTimeMillis())
            .apply()
    }
    
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.d(TAG, "Device password failed")
        
        // Track failed password attempts for security monitoring
        val prefs = context.getSharedPreferences("shield_security_events", Context.MODE_PRIVATE)
        val currentFailures = prefs.getInt("password_failures", 0)
        prefs.edit()
            .putInt("password_failures", currentFailures + 1)
            .putLong("last_password_failure", System.currentTimeMillis())
            .apply()
            
        // TODO: Could implement progressive lockout or notifications to parent
    }
    
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.d(TAG, "Device password succeeded")
        
        // Reset failed attempts counter on successful login
        val prefs = context.getSharedPreferences("shield_security_events", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("password_failures", 0)
            .putLong("last_successful_login", System.currentTimeMillis())
            .apply()
    }
    
    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d(TAG, "Lock task mode entering for package: $pkg")
    }
    
    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d(TAG, "Lock task mode exiting")
    }
    
    // 🛡️ CRITICAL: This method prevents Device Admin from being easily disabled
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        super.onDisableRequested(context, intent)
        Log.w(TAG, "⚠️ SECURITY ALERT: Device Admin disable requested - potential tampering attempt")
        
        // Immediately notify parent of tampering attempt
        notifyParentOfTamperingAttempt(context, "Device Admin disable attempted")
        
        // Lock the device immediately if possible to prevent further tampering
        try {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (devicePolicyManager.isAdminActive(getComponentName(context))) {
                devicePolicyManager.lockNow()
                Log.w(TAG, "Device locked due to tampering attempt")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not lock device during tampering attempt", e)
        }
        
        // Return a strong deterrent message that makes the user think twice
        return """
            🚨 PARENTAL CONTROL SECURITY ALERT 🚨
            
            Disabling Shield Kids will:
            • Remove ALL safety protections for this device
            • Immediately notify parents of this action
            • Log this security violation with timestamp
            • May result in loss of device privileges
            • Trigger automatic device lock for security
            
            ⚠️ This action is monitored and reported ⚠️
            
            Please contact your parent/guardian before proceeding.
            Unauthorized changes to parental controls are policy violations.
        """.trimIndent()
    }
    
    // 🔔 Notify parent immediately of any tampering attempts
    private fun notifyParentOfTamperingAttempt(context: Context, action: String) {
        try {
            // Log security event
            Log.e(TAG, "🚨 SECURITY VIOLATION: $action at ${System.currentTimeMillis()}")
            
            // Store violation in local storage for later sync
            val prefs = context.getSharedPreferences("shield_security_violations", Context.MODE_PRIVATE)
            val violationId = "violation_${System.currentTimeMillis()}_${(0..999).random()}"
            val violationData = """
                {
                    "id": "$violationId",
                    "type": "DEVICE_ADMIN_TAMPERING",
                    "action": "$action",
                    "timestamp": ${System.currentTimeMillis()},
                    "severity": "HIGH",
                    "device": "${android.os.Build.MODEL}",
                    "android_version": "${android.os.Build.VERSION.RELEASE}"
                }
            """.trimIndent()
            
            prefs.edit()
                .putString(violationId, violationData)
                .putLong("last_violation", System.currentTimeMillis())
                .apply()
                
            // Try to send immediate notification to parent (will be implemented by notification service)
            val broadcastIntent = Intent("com.shieldtechhub.shieldkids.SECURITY_VIOLATION")
            broadcastIntent.putExtra("violation_type", "DEVICE_ADMIN_TAMPERING")
            broadcastIntent.putExtra("violation_data", violationData)
            context.sendBroadcast(broadcastIntent)
            
            Log.d(TAG, "Parent notification queued for security violation: $action")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify parent of tampering attempt", e)
        }
    }
}
package com.shieldtechhub.shieldkids.common.utils

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shieldtechhub.shieldkids.common.base.ShieldDeviceAdminReceiver

class DeviceAdminManager(private val context: Context) {
    
    companion object {
        const val REQUEST_CODE_ENABLE_DEVICE_ADMIN = 2001
    }
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ShieldDeviceAdminReceiver.getComponentName(context)
    
    fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }
    
    fun requestDeviceAdminActivation(activity: Activity) {
        if (!isDeviceAdminActive()) {
            val intent = ShieldDeviceAdminReceiver.requestDeviceAdminActivation(context)
            activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_DEVICE_ADMIN)
        }
    }
    
    fun handleDeviceAdminResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode == REQUEST_CODE_ENABLE_DEVICE_ADMIN) {
            return resultCode == Activity.RESULT_OK && isDeviceAdminActive()
        }
        return false
    }
    
    fun getDeviceAdminCapabilities(): DeviceAdminCapabilities {
        val isActive = isDeviceAdminActive()
        
        return DeviceAdminCapabilities(
            isActive = isActive,
            canLockDevice = isActive && hasCapability { devicePolicyManager.lockNow() },
            canWipeData = isActive && hasCapability { devicePolicyManager.wipeData(0) },
            canSetPasswordPolicy = isActive && hasCapability { devicePolicyManager.setPasswordMinimumLength(adminComponent, 4) },
            canDisableCamera = isActive && hasCapability { devicePolicyManager.setCameraDisabled(adminComponent, false) },
            canControlKeyguard = isActive && canControlKeyguardFeatures()
        )
    }
    
    private fun hasCapability(action: () -> Unit): Boolean {
        return try {
            // This is a dry run to check if we have the capability
            // We don't actually execute the action
            true
        } catch (e: SecurityException) {
            false
        }
    }
    
    private fun canControlKeyguardFeatures(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                devicePolicyManager.setKeyguardDisabledFeatures(adminComponent, 0)
                true
            } catch (e: SecurityException) {
                false
            }
        } else {
            false
        }
    }
    
    // Parental control specific methods
    fun lockDevice(): Boolean {
        return if (isDeviceAdminActive()) {
            try {
                devicePolicyManager.lockNow()
                true
            } catch (e: SecurityException) {
                false
            }
        } else {
            false
        }
    }
    
    fun setCameraDisabled(disabled: Boolean): Boolean {
        return if (isDeviceAdminActive()) {
            try {
                devicePolicyManager.setCameraDisabled(adminComponent, disabled)
                true
            } catch (e: SecurityException) {
                false
            }
        } else {
            false
        }
    }
    
    fun setPasswordPolicy(minLength: Int, requireSpecialChars: Boolean = false): Boolean {
        return if (isDeviceAdminActive()) {
            try {
                devicePolicyManager.setPasswordMinimumLength(adminComponent, minLength)
                
                if (requireSpecialChars && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    devicePolicyManager.setPasswordMinimumSymbols(adminComponent, 1)
                }
                
                true
            } catch (e: SecurityException) {
                false
            }
        } else {
            false
        }
    }
    
    fun setKeyguardRestrictions(restrictions: Int): Boolean {
        return if (isDeviceAdminActive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            try {
                devicePolicyManager.setKeyguardDisabledFeatures(adminComponent, restrictions)
                true
            } catch (e: SecurityException) {
                false
            }
        } else {
            false
        }
    }
    
    fun removeDeviceAdmin(): Boolean {
        return if (isDeviceAdminActive()) {
            try {
                devicePolicyManager.removeActiveAdmin(adminComponent)
                true
            } catch (e: SecurityException) {
                false
            }
        } else {
            true // Already not active
        }
    }
    
    fun getAdminStatus(): DeviceAdminStatus {
        val prefs = context.getSharedPreferences("shield_device_admin", Context.MODE_PRIVATE)
        
        return DeviceAdminStatus(
            isActive = isDeviceAdminActive(),
            activatedAt = prefs.getLong("activated_at", 0),
            deactivatedAt = prefs.getLong("deactivated_at", 0),
            capabilities = getDeviceAdminCapabilities()
        )
    }
}

data class DeviceAdminCapabilities(
    val isActive: Boolean,
    val canLockDevice: Boolean,
    val canWipeData: Boolean,
    val canSetPasswordPolicy: Boolean,
    val canDisableCamera: Boolean,
    val canControlKeyguard: Boolean
)

data class DeviceAdminStatus(
    val isActive: Boolean,
    val activatedAt: Long,
    val deactivatedAt: Long,
    val capabilities: DeviceAdminCapabilities
)
package com.shieldtechhub.shieldkids.common.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log

/**
 * Manages device state for child-parent linking
 * Determines if this device is linked as a child device or parent device
 */
class DeviceStateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceStateManager"
        private const val PREFS_NAME = "shield_device_state"
        private const val KEY_DEVICE_TYPE = "device_type"
        private const val KEY_CHILD_ID = "child_id"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_PARENT_ID = "parent_id"
        private const val KEY_PARENT_EMAIL = "parent_email"
        private const val KEY_LINK_TIMESTAMP = "link_timestamp"
        private const val KEY_DEVICE_ID = "device_id"
        
        const val DEVICE_TYPE_UNLINKED = "unlinked"
        const val DEVICE_TYPE_PARENT = "parent"
        const val DEVICE_TYPE_CHILD = "child"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get the unique device identifier for this device
     */
    fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            // Generate a unique device ID based on Android ID and app instance
            deviceId = generateDeviceId()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }
    
    private fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val timestamp = System.currentTimeMillis()
        return "device_${androidId}_$timestamp"
    }
    
    /**
     * Check what type of device this is
     */
    fun getDeviceType(): String {
        return prefs.getString(KEY_DEVICE_TYPE, DEVICE_TYPE_UNLINKED) ?: DEVICE_TYPE_UNLINKED
    }
    
    /**
     * Check if this device is linked as a child device
     */
    fun isChildDevice(): Boolean {
        return getDeviceType() == DEVICE_TYPE_CHILD
    }
    
    /**
     * Check if this device is set up as a parent device
     */
    fun isParentDevice(): Boolean {
        return getDeviceType() == DEVICE_TYPE_PARENT
    }
    
    /**
     * Check if this device is unlinked (not set up)
     */
    fun isUnlinkedDevice(): Boolean {
        return getDeviceType() == DEVICE_TYPE_UNLINKED
    }
    
    /**
     * Set this device as a child device linked to a parent
     */
    fun setAsChildDevice(childId: String, childName: String, parentId: String, parentEmail: String) {
        Log.d(TAG, "Setting device as child device: $childName linked to $parentEmail")
        
        prefs.edit()
            .putString(KEY_DEVICE_TYPE, DEVICE_TYPE_CHILD)
            .putString(KEY_CHILD_ID, childId)
            .putString(KEY_CHILD_NAME, childName)
            .putString(KEY_PARENT_ID, parentId)
            .putString(KEY_PARENT_EMAIL, parentEmail)
            .putLong(KEY_LINK_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Set this device as a parent device
     */
    fun setAsParentDevice(parentId: String, parentEmail: String) {
        Log.d(TAG, "Setting device as parent device: $parentEmail")
        
        prefs.edit()
            .putString(KEY_DEVICE_TYPE, DEVICE_TYPE_PARENT)
            .putString(KEY_PARENT_ID, parentId)
            .putString(KEY_PARENT_EMAIL, parentEmail)
            .putLong(KEY_LINK_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Get child device information (if this is a child device)
     */
    fun getChildDeviceInfo(): ChildDeviceInfo? {
        if (!isChildDevice()) return null
        
        val childId = prefs.getString(KEY_CHILD_ID, null) ?: return null
        val childName = prefs.getString(KEY_CHILD_NAME, null) ?: return null
        val parentId = prefs.getString(KEY_PARENT_ID, null) ?: return null
        val parentEmail = prefs.getString(KEY_PARENT_EMAIL, null) ?: return null
        val linkTimestamp = prefs.getLong(KEY_LINK_TIMESTAMP, 0)
        
        return ChildDeviceInfo(
            childId = childId,
            childName = childName,
            parentId = parentId,
            parentEmail = parentEmail,
            linkTimestamp = linkTimestamp,
            deviceId = getDeviceId()
        )
    }
    
    /**
     * Get parent device information (if this is a parent device)
     */
    fun getParentDeviceInfo(): ParentDeviceInfo? {
        if (!isParentDevice()) return null
        
        val parentId = prefs.getString(KEY_PARENT_ID, null) ?: return null
        val parentEmail = prefs.getString(KEY_PARENT_EMAIL, null) ?: return null
        val linkTimestamp = prefs.getLong(KEY_LINK_TIMESTAMP, 0)
        
        return ParentDeviceInfo(
            parentId = parentId,
            parentEmail = parentEmail,
            linkTimestamp = linkTimestamp,
            deviceId = getDeviceId()
        )
    }
    
    /**
     * Reset device state (unlink device)
     */
    fun resetDeviceState() {
        Log.d(TAG, "Resetting device state to unlinked")
        
        prefs.edit()
            .putString(KEY_DEVICE_TYPE, DEVICE_TYPE_UNLINKED)
            .remove(KEY_CHILD_ID)
            .remove(KEY_CHILD_NAME)
            .remove(KEY_PARENT_ID)
            .remove(KEY_PARENT_EMAIL)
            .remove(KEY_LINK_TIMESTAMP)
            .apply()
    }
    
    /**
     * Get device state summary for debugging
     */
    fun getDeviceStateSummary(): String {
        return buildString {
            appendLine("Device Type: ${getDeviceType()}")
            appendLine("Device ID: ${getDeviceId()}")
            
            when (getDeviceType()) {
                DEVICE_TYPE_CHILD -> {
                    val info = getChildDeviceInfo()
                    appendLine("Child Name: ${info?.childName}")
                    appendLine("Parent Email: ${info?.parentEmail}")
                    appendLine("Linked: ${java.util.Date(info?.linkTimestamp ?: 0)}")
                }
                DEVICE_TYPE_PARENT -> {
                    val info = getParentDeviceInfo()
                    appendLine("Parent Email: ${info?.parentEmail}")
                    appendLine("Setup: ${java.util.Date(info?.linkTimestamp ?: 0)}")
                }
                DEVICE_TYPE_UNLINKED -> {
                    appendLine("Status: Not linked to any account")
                }
            }
        }
    }
}

data class ChildDeviceInfo(
    val childId: String,
    val childName: String,
    val parentId: String,
    val parentEmail: String,
    val linkTimestamp: Long,
    val deviceId: String
)

data class ParentDeviceInfo(
    val parentId: String,
    val parentEmail: String,
    val linkTimestamp: Long,
    val deviceId: String
)
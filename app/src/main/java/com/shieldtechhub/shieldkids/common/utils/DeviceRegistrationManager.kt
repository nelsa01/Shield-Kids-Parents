package com.shieldtechhub.shieldkids.common.utils

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.features.core.ShieldKidsApplication
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeviceRegistrationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceRegistration"
        
        @Volatile
        private var INSTANCE: DeviceRegistrationManager? = null
        
        fun getInstance(context: Context): DeviceRegistrationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceRegistrationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val db = FirebaseFirestore.getInstance()
    private val deviceStateManager = DeviceStateManager(context)
    
    /**
     * Ensure device document exists in Firebase before syncing data
     */
    suspend fun ensureDeviceDocumentExists(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if Firebase is ready
            val app = ShieldKidsApplication.getInstance()
            if (app == null || !app.isFirebaseReady()) {
                Log.w(TAG, "Firebase not ready, cannot ensure device document")
                return@withContext false
            }
            
            // Only proceed for child devices
            if (!deviceStateManager.isChildDevice()) {
                Log.d(TAG, "Not a child device, no need to create device document")
                return@withContext true
            }
            
            val childInfo = deviceStateManager.getChildDeviceInfo()
            if (childInfo == null) {
                Log.e(TAG, "Child device info not found, cannot create device document")
                return@withContext false
            }
            
            val deviceDocId = "device_${childInfo.deviceId}"
            val deviceRef = db.collection("children")
                .document(childInfo.childId)
                .collection("devices")
                .document(deviceDocId)
            
            // Check if device document exists
            val deviceDoc = deviceRef.get().await()
            
            if (!deviceDoc.exists()) {
                Log.i(TAG, "Device document does not exist, creating: $deviceDocId")
                return@withContext createDeviceDocument(childInfo.childId, childInfo.deviceId)
            } else {
                Log.d(TAG, "Device document already exists: $deviceDocId")
                return@withContext true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure device document exists", e)
            return@withContext false
        }
    }
    
    /**
     * Create device document with all required fields
     */
    private suspend fun createDeviceDocument(childId: String, deviceId: String): Boolean {
        try {
            val deviceDocId = "device_$deviceId"
            
            val deviceData = mapOf(
                "deviceId" to deviceId,
                "deviceModel" to android.os.Build.MODEL,
                "androidVersion" to android.os.Build.VERSION.RELEASE,
                "deviceName" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                "brand" to android.os.Build.BRAND,
                "manufacturer" to android.os.Build.MANUFACTURER,
                "sdkVersion" to android.os.Build.VERSION.SDK_INT,
                "createdAt" to System.currentTimeMillis(),
                "lastSeen" to System.currentTimeMillis(),
                "status" to "active",
                "appSyncStatus" to "INITIAL",
                "lastAppSyncTime" to null,
                "lastPolicyUpdateTime" to null,
                "lastScreenTimeSyncTime" to null,
                "totalAppsCount" to 0,
                "appInventoryHash" to "",
                "isOnline" to true
            )
            
            val deviceRef = db.collection("children")
                .document(childId)
                .collection("devices")
                .document(deviceDocId)
            
            deviceRef.set(deviceData).await()
            
            Log.i(TAG, "✅ Successfully created device document: $deviceDocId")
            
            // Also update the parent devices field for backward compatibility
            updateParentDevicesField(childId, deviceId, deviceData as Map<String, Any>)
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create device document", e)
            return false
        }
    }
    
    /**
     * Update parent devices field for backward compatibility
     */
    private suspend fun updateParentDevicesField(childId: String, deviceId: String, deviceData: Map<String, Any>) {
        try {
            val childRef = db.collection("children").document(childId)
            val deviceInfo = mapOf(
                "deviceId" to deviceId,
                "deviceModel" to deviceData["deviceModel"],
                "androidVersion" to deviceData["androidVersion"],
                "deviceName" to deviceData["deviceName"],
                "linkTimestamp" to deviceData["createdAt"],
                "lastSeen" to System.currentTimeMillis()
            )
            
            childRef.update("devices.$deviceId", deviceInfo).await()
            Log.d(TAG, "Updated parent devices field for backward compatibility")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update parent devices field (not critical)", e)
        }
    }
    
    /**
     * Update device last seen timestamp
     */
    suspend fun updateDeviceHeartbeat(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!deviceStateManager.isChildDevice()) return@withContext true
            
            val childInfo = deviceStateManager.getChildDeviceInfo() ?: return@withContext false
            val deviceDocId = "device_${childInfo.deviceId}"
            
            val deviceRef = db.collection("children")
                .document(childInfo.childId)
                .collection("devices")
                .document(deviceDocId)
            
            deviceRef.update(
                mapOf(
                    "lastSeen" to System.currentTimeMillis(),
                    "isOnline" to true
                )
            ).await()
            
            return@withContext true
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update device heartbeat", e)
            return@withContext false
        }
    }
    
    /**
     * Check if device is properly registered
     */
    suspend fun isDeviceRegistered(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!deviceStateManager.isChildDevice()) return@withContext true
            
            val childInfo = deviceStateManager.getChildDeviceInfo() ?: return@withContext false
            val deviceDocId = "device_${childInfo.deviceId}"
            
            val deviceRef = db.collection("children")
                .document(childInfo.childId)
                .collection("devices")
                .document(deviceDocId)
            
            val deviceDoc = deviceRef.get().await()
            return@withContext deviceDoc.exists()
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check device registration", e)
            return@withContext false
        }
    }
    
    /**
     * Get device registration status for debugging
     */
    suspend fun getDeviceRegistrationStatus(): Map<String, Any> = withContext(Dispatchers.IO) {
        return@withContext try {
            val childInfo = deviceStateManager.getChildDeviceInfo()
            val isChildDevice = deviceStateManager.isChildDevice()
            
            if (!isChildDevice || childInfo == null) {
                mapOf(
                    "isChildDevice" to isChildDevice,
                    "childInfo" to (childInfo != null),
                    "registered" to false,
                    "error" to "Not a child device or missing child info"
                )
            } else {
                val deviceDocId = "device_${childInfo.deviceId}"
                val deviceRef = db.collection("children")
                    .document(childInfo.childId)
                    .collection("devices")
                    .document(deviceDocId)
                
                val deviceDoc = deviceRef.get().await()
                
                mapOf(
                    "isChildDevice" to true,
                    "childId" to childInfo.childId,
                    "deviceId" to childInfo.deviceId,
                    "deviceDocId" to deviceDocId,
                    "registered" to deviceDoc.exists(),
                    "documentData" to (deviceDoc.data ?: emptyMap<String, Any>()),
                    "lastModified" to System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            mapOf(
                "error" to (e.message ?: "Unknown error"),
                "registered" to false
            )
        }
    }
}
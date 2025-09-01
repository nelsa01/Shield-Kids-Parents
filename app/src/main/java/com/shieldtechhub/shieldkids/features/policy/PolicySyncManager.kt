package com.shieldtechhub.shieldkids.features.policy

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PolicySyncManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PolicySyncManager"
        
        @Volatile
        private var INSTANCE: PolicySyncManager? = null
        
        fun getInstance(context: Context): PolicySyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PolicySyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val db = FirebaseFirestore.getInstance()
    private val deviceStateManager = DeviceStateManager(context)
    private val policyEnforcementManager = PolicyEnforcementManager.getInstance(context)
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var policyListener: ListenerRegistration? = null
    
    /**
     * Save policy to Firebase (called from parent device)
     */
    suspend fun savePolicyToFirebase(childId: String, deviceId: String, policy: DevicePolicy): Boolean {
        return try {
            Log.d(TAG, "Saving policy to Firebase for child: $childId, device: $deviceId")
            
            val policyData = mapOf(
                "policy" to policy.toJson(),
                "deviceId" to deviceId,
                "childId" to childId,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to policy.updatedAt,
                "version" to System.currentTimeMillis(), // Version for conflict resolution
                "createdBy" to "parent", // Track who created the policy
                "status" to "active"
            )
            
            // Save to multiple locations for redundancy and easy access
            
            // 1. Save to device-specific policies collection
            db.collection("policies")
                .document(deviceId)
                .set(policyData)
                .await()
            
            // 2. Save to child's device collection for backup
            val deviceDocId = if (deviceId.startsWith("device_")) deviceId else "device_$deviceId"
            
            // First ensure the device document exists
            val deviceDocRef = db.collection("children")
                .document(childId)
                .collection("devices")
                .document(deviceDocId)
            
            // Create or update the main device document with basic info
            val deviceData = mapOf(
                "deviceId" to deviceId.removePrefix("device_"),
                "lastUpdated" to System.currentTimeMillis(),
                "status" to "active",
                "policyApplied" to true
            )
            
            deviceDocRef.set(deviceData, com.google.firebase.firestore.SetOptions.merge()).await()
            Log.d(TAG, "Created/updated device document: $deviceDocId")
            
            // Then save the policy in the subcollection
            deviceDocRef.collection("data")
                .document("policy")
                .set(policyData)
                .await()
            
            Log.i(TAG, "Policy successfully saved to Firebase")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save policy to Firebase", e)
            false
        }
    }
    
    /**
     * Start listening for policy updates (called from child device)
     */
    fun startPolicySync() {
        if (!deviceStateManager.isChildDevice()) {
            Log.w(TAG, "Policy sync should only run on child devices")
            return
        }
        
        val childInfo = deviceStateManager.getChildDeviceInfo()
        if (childInfo == null) {
            Log.e(TAG, "Cannot start policy sync - child device info not found")
            return
        }
        
        Log.i(TAG, "Starting policy sync for device: ${childInfo.deviceId}")
        
        // Listen for policy changes
        policyListener = db.collection("policies")
            .document(childInfo.deviceId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening for policy updates", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null && snapshot.exists()) {
                    serviceScope.launch {
                        handlePolicyUpdate(snapshot.data)
                    }
                } else {
                    Log.d(TAG, "No policy found for this device")
                }
            }
    }
    
    /**
     * Stop policy sync listener
     */
    fun stopPolicySync() {
        policyListener?.remove()
        policyListener = null
        Log.i(TAG, "Stopped policy sync listener")
    }
    
    /**
     * Handle incoming policy update from Firebase
     */
    private suspend fun handlePolicyUpdate(policyData: Map<String, Any>?) {
        if (policyData == null) return
        
        try {
            val policyJson = policyData["policy"] as? String
            val deviceId = policyData["deviceId"] as? String
            val version = policyData["version"] as? Long
            val status = policyData["status"] as? String
            
            if (policyJson == null || deviceId == null) {
                Log.e(TAG, "Invalid policy data received")
                return
            }
            
            if (status != "active") {
                Log.d(TAG, "Ignoring inactive policy")
                return
            }
            
            Log.d(TAG, "Received policy update for device: $deviceId, version: $version")
            
            // Check if this is a newer version than what we have locally
            if (shouldApplyPolicyUpdate(deviceId, version)) {
                val policy = DevicePolicy.fromJson(policyJson)
                
                // Apply the policy locally
                val success = policyEnforcementManager.applyDevicePolicy(deviceId, policy)
                
                if (success) {
                    Log.i(TAG, "Successfully applied policy update")
                    
                    // Save the version locally to avoid reprocessing
                    savePolicyVersion(deviceId, version)
                    
                    // Acknowledge receipt back to Firebase
                    acknowledgePolicyReceipt(deviceId, version)
                } else {
                    Log.e(TAG, "Failed to apply policy update")
                    reportPolicyError(deviceId, version, "Failed to apply policy locally")
                }
            } else {
                Log.d(TAG, "Policy update skipped - not newer than current version")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling policy update", e)
            
            val deviceId = policyData["deviceId"] as? String
            val version = policyData["version"] as? Long
            if (deviceId != null && version != null) {
                reportPolicyError(deviceId, version, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Check if we should apply this policy update
     */
    private fun shouldApplyPolicyUpdate(deviceId: String, newVersion: Long?): Boolean {
        if (newVersion == null) return true
        
        val prefs = context.getSharedPreferences("policy_sync", Context.MODE_PRIVATE)
        val currentVersion = prefs.getLong("policy_version_$deviceId", 0)
        
        return newVersion > currentVersion
    }
    
    /**
     * Save the policy version locally
     */
    private fun savePolicyVersion(deviceId: String, version: Long?) {
        if (version == null) return
        
        val prefs = context.getSharedPreferences("policy_sync", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("policy_version_$deviceId", version)
            .putLong("policy_applied_at_$deviceId", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Acknowledge policy receipt back to Firebase
     */
    private suspend fun acknowledgePolicyReceipt(deviceId: String, version: Long?) {
        try {
            val ackData = mapOf(
                "acknowledgedAt" to System.currentTimeMillis(),
                "acknowledgedVersion" to version,
                "deviceStatus" to "policy_applied"
            )
            
            db.collection("policies")
                .document(deviceId)
                .update(ackData)
                .await()
            
            Log.d(TAG, "Policy receipt acknowledged")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acknowledge policy receipt", e)
        }
    }
    
    /**
     * Report policy application error back to Firebase
     */
    private suspend fun reportPolicyError(deviceId: String, version: Long?, error: String) {
        try {
            val errorData = mapOf(
                "lastError" to error,
                "lastErrorAt" to System.currentTimeMillis(),
                "failedVersion" to version,
                "deviceStatus" to "policy_error"
            )
            
            db.collection("policies")
                .document(deviceId)
                .update(errorData)
                .await()
            
            Log.w(TAG, "Policy error reported to Firebase: $error")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report policy error", e)
        }
    }
    
    /**
     * Fetch current policy from Firebase (for manual sync)
     */
    suspend fun fetchCurrentPolicy(deviceId: String): DevicePolicy? {
        return try {
            val snapshot = db.collection("policies")
                .document(deviceId)
                .get()
                .await()
            
            if (snapshot.exists()) {
                val policyJson = snapshot.getString("policy")
                val status = snapshot.getString("status")
                
                if (policyJson != null && status == "active") {
                    DevicePolicy.fromJson(policyJson)
                } else null
            } else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch current policy", e)
            null
        }
    }
    
    /**
     * Delete policy from Firebase (called when removing device)
     */
    suspend fun deletePolicyFromFirebase(childId: String, deviceId: String): Boolean {
        return try {
            // Delete from policies collection
            db.collection("policies")
                .document(deviceId)
                .delete()
                .await()
            
            // Delete from child's device collection
            val deviceDocId = if (deviceId.startsWith("device_")) deviceId else "device_$deviceId"
            db.collection("children")
                .document(childId)
                .collection("devices")
                .document(deviceDocId)
                .collection("data")
                .document("policy")
                .delete()
                .await()
            
            Log.i(TAG, "Policy deleted from Firebase")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete policy from Firebase", e)
            false
        }
    }
    
    /**
     * Get policy sync status for debugging
     */
    fun getPolicySyncStatus(deviceId: String): Map<String, Any> {
        val prefs = context.getSharedPreferences("policy_sync", Context.MODE_PRIVATE)
        
        return mapOf(
            "isListening" to (policyListener != null),
            "currentVersion" to prefs.getLong("policy_version_$deviceId", 0),
            "lastAppliedAt" to prefs.getLong("policy_applied_at_$deviceId", 0),
            "deviceId" to deviceId,
            "isChildDevice" to deviceStateManager.isChildDevice()
        )
    }
    
    /**
     * Cleanup resources
     */
    fun destroy() {
        stopPolicySync()
        serviceScope.cancel()
        INSTANCE = null
    }
}
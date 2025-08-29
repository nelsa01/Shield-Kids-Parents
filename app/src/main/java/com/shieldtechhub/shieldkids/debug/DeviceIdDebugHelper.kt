package com.shieldtechhub.shieldkids.debug

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object DeviceIdDebugHelper {
    private const val TAG = "DeviceIdDebug"
    
    /**
     * Debug device ID mismatch by listing all actual device IDs in Firebase
     */
    suspend fun debugDeviceIds(childId: String): String {
        val debugLog = StringBuilder()
        val db = FirebaseFirestore.getInstance()
        
        fun log(message: String) {
            val logMessage = "$TAG: $message"
            Log.d(TAG, message)
            debugLog.appendLine(logMessage)
        }
        
        try {
            log("🔍 Debugging Device IDs for child: $childId")
            log("=" + "=".repeat(49))
            
            // List all devices under this child
            val devicesSnapshot = db.collection("children")
                .document(childId)
                .collection("devices")
                .get()
                .await()
                
            if (devicesSnapshot.isEmpty) {
                log("❌ NO DEVICES FOUND in Firebase!")
                log("   📍 Path checked: children/$childId/devices")
                log("   💡 This means child device has never synced data")
            } else {
                log("✅ Found ${devicesSnapshot.documents.size} devices in Firebase:")
                
                devicesSnapshot.documents.forEachIndexed { index, deviceDoc ->
                    log("")
                    log("📱 Device #${index + 1}:")
                    log("   🆔 Device ID: '${deviceDoc.id}' (length: ${deviceDoc.id.length})")
                    log("   📊 Device data keys: ${deviceDoc.data?.keys}")
                    
                    // Check if this device has data collection
                    val dataSnapshot = deviceDoc.reference.collection("data").get().await()
                    if (dataSnapshot.isEmpty) {
                        log("   ❌ No data documents found")
                    } else {
                        log("   ✅ Found ${dataSnapshot.documents.size} data documents:")
                        dataSnapshot.documents.forEach { dataDoc ->
                            log("     📄 ${dataDoc.id}")
                        }
                    }
                    
                    // Check device metadata
                    val deviceData = deviceDoc.data
                    deviceData?.forEach { (key, value) ->
                        when (key) {
                            "deviceName", "deviceType" -> {
                                log("   📋 $key: $value")
                            }
                            "lastAppSyncTime", "lastSyncTime" -> {
                                val timestamp = value as? Long
                                if (timestamp != null) {
                                    val date = java.util.Date(timestamp)
                                    val minutesAgo = (System.currentTimeMillis() - timestamp) / (1000 * 60)
                                    log("   🕒 $key: $date ($minutesAgo min ago)")
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            log("💥 Exception during device ID debug: ${e.message}")
        }
        
        return debugLog.toString()
    }
    
    /**
     * Get actual device IDs from Firebase
     */
    suspend fun getActualDeviceIds(childId: String): List<String> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val devicesSnapshot = db.collection("children")
                .document(childId)
                .collection("devices")
                .get()
                .await()
                
            devicesSnapshot.documents.map { it.id }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get actual device IDs", e)
            emptyList()
        }
    }
}
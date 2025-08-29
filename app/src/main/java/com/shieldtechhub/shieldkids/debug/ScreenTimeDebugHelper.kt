package com.shieldtechhub.shieldkids.debug

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object ScreenTimeDebugHelper {
    private const val TAG = "ScreenTimeDebug"
    
    /**
     * Comprehensive debug of Firebase structure and screen time data retrieval
     */
    suspend fun debugFirebaseScreenTimeAccess(childId: String, deviceId: String): String {
        val debugLog = StringBuilder()
        val db = FirebaseFirestore.getInstance()
        
        fun log(message: String) {
            val logMessage = "$TAG: $message"
            Log.d(TAG, message)
            debugLog.appendLine(logMessage)
        }
        
        try {
            log("🚀 Starting Firebase Screen Time Debug")
            log("=" + "=".repeat(49))
            log("📋 Input Parameters:")
            log("   👶 Child ID: '$childId' (length: ${childId.length})")
            log("   📱 Device ID: '$deviceId' (length: ${deviceId.length})")
            log("   🕒 Timestamp: ${System.currentTimeMillis()}")
            log("")
            
            // Step 1: Check if child document exists
            log("🔍 Step 1: Checking if child document exists...")
            val childDocRef = db.collection("children").document(childId)
            val childSnapshot = childDocRef.get().await()
            
            if (childSnapshot.exists()) {
                log("✅ Child document EXISTS")
                val childData = childSnapshot.data
                log("   📊 Child document keys: ${childData?.keys}")
                log("   📅 Child document size: ${childData?.size} fields")
            } else {
                log("❌ Child document DOES NOT EXIST")
                log("   📍 Path checked: children/$childId")
                return debugLog.toString()
            }
            
            // Step 2: Check devices collection
            log("")
            log("🔍 Step 2: Checking devices collection...")
            val devicesCollectionRef = childDocRef.collection("devices")
            val devicesSnapshot = devicesCollectionRef.get().await()
            
            if (!devicesSnapshot.isEmpty) {
                log("✅ Devices collection EXISTS with ${devicesSnapshot.documents.size} devices")
                devicesSnapshot.documents.forEach { deviceDoc ->
                    log("   📱 Found device: '${deviceDoc.id}'")
                    if (deviceDoc.id == deviceId) {
                        log("   ✅ TARGET DEVICE FOUND!")
                    }
                }
            } else {
                log("❌ Devices collection is EMPTY")
                return debugLog.toString()
            }
            
            // Step 3: Check specific device document
            log("")
            log("🔍 Step 3: Checking target device document...")
            val deviceDocRef = devicesCollectionRef.document(deviceId)
            val deviceSnapshot = deviceDocRef.get().await()
            
            if (deviceSnapshot.exists()) {
                log("✅ Target device document EXISTS")
                val deviceData = deviceSnapshot.data
                log("   📊 Device document keys: ${deviceData?.keys}")
                log("   📅 Device document size: ${deviceData?.size} fields")
                
                // Log some key device info
                deviceData?.forEach { (key, value) ->
                    when (key) {
                        "lastAppSyncTime", "lastSyncTime" -> {
                            val timestamp = value as? Long
                            if (timestamp != null) {
                                val date = java.util.Date(timestamp)
                                log("   🕒 $key: $date ($timestamp)")
                            }
                        }
                        "appSyncStatus", "deviceName", "deviceType" -> {
                            log("   📋 $key: $value")
                        }
                    }
                }
            } else {
                log("❌ Target device document DOES NOT EXIST")
                log("   📍 Path checked: children/$childId/devices/$deviceId")
                return debugLog.toString()
            }
            
            // Step 4: Check data collection
            log("")
            log("🔍 Step 4: Checking data collection...")
            val dataCollectionRef = deviceDocRef.collection("data")
            val dataSnapshot = dataCollectionRef.get().await()
            
            if (!dataSnapshot.isEmpty) {
                log("✅ Data collection EXISTS with ${dataSnapshot.documents.size} documents")
                dataSnapshot.documents.forEach { dataDoc ->
                    log("   📄 Found data document: '${dataDoc.id}'")
                    val docSize = dataDoc.data?.size ?: 0
                    log("   📊 Document size: $docSize fields")
                    
                    if (dataDoc.id == "appInventory") {
                        log("   🎯 Found APP INVENTORY document!")
                    }
                    if (dataDoc.id.startsWith("screen_time_")) {
                        log("   🕒 Found SCREEN TIME document!")
                    }
                }
            } else {
                log("❌ Data collection is EMPTY")
                return debugLog.toString()
            }
            
            // Step 5: Check app inventory document specifically
            log("")
            log("🔍 Step 5: Checking appInventory document...")
            val appInventoryRef = dataCollectionRef.document("appInventory")
            val appInventorySnapshot = appInventoryRef.get().await()
            
            if (appInventorySnapshot.exists()) {
                log("✅ AppInventory document EXISTS")
                val appInventoryData = appInventorySnapshot.data
                log("   📊 AppInventory top-level keys: ${appInventoryData?.keys}")
                
                // Check for screen time data specifically
                val screenTimeData = appInventoryData?.get("screenTime")
                if (screenTimeData != null) {
                    log("   🎯 SCREEN TIME DATA FOUND!")
                    
                    if (screenTimeData is Map<*, *>) {
                        log("   📊 Screen time keys: ${screenTimeData.keys}")
                        
                        val todayData = screenTimeData["today"]
                        if (todayData != null && todayData is Map<*, *>) {
                            log("   📅 TODAY DATA FOUND!")
                            log("   📊 Today data keys: ${todayData.keys}")
                            
                            // Log key metrics
                            todayData.forEach { (key, value) ->
                                when (key) {
                                    "totalScreenTimeMs" -> {
                                        val timeMs = value as? Long ?: 0
                                        val timeFormatted = formatDuration(timeMs)
                                        log("   ⏱️ Total screen time: $timeFormatted ($timeMs ms)")
                                    }
                                    "screenUnlocks" -> {
                                        log("   🔓 Screen unlocks: $value")
                                    }
                                    "topApps" -> {
                                        val apps = value as? List<*>
                                        log("   📱 Top apps count: ${apps?.size}")
                                        apps?.take(3)?.forEach { app ->
                                            if (app is Map<*, *>) {
                                                val appName = app["appName"]
                                                val timeMs = app["totalTimeMs"] as? Long ?: 0
                                                log("     - $appName: ${formatDuration(timeMs)}")
                                            }
                                        }
                                    }
                                    "categoryBreakdown" -> {
                                        val categories = value as? Map<*, *>
                                        log("   📊 Categories: ${categories?.keys}")
                                    }
                                }
                            }
                        } else {
                            log("   ❌ TODAY DATA NOT FOUND in screen time")
                        }
                    } else {
                        log("   ❌ Screen time data is not a map: ${screenTimeData::class.java}")
                    }
                } else {
                    log("   ❌ NO SCREEN TIME DATA in appInventory")
                }
                
                // Check last sync time
                val lastSyncTime = appInventoryData?.get("lastSyncTime") as? Long
                if (lastSyncTime != null) {
                    val syncDate = java.util.Date(lastSyncTime)
                    val minutesAgo = (System.currentTimeMillis() - lastSyncTime) / (1000 * 60)
                    log("   🕒 Last sync: $syncDate ($minutesAgo minutes ago)")
                }
                
            } else {
                log("❌ AppInventory document DOES NOT EXIST")
                log("   📍 Path checked: children/$childId/devices/$deviceId/data/appInventory")
            }
            
            // Step 6: Test the actual retrieval method
            log("")
            log("🔍 Step 6: Testing actual retrieval method...")
            try {
                val retrievedData = getScreenTimeFromAppInventoryDebug(childId, deviceId)
                if (retrievedData != null) {
                    log("✅ Retrieval method SUCCESS")
                    log("   📊 Retrieved keys: ${retrievedData.keys}")
                } else {
                    log("❌ Retrieval method returned NULL")
                }
            } catch (e: Exception) {
                log("💥 Retrieval method EXCEPTION: ${e.message}")
                log("   🔍 Exception type: ${e::class.java.simpleName}")
                log("   📍 Stack trace: ${e.stackTrace.take(3).joinToString(", ")}")
            }
            
            log("")
            log("🏁 Debug Complete!")
            log("=" + "=".repeat(49))
            
        } catch (e: Exception) {
            log("💥 DEBUG EXCEPTION: ${e.message}")
            log("   🔍 Exception type: ${e::class.java.simpleName}")
        }
        
        return debugLog.toString()
    }
    
    private suspend fun getScreenTimeFromAppInventoryDebug(childId: String, deviceId: String): Map<String, Any>? {
        val db = FirebaseFirestore.getInstance()
        
        val snapshot = db.collection("children")
            .document(childId)
            .collection("devices")
            .document(deviceId)
            .collection("data")
            .document("appInventory")
            .get()
            .await()
        
        if (snapshot.exists()) {
            val data = snapshot.data
            val screenTimeData = data?.get("screenTime") as? Map<String, Any>
            return screenTimeData?.get("today") as? Map<String, Any>
        }
        
        return null
    }
    
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
}
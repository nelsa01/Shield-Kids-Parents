package com.shieldtechhub.shieldkids.debug

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.features.core.ShieldKidsApplication
import com.shieldtechhub.shieldkids.common.utils.DeviceRegistrationManager
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import kotlinx.coroutines.launch

class FirebaseDebugActivity : AppCompatActivity() {
    
    private lateinit var debugOutput: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_test) // Reuse existing layout
        
        supportActionBar?.title = "Firebase Debug"
        
        debugOutput = findViewById(R.id.tvTestResults)
        
        runFirebaseDebugging()
    }
    
    private fun runFirebaseDebugging() {
        lifecycleScope.launch {
            val output = StringBuilder()
            
            output.appendLine("🔥 FIREBASE DEBUG REPORT")
            output.appendLine("=" .repeat(50))
            output.appendLine()
            
            // 1. Application Status
            output.appendLine("📱 APPLICATION STATUS:")
            val app = ShieldKidsApplication.getInstance()
            if (app != null) {
                output.appendLine("  ✅ Application instance available")
                output.appendLine("  🔥 Firebase ready: ${app.isFirebaseReady()}")
                
                val firebaseStatus = app.getFirebaseStatus()
                firebaseStatus.forEach { (key, value) ->
                    output.appendLine("  📊 $key: $value")
                }
            } else {
                output.appendLine("  ❌ Application instance not available")
            }
            output.appendLine()
            
            // 2. Device State
            output.appendLine("📱 DEVICE STATE:")
            val deviceStateManager = DeviceStateManager(this@FirebaseDebugActivity)
            output.appendLine("  🔹 Is child device: ${deviceStateManager.isChildDevice()}")
            output.appendLine("  🔹 Is parent device: ${deviceStateManager.isParentDevice()}")
            
            val childInfo = deviceStateManager.getChildDeviceInfo()
            if (childInfo != null) {
                output.appendLine("  👶 Child ID: ${childInfo.childId}")
                output.appendLine("  📱 Device ID: ${childInfo.deviceId}")
                output.appendLine("  👨‍👩‍👧‍👦 Parent ID: ${childInfo.parentId}")
            } else {
                output.appendLine("  ❌ No child device info available")
            }
            output.appendLine()
            
            // 3. Device Registration Status
            output.appendLine("📋 DEVICE REGISTRATION:")
            try {
                val registrationManager = DeviceRegistrationManager.getInstance(this@FirebaseDebugActivity)
                val registrationStatus = registrationManager.getDeviceRegistrationStatus()
                
                registrationStatus.forEach { (key, value) ->
                    when (key) {
                        "registered" -> output.appendLine("  📝 Device registered: ${if (value as Boolean) "✅ YES" else "❌ NO"}")
                        "error" -> output.appendLine("  ❌ Error: $value")
                        "childId" -> output.appendLine("  👶 Child ID: $value")
                        "deviceId" -> output.appendLine("  📱 Device ID: $value")
                        "deviceDocId" -> output.appendLine("  📄 Document ID: $value")
                        "documentData" -> {
                            if (value is Map<*, *> && value.isNotEmpty()) {
                                output.appendLine("  📊 Document fields: ${value.keys.joinToString(", ")}")
                            }
                        }
                        "lastModified" -> {
                            if ((value as Long) > 0) {
                                val date = java.util.Date(value * 1000)
                                output.appendLine("  🕒 Last modified: $date")
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                output.appendLine("  ❌ Failed to check registration: ${e.message}")
            }
            output.appendLine()
            
            // 4. Auto-Fix Attempt
            output.appendLine("🔧 AUTO-FIX ATTEMPT:")
            try {
                val registrationManager = DeviceRegistrationManager.getInstance(this@FirebaseDebugActivity)
                
                if (deviceStateManager.isChildDevice()) {
                    output.appendLine("  🔄 Attempting to ensure device document exists...")
                    val success = registrationManager.ensureDeviceDocumentExists()
                    
                    if (success) {
                        output.appendLine("  ✅ Device document creation/check successful")
                        
                        // Update heartbeat
                        val heartbeatSuccess = registrationManager.updateDeviceHeartbeat()
                        output.appendLine("  💓 Heartbeat update: ${if (heartbeatSuccess) "✅ SUCCESS" else "❌ FAILED"}")
                        
                        // Re-check registration
                        val isNowRegistered = registrationManager.isDeviceRegistered()
                        output.appendLine("  📝 Device now registered: ${if (isNowRegistered) "✅ YES" else "❌ NO"}")
                        
                    } else {
                        output.appendLine("  ❌ Failed to ensure device document exists")
                    }
                } else {
                    output.appendLine("  ⏭️ Not a child device, auto-fix not needed")
                }
                
            } catch (e: Exception) {
                output.appendLine("  ❌ Auto-fix failed: ${e.message}")
            }
            output.appendLine()
            
            // 5. Recommendations
            output.appendLine("💡 RECOMMENDATIONS:")
            if (app == null || !app.isFirebaseReady()) {
                output.appendLine("  🔥 Restart the app to initialize Firebase")
            }
            
            if (deviceStateManager.isChildDevice() && childInfo == null) {
                output.appendLine("  🔗 Re-link this device using the parent's reference code")
            }
            
            if (deviceStateManager.isChildDevice()) {
                output.appendLine("  📊 Check parent-side app for device data")
                output.appendLine("  🔄 Try manual sync from device settings")
            }
            
            output.appendLine()
            output.appendLine("🏁 DEBUG COMPLETE")
            
            debugOutput.text = output.toString()
        }
    }
}
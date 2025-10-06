package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shieldtechhub.shieldkids.databinding.ActivityPolicyDebugBinding
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import com.shieldtechhub.shieldkids.features.app_blocking.ShieldAccessibilityService
import com.shieldtechhub.shieldkids.features.app_blocking.AppBlockingManager
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import kotlinx.coroutines.launch

class PolicyDebugActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPolicyDebugBinding
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var policySyncManager: PolicySyncManager
    private lateinit var appBlockingManager: AppBlockingManager
    private lateinit var appInventoryManager: AppInventoryManager
    private lateinit var deviceStateManager: DeviceStateManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPolicyDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize managers
        policyManager = PolicyEnforcementManager.getInstance(this)
        policySyncManager = PolicySyncManager.getInstance(this)
        appBlockingManager = AppBlockingManager.getInstance(this)
        appInventoryManager = AppInventoryManager(this)
        deviceStateManager = DeviceStateManager(this)
        
        setupUI()
        loadDebugInfo()
    }
    
    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.btnDebugChrome.setOnClickListener {
            debugChromeBlocking()
        }
        
        binding.btnDebugBlocking.setOnClickListener {
            debugAppBlockingIssue()
        }
        
        binding.btnTestAccessibility.setOnClickListener {
            testAccessibilityService()
        }
        
        binding.btnTestOverlay.setOnClickListener {
            testBlockingOverlay()
        }
        
        binding.btnRefreshDebug.setOnClickListener {
            loadDebugInfo()
        }
    }
    
    private fun loadDebugInfo() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val debugInfo = StringBuilder()
                
                // Device State
                debugInfo.appendLine("=== DEVICE STATE ===")
                debugInfo.appendLine("Device Type: ${deviceStateManager.getDeviceType()}")
                debugInfo.appendLine("Device ID: ${deviceStateManager.getDeviceId()}")
                debugInfo.appendLine()
                
                // Accessibility Service Status
                debugInfo.appendLine("=== ACCESSIBILITY SERVICE ===")
                val isAccessibilityEnabled = ShieldAccessibilityService.isServiceEnabled(this@PolicyDebugActivity)
                debugInfo.appendLine("Enabled: $isAccessibilityEnabled")
                debugInfo.appendLine()
                
                // Overlay Permission
                debugInfo.appendLine("=== OVERLAY PERMISSION ===")
                val hasOverlayPermission = appBlockingManager.hasOverlayPermission()
                debugInfo.appendLine("Granted: $hasOverlayPermission")
                debugInfo.appendLine()
                
                // Chrome App Info
                debugInfo.appendLine("=== CHROME APP INFO ===")
                val chromePackages = listOf(
                    "com.google.android.apps.chrome",
                    "com.android.chrome",
                    "com.chrome.beta",
                    "com.chrome.canary"
                )
                
                for (packageName in chromePackages) {
                    val appInfo = appInventoryManager.getAppInfo(packageName)
                    if (appInfo != null) {
                        debugInfo.appendLine("Package: $packageName")
                        debugInfo.appendLine("Name: ${appInfo.name}")
                        debugInfo.appendLine("Category: ${appInfo.category}")
                        debugInfo.appendLine("System App: ${appInfo.isSystemApp}")
                        debugInfo.appendLine("Enabled: ${appInfo.isEnabled}")
                        debugInfo.appendLine()
                        break
                    }
                }
                
                // Policy Status
                debugInfo.appendLine("=== POLICY STATUS ===")
                val activePolicies = policyManager.activePolicies.value
                debugInfo.appendLine("Active Policies: ${activePolicies.size}")
                
                for (packageName in chromePackages) {
                    val isBlocked = policyManager.isAppBlocked(packageName)
                    val blockReason = policyManager.getAppBlockReason(packageName)
                    debugInfo.appendLine("$packageName blocked: $isBlocked")
                    if (blockReason != null) {
                        debugInfo.appendLine("Block reason: $blockReason")
                    }
                }
                debugInfo.appendLine()
                
                // Policy Sync Status
                if (deviceStateManager.isChildDevice()) {
                    val childInfo = deviceStateManager.getChildDeviceInfo()
                    if (childInfo != null) {
                        debugInfo.appendLine("=== POLICY SYNC STATUS ===")
                        val syncStatus = policySyncManager.getPolicySyncStatus(childInfo.deviceId)
                        syncStatus.forEach { (key, value) ->
                            debugInfo.appendLine("$key: $value")
                        }
                    }
                }
                
                binding.tvDebugInfo.text = debugInfo.toString()
                
            } catch (e: Exception) {
                binding.tvDebugInfo.text = "Error loading debug info: ${e.message}"
                Log.e("PolicyDebug", "Error loading debug info", e)
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun debugAppBlockingIssue() {
        lifecycleScope.launch {
            try {
                val debugInfo = StringBuilder()
                debugInfo.appendLine("=== APP BLOCKING AUTO-DISABLE DEBUG ===")
                
                // 1. Check device type
                val deviceType = deviceStateManager.getDeviceType()
                debugInfo.appendLine("Device Type: $deviceType")
                debugInfo.appendLine("Is Child Device: ${deviceStateManager.isChildDevice()}")
                debugInfo.appendLine("Is Parent Device: ${deviceStateManager.isParentDevice()}")
                debugInfo.appendLine("Device ID: ${deviceStateManager.getDeviceId()}")
                debugInfo.appendLine()
                
                // 2. Check policy versions and sync
                if (deviceStateManager.isChildDevice()) {
                    val childInfo = deviceStateManager.getChildDeviceInfo()
                    if (childInfo != null) {
                        debugInfo.appendLine("=== POLICY SYNC STATUS ===")
                        val syncStatus = policySyncManager.getPolicySyncStatus(childInfo.deviceId)
                        syncStatus.forEach { (key, value) ->
                            debugInfo.appendLine("$key: $value")
                        }
                        debugInfo.appendLine()
                        
                        // 3. Check Firebase policy vs local policy
                        debugInfo.appendLine("=== FIREBASE VS LOCAL POLICY ===")
                        val firebasePolicy = policySyncManager.fetchCurrentPolicy(childInfo.deviceId)
                        val localPolicies = policyManager.activePolicies.value
                        val localPolicy = localPolicies[childInfo.deviceId]
                        
                        debugInfo.appendLine("Firebase Policy Exists: ${firebasePolicy != null}")
                        debugInfo.appendLine("Local Policy Exists: ${localPolicy != null}")
                        
                        if (firebasePolicy != null && localPolicy != null) {
                            debugInfo.appendLine("Firebase Policy Updated: ${java.util.Date(firebasePolicy.updatedAt)}")
                            debugInfo.appendLine("Local Policy Updated: ${java.util.Date(localPolicy.updatedAt)}")
                            debugInfo.appendLine("Policies Match: ${firebasePolicy == localPolicy}")
                        }
                        debugInfo.appendLine()
                    }
                }
                
                // 4. Check accessibility service status
                debugInfo.appendLine("=== ENFORCEMENT STATUS ===")
                val isAccessibilityEnabled = ShieldAccessibilityService.isServiceEnabled(this@PolicyDebugActivity)
                debugInfo.appendLine("Accessibility Service: $isAccessibilityEnabled")
                debugInfo.appendLine("Overlay Permission: ${appBlockingManager.hasOverlayPermission()}")
                
                // 5. Check specific app blocking status
                val testPackages = listOf("com.android.chrome", "com.google.android.apps.chrome", "com.facebook.katana")
                for (packageName in testPackages) {
                    val isBlocked = policyManager.isAppBlocked(packageName)
                    debugInfo.appendLine("$packageName blocked: $isBlocked")
                }
                
                binding.tvDebugInfo.text = debugInfo.toString()
                
            } catch (e: Exception) {
                binding.tvDebugInfo.text = "App blocking debug failed: ${e.message}\n${e.stackTraceToString()}"
                Log.e("PolicyDebug", "App blocking debug failed", e)
            }
        }
    }
    
    private fun debugChromeBlocking() {
        lifecycleScope.launch {
            try {
                val chromePackages = listOf(
                    "com.google.android.apps.chrome",
                    "com.android.chrome", 
                    "com.chrome.beta",
                    "com.chrome.canary"
                )
                
                val debugInfo = StringBuilder()
                debugInfo.appendLine("=== CHROME BLOCKING DEBUG ===")
                
                for (packageName in chromePackages) {
                    val appInfo = appInventoryManager.getAppInfo(packageName)
                    if (appInfo != null) {
                        debugInfo.appendLine("Found Chrome: $packageName")
                        debugInfo.appendLine("App Name: ${appInfo.name}")
                        debugInfo.appendLine("Category: ${appInfo.category}")
                        
                        // Check if blocked
                        val isBlocked = policyManager.isAppBlocked(packageName)
                        val blockReason = policyManager.getAppBlockReason(packageName)
                        
                        debugInfo.appendLine("Currently Blocked: $isBlocked")
                        if (blockReason != null) {
                            debugInfo.appendLine("Block Reason: $blockReason")
                        }
                        
                        // Check active policies
                        val activePolicies = policyManager.activePolicies.value
                        debugInfo.appendLine("Active Policies Count: ${activePolicies.size}")
                        
                        activePolicies.forEach { (deviceId, policy) ->
                            val chromePolicy = policy.appPolicies.find { it.packageName == packageName }
                            if (chromePolicy != null) {
                                debugInfo.appendLine("Policy Found: ${chromePolicy.action} - ${chromePolicy.reason}")
                            } else {
                                debugInfo.appendLine("No specific policy for Chrome in device: $deviceId")
                            }
                        }
                        
                        break
                    }
                }
                
                binding.tvDebugInfo.text = debugInfo.toString()
                
            } catch (e: Exception) {
                binding.tvDebugInfo.text = "Chrome debug failed: ${e.message}"
                Log.e("PolicyDebug", "Chrome debug failed", e)
            }
        }
    }
    
    private fun testAccessibilityService() {
        val isEnabled = ShieldAccessibilityService.isServiceEnabled(this)
        
        if (isEnabled) {
            Toast.makeText(this, "✅ Accessibility Service is enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Accessibility Service is disabled", Toast.LENGTH_LONG).show()
            
            // Open accessibility settings
            try {
                startActivity(ShieldAccessibilityService.getServiceIntent())
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open accessibility settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun testBlockingOverlay() {
        if (!appBlockingManager.hasOverlayPermission()) {
            Toast.makeText(this, "❌ Overlay permission not granted", Toast.LENGTH_LONG).show()
            
            try {
                startActivity(appBlockingManager.getOverlayPermissionIntent())
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open overlay permission settings", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        // Test the blocking overlay
        Toast.makeText(this, "Testing blocking overlay...", Toast.LENGTH_SHORT).show()
        
        // Show test overlay for Chrome
        appBlockingManager.showBlockingOverlay(
            "com.google.android.apps.chrome",
            "Test blocking message - Chrome blocked by parent"
        )
    }
}
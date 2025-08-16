package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shieldtechhub.shieldkids.common.base.ServiceManager
import com.shieldtechhub.shieldkids.common.utils.AndroidVersionUtils
import com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager
import com.shieldtechhub.shieldkids.common.utils.PermissionManager
import com.shieldtechhub.shieldkids.databinding.ActivitySystemTestBinding
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import kotlinx.coroutines.launch

class SystemTestActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySystemTestBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var deviceAdminManager: DeviceAdminManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var appInventoryManager: AppInventoryManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure proper system UI visibility - prevent fullscreen overlap
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
        
        binding = ActivitySystemTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeManagers()
        setupUI()
        runSystemTests()
    }
    
    private fun initializeManagers() {
        permissionManager = PermissionManager(this)
        deviceAdminManager = DeviceAdminManager(this)
        serviceManager = ServiceManager(this)
        appInventoryManager = AppInventoryManager(this)
    }
    
    private fun setupUI() {
        binding.btnTestPermissions.setOnClickListener {
            testPermissions()
        }
        
        binding.btnTestDeviceAdmin.setOnClickListener {
            testDeviceAdmin()
        }
        
        binding.btnTestAppInventory.setOnClickListener {
            testAppInventory()
        }
        
        binding.btnTestServices.setOnClickListener {
            testServices()
        }
        
        binding.btnManagePermissions.setOnClickListener {
            startActivity(Intent(this, PermissionRequestActivity::class.java))
        }
        
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun runSystemTests() {
        val results = StringBuilder()
        
        // Android Version Test
        results.appendLine("🔍 SYSTEM TESTS")
        results.appendLine("================")
        results.appendLine("Android Version: ${AndroidVersionUtils.getVersionName()}")
        results.appendLine("Supported: ${AndroidVersionUtils.isSupportedVersion()}")
        results.appendLine()
        
        // Feature Availability
        results.appendLine("📱 FEATURE AVAILABILITY")
        results.appendLine("======================")
        AndroidVersionUtils.getFeatureAvailability().forEach { (feature, available) ->
            val status = if (available) "✅" else "❌"
            results.appendLine("$status $feature")
        }
        results.appendLine()
        
        // Permission Status
        results.appendLine("🔐 PERMISSION STATUS")
        results.appendLine("===================")
        permissionManager.getPermissionStatusSummary().forEach { (permission, status) ->
            val statusIcon = when (status) {
                com.shieldtechhub.shieldkids.common.utils.PermissionStatus.GRANTED -> "✅"
                com.shieldtechhub.shieldkids.common.utils.PermissionStatus.DENIED -> "❌"
                com.shieldtechhub.shieldkids.common.utils.PermissionStatus.NOT_REQUESTED -> "⏳"
                com.shieldtechhub.shieldkids.common.utils.PermissionStatus.PERMANENTLY_DENIED -> "🚫"
            }
            results.appendLine("$statusIcon ${permission.substringAfterLast(".")}")
        }
        results.appendLine()
        
        // Device Admin Status
        results.appendLine("🛡️ DEVICE ADMIN STATUS")
        results.appendLine("=====================")
        val adminStatus = deviceAdminManager.getAdminStatus()
        results.appendLine("Active: ${if (adminStatus.isActive) "✅" else "❌"}")
        results.appendLine("Can Lock Device: ${if (adminStatus.capabilities.canLockDevice) "✅" else "❌"}")
        results.appendLine("Can Control Camera: ${if (adminStatus.capabilities.canDisableCamera) "✅" else "❌"}")
        results.appendLine()
        
        // Service Status
        results.appendLine("⚙️ SERVICE STATUS")
        results.appendLine("================")
        val serviceStatus = serviceManager.getServiceStatus()
        results.appendLine("Monitoring Active: ${if (serviceStatus.isMonitoringActive) "✅" else "❌"}")
        results.appendLine("Can Start Services: ${if (serviceStatus.canStartServices) "✅" else "❌"}")
        results.appendLine()
        
        // Warnings
        val warnings = AndroidVersionUtils.getCompatibilityWarnings()
        if (warnings.isNotEmpty()) {
            results.appendLine("⚠️ COMPATIBILITY WARNINGS")
            results.appendLine("=========================")
            warnings.forEach { warning ->
                results.appendLine("⚠️ $warning")
            }
            results.appendLine()
        }
        
        binding.tvTestResults.text = results.toString()
    }
    
    private fun testPermissions() {
        val missingPermissions = permissionManager.getMissingEssentialPermissions()
        
        if (missingPermissions.isEmpty()) {
            Toast.makeText(this, "✅ All essential permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Missing ${missingPermissions.size} permissions", Toast.LENGTH_SHORT).show()
            permissionManager.requestPermissions(this, missingPermissions.toTypedArray())
        }
    }
    
    private fun testDeviceAdmin() {
        if (deviceAdminManager.isDeviceAdminActive()) {
            Toast.makeText(this, "✅ Device Admin is active!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "⚠️ Device Admin not active - starting setup", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, DeviceAdminSetupActivity::class.java))
        }
    }
    
    private fun testAppInventory() {
        binding.tvTestResults.text = "🔍 Scanning installed apps..."
        
        lifecycleScope.launch {
            try {
                val result = appInventoryManager.refreshAppInventory()
                
                val inventoryText = buildString {
                    appendLine("📱 APP INVENTORY RESULTS")
                    appendLine("=======================")
                    appendLine("Total Apps: ${result.totalApps}")
                    appendLine("User Apps: ${result.userApps}")
                    appendLine("System Apps: ${result.systemApps}")
                    appendLine("Scan Time: ${result.scanTimeMs}ms")
                    appendLine()
                    appendLine("📊 BY CATEGORY")
                    appendLine("==============")
                    result.categories.forEach { (category, count) ->
                        appendLine("${category.name}: $count apps")
                    }
                    appendLine()
                    appendLine("📋 SAMPLE USER APPS")
                    appendLine("==================")
                    result.apps.filter { !it.isSystemApp }.take(10).forEach { app ->
                        appendLine("• ${app.name} (${app.category.name})")
                    }
                }
                
                binding.tvTestResults.text = inventoryText
                Toast.makeText(this@SystemTestActivity, "✅ App inventory complete!", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                binding.tvTestResults.text = "❌ App inventory failed: ${e.message}"
                Toast.makeText(this@SystemTestActivity, "❌ App inventory failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun testServices() {
        val wasRunning = serviceManager.isServiceRunning(com.shieldtechhub.shieldkids.common.base.ShieldMonitoringService::class.java)
        
        if (wasRunning) {
            serviceManager.stopMonitoringService()
            Toast.makeText(this, "🛑 Monitoring service stopped", Toast.LENGTH_SHORT).show()
        } else {
            serviceManager.startMonitoringService()
            Toast.makeText(this, "▶️ Monitoring service started", Toast.LENGTH_SHORT).show()
        }
        
        // Refresh the display after a short delay
        binding.root.postDelayed({
            runSystemTests()
        }, 1000)
    }
}
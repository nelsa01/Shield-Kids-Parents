package com.shieldtechhub.shieldkids

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.shieldtechhub.shieldkids.adapters.PermissionStatusAdapter
import com.shieldtechhub.shieldkids.common.utils.PermissionManager
import com.shieldtechhub.shieldkids.common.utils.PermissionResult
import com.shieldtechhub.shieldkids.common.utils.PermissionStatus
import com.shieldtechhub.shieldkids.databinding.ActivityPermissionRequestBinding

class PermissionRequestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionRequestBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var adapter: PermissionStatusAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionRequestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)
        setupUI()
        checkLaunchContext()
        checkAndDisplayPermissions()
    }

    private fun checkLaunchContext() {
        val fromLaunch = intent.getBooleanExtra("from_launch", false)
        val showWelcome = intent.getBooleanExtra("show_welcome", false)
        val fromSettings = intent.getBooleanExtra("from_settings", false)
        
        when {
            fromLaunch && showWelcome -> {
                // Update UI for launch context
                binding.toolbar.title = "Welcome Back!"
                binding.tvPermissionTitle.text = "Setup Required"
                binding.tvPermissionDescription.text = "Welcome back! Let's quickly set up the permissions Shield Kids needs to keep your children safe."
            }
            fromSettings -> {
                // Update UI for settings context
                binding.toolbar.title = "Permissions"
                binding.tvPermissionTitle.text = "Manage Permissions"
                binding.tvPermissionDescription.text = "Review and manage the permissions Shield Kids needs to protect your children effectively."
            }
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        adapter = PermissionStatusAdapter { permission ->
            handlePermissionClick(permission)
        }
        
        binding.recyclerViewPermissions.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPermissions.adapter = adapter

        binding.btnRequestAllPermissions.setOnClickListener {
            requestAllMissingPermissions()
        }

        binding.btnContinueAnyway.setOnClickListener {
            showContinueWarningDialog()
        }
        
        // Handle navigation based on launch context
        val fromLaunch = intent.getBooleanExtra("from_launch", false)
        val fromSettings = intent.getBooleanExtra("from_settings", false)
        
        when {
            fromLaunch -> {
                binding.toolbar.setNavigationOnClickListener { 
                    // If from launch, go to main dashboard instead of just finishing
                    startActivity(Intent(this, ParentDashboardActivity::class.java))
                    finish()
                }
            }
            fromSettings -> {
                // If from settings, just go back (default behavior)
                binding.toolbar.setNavigationOnClickListener { finish() }
            }
        }
    }

    private fun checkAndDisplayPermissions() {
        val permissionStatuses = permissionManager.getPermissionStatusSummary()
        val permissionItems = permissionStatuses.map { (permission, status) ->
            PermissionStatusAdapter.PermissionItem(
                permission = permission,
                status = status,
                description = permissionManager.getPermissionDescription(permission),
                isEssential = PermissionManager.ESSENTIAL_PERMISSIONS.contains(permission),
                requiresSpecialHandling = permissionManager.isSpecialPermissionRequired(permission)
            )
        }
        
        adapter.updatePermissions(permissionItems)
        updateButtonStates()
    }

    private fun updateButtonStates() {
        val missingEssential = permissionManager.getMissingEssentialPermissions()
        val fromLaunch = intent.getBooleanExtra("from_launch", false)
        val fromSettings = intent.getBooleanExtra("from_settings", false)
        
        binding.btnRequestAllPermissions.isEnabled = missingEssential.isNotEmpty()
        binding.btnRequestAllPermissions.text = when {
            missingEssential.isEmpty() && fromLaunch -> "Continue to Dashboard"
            missingEssential.isEmpty() && fromSettings -> "All Permissions Granted"
            missingEssential.isEmpty() -> "All Essential Permissions Granted"
            else -> "Request ${missingEssential.size} Missing Permissions"
        }

        // Show/hide continue anyway button based on context and missing permissions
        binding.btnContinueAnyway.visibility = when {
            fromSettings -> android.view.View.GONE // Don't show "continue anyway" in settings
            missingEssential.isNotEmpty() -> android.view.View.VISIBLE
            else -> android.view.View.GONE
        }
        
        // Handle button click based on context
        when {
            missingEssential.isEmpty() && fromLaunch -> {
                binding.btnRequestAllPermissions.setOnClickListener {
                    startActivity(Intent(this, ParentDashboardActivity::class.java))
                    finish()
                }
            }
            missingEssential.isEmpty() && fromSettings -> {
                binding.btnRequestAllPermissions.setOnClickListener {
                    finish() // Just go back to settings
                }
            }
        }
    }

    private fun handlePermissionClick(permission: String) {
        when {
            permissionManager.isSpecialPermissionRequired(permission) -> {
                handleSpecialPermission(permission)
            }
            permissionManager.checkPermissionStatus(permission) == PermissionStatus.PERMANENTLY_DENIED -> {
                showSettingsDialog(permission)
            }
            else -> {
                requestSinglePermission(permission)
            }
        }
    }

    private fun handleSpecialPermission(permission: String) {
        when (permission) {
            android.Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                if (!Settings.canDrawOverlays(this)) {
                    showSpecialPermissionDialog(
                        "Display Over Other Apps",
                        "This permission allows Shield Kids to display emergency controls and safety overlays when needed.",
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                              Uri.parse("package:$packageName"))
                    )
                }
            }
            android.Manifest.permission.PACKAGE_USAGE_STATS -> {
                if (!hasUsageStatsPermission()) {
                    showSpecialPermissionDialog(
                        "Usage Access",
                        "This permission allows Shield Kids to monitor app usage and screen time for parental controls.",
                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    )
                }
            }
            com.shieldtechhub.shieldkids.common.utils.PermissionManager.DEVICE_ADMIN_PERMISSION -> {
                val deviceAdminManager = com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager(this)
                if (!deviceAdminManager.isDeviceAdminActive()) {
                    showDeviceAdminPermissionDialog()
                }
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        return permissionManager.checkPermissionStatus(android.Manifest.permission.PACKAGE_USAGE_STATS) == PermissionStatus.GRANTED
    }

    private fun showSpecialPermissionDialog(title: String, message: String, intent: Intent) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("$message\n\nYou'll be taken to Android settings to enable this permission.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open settings", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun showSettingsDialog(permission: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("${permissionManager.getPermissionDescription(permission)}\n\nThis permission was permanently denied. Please enable it in app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun requestSinglePermission(permission: String) {
        permissionManager.requestPermissions(this, arrayOf(permission))
    }

    private fun requestAllMissingPermissions() {
        val missingPermissions = permissionManager.getMissingEssentialPermissions()
        val normalPermissions = missingPermissions.filter { permission ->
            !permissionManager.isSpecialPermissionRequired(permission)
        }
        
        if (normalPermissions.isNotEmpty()) {
            permissionManager.requestPermissions(this, normalPermissions.toTypedArray())
        }
        
        // Handle special permissions one by one
        val specialPermissions = missingPermissions.filter { permission ->
            permissionManager.isSpecialPermissionRequired(permission)
        }
        
        specialPermissions.forEach { permission ->
            handleSpecialPermission(permission)
        }
    }

    private fun showContinueWarningDialog() {
        val fromLaunch = intent.getBooleanExtra("from_launch", false)
        
        AlertDialog.Builder(this)
            .setTitle("Warning")
            .setMessage("Shield Kids requires certain permissions to provide full parental control functionality. Continuing without these permissions may limit the app's effectiveness.\n\nAre you sure you want to continue?")
            .setPositiveButton("Continue Anyway") { _, _ ->
                if (fromLaunch) {
                    // If launched from startup, go to main dashboard
                    startActivity(Intent(this, ParentDashboardActivity::class.java))
                }
                finish()
            }
            .setNegativeButton("Grant Permissions") { _, _ ->
                requestAllMissingPermissions()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        val results = permissionManager.handlePermissionResult(requestCode, permissions, grantResults)
        handlePermissionResults(results)
    }

    private fun handlePermissionResults(results: List<PermissionResult>) {
        // Use the enhanced permission callback system
        permissionManager.processPermissionResults(results)
        
        var hasNewGrants = false
        var hasNewDenials = false
        
        results.forEach { result ->
            when (result.status) {
                PermissionStatus.GRANTED -> hasNewGrants = true
                PermissionStatus.DENIED, PermissionStatus.PERMANENTLY_DENIED -> hasNewDenials = true
                else -> {}
            }
        }
        
        if (hasNewGrants) {
            Toast.makeText(this, "Permissions granted successfully", Toast.LENGTH_SHORT).show()
        }
        
        if (hasNewDenials) {
            Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show()
        }
        
        // Refresh the permission display
        checkAndDisplayPermissions()
    }

    private fun showDeviceAdminPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Device Administrator Required")
            .setMessage("Shield Kids needs device administrator permissions to:\n\n• Block harmful apps\n• Enforce screen time limits\n• Protect device settings\n• Ensure parental controls work properly\n\nYou'll be taken to the device admin setup screen.")
            .setPositiveButton("Enable Device Admin") { _, _ ->
                val intent = Intent(this, DeviceAdminSetupActivity::class.java)
                startActivity(intent)
            }
            .setNegativeButton("Skip") { _, _ ->
                Toast.makeText(this, "Device admin is required for full parental control functionality", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh permissions when returning from settings
        checkAndDisplayPermissions()
    }
}
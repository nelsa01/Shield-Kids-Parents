package com.shieldtechhub.shieldkids

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager
import com.shieldtechhub.shieldkids.databinding.ActivityDeviceAdminSetupBinding

class DeviceAdminSetupActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDeviceAdminSetupBinding
    private lateinit var deviceAdminManager: DeviceAdminManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceAdminSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        deviceAdminManager = DeviceAdminManager(this)
        
        setupUI()
        checkCurrentStatus()
    }
    
    private fun setupUI() {
        binding.btnActivateDeviceAdmin.setOnClickListener {
            requestDeviceAdmin()
        }
        
        binding.btnSkip.setOnClickListener {
            showSkipWarning()
        }
        
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun checkCurrentStatus() {
        val status = deviceAdminManager.getAdminStatus()
        
        if (status.isActive) {
            // Device admin is already active
            binding.tvStatus.text = "✅ Device Admin is Active"
            binding.btnActivateDeviceAdmin.text = "Continue"
            binding.btnActivateDeviceAdmin.setOnClickListener {
                navigateToNextStep()
            }
        } else {
            // Device admin needs to be activated
            binding.tvStatus.text = "⚠️ Device Admin Required"
            updateCapabilitiesDisplay(status.capabilities)
        }
    }
    
    private fun updateCapabilitiesDisplay(capabilities: com.shieldtechhub.shieldkids.common.utils.DeviceAdminCapabilities) {
        val capabilitiesText = buildString {
            appendLine("Shield Kids needs these permissions:")
            appendLine()
            appendLine("🔒 Lock Device - For emergency situations")
            appendLine("📱 Control Camera - Restrict camera access when needed")
            appendLine("🔑 Password Policies - Ensure device security")
            appendLine("🛡️ Keyguard Control - Manage lock screen features")
            appendLine()
            appendLine("These permissions help protect your child and enforce parental controls safely.")
        }
        
        binding.tvCapabilities.text = capabilitiesText
    }
    
    private fun requestDeviceAdmin() {
        if (deviceAdminManager.isDeviceAdminActive()) {
            navigateToNextStep()
        } else {
            deviceAdminManager.requestDeviceAdminActivation(this)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == DeviceAdminManager.REQUEST_CODE_ENABLE_DEVICE_ADMIN) {
            val success = deviceAdminManager.handleDeviceAdminResult(requestCode, resultCode)
            
            if (success) {
                Toast.makeText(this, "Device Admin activated successfully!", Toast.LENGTH_LONG).show()
                checkCurrentStatus()
                
                // Small delay to let the user see the success message
                binding.root.postDelayed({
                    navigateToNextStep()
                }, 1500)
            } else {
                Toast.makeText(this, "Device Admin activation was cancelled. Some features may not work properly.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showSkipWarning() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Skip Device Admin Setup?")
            .setMessage("Without Device Admin privileges, Shield Kids cannot:\n\n" +
                    "• Lock the device in emergencies\n" +
                    "• Enforce camera restrictions\n" +
                    "• Apply password policies\n" +
                    "• Fully protect your child\n\n" +
                    "Are you sure you want to continue without these protections?")
            .setPositiveButton("Continue Anyway") { _, _ ->
                navigateToNextStep()
            }
            .setNegativeButton("Go Back") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun navigateToNextStep() {
        // Navigate back to the previous activity or continue setup flow
        val resultIntent = Intent().apply {
            putExtra("device_admin_active", deviceAdminManager.isDeviceAdminActive())
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
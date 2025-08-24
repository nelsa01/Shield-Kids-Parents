package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityDeviceSettingsBinding

class DeviceSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeviceSettingsBinding
    private val db = FirebaseFirestore.getInstance()
    
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var childId: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        deviceName = intent.getStringExtra("deviceName") ?: "Unknown Device"
        childId = intent.getStringExtra("childId") ?: ""
        
        setupUI()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // Set device name
        binding.tvDeviceName.text = deviceName
        
        // Set device email (placeholder for now)
        binding.tvDeviceEmail.text = "device@shieldtechclub.com"
        
        // Set device initials based on device name
        val initials = if (deviceName.length >= 2) {
            deviceName.take(2).uppercase()
        } else {
            "DV"
        }
        binding.tvDeviceInitials.text = initials
    }
    
    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // General section options
        binding.btnApps.setOnClickListener {
            val intent = Intent(this, AppManagementActivity::class.java)
            intent.putExtra("childId", childId)
            intent.putExtra("deviceId", deviceId)
            startActivity(intent)
        }
        
        binding.btnInternet.setOnClickListener {
            Toast.makeText(this, "Internet settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnDeviceUsage.setOnClickListener {
            Toast.makeText(this, "Device usage settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnLocation.setOnClickListener {
            Toast.makeText(this, "Location settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        // Other section options
        binding.btnNotifications.setOnClickListener {
            Toast.makeText(this, "Notification settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnInstallation.setOnClickListener {
            Toast.makeText(this, "Installation settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnAbout.setOnClickListener {
            Toast.makeText(this, "About device coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnPrivacy.setOnClickListener {
            Toast.makeText(this, "Privacy & Policy coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        // Remove Device button
        binding.btnRemoveDevice.setOnClickListener {
            showRemoveDeviceConfirmation()
        }
        
        // Bottom navigation
        binding.navHome.setOnClickListener {
            // Navigate back to parent dashboard
            val intent = Intent(this, ParentDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
        
        binding.navLocation.setOnClickListener {
            Toast.makeText(this, "Location feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.navLightning.setOnClickListener {
            Toast.makeText(this, "Lightning feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnSettings.setOnClickListener {
            // Already in settings, do nothing or show toast
            Toast.makeText(this, "You are already in settings", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showRemoveDeviceConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Remove Device")
            .setMessage("Are you sure you want to remove '$deviceName' from this child's account? This action cannot be undone and will disconnect the device immediately.")
            .setPositiveButton("Remove") { _, _ ->
                removeDevice()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun removeDevice() {
        // Show loading
        Toast.makeText(this, "Removing device...", Toast.LENGTH_SHORT).show()

        // First, remove device from child's devices HashMap
        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener { childDoc ->
                if (childDoc.exists()) {
                    val currentDevices = childDoc.get("devices") as? HashMap<String, Any> ?: HashMap()
                    currentDevices.remove(deviceId)
                    
                    // Update child document
                    childDoc.reference.update("devices", currentDevices)
                        .addOnSuccessListener {
                            // Now delete the device document
                            db.collection("devices").document(deviceId)
                                .delete()
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Device '$deviceName' removed successfully", Toast.LENGTH_SHORT).show()
                                    // Navigate back to child detail
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to delete device: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update child: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Child document not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get child: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
}

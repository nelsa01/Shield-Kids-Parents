package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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

        deviceId = intent.getStringExtra("deviceId") ?: ""
        deviceName = intent.getStringExtra("deviceName") ?: ""
        childId = intent.getStringExtra("childId") ?: ""

        if (deviceId.isEmpty()) {
            Toast.makeText(this, "Device ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadDeviceSettings()
    }

    private fun setupUI() {
        binding.tvDeviceName.text = "Device: $deviceName"
        
        // Screen Time Setup
        binding.seekBarScreenTime.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvScreenTimeValue.text = "${progress} hours"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Geofence Setup
        binding.cbGeofence.setOnCheckedChangeListener { _, isChecked ->
            binding.geofenceContainer.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Content Filter Setup
        binding.cbContentFilter.setOnCheckedChangeListener { _, isChecked ->
            binding.contentFilterContainer.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        // App Restrictions Setup
        binding.cbAppRestrictions.setOnCheckedChangeListener { _, isChecked ->
            binding.appRestrictionsContainer.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Save Button
        binding.btnSaveSettings.setOnClickListener {
            saveDeviceSettings()
        }

        // Remove Device Button
        binding.btnRemoveDevice.setOnClickListener {
            showRemoveDeviceConfirmation()
        }

        // Back Button
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun loadDeviceSettings() {
        db.collection("devices").document(deviceId)
            .get()
            .addOnSuccessListener { deviceDoc ->
                if (deviceDoc.exists()) {
                    val settings = deviceDoc.get("settings") as? HashMap<String, Any> ?: HashMap()
                    
                    // Load screen time
                    val screenTime = settings["screenTime"] as? Long ?: 0
                    binding.seekBarScreenTime.progress = screenTime.toInt()
                    binding.tvScreenTimeValue.text = "${screenTime} hours"
                    
                    // Load geofence
                    val geofence = settings["geofence"] as? HashMap<String, Any> ?: HashMap()
                    val geofenceEnabled = geofence["enabled"] as? Boolean ?: false
                    binding.cbGeofence.isChecked = geofenceEnabled
                    binding.geofenceContainer.visibility = if (geofenceEnabled) android.view.View.VISIBLE else android.view.View.GONE
                    
                    // Load content filter
                    val contentFilter = settings["contentFilter"] as? HashMap<String, Any> ?: HashMap()
                    val contentFilterEnabled = contentFilter["enabled"] as? Boolean ?: false
                    binding.cbContentFilter.isChecked = contentFilterEnabled
                    binding.contentFilterContainer.visibility = if (contentFilterEnabled) android.view.View.VISIBLE else android.view.View.GONE
                    
                    // Load app restrictions
                    val appRestrictions = settings["appRestrictions"] as? HashMap<String, Any> ?: HashMap()
                    val appRestrictionsEnabled = appRestrictions["enabled"] as? Boolean ?: false
                    binding.cbAppRestrictions.isChecked = appRestrictionsEnabled
                    binding.appRestrictionsContainer.visibility = if (appRestrictionsEnabled) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading settings: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDeviceSettings() {
        val screenTime = binding.seekBarScreenTime.progress.toLong()
        val geofenceEnabled = binding.cbGeofence.isChecked
        val contentFilterEnabled = binding.cbContentFilter.isChecked
        val appRestrictionsEnabled = binding.cbAppRestrictions.isChecked
        
        // Get geofence locations if enabled
        val geofenceLocations = if (geofenceEnabled) {
            // For now, just add a default location (you can enhance this later)
            listOf(0.0, 0.0) // Latitude, Longitude
        } else {
            listOf<Double>()
        }
        
        // Get blocked apps if restrictions enabled
        val blockedApps = if (appRestrictionsEnabled) {
            // For now, just add some common apps (you can enhance this later)
            listOf("com.example.game", "com.example.social")
        } else {
            listOf<String>()
        }
        
        val settings = hashMapOf(
            "screenTime" to screenTime,
            "geofence" to hashMapOf(
                "enabled" to geofenceEnabled,
                "locations" to geofenceLocations
            ),
            "contentFilter" to hashMapOf(
                "enabled" to contentFilterEnabled,
                "categories" to listOf("violence", "adult", "gambling")
            ),
            "appRestrictions" to hashMapOf(
                "enabled" to appRestrictionsEnabled,
                "blockedApps" to blockedApps
            )
        )
        
        // Update device settings
        db.collection("devices").document(deviceId)
            .update("settings", settings)
            .addOnSuccessListener {
                Toast.makeText(this, "Device settings updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save settings: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRemoveDeviceConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Remove Device")
            .setMessage("Are you sure you want to remove '$deviceName'? This action cannot be undone.")
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
                    val currentDevices = childDoc.get("devices") as? HashMap<String, String> ?: HashMap()
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

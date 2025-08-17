package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shieldtechhub.shieldkids.databinding.ActivityDeviceSettingsBinding

class DeviceSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeviceSettingsBinding
    
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
            val intent = Intent(this, AppListActivity::class.java)
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
        
        binding.btnPassCode.setOnClickListener {
            Toast.makeText(this, "PassCode settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnAbout.setOnClickListener {
            Toast.makeText(this, "About device coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnPrivacy.setOnClickListener {
            Toast.makeText(this, "Privacy & Policy coming soon!", Toast.LENGTH_SHORT).show()
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
}

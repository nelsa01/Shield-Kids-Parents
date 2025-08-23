package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityAddDeviceBinding

class AddDeviceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddDeviceBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var childId: String = ""
    private var isFromAddChild: Boolean = false
    
    private var selectedDeviceType: String = "iOS" // Default to iOS as shown in screenshot
    private var selectedInstallMethod: String = "Link" // Default install method

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childId = intent.getStringExtra("childId") ?: ""
        isFromAddChild = intent.getBooleanExtra("isFromAddChild", false)
        if (childId.isEmpty()) { 
            finish()
            return 
        }

        setupUI()
        setupClickListeners()
    }
    
    private fun setupUI() {
        // Set initial selection (iOS as shown in screenshot)
        updateDeviceSelection("iOS")
        updateInstallMethodSelection("Link")
    }

    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Device type selection
        binding.deviceAndroid1.setOnClickListener { updateDeviceSelection("Android") }
        binding.deviceiOS.setOnClickListener { updateDeviceSelection("iOS") }
        binding.deviceWindows.setOnClickListener { updateDeviceSelection("Windows") }
        binding.deviceAndroid2.setOnClickListener { updateDeviceSelection("Android") }

        // Install method selection
        binding.installLink.setOnClickListener { updateInstallMethodSelection("Link") }
        binding.installQR.setOnClickListener { updateInstallMethodSelection("QR Code") }

        // Done button
        binding.btnDone.setOnClickListener {
            createDevice()
        }

        // Bottom navigation
        binding.navHome.setOnClickListener {
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
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateDeviceSelection(deviceType: String) {
        selectedDeviceType = deviceType
        
        // Reset all indicators
        binding.indicatorAndroid1.visibility = View.GONE
        binding.indicatoriOS.visibility = View.GONE
        binding.indicatorWindows.visibility = View.GONE
        binding.indicatorAndroid2.visibility = View.GONE
        
        // Reset all backgrounds to default
        resetDeviceBackgrounds()
        
        // Show indicator and update background for selected device
        when (deviceType) {
            "Android" -> {
                binding.indicatorAndroid1.visibility = View.VISIBLE
                updateDeviceBackground(binding.deviceAndroid1, true)
            }
            "iOS" -> {
                binding.indicatoriOS.visibility = View.VISIBLE
                updateDeviceBackground(binding.deviceiOS, true)
            }
            "Windows" -> {
                binding.indicatorWindows.visibility = View.VISIBLE
                updateDeviceBackground(binding.deviceWindows, true)
            }
        }
    }
    
    private fun updateInstallMethodSelection(method: String) {
        selectedInstallMethod = method
        
        // Reset backgrounds
        resetInstallMethodBackgrounds()
        
        // Update selected background
        when (method) {
            "Link" -> updateInstallMethodBackground(binding.installLink, true)
            "QR Code" -> updateInstallMethodBackground(binding.installQR, true)
        }
    }
    
    private fun resetDeviceBackgrounds() {
        updateDeviceBackground(binding.deviceAndroid1, false)
        updateDeviceBackground(binding.deviceiOS, false)
        updateDeviceBackground(binding.deviceWindows, false)
        updateDeviceBackground(binding.deviceAndroid2, false)
    }
    
    private fun resetInstallMethodBackgrounds() {
        updateInstallMethodBackground(binding.installLink, false)
        updateInstallMethodBackground(binding.installQR, false)
    }
    
    private fun updateDeviceBackground(layout: LinearLayout, isSelected: Boolean) {
        if (isSelected) {
            layout.background = ContextCompat.getDrawable(this, R.drawable.card_background_selectable)
            layout.setBackgroundResource(android.R.color.transparent)
            layout.setBackgroundColor(ContextCompat.getColor(this, R.color.teal_50))
        } else {
            layout.background = ContextCompat.getDrawable(this, R.drawable.card_background)
        }
    }
    
    private fun updateInstallMethodBackground(layout: LinearLayout, isSelected: Boolean) {
        if (isSelected) {
            layout.background = ContextCompat.getDrawable(this, R.drawable.card_background_selectable)
            layout.setBackgroundResource(android.R.color.transparent)
            layout.setBackgroundColor(ContextCompat.getColor(this, R.color.teal_50))
        } else {
            layout.background = ContextCompat.getDrawable(this, R.drawable.card_background)
        }
    }

    private fun createDevice() {
        // Generate a 6-character linking code
        val linkingCode = SecurityUtils.generateRefNumber()
        val linkingCodeHash = SecurityUtils.hashRefNumber(linkingCode)
        
        // Generate device name based on type
        val deviceName = "$selectedDeviceType Device"
        
        // Create pending device with minimal structure
        val pendingDevice = hashMapOf(
            "childId" to childId,
            "deviceName" to deviceName,
            "deviceType" to selectedDeviceType.lowercase(),
            "linkingCode" to linkingCode,
            "linkingCodeHash" to linkingCodeHash,
            "installMethod" to selectedInstallMethod,
            "createdAt" to System.currentTimeMillis(),
            "expiresAt" to (System.currentTimeMillis() + 24 * 60 * 60 * 1000), // 24 hours
            "parentId" to auth.currentUser?.uid
        )

        // Show loading
        binding.btnDone.text = "Creating..."
        binding.btnDone.isEnabled = false

        // Save to pending_devices collection (NOT devices)
        db.collection("pending_devices").add(pendingDevice)
            .addOnSuccessListener { documentReference ->
                // Update child's refNumberHash for verification, but DON'T add to devices
                val childRef = db.collection("children").document(childId)
                childRef.update("refNumberHash", linkingCodeHash)
                    .addOnSuccessListener {
                        // Navigate to waiting screen with pending device info
                        val intent = Intent(this, WaitingForSetupActivity::class.java)
                        intent.putExtra("pendingDeviceId", documentReference.id)
                        intent.putExtra("deviceName", deviceName)
                        intent.putExtra("linkingCode", linkingCode)
                        intent.putExtra("childId", childId)
                        intent.putExtra("installMethod", selectedInstallMethod)
                        intent.putExtra("isFromAddChild", isFromAddChild)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        binding.btnDone.text = "Done"
                        binding.btnDone.isEnabled = true
                        Toast.makeText(this, "Failed to update child: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e -> 
                binding.btnDone.text = "Done"
                binding.btnDone.isEnabled = true
                Toast.makeText(this, "Failed to create pending device: ${e.localizedMessage}", Toast.LENGTH_SHORT).show() 
            }
    }
}


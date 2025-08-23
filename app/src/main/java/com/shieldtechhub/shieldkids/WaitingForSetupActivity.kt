package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityWaitingForSetupBinding
import android.widget.Toast

class WaitingForSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWaitingForSetupBinding
    private val db = FirebaseFirestore.getInstance()
    
    private var deviceId: String = ""
    private var pendingDeviceId: String = ""
    private var deviceName: String = ""
    private var linkingCode: String = ""
    private var childId: String = ""
    private var isChecking = true
    private var isFromAddChild: Boolean = false
    private var isChildConnected = false
    private var checkCount = 0
    private val maxCheckCount = 12 // 1 minute (12 * 5 seconds)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaitingForSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get extras from intent
        deviceId = intent.getStringExtra("deviceId") ?: ""
        pendingDeviceId = intent.getStringExtra("pendingDeviceId") ?: ""
        deviceName = intent.getStringExtra("deviceName") ?: ""
        childId = intent.getStringExtra("childId") ?: ""
        isFromAddChild = intent.getBooleanExtra("isFromAddChild", false)
        
        // Handle both String and Int linking codes
        linkingCode = intent.getStringExtra("linkingCode") ?: intent.getIntExtra("linkingCode", 0).toString()

        if (childId.isEmpty() || linkingCode.isEmpty() || linkingCode == "0") {
            finish()
            return
        }

        setupUI()
        startDeviceStatusCheck()
    }

    private fun setupUI() {
        if (isFromAddChild) {
            // Flow from AddChild -> AddDevice - showing reference number for newly created child
            binding.tvDeviceName.text = "Device Setup for New Child"
            binding.tvLinkingCode.text = linkingCode
            binding.tvInstructions.text = "Share this reference number with your child so they can link their device to this profile."
            binding.tvStatus.text = "Waiting for child to enter this code on their device..."
        } else {
            // Flow from direct AddDevice - adding device to existing child
            binding.tvDeviceName.text = deviceName
            binding.tvLinkingCode.text = linkingCode
            binding.tvInstructions.text = "Share this code with your child so they can verify and link their device."
            binding.tvStatus.text = "Waiting for child to verify the code..."
        }
        
        // Both flows now work the same way - show setup button but keep it disabled until child connects
        binding.btnSetupDevice.visibility = android.view.View.VISIBLE
        updateSetupButtonState(false) // Initially disabled
        
        binding.btnSetupDevice.setOnClickListener {
            if (isChildConnected) {
                val intent = Intent(this, DeviceSetupActivity::class.java)
                intent.putExtra("deviceId", deviceId)
                intent.putExtra("deviceName", deviceName)
                intent.putExtra("childId", childId)
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Please wait for the child to verify the code first", Toast.LENGTH_SHORT).show()
            }
        }

        // Remove Device Button
        binding.btnRemoveDevice.setOnClickListener {
            showRemoveDeviceConfirmation()
        }

        binding.btnBackToChildren.setOnClickListener {
            val intent = Intent(this, ParentDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
        
        // Add long press to restart checking (useful for timeout situations)
        binding.btnBackToChildren.setOnLongClickListener {
            if (!isChecking) {
                restartDeviceStatusCheck()
                Toast.makeText(this, "Restarting connection check...", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun startDeviceStatusCheck() {
        // Check device status every 5 seconds with timeout
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isChecking && checkCount < maxCheckCount) {
                    checkCount++
                    checkDeviceStatus()
                    handler.postDelayed(this, 5000)
                } else if (checkCount >= maxCheckCount) {
                    // Timeout reached
                    onCheckTimeout()
                }
            }
        }
        handler.post(runnable)
    }
    
    private fun onCheckTimeout() {
        isChecking = false
        binding.progressBar.visibility = android.view.View.GONE
        binding.tvStatus.text = "Connection timeout. Please check if the child device is online and try refreshing."
        binding.tvStatus.setTextColor(resources.getColor(R.color.error_red, null))
        
        // Add a refresh button functionality if it exists
        binding.btnBackToChildren.visibility = android.view.View.VISIBLE
        
        // Show a retry option
        Toast.makeText(this, "Connection timeout. You can try adding the device again.", Toast.LENGTH_LONG).show()
    }
    
    private fun restartDeviceStatusCheck() {
        checkCount = 0
        isChecking = true
        isChildConnected = false
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_600))
        updateSetupButtonState(false)
        startDeviceStatusCheck()
    }
    
    private fun updateSetupButtonState(enabled: Boolean) {
        binding.btnSetupDevice.isEnabled = enabled
        if (enabled) {
            binding.btnSetupDevice.background = ContextCompat.getDrawable(this, R.drawable.button_outline_teal)
            binding.btnSetupDevice.setTextColor(ContextCompat.getColor(this, R.color.teal_500))
        } else {
            binding.btnSetupDevice.background = ContextCompat.getDrawable(this, R.drawable.button_outline_gray)
            binding.btnSetupDevice.setTextColor(ContextCompat.getColor(this, R.color.gray_400))
        }
    }

    private fun checkDeviceStatus() {
        // Both flows now create pending devices, so always use pending device verification
        // The isFromAddChild flag only affects UI messaging
        checkPendingDeviceVerification()
    }
    
    private fun checkChildDeviceConnection() {
        // Check if device exists and has been verified by child (has deviceName in child's devices)
        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener { childDoc ->
                if (childDoc.exists()) {
                    val devices = childDoc.get("devices") as? HashMap<String, Any> ?: HashMap()
                    val deviceExists = devices.containsKey(deviceId)
                    
                    if (deviceExists && !isChildConnected) {
                        // Child has connected! Enable setup button
                        isChildConnected = true
                        updateUIForChildConnected()
                    } else if (deviceExists && isChildConnected) {
                        // Check if device setup is complete
                        checkDeviceSetupComplete()
                    } else {
                        // Still waiting for child to connect
                        updateUIForWaiting()
                    }
                } else {
                    updateUIForWaiting()
                }
            }
            .addOnFailureListener { e ->
                binding.tvStatus.text = "Error checking status: ${e.localizedMessage}"
                binding.tvStatus.setTextColor(resources.getColor(R.color.error_red, null))
            }
    }
    
    private fun checkDeviceSetupComplete() {
        db.collection("devices").document(deviceId)
            .get()
            .addOnSuccessListener { deviceDoc ->
                if (deviceDoc.exists()) {
                    val isSetupComplete = deviceDoc.getBoolean("isConnected") ?: false
                    if (isSetupComplete) {
                        isChecking = false
                        updateUIForSetupComplete()
                    } else {
                        updateUIForChildConnected() // Keep showing child connected state
                    }
                }
            }
    }
    
    private fun updateUIForWaiting() {
        binding.ivStatusIcon.setImageResource(R.drawable.ic_clock)
        binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_500))
        binding.tvStatus.text = "Waiting for child to verify the code..."
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_600))
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.llChildConnected.visibility = android.view.View.GONE
        updateSetupButtonState(false)
    }
    
    private fun updateUIForChildConnected() {
        binding.ivStatusIcon.setImageResource(R.drawable.ic_check)
        binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
        binding.tvStatus.text = "Child verified the code successfully!"
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.teal_500))
        binding.progressBar.visibility = android.view.View.GONE
        binding.llChildConnected.visibility = android.view.View.VISIBLE
        updateSetupButtonState(true)
    }
    
    private fun updateUIForSetupComplete() {
        binding.ivStatusIcon.setImageResource(R.drawable.ic_check)
        binding.ivStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
        binding.tvStatus.text = "Device setup complete! Child's device is ready to use."
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.teal_500))
        binding.progressBar.visibility = android.view.View.GONE
        binding.llChildConnected.visibility = android.view.View.VISIBLE
        binding.btnSetupDevice.visibility = android.view.View.GONE
        binding.btnBackToChildren.visibility = android.view.View.VISIBLE
    }
    
    private fun checkPendingDeviceVerification() {
        // Check if child has verified the linking code by checking if device moved to child's devices
        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener { childDoc ->
                if (childDoc.exists()) {
                    val devices = childDoc.get("devices") as? HashMap<String, Any> ?: HashMap()
                    
                    // Check if any device in child's devices matches our pending device
                    val hasVerifiedDevice = devices.values.any { deviceData ->
                        when (deviceData) {
                            is Map<*, *> -> {
                                val deviceInfo = deviceData as Map<String, Any>
                                deviceInfo["deviceName"] == deviceName
                            }
                            else -> false
                        }
                    }
                    
                    if (hasVerifiedDevice && !isChildConnected) {
                        // Child verified! Now create the actual device and clean up pending
                        createVerifiedDevice()
                    } else if (hasVerifiedDevice && isChildConnected) {
                        // Already verified, check if setup is complete
                        if (deviceId.isNotEmpty()) {
                            checkDeviceSetupComplete()
                        }
                    } else {
                        // Still waiting for verification
                        updateUIForWaiting()
                    }
                } else {
                    updateUIForWaiting()
                }
            }
            .addOnFailureListener { e ->
                binding.tvStatus.text = "Error checking status: ${e.localizedMessage}"
                binding.tvStatus.setTextColor(resources.getColor(R.color.error_red, null))
            }
    }
    
    private fun createVerifiedDevice() {
        if (pendingDeviceId.isEmpty()) return
        
        // Get the pending device data
        db.collection("pending_devices").document(pendingDeviceId)
            .get()
            .addOnSuccessListener { pendingDoc ->
                if (pendingDoc.exists()) {
                    // Create full device document with all settings
                    val device = hashMapOf(
                        "childId" to childId,
                        "deviceName" to deviceName,
                        "deviceType" to (pendingDoc.getString("deviceType") ?: "android"),
                        "linkingCode" to linkingCode,
                        "installMethod" to (pendingDoc.getString("installMethod") ?: "Link"),
                        "isConnected" to false,
                        "createdAt" to System.currentTimeMillis(),
                        "settings" to hashMapOf(
                            "screenTime" to 0,
                            "geofence" to hashMapOf(
                                "enabled" to false,
                                "locations" to listOf<Double>()
                            ),
                            "contentFilter" to hashMapOf(
                                "enabled" to false,
                                "categories" to listOf<String>()
                            ),
                            "appRestrictions" to hashMapOf(
                                "enabled" to false,
                                "blockedApps" to listOf<String>()
                            )
                        )
                    )
                    
                    // Create actual device document
                    db.collection("devices").add(device)
                        .addOnSuccessListener { deviceDocRef ->
                            deviceId = deviceDocRef.id
                            isChildConnected = true
                            
                            // Delete the pending device
                            pendingDoc.reference.delete()
                            
                            // Update UI to show child connected
                            updateUIForChildConnected()
                        }
                        .addOnFailureListener { e ->
                            binding.tvStatus.text = "Error creating device: ${e.localizedMessage}"
                            binding.tvStatus.setTextColor(resources.getColor(R.color.error_red, null))
                        }
                } else {
                    binding.tvStatus.text = "Pending device not found"
                    binding.tvStatus.setTextColor(resources.getColor(R.color.error_red, null))
                }
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

        if (deviceId.isNotEmpty()) {
            // Remove verified device
            removeVerifiedDevice()
        } else if (pendingDeviceId.isNotEmpty()) {
            // Remove pending device
            removePendingDevice()
        } else {
            Toast.makeText(this, "No device to remove", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun removeVerifiedDevice() {
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
                                    navigateBackToDashboard()
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
    
    private fun removePendingDevice() {
        // Simply delete the pending device document
        db.collection("pending_devices").document(pendingDeviceId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Pending device '$deviceName' removed successfully", Toast.LENGTH_SHORT).show()
                navigateBackToDashboard()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to remove pending device: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun navigateBackToDashboard() {
        val intent = Intent(this, ParentDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop checking status
        isChecking = false
    }
}

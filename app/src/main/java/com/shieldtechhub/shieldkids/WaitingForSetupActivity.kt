package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityWaitingForSetupBinding
import android.widget.Toast

class WaitingForSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWaitingForSetupBinding
    private val db = FirebaseFirestore.getInstance()
    
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var linkingCode: Int = 0
    private var childId: String = ""
    private var isChecking = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaitingForSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get extras from intent
        deviceId = intent.getStringExtra("deviceId") ?: ""
        deviceName = intent.getStringExtra("deviceName") ?: ""
        linkingCode = intent.getIntExtra("linkingCode", 0)
        childId = intent.getStringExtra("childId") ?: ""

        if (deviceId.isEmpty() || deviceName.isEmpty() || linkingCode == 0) {
            finish()
            return
        }

        setupUI()
        startDeviceStatusCheck()
    }

    private fun setupUI() {
        binding.tvDeviceName.text = deviceName
        binding.tvLinkingCode.text = linkingCode.toString()
        binding.tvInstructions.text = "Device is waiting for setup. Configure device settings to complete connection."

        // Show setup button immediately
        binding.btnSetupDevice.visibility = android.view.View.VISIBLE
        binding.btnSetupDevice.setOnClickListener {
            val intent = Intent(this, DeviceSetupActivity::class.java)
            intent.putExtra("deviceId", deviceId)
            intent.putExtra("deviceName", deviceName)
            intent.putExtra("childId", childId)
            startActivity(intent)
            finish()
        }

        // Remove Device Button
        binding.btnRemoveDevice.setOnClickListener {
            showRemoveDeviceConfirmation()
        }

        binding.btnBackToChildren.setOnClickListener {
            val intent = Intent(this, ChildrenDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun startDeviceStatusCheck() {
        // Check device status every 2 seconds
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isChecking) {
                    checkDeviceStatus()
                    handler.postDelayed(this, 2000)
                }
            }
        }
        handler.post(runnable)
    }

    private fun checkDeviceStatus() {
        // Check if device setup is complete (device is connected)
        db.collection("devices").document(deviceId)
            .get()
            .addOnSuccessListener get@{ deviceDoc ->
                if (deviceDoc.exists()) {
                    val isConnected = deviceDoc.getBoolean("isConnected") ?: false
                    if (isConnected) {
                        isChecking = false // Stop checking
                        binding.tvStatus.text = "Device Setup Complete! Child's device is now connected."
                        binding.tvStatus.setTextColor(resources.getColor(R.color.teal_500, null))
                        binding.progressBar.visibility = android.view.View.GONE
                        binding.btnSetupDevice.visibility = android.view.View.GONE
                        binding.btnBackToChildren.visibility = android.view.View.VISIBLE
                        return@get
                    }
                }

                // Still waiting for setup to complete
                binding.tvStatus.text = "Waiting for device setup to complete..."
                binding.progressBar.visibility = android.view.View.VISIBLE
            }
            .addOnFailureListener { e ->
                binding.tvStatus.text = "Error checking status: ${e.localizedMessage}"
                binding.tvStatus.setTextColor(resources.getColor(R.color.error_red, null))
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
                                    // Navigate back to children dashboard
                                    val intent = Intent(this, ChildrenDashboardActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    startActivity(intent)
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

    override fun onDestroy() {
        super.onDestroy()
        // Stop checking status
        isChecking = false
    }
}

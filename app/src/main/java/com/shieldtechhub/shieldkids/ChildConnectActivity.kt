package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityChildConnectBinding

class ChildConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildConnectBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.tvTitle.text = "Connect Your Device"
        binding.tvInstructions.text = "Enter the 6-digit linking code from your parent to connect your device"

        binding.btnConnect.setOnClickListener {
            val linkingCode = binding.etLinkingCode.text.toString().trim()
            
            if (linkingCode.isEmpty()) {
                Toast.makeText(this, "Please enter the linking code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (linkingCode.length != 6) {
                Toast.makeText(this, "Linking code must be 6 digits", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            connectDevice(linkingCode)
        }
    }

    private fun connectDevice(linkingCode: String) {
        // Find device with this linking code
        db.collection("devices")
            .whereEqualTo("linkingCode", linkingCode.toInt())
            .get()
            .addOnSuccessListener { devicesSnap ->
                if (!devicesSnap.isEmpty) {
                    val deviceDoc = devicesSnap.documents[0]
                    val deviceId = deviceDoc.id
                    
                    // Check if device is already connected
                    val isConnected = deviceDoc.getBoolean("isConnected") ?: false
                    
                    if (isConnected) {
                        // Device is already connected
                        Toast.makeText(this, "Device is already connected!", Toast.LENGTH_LONG).show()
                        binding.tvStatus.text = "Device Already Connected!"
                        binding.tvStatus.setTextColor(resources.getColor(R.color.teal_500, null))
                        binding.btnConnect.isEnabled = false
                        binding.etLinkingCode.isEnabled = false
                    } else {
                        // Device found but not connected yet - show waiting message
                        Toast.makeText(this, "Device found! Waiting for parent to complete setup...", Toast.LENGTH_LONG).show()
                        binding.tvStatus.text = "Device Found! Waiting for parent to complete setup..."
                        binding.tvStatus.setTextColor(resources.getColor(R.color.orange, null))
                        binding.btnConnect.isEnabled = false
                        binding.etLinkingCode.isEnabled = false
                        
                        // Start monitoring device status
                        startDeviceStatusMonitoring(deviceId)
                    }
                } else {
                    Toast.makeText(this, "Invalid linking code", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error connecting: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startDeviceStatusMonitoring(deviceId: String) {
        // Monitor device status until it's connected
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                db.collection("devices").document(deviceId)
                    .get()
                    .addOnSuccessListener { deviceDoc ->
                        if (deviceDoc.exists()) {
                            val isConnected = deviceDoc.getBoolean("isConnected") ?: false
                            if (isConnected) {
                                // Device is now connected!
                                binding.tvStatus.text = "Device Connected Successfully!"
                                binding.tvStatus.setTextColor(resources.getColor(R.color.teal_500, null))
                                
                                // Navigate back after a delay
                                handler.postDelayed({
                                    finish()
                                }, 2000)
                            } else {
                                // Still waiting, check again in 2 seconds
                                handler.postDelayed(this, 2000)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        // Error checking, try again in 2 seconds
                        handler.postDelayed(this, 2000)
                    }
            }
        }
        handler.post(runnable)
    }
}

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
    private var deviceName: String = ""
    private var linkingCode: String = ""
    private var childId: String = ""
    private var isChecking = true
    private var isFromAddChild: Boolean = false
    private var isChildConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaitingForSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get extras from intent
        deviceId = intent.getStringExtra("deviceId") ?: ""
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
            // Flow from AddChildActivity - showing reference number for child to use
            binding.tvDeviceName.text = "Child Profile Created"
            binding.tvLinkingCode.text = linkingCode
            binding.tvInstructions.text = "Share this reference number with your child so they can link their device to this profile."
            binding.tvStatus.text = "Waiting for child to enter this code on their device..."
            
            // Hide device-specific buttons for this flow
            binding.btnSetupDevice.visibility = android.view.View.GONE
            binding.btnRemoveDevice.visibility = android.view.View.GONE
            binding.btnBackToChildren.visibility = android.view.View.VISIBLE
            
        } else {
            // Flow from AddDeviceActivity - device document exists
            binding.tvDeviceName.text = deviceName
            binding.tvLinkingCode.text = linkingCode
            binding.tvInstructions.text = "Share this code with your child so they can verify and link their device."
            
            // Show setup button but keep it disabled until child connects
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
        if (isFromAddChild) {
            // Check if child's devices collection has been updated (someone linked using the reference number)
            db.collection("children").document(childId)
                .get()
                .addOnSuccessListener { childDoc ->
                    if (childDoc.exists()) {
                        val devices = childDoc.get("devices") as? HashMap<String, Any> ?: HashMap()
                        if (devices.isNotEmpty()) {
                            isChecking = false // Stop checking
                            binding.tvStatus.text = "Device linked successfully! Child device is now connected."
                            binding.tvStatus.setTextColor(resources.getColor(R.color.teal_500, null))
                            binding.progressBar.visibility = android.view.View.GONE
                            return@addOnSuccessListener
                        }
                    }
                    
                    // Still waiting for child to link
                    binding.tvStatus.text = "Waiting for child to enter this code on their device..."
                    binding.progressBar.visibility = android.view.View.VISIBLE
                }
                .addOnFailureListener { e ->
                    binding.tvStatus.text = "Error checking status: ${e.localizedMessage}"
                    binding.tvStatus.setTextColor(resources.getColor(R.color.error_red, null))
                }
        } else {
            // Check for child device connection first, then device setup
            checkChildDeviceConnection()
        }
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
                                    // Navigate back to parent dashboard
                                    val intent = Intent(this, ParentDashboardActivity::class.java)
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

package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityChildDetailBinding
import android.widget.Button
import androidx.core.content.ContextCompat
class ChildDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildDetailBinding
    private val db = FirebaseFirestore.getInstance()

    private var childId: String = ""
    private var childName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        childId = intent.getStringExtra("childId") ?: ""
        childName = intent.getStringExtra("childName") ?: ""

        if (childId.isEmpty()) {
            Toast.makeText(this, "Missing child info", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        binding.tvChildName.text = childName

        binding.btnAddDevice.setOnClickListener {
            val i = Intent(this, AddDeviceActivity::class.java)
            i.putExtra("childId", childId)
            startActivity(i)
        }

        loadDevices()
    }

    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    private fun loadDevices() {
        // Get the child document to access the devices hashmap
        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener { childDoc ->
                if (childDoc.exists()) {
                    val devices = childDoc.get("devices") as? HashMap<String, String> ?: HashMap()
                    displayDevices(devices)
                } else {
                    displayDevices(HashMap())
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading devices: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                displayDevices(HashMap())
            }
    }

    private fun displayDevices(devices: HashMap<String, String>) {
        val container = binding.devicesContainer
        container.removeAllViews()
        
        if (devices.isEmpty()) {
            binding.btnAddDevice.visibility = android.view.View.VISIBLE
            addInfoText(container, "No devices yet. Add one to get started!")
        } else {
            binding.btnAddDevice.visibility = android.view.View.VISIBLE
            
            // Add header
            val headerText = TextView(this)
            headerText.text = "Connected Devices:"
            headerText.textSize = 16f
            headerText.setTypeface(null, android.graphics.Typeface.BOLD)
            headerText.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
            container.addView(headerText)
            
            // Display each device
            devices.forEach { (deviceId, deviceName) ->
                addDeviceItem(container, deviceId, deviceName)
            }
        }
    }

    private fun addDeviceItem(container: LinearLayout, deviceId: String, deviceName: String) {
        val deviceView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = resources.getDrawable(R.drawable.rectangle_background, null)
            setPadding(16, 12, 16, 12)
            
            // Make the entire device view clickable
            isClickable = true
            isFocusable = true
            foreground = resources.getDrawable(android.R.drawable.list_selector_background, null)
            
            setOnClickListener {
                // Navigate to device settings/edit screen
                val intent = Intent(this@ChildDetailActivity, DeviceSettingsActivity::class.java)
                intent.putExtra("deviceId", deviceId)
                intent.putExtra("deviceName", deviceName)
                intent.putExtra("childId", childId)
                startActivity(intent)
            }
        }

        val icon = TextView(this).apply {
            text = "📱"
            textSize = 20f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
        }

        val nameText = TextView(this).apply {
            text = deviceName
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.black))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { 
                weight = 1f
                marginEnd = 12 
            }
        }

        val statusText = TextView(this).apply {
            text = "Connected"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.teal_500))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 12 }
        }

        // Add an edit icon to indicate the device is clickable
        val editIcon = TextView(this).apply {
            text = "✏️"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        deviceView.addView(icon)
        deviceView.addView(nameText)
        deviceView.addView(statusText)
        deviceView.addView(editIcon)
        container.addView(deviceView)
    }

    private fun addInfoText(container: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 14f
        tv.setTextColor(resources.getColor(R.color.gray_500, null))
        tv.gravity = android.view.Gravity.CENTER
        tv.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 32
        }
        container.addView(tv)
    }

    private fun showRemoveDeviceConfirmation(deviceId: String, deviceName: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Remove Device")
            .setMessage("Are you sure you want to remove '$deviceName'? This action cannot be undone.")
            .setPositiveButton("Remove") { _, _ ->
                removeDevice(deviceId, deviceName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeDevice(deviceId: String, deviceName: String) {
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
                                    // Refresh the devices list
                                    loadDevices()
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


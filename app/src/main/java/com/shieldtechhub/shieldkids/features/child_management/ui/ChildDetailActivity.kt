package com.shieldtechhub.shieldkids.features.child_management.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.shieldtechhub.shieldkids.common.utils.FirestoreSyncManager
import com.shieldtechhub.shieldkids.common.utils.ImageLoader
import com.shieldtechhub.shieldkids.databinding.ActivityChildDetailBinding
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager
import com.shieldtechhub.shieldkids.features.screen_time.ui.ScreenTimeDashboardActivity
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import com.shieldtechhub.shieldkids.features.settings.DeviceSettingsActivity
import com.shieldtechhub.shieldkids.features.settings.SettingsActivity
import com.shieldtechhub.shieldkids.features.device_setup.ui.AddDeviceActivity
import com.shieldtechhub.shieldkids.features.dashboard.ParentDashboardActivity
import com.shieldtechhub.shieldkids.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
class ChildDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildDetailBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var screenTimeCollector: ScreenTimeCollector
    private lateinit var appInventoryManager: AppInventoryManager
    private lateinit var screenTimeService: ScreenTimeService
    private var devicesListener: ListenerRegistration? = null

    private var childId: String = ""
    private var childName: String = ""
    private var profileImageUri: String = ""
    private var birthYear: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize services
        screenTimeCollector = ScreenTimeCollector.getInstance(this)
        appInventoryManager = AppInventoryManager(this)
        screenTimeService = ScreenTimeService.getInstance(this)

        // Get intent data
        childId = intent.getStringExtra("childId") ?: ""
        childName = intent.getStringExtra("childName") ?: ""
        profileImageUri = intent.getStringExtra("profileImageUri") ?: ""
        birthYear = intent.getLongExtra("birthYear", 0)

        if (childId.isEmpty()) {
            Toast.makeText(this, "Missing child info", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        setupUI()
        setupClickListeners()
        loadChildData()
    }

    override fun onResume() {
        super.onResume()
        // no-op; devices listener will keep devices in sync
    }

    private fun setupUI() {
        // Set child name
        binding.tvChildName.text = childName
        
        // Set device status to Active by default
        binding.tvDeviceStatus.text = "Active"

        // Set profile image
        ImageLoader.loadInto(this, binding.ivChildAvatar, profileImageUri, R.drawable.kidprofile)
        
        // Set loading state for screen time
        binding.tvTimeLimit.text = "Loading..."
    }

    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // Delete child button
        binding.btnDeleteChild.setOnClickListener {
            showDeleteChildConfirmation()
        }

        // Action buttons
        binding.btnRequests.setOnClickListener {
            Toast.makeText(this, "Requests feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnScreenTime.setOnClickListener {
            Toast.makeText(this, "Select a device to view screen time", Toast.LENGTH_SHORT).show()
        }

        binding.btnHistory.setOnClickListener {
            Toast.makeText(this, "History feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Add Device button
        binding.btnAddDevice.setOnClickListener {
            val intent = Intent(this, AddDeviceActivity::class.java)
            intent.putExtra("childId", childId)
            startActivity(intent)
        }


        // Most visited location
        binding.btnLocation.setOnClickListener {
            Toast.makeText(this, "Location feature coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Bottom navigation
        binding.navHome.setOnClickListener {
            finish() // Go back to parent dashboard
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

    private fun loadChildData() {
        loadDevices()
        loadScreenTimeData()
    }

    private fun loadDevices() {
        devicesListener?.remove()
        devicesListener = FirestoreSyncManager.listenChildDevices(childId) { devices, _ ->
            displayDevices(devices)
        }
    }

    private fun displayDevices(devices: HashMap<String, Any>) {
        val container = binding.devicesContainer
        container.removeAllViews()
        
        // Update status indicator
        updateDeviceStatusIndicator(devices)
        
        if (devices.isEmpty()) {
            // Show placeholder message
            addEmptyDeviceMessage(container)
        } else {
            // Display each device
            devices.forEach { (deviceId, deviceData) ->
                when (deviceData) {
                    is String -> {
                        // Legacy format: device name is stored as string
                        addDeviceIcon(container, deviceId, deviceData, "android")
                    }
                    is Map<*, *> -> {
                        // New format: device info is stored as map
                        val deviceInfo = deviceData as Map<String, Any>
                        val deviceName = deviceInfo["deviceName"] as? String ?: "Unknown Device"
                        val deviceType = deviceInfo["deviceType"] as? String ?: "android"
                        addDeviceIcon(container, deviceId, deviceName, deviceType)
                    }
                    else -> {
                        // Unknown format, use fallback
                        addDeviceIcon(container, deviceId, "Unknown Device", "android")
                    }
                }
            }
        }
    }

    private fun updateDeviceStatusIndicator(devices: HashMap<String, Any>) {
        val statusIndicator = binding.deviceStatusIndicator
        if (devices.isEmpty()) {
            statusIndicator.setBackgroundResource(R.drawable.circle_gray_dot)
            binding.tvDeviceStatus.text = "Offline"
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
        } else {
            statusIndicator.setBackgroundResource(R.drawable.circle_green_dot)
            binding.tvDeviceStatus.text = "Active"
            binding.tvDeviceStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_600))
        }
    }

    private fun addEmptyDeviceMessage(container: LinearLayout) {
        val messageView = TextView(this).apply {
            text = "No devices linked yet"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@ChildDetailActivity, R.color.gray_500))
            gravity = android.view.Gravity.CENTER
            setPadding(16, 32, 16, 32)
        }
        container.addView(messageView)
    }

    private suspend fun getChildDevices(): HashMap<String, Any> {
        return try {
            // Use the existing Firebase sync method to get devices
            val snapshot = db.collection("children").document(childId).collection("devices").get().await()
            val devices = hashMapOf<String, Any>()
            snapshot.documents.forEach { doc ->
                devices[doc.id] = doc.data ?: emptyMap<String, Any>()
            }
            devices
        } catch (e: Exception) {
            Log.e("ChildDetailActivity", "Failed to get child devices", e)
            hashMapOf<String, Any>()
        }
    }

    private fun addDeviceIcon(container: LinearLayout, deviceId: String, deviceName: String, deviceType: String) {
        val deviceView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 24
            }
            setPadding(8, 8, 8, 8)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(this@ChildDetailActivity, android.R.drawable.list_selector_background)
            
            setOnClickListener {
                // Show options dialog for device actions
                showDeviceOptionsDialog(deviceId, deviceName)
            }
        }

        // Device icon
        val deviceIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(128, 128).apply {
                bottomMargin = 8
            }
            background = ContextCompat.getDrawable(this@ChildDetailActivity, R.drawable.circle_device_bg)
            setPadding(16, 16, 16, 16)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            
            // Set appropriate icon based on device type
            when (deviceType.lowercase()) {
                "windows" -> {
                    setImageResource(R.drawable.ic_device_windows)
                }
                "android" -> {
                    setImageResource(R.drawable.ic_device_android)
                }
                "ios" -> {
                    setImageResource(R.drawable.ic_device_ios)
                }
                else -> {
                    setImageResource(R.drawable.ic_device_android) // Default to Android
                }
            }
            
            setColorFilter(ContextCompat.getColor(this@ChildDetailActivity, R.color.white))
        }

        // Device name
        val deviceNameView = TextView(this).apply {
            text = deviceName
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@ChildDetailActivity, R.color.gray_600))
            gravity = android.view.Gravity.CENTER
            maxLines = 1
            setSingleLine(true)
            // Truncate long names
            if (deviceName.length > 10) {
                this.text = "${deviceName.take(8)}.."
            }
        }

        deviceView.addView(deviceIcon)
        deviceView.addView(deviceNameView)
        container.addView(deviceView)
    }


    private fun loadScreenTimeData() {
        lifecycleScope.launch {
            try {
                Log.d("ChildDetailActivity", "Loading aggregated screen time data for child: $childId")
                
                // Get child devices first
                val devices = getChildDevices()
                if (devices.isEmpty()) {
                    Log.d("ChildDetailActivity", "No devices found for child: $childId")
                    binding.tvTimeLimit.text = "No devices"
                    return@launch
                }
                
                // Calculate total screen time across all devices
                val today = Date()
                var totalScreenTimeMs = 0L
                var devicesWithData = 0
                
                devices.keys.forEach { deviceId ->
                    try {
                        val deviceUsageData = screenTimeService.getDailyUsageFromFirebase(today, childId, deviceId)
                        if (deviceUsageData != null) {
                            val deviceScreenTime = deviceUsageData["totalScreenTimeMs"] as? Long ?: 0L
                            totalScreenTimeMs += deviceScreenTime
                            if (deviceScreenTime > 0) devicesWithData++
                        }
                    } catch (e: Exception) {
                        Log.e("ChildDetailActivity", "Error loading data for device $deviceId", e)
                    }
                }
                
                if (totalScreenTimeMs > 0) {
                    val totalMinutes = totalScreenTimeMs / (1000 * 60)
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    
                    binding.tvTimeLimit.text = if (hours > 0) {
                        "${hours}h ${minutes}m"
                    } else if (minutes > 0) {
                        "${minutes}m"
                    } else {
                        "<1m"
                    }
                    
                    Log.d("ChildDetailActivity", "Loaded aggregated screen time: ${binding.tvTimeLimit.text} across $devicesWithData devices")
                } else {
                    Log.d("ChildDetailActivity", "No screen time data found across ${devices.size} devices")
                    binding.tvTimeLimit.text = "No data today"
                }
                
            } catch (e: Exception) {
                Log.e("ChildDetailActivity", "Failed to load aggregated screen time data", e)
                binding.tvTimeLimit.text = "Error loading"
            }
        }
    }
    
    private fun showDeviceOptionsDialog(deviceId: String, deviceName: String) {
        // Navigate directly to device settings which now includes screen time
        val intent = Intent(this, DeviceSettingsActivity::class.java)
        intent.putExtra("deviceId", deviceId)
        intent.putExtra("deviceName", deviceName)
        intent.putExtra("childId", childId)
        startActivity(intent)
    }

    private fun showDeleteChildConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Child Account")
            .setMessage("Are you sure you want to permanently delete $childName's account? This will:\n\n• Remove all linked devices\n• Delete all monitoring data\n• Cannot be undone\n\nThis action is irreversible.")
            .setPositiveButton("Delete") { _, _ ->
                deleteChild()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteChild() {
        // Show loading
        Toast.makeText(this, "Deleting child account...", Toast.LENGTH_SHORT).show()

        // First get all devices to delete them
        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener { childDoc ->
                if (childDoc.exists()) {
                    val devices = childDoc.get("devices") as? HashMap<String, Any> ?: HashMap()
                    
                    // Delete all associated devices first
                    if (devices.isNotEmpty()) {
                        var deletedDeviceCount = 0
                        val totalDevices = devices.size
                        
                        devices.keys.forEach { deviceId ->
                            db.collection("devices").document(deviceId)
                                .delete()
                                .addOnSuccessListener {
                                    deletedDeviceCount++
                                    if (deletedDeviceCount == totalDevices) {
                                        // All devices deleted, now delete child
                                        deleteChildDocument()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    // Continue even if device deletion fails
                                    deletedDeviceCount++
                                    if (deletedDeviceCount == totalDevices) {
                                        // All device deletion attempts completed
                                        deleteChildDocument()
                                    }
                                }
                        }
                    } else {
                        // No devices, directly delete child
                        deleteChildDocument()
                    }
                } else {
                    Toast.makeText(this, "Child not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete child: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun deleteChildDocument() {
        // Delete the child document
        db.collection("children").document(childId)
            .delete()
            .addOnSuccessListener {
                // Also remove from parent's children list
                removeChildFromParent()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete child document: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun removeChildFromParent() {
        // Remove child from parent's children array
        // We need to get the current parent ID - this should be stored in SharedPreferences or passed as intent extra
        val parentId = getSharedPreferences("shield_prefs", MODE_PRIVATE).getString("currentUserId", "") ?: ""
        
        if (parentId.isNotEmpty()) {
            db.collection("parents").document(parentId)
                .get()
                .addOnSuccessListener { parentDoc ->
                    if (parentDoc.exists()) {
                        // Handle both HashMap and ArrayList formats
                        val childrenData = parentDoc.get("children")
                        
                        val updateTask = when (childrenData) {
                            is HashMap<*, *> -> {
                                // Children stored as HashMap<childId, childName>
                                val currentChildren = childrenData as HashMap<String, String>
                                currentChildren.remove(childId)
                                parentDoc.reference.update("children", currentChildren)
                            }
                            is ArrayList<*> -> {
                                // Children stored as ArrayList<childId>
                                val currentChildren = childrenData as ArrayList<String>
                                currentChildren.remove(childId)
                                parentDoc.reference.update("children", currentChildren)
                            }
                            else -> {
                                // Create empty HashMap if no children data exists
                                parentDoc.reference.update("children", HashMap<String, String>())
                            }
                        }
                        
                        updateTask
                            .addOnSuccessListener {
                                Toast.makeText(this, "$childName has been permanently deleted", Toast.LENGTH_SHORT).show()
                                // Navigate back to parent dashboard
                                val intent = Intent(this, ParentDashboardActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Child deleted but failed to update parent: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                    } else {
                        Toast.makeText(this, "Child deleted successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
        } else {
            Toast.makeText(this, "Child deleted successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        devicesListener?.remove()
        devicesListener = null
    }

}


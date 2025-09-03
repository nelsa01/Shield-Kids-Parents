package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityDeviceSettingsBinding
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import com.shieldtechhub.shieldkids.adapters.TopAppsAdapter
import com.shieldtechhub.shieldkids.adapters.TopAppItem
import com.shieldtechhub.shieldkids.debug.ScreenTimeDebugHelper
import com.shieldtechhub.shieldkids.debug.DeviceIdDebugHelper
import kotlinx.coroutines.launch
import java.util.Date

class DeviceSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeviceSettingsBinding
    private val db = FirebaseFirestore.getInstance()
    private lateinit var screenTimeService: ScreenTimeService
    
    private var deviceId: String = ""
    private var deviceName: String = ""
    private var childId: String = ""
    
    companion object {
        private const val TAG = "DeviceSettingsActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        deviceName = intent.getStringExtra("deviceName") ?: "Unknown Device"
        childId = intent.getStringExtra("childId") ?: ""
        
        // Initialize services
        screenTimeService = ScreenTimeService.getInstance(this)
        
        setupUI()
        setupClickListeners()
        loadScreenTimeData()
    }
    
    private fun setupUI() {
        // Set device name
        binding.tvDeviceName.text = deviceName
        
        // Set device email (placeholder for now)
        binding.tvDeviceEmail.text = "device@shieldtechclub.com"
        
        // Add debug functionality - long click on device name for debug
        binding.tvDeviceName.setOnLongClickListener {
            Log.d(TAG, "🐛 MANUAL DEBUG TRIGGER - Long click on device name")
            lifecycleScope.launch {
                val debugResults = ScreenTimeDebugHelper.debugFirebaseScreenTimeAccess(childId, deviceId)
                Log.d(TAG, "🐛 MANUAL DEBUG RESULTS:\n$debugResults")
                Toast.makeText(this@DeviceSettingsActivity, "Debug logged - check LogCat for 'ScreenTimeDebug'", Toast.LENGTH_LONG).show()
            }
            true
        }
        
        // Firebase debug - triple tap on device initials
        var tapCount = 0
        binding.tvDeviceInitials.setOnClickListener {
            tapCount++
            Handler(Looper.getMainLooper()).postDelayed({
                if (tapCount >= 3) {
                    // Triple tap detected - open Firebase debug
                    val intent = Intent(this, com.shieldtechhub.shieldkids.debug.FirebaseDebugActivity::class.java)
                    startActivity(intent)
                }
                tapCount = 0
            }, 500)
        }
        
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
            val intent = Intent(this, com.shieldtechhub.shieldkids.features.screen_time.ui.ScreenTimeReportsActivity::class.java)
            intent.putExtra("childId", childId)
            intent.putExtra("deviceId", deviceId)
            intent.putExtra("deviceName", deviceName)
            startActivity(intent)
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
    
    private fun loadScreenTimeData() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "🚀 Starting loadScreenTimeData() debug")
                Log.d(TAG, "📍 DeviceSettingsActivity - Screen Time Card")
                
                // Run comprehensive debug
                val debugResults = ScreenTimeDebugHelper.debugFirebaseScreenTimeAccess(childId, deviceId)
                Log.d(TAG, "📋 Debug Results:\n$debugResults")
                
                // Also debug device IDs to see actual vs expected
                val deviceIdDebug = DeviceIdDebugHelper.debugDeviceIds(childId)
                Log.d(TAG, "🔍 Device ID Debug:\n$deviceIdDebug")
                
                val today = Date()
                // Use the new method that reads from app inventory document
                val usageData = screenTimeService.getScreenTimeFromAppInventory(childId, deviceId)
                
                if (usageData != null) {
                    // Extract data from Firebase response
                    val totalScreenTimeMs = usageData["totalScreenTimeMs"] as? Long ?: 0L
                    val screenUnlocks = usageData["screenUnlocks"] as? Long ?: 0L
                    val appCount = usageData["appCount"] as? Long ?: 0L
                    val topAppsData = usageData["topApps"] as? List<Map<String, Any>> ?: emptyList()
                    
                    // Update UI
                    updateScreenTimeUI(totalScreenTimeMs, screenUnlocks.toInt(), appCount.toInt(), topAppsData)
                    
                    Log.d(TAG, "Screen time data loaded successfully")
                } else {
                    Log.d(TAG, "No screen time data found for today")
                    updateScreenTimeUI(0L, 0, 0, emptyList())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load screen time data", e)
                updateScreenTimeUI(0L, 0, 0, emptyList())
            }
        }
    }
    
    private fun updateScreenTimeUI(totalTimeMs: Long, unlocks: Int, appCount: Int, topApps: List<Map<String, Any>>) {
        // Format total time
        val totalMinutes = totalTimeMs / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        val formattedTime = when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            totalTimeMs > 0 -> "<1m"
            else -> "No data"
        }
        
        // Update main stats
        binding.tvScreenTimeTotal.text = formattedTime
        binding.tvAppsUsed.text = appCount.toString()
        binding.tvScreenUnlocks.text = unlocks.toString()
        
        // Update top apps list
        val topAppItems = topApps.take(5).map { appData ->
            val appName = appData["appName"] as? String ?: "Unknown App"
            val packageName = appData["packageName"] as? String ?: ""
            val appTimeMs = appData["totalTimeMs"] as? Long ?: 0L
            val category = appData["category"] as? String ?: "Other"
            
            val appMinutes = appTimeMs / (1000 * 60)
            val appFormattedTime = when {
                appMinutes >= 60 -> "${appMinutes / 60}h ${appMinutes % 60}m"
                appMinutes > 0 -> "${appMinutes}m"
                appTimeMs > 0 -> "<1m"
                else -> "0m"
            }
            
            TopAppItem(appName, packageName, appFormattedTime, category)
        }
        
        // Set up RecyclerView
        binding.rvTopApps.layoutManager = LinearLayoutManager(this)
        binding.rvTopApps.adapter = TopAppsAdapter(topAppItems)
        
        Log.d(TAG, "Screen time UI updated: $formattedTime, $appCount apps, $unlocks unlocks")
    }
}

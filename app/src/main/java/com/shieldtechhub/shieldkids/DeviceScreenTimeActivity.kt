package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.view.View
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.shieldtechhub.shieldkids.databinding.ActivityDeviceScreenTimeBinding
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.PolicySyncManager
import com.shieldtechhub.shieldkids.features.policy.model.DevicePolicy
import kotlinx.coroutines.launch

class DeviceScreenTimeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityDeviceScreenTimeBinding
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var policySyncManager: PolicySyncManager
    
    private var deviceId: String = ""
    private var childId: String = ""
    private var currentPolicy: DevicePolicy? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceScreenTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get intent data
        deviceId = intent.getStringExtra("deviceId") ?: ""
        childId = intent.getStringExtra("childId") ?: ""
        
        if (deviceId.isEmpty() || childId.isEmpty()) {
            Toast.makeText(this, "Missing device information", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize managers
        policyManager = PolicyEnforcementManager.getInstance(this)
        policySyncManager = PolicySyncManager.getInstance(this)
        
        setupUI()
        loadCurrentPolicy()
    }
    
    private fun setupUI() {
        // Set toolbar
        binding.toolbar.title = "Screen Time Limits"
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        // Setup click listeners
        setupWeekdayScreenTime()
        setupWeekendScreenTime()
        setupWeeklyScreenTime()
        setupBedtime()
        setupBreakReminders()
        setupUsageWarnings()
        setupAdvancedSettings()
        
        // Save button
        binding.btnSave.setOnClickListener {
            saveScreenTimeSettings()
        }
        
        // Reset to defaults button
        binding.btnReset.setOnClickListener {
            showResetDialog()
        }
    }
    
    private fun setupWeekdayScreenTime() {
        binding.layoutWeekdayTime.setOnClickListener {
            val currentMinutes = currentPolicy?.weekdayScreenTime?.toInt() ?: 120
            showTimePickerDialog("Weekday Screen Time", currentMinutes) { minutes ->
                binding.tvWeekdayTime.text = formatMinutesToHoursMinutes(minutes)
                binding.tvWeekdayTime.tag = minutes
            }
        }
    }
    
    private fun setupWeekendScreenTime() {
        binding.layoutWeekendTime.setOnClickListener {
            val currentMinutes = currentPolicy?.weekendScreenTime?.toInt() ?: 180
            showTimePickerDialog("Weekend Screen Time", currentMinutes) { minutes ->
                binding.tvWeekendTime.text = formatMinutesToHoursMinutes(minutes)
                binding.tvWeekendTime.tag = minutes
            }
        }
    }
    
    private fun setupWeeklyScreenTime() {
        binding.switchWeeklyLimit.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutWeeklyTime.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.layoutWeeklyTime.setOnClickListener {
            val currentMinutes = currentPolicy?.weeklyScreenTime?.toInt() ?: (7 * 120) // 7 days * 2 hours
            showTimePickerDialog("Weekly Screen Time", currentMinutes, true) { minutes ->
                binding.tvWeeklyTime.text = formatMinutesToDaysHours(minutes)
                binding.tvWeeklyTime.tag = minutes
            }
        }
    }
    
    private fun setupBedtime() {
        binding.switchBedtime.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutBedtimeSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.layoutBedtimeStart.setOnClickListener {
            val currentTime = currentPolicy?.bedtimeStart ?: "21:00"
            showTimePickerDialog(currentTime) { hour, minute ->
                val timeString = String.format("%02d:%02d", hour, minute)
                binding.tvBedtimeStart.text = timeString
                binding.tvBedtimeStart.tag = timeString
            }
        }
        
        binding.layoutBedtimeEnd.setOnClickListener {
            val currentTime = currentPolicy?.bedtimeEnd ?: "07:00"
            showTimePickerDialog(currentTime) { hour, minute ->
                val timeString = String.format("%02d:%02d", hour, minute)
                binding.tvBedtimeEnd.text = timeString
                binding.tvBedtimeEnd.tag = timeString
            }
        }
    }
    
    private fun setupBreakReminders() {
        binding.switchBreakReminders.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutBreakSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.layoutBreakInterval.setOnClickListener {
            val currentMinutes = currentPolicy?.breakInterval?.toInt() ?: 60
            showNumberPickerDialog("Break Reminder Interval", currentMinutes, 15, 180, "minutes") { minutes ->
                binding.tvBreakInterval.text = "$minutes minutes"
                binding.tvBreakInterval.tag = minutes
            }
        }
        
        binding.layoutBreakDuration.setOnClickListener {
            val currentMinutes = currentPolicy?.breakDuration?.toInt() ?: 15
            showNumberPickerDialog("Break Duration", currentMinutes, 5, 60, "minutes") { minutes ->
                binding.tvBreakDuration.text = "$minutes minutes"
                binding.tvBreakDuration.tag = minutes
            }
        }
    }
    
    private fun setupUsageWarnings() {
        binding.switchUsageWarnings.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutWarningSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.layoutGracePeriod.setOnClickListener {
            val currentMinutes = currentPolicy?.gracePeriod?.toInt() ?: 5
            showNumberPickerDialog("Grace Period", currentMinutes, 0, 15, "minutes") { minutes ->
                binding.tvGracePeriod.text = if (minutes == 0) "No grace period" else "$minutes minutes"
                binding.tvGracePeriod.tag = minutes
            }
        }
    }
    
    private fun setupAdvancedSettings() {
        binding.layoutAdvancedSettings.setOnClickListener {
            // TODO: Open advanced settings dialog
            Toast.makeText(this, "Advanced settings coming soon", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadCurrentPolicy() {
        lifecycleScope.launch {
            try {
                val policies = policyManager.activePolicies.value
                currentPolicy = policies[deviceId]
                
                updateUI()
                
            } catch (e: Exception) {
                Toast.makeText(this@DeviceScreenTimeActivity, "Failed to load current settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateUI() {
        currentPolicy?.let { policy ->
            // Weekday/Weekend screen time
            binding.tvWeekdayTime.text = formatMinutesToHoursMinutes(policy.weekdayScreenTime.toInt())
            binding.tvWeekdayTime.tag = policy.weekdayScreenTime.toInt()
            
            binding.tvWeekendTime.text = formatMinutesToHoursMinutes(policy.weekendScreenTime.toInt())
            binding.tvWeekendTime.tag = policy.weekendScreenTime.toInt()
            
            // Weekly limit
            if (policy.weeklyScreenTime > 0) {
                binding.switchWeeklyLimit.isChecked = true
                binding.layoutWeeklyTime.visibility = View.VISIBLE
                binding.tvWeeklyTime.text = formatMinutesToDaysHours(policy.weeklyScreenTime.toInt())
                binding.tvWeeklyTime.tag = policy.weeklyScreenTime.toInt()
            }
            
            // Bedtime
            if (policy.bedtimeStart != null && policy.bedtimeEnd != null) {
                binding.switchBedtime.isChecked = true
                binding.layoutBedtimeSettings.visibility = View.VISIBLE
                binding.tvBedtimeStart.text = policy.bedtimeStart
                binding.tvBedtimeStart.tag = policy.bedtimeStart
                binding.tvBedtimeEnd.text = policy.bedtimeEnd
                binding.tvBedtimeEnd.tag = policy.bedtimeEnd
            }
            
            // Break reminders
            if (policy.breakReminders) {
                binding.switchBreakReminders.isChecked = true
                binding.layoutBreakSettings.visibility = View.VISIBLE
                binding.tvBreakInterval.text = "${policy.breakInterval} minutes"
                binding.tvBreakInterval.tag = policy.breakInterval.toInt()
                binding.tvBreakDuration.text = "${policy.breakDuration} minutes"
                binding.tvBreakDuration.tag = policy.breakDuration.toInt()
            }
            
            // Usage warnings
            binding.switchUsageWarnings.isChecked = policy.usageWarnings
            if (policy.usageWarnings) {
                binding.layoutWarningSettings.visibility = View.VISIBLE
                binding.tvGracePeriod.text = if (policy.gracePeriod == 0L) "No grace period" else "${policy.gracePeriod} minutes"
                binding.tvGracePeriod.tag = policy.gracePeriod.toInt()
            }
        } ?: run {
            // Set default values
            binding.tvWeekdayTime.text = "2 hours"
            binding.tvWeekdayTime.tag = 120
            binding.tvWeekendTime.text = "3 hours"
            binding.tvWeekendTime.tag = 180
            binding.tvBedtimeStart.text = "21:00"
            binding.tvBedtimeStart.tag = "21:00"
            binding.tvBedtimeEnd.text = "07:00"
            binding.tvBedtimeEnd.tag = "07:00"
            binding.tvBreakInterval.text = "60 minutes"
            binding.tvBreakInterval.tag = 60
            binding.tvBreakDuration.text = "15 minutes"
            binding.tvBreakDuration.tag = 15
            binding.tvGracePeriod.text = "5 minutes"
            binding.tvGracePeriod.tag = 5
        }
    }
    
    private fun saveScreenTimeSettings() {
        lifecycleScope.launch {
            try {
                val updatedPolicy = buildUpdatedPolicy()
                
                val success = policyManager.applyDevicePolicy(deviceId, updatedPolicy)
                if (success) {
                    // Sync to Firebase
                    val syncSuccess = policySyncManager.savePolicyToFirebase(childId, deviceId, updatedPolicy)
                    if (syncSuccess) {
                        Toast.makeText(this@DeviceScreenTimeActivity, "Screen time settings saved", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@DeviceScreenTimeActivity, "Settings saved locally but failed to sync to child device", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@DeviceScreenTimeActivity, "Failed to save settings", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@DeviceScreenTimeActivity, "Error saving settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun buildUpdatedPolicy(): DevicePolicy {
        val basePolicy = currentPolicy ?: DevicePolicy.createDefault(deviceId)
        
        return basePolicy.copy(
            weekdayScreenTime = (binding.tvWeekdayTime.tag as? Int)?.toLong() ?: 120L,
            weekendScreenTime = (binding.tvWeekendTime.tag as? Int)?.toLong() ?: 180L,
            weeklyScreenTime = if (binding.switchWeeklyLimit.isChecked) {
                (binding.tvWeeklyTime.tag as? Int)?.toLong() ?: 0L
            } else 0L,
            bedtimeStart = if (binding.switchBedtime.isChecked) binding.tvBedtimeStart.tag as? String else null,
            bedtimeEnd = if (binding.switchBedtime.isChecked) binding.tvBedtimeEnd.tag as? String else null,
            breakReminders = binding.switchBreakReminders.isChecked,
            breakInterval = (binding.tvBreakInterval.tag as? Int)?.toLong() ?: 60L,
            breakDuration = (binding.tvBreakDuration.tag as? Int)?.toLong() ?: 15L,
            usageWarnings = binding.switchUsageWarnings.isChecked,
            gracePeriod = (binding.tvGracePeriod.tag as? Int)?.toLong() ?: 5L,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    private fun showResetDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset to Defaults")
            .setMessage("Are you sure you want to reset all screen time settings to default values?")
            .setPositiveButton("Reset") { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun resetToDefaults() {
        currentPolicy = DevicePolicy.createDefault(deviceId)
        updateUI()
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }
    
    // Helper methods for dialogs
    private fun showTimePickerDialog(title: String, currentMinutes: Int, isWeekly: Boolean = false, onTimeSet: (Int) -> Unit) {
        val hours = currentMinutes / 60
        val minutes = currentMinutes % 60
        
        val timePicker = TimePicker(this).apply {
            setIs24HourView(true)
            hour = if (isWeekly) hours else hours.coerceAtMost(23)
            minute = minutes
        }
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(timePicker)
            .setPositiveButton("OK") { _, _ ->
                val selectedMinutes = (timePicker.hour * 60) + timePicker.minute
                onTimeSet(selectedMinutes)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showTimePickerDialog(currentTime: String, onTimeSet: (Int, Int) -> Unit) {
        val parts = currentTime.split(":")
        val hour = parts[0].toIntOrNull() ?: 21
        val minute = parts[1].toIntOrNull() ?: 0
        
        val timePicker = TimePicker(this).apply {
            setIs24HourView(true)
            this.hour = hour
            this.minute = minute
        }
        
        AlertDialog.Builder(this)
            .setTitle("Select Time")
            .setView(timePicker)
            .setPositiveButton("OK") { _, _ ->
                onTimeSet(timePicker.hour, timePicker.minute)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showNumberPickerDialog(title: String, current: Int, min: Int, max: Int, unit: String, onValueSet: (Int) -> Unit) {
        val values = (min..max).toList().toTypedArray()
        val currentIndex = (current - min).coerceIn(0, values.size - 1)
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(values.map { "$it $unit" }.toTypedArray(), currentIndex) { dialog, which ->
                onValueSet(values[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // Utility methods
    private fun formatMinutesToHoursMinutes(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        return when {
            hours == 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }
    
    private fun formatMinutesToDaysHours(totalMinutes: Int): String {
        val days = totalMinutes / (24 * 60)
        val remainingMinutes = totalMinutes % (24 * 60)
        val hours = remainingMinutes / 60
        val minutes = remainingMinutes % 60
        
        return when {
            days > 0 && hours > 0 -> "${days}d ${hours}h"
            days > 0 -> "${days}d"
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}
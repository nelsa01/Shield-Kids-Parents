package com.shieldtechhub.shieldkids.features.core

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.common.base.ShieldDeviceAdminReceiver
import com.shieldtechhub.shieldkids.common.utils.DeviceAdminManager

/**
 * 🆘 Emergency Reactivation Activity
 * 
 * Critical security screen shown when Device Admin is disabled.
 * Forces immediate reactivation with countdown timer and parent notification.
 */
class EmergencyReactivationActivity : Activity() {
    
    private lateinit var deviceAdminManager: DeviceAdminManager
    private var countdownTimer: CountDownTimer? = null
    private var reactivationAttempts = 0
    
    companion object {
        private const val COUNTDOWN_TIME_MS = 60000L // 60 seconds
        private const val MAX_REACTIVATION_ATTEMPTS = 3
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_reactivation)
        
        deviceAdminManager = DeviceAdminManager(this)
        
        setupCriticalDisplay()
        setupReactivationInterface()
        startEmergencyCountdown()
        notifyParentOfCriticalSituation()
    }
    
    private fun setupCriticalDisplay() {
        // Make this activity persistent and urgent
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        // Set critical alert styling
        findViewById<TextView>(R.id.tv_emergency_title).text = "🆘 CRITICAL SECURITY BREACH"
        findViewById<TextView>(R.id.tv_emergency_message).text = """
            Shield Kids protection has been DISABLED!
            
            This is a SERIOUS SECURITY VIOLATION that puts your child at risk.
            
            • All parental controls are now INACTIVE
            • Parents have been immediately notified
            • This incident is being logged and reported
            • Device security is COMPROMISED
            
            IMMEDIATE ACTION REQUIRED:
            Device Admin protection must be re-enabled NOW to restore safety.
        """.trimIndent()
    }
    
    private fun setupReactivationInterface() {
        val btnReactivate = findViewById<Button>(R.id.btn_reactivate_now)
        val btnContactParent = findViewById<Button>(R.id.btn_contact_parent_emergency)
        val tvAttemptCount = findViewById<TextView>(R.id.tv_attempt_count)
        
        btnReactivate.setOnClickListener {
            attemptReactivation()
        }
        
        btnContactParent.setOnClickListener {
            initiateParentContact()
        }
        
        // Update attempt counter
        updateAttemptCounter()
    }
    
    private fun startEmergencyCountdown() {
        val progressBar = findViewById<ProgressBar>(R.id.progress_countdown)
        val tvCountdown = findViewById<TextView>(R.id.tv_countdown)
        
        progressBar.max = (COUNTDOWN_TIME_MS / 1000).toInt()
        
        countdownTimer = object : CountDownTimer(COUNTDOWN_TIME_MS, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                progressBar.progress = secondsRemaining
                
                tvCountdown.text = when {
                    secondsRemaining > 30 -> "⏰ Time to resolve: ${secondsRemaining}s"
                    secondsRemaining > 10 -> "⚠️ URGENT: ${secondsRemaining}s remaining"
                    else -> "🚨 CRITICAL: ${secondsRemaining}s"
                }
                
                // Change colors as time runs out
                when {
                    secondsRemaining <= 10 -> {
                        tvCountdown.setTextColor(getColor(android.R.color.holo_red_dark))
                        tvCountdown.textSize = 18f
                    }
                    secondsRemaining <= 30 -> {
                        tvCountdown.setTextColor(getColor(android.R.color.holo_orange_dark))
                    }
                }
            }
            
            override fun onFinish() {
                handleCountdownExpiry()
            }
        }.start()
    }
    
    private fun attemptReactivation() {
        reactivationAttempts++
        updateAttemptCounter()
        
        if (reactivationAttempts >= MAX_REACTIVATION_ATTEMPTS) {
            handleMaxAttemptsReached()
            return
        }
        
        // Check if Device Admin is actually inactive
        if (deviceAdminManager.isDeviceAdminActive()) {
            handleReactivationSuccess()
            return
        }
        
        // Request Device Admin activation
        try {
            deviceAdminManager.requestDeviceAdminActivation(this)
        } catch (e: Exception) {
            handleReactivationFailure(e.message ?: "Unknown error")
        }
    }
    
    private fun updateAttemptCounter() {
        val tvAttemptCount = findViewById<TextView>(R.id.tv_attempt_count)
        val remaining = MAX_REACTIVATION_ATTEMPTS - reactivationAttempts
        
        tvAttemptCount.text = when {
            remaining > 1 -> "Reactivation attempts remaining: $remaining"
            remaining == 1 -> "⚠️ FINAL ATTEMPT: $remaining attempt remaining"
            else -> "🚨 NO ATTEMPTS REMAINING - Contact parent immediately"
        }
        
        if (remaining <= 1) {
            tvAttemptCount.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    private fun handleReactivationSuccess() {
        countdownTimer?.cancel()
        
        findViewById<TextView>(R.id.tv_status_message).apply {
            text = "✅ SUCCESS: Shield Kids protection reactivated!"
            setTextColor(getColor(android.R.color.holo_green_dark))
            visibility = android.view.View.VISIBLE
        }
        
        // Log successful reactivation
        val prefs = getSharedPreferences("emergency_reactivation", MODE_PRIVATE)
        prefs.edit()
            .putLong("last_successful_reactivation", System.currentTimeMillis())
            .putInt("total_attempts", reactivationAttempts)
            .apply()
        
        // Notify parent of resolution
        sendBroadcast(Intent("com.shieldtechhub.shieldkids.REACTIVATION_SUCCESS"))
        
        // Close after short delay
        findViewById<TextView>(R.id.tv_countdown).text = "Closing in 3 seconds..."
        
        android.os.Handler().postDelayed({
            finish()
        }, 3000)
    }
    
    private fun handleReactivationFailure(error: String) {
        findViewById<TextView>(R.id.tv_status_message).apply {
            text = "❌ Reactivation failed: $error"
            setTextColor(getColor(android.R.color.holo_red_dark))
            visibility = android.view.View.VISIBLE
        }
        
        // Log failure
        val prefs = getSharedPreferences("emergency_reactivation", MODE_PRIVATE)
        prefs.edit()
            .putLong("last_failure", System.currentTimeMillis())
            .putString("last_failure_reason", error)
            .apply()
        
        if (reactivationAttempts >= MAX_REACTIVATION_ATTEMPTS) {
            handleMaxAttemptsReached()
        }
    }
    
    private fun handleMaxAttemptsReached() {
        countdownTimer?.cancel()
        
        findViewById<TextView>(R.id.tv_status_message).apply {
            text = """
                🚨 MAXIMUM ATTEMPTS REACHED
                
                Shield Kids protection could not be restored.
                IMMEDIATE PARENT CONTACT REQUIRED.
                
                Device security remains COMPROMISED.
            """.trimIndent()
            setTextColor(getColor(android.R.color.holo_red_dark))
            visibility = android.view.View.VISIBLE
        }
        
        // Disable reactivation button
        findViewById<Button>(R.id.btn_reactivate_now).apply {
            text = "❌ Max Attempts Reached"
            isEnabled = false
        }
        
        // Force parent contact
        initiateParentContact()
        
        // Log critical failure
        val prefs = getSharedPreferences("emergency_reactivation", MODE_PRIVATE)
        prefs.edit()
            .putLong("max_attempts_reached", System.currentTimeMillis())
            .putInt("failed_attempts", reactivationAttempts)
            .putBoolean("requires_parent_intervention", true)
            .apply()
    }
    
    private fun handleCountdownExpiry() {
        findViewById<TextView>(R.id.tv_countdown).apply {
            text = "⏰ TIME EXPIRED"
            setTextColor(getColor(android.R.color.holo_red_dark))
            textSize = 20f
        }
        
        findViewById<TextView>(R.id.tv_status_message).apply {
            text = """
                ⏰ COUNTDOWN EXPIRED
                
                Device remains UNPROTECTED for too long.
                Escalating to emergency protocols.
                
                Parent notification priority: CRITICAL
            """.trimIndent()
            setTextColor(getColor(android.R.color.holo_red_dark))
            visibility = android.view.View.VISIBLE
        }
        
        // Escalate to critical parent notification
        sendBroadcast(Intent("com.shieldtechhub.shieldkids.EMERGENCY_ESCALATION").apply {
            putExtra("reason", "Reactivation countdown expired")
            putExtra("attempts_made", reactivationAttempts)
        })
    }
    
    private fun initiateParentContact() {
        try {
            val contactIntent = Intent("com.shieldtechhub.shieldkids.EMERGENCY_PARENT_CONTACT")
            contactIntent.putExtra("emergency_type", "DEVICE_ADMIN_DISABLED")
            contactIntent.putExtra("attempts_made", reactivationAttempts)
            contactIntent.putExtra("timestamp", System.currentTimeMillis())
            
            sendBroadcast(contactIntent)
            
            findViewById<TextView>(R.id.tv_contact_status).apply {
                text = "📞 Emergency parent contact initiated..."
                visibility = android.view.View.VISIBLE
            }
            
        } catch (e: Exception) {
            findViewById<TextView>(R.id.tv_contact_status).apply {
                text = "❌ Parent contact failed. Please call directly."
                setTextColor(getColor(android.R.color.holo_red_dark))
                visibility = android.view.View.VISIBLE
            }
        }
    }
    
    private fun notifyParentOfCriticalSituation() {
        val emergencyIntent = Intent("com.shieldtechhub.shieldkids.CRITICAL_SECURITY_ALERT")
        emergencyIntent.putExtra("alert_type", "DEVICE_ADMIN_DISABLED")
        emergencyIntent.putExtra("severity", "CRITICAL")
        emergencyIntent.putExtra("requires_immediate_action", true)
        emergencyIntent.putExtra("timestamp", System.currentTimeMillis())
        
        sendBroadcast(emergencyIntent)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (deviceAdminManager.handleDeviceAdminResult(requestCode, resultCode)) {
            handleReactivationSuccess()
        } else {
            handleReactivationFailure("Device Admin activation was cancelled or failed")
        }
    }
    
    override fun onBackPressed() {
        // Prevent dismissal during critical security situation
        findViewById<TextView>(R.id.tv_status_message).apply {
            text = "⚠️ Cannot dismiss during security emergency. Must reactivate protection first."
            setTextColor(getColor(android.R.color.holo_orange_dark))
            visibility = android.view.View.VISIBLE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
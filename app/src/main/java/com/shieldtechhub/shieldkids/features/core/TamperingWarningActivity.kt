package com.shieldtechhub.shieldkids.features.core

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.shieldtechhub.shieldkids.R

/**
 * 🚨 Tampering Warning Activity
 * 
 * Full-screen warning displayed when tampering is detected.
 * This activity is designed to be persistent and difficult to dismiss.
 */
class TamperingWarningActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tampering_warning)
        
        // Make this activity difficult to dismiss
        setupPersistentDisplay()
        
        // Get warning details from intent
        val message = intent.getStringExtra("message") ?: "Security violation detected"
        val severity = intent.getStringExtra("severity") ?: "MEDIUM"
        
        setupWarningContent(message, severity)
        setupButtons()
    }
    
    private fun setupPersistentDisplay() {
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Make it fullscreen
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        
        // Show over lock screen if possible
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        // Prevent back button from closing
        // (This will be handled in onBackPressed())
    }
    
    private fun setupWarningContent(message: String, severity: String) {
        findViewById<TextView>(R.id.tv_warning_title).apply {
            text = when (severity) {
                "HIGH", "CRITICAL" -> "🚨 CRITICAL SECURITY ALERT"
                "MEDIUM" -> "⚠️ SECURITY WARNING"
                else -> "ℹ️ SECURITY NOTICE"
            }
        }
        
        findViewById<TextView>(R.id.tv_warning_message).text = message
        
        findViewById<TextView>(R.id.tv_warning_details).text = """
            This alert was triggered because:
            • Unauthorized attempt to modify parental controls
            • Parents have been automatically notified
            • This action is being logged for security review
            • Device may be locked for protection
            
            If this was done by a parent, please use the parent app to make changes.
            If this was done by mistake, please contact your parent/guardian.
        """.trimIndent()
    }
    
    private fun setupButtons() {
        findViewById<Button>(R.id.btn_contact_parent).setOnClickListener {
            // Try to open parent contact method
            contactParent()
        }
        
        findViewById<Button>(R.id.btn_acknowledge).setOnClickListener {
            // Only allow dismissal after acknowledgment
            acknowledgeWarning()
        }
        
        // Emergency button for critical situations
        findViewById<Button>(R.id.btn_emergency).setOnClickListener {
            handleEmergencyRequest()
        }
    }
    
    private fun contactParent() {
        try {
            // This could open messaging app, call parent, or send notification
            val intent = Intent("com.shieldtechhub.shieldkids.CONTACT_PARENT")
            intent.putExtra("reason", "Security alert acknowledgment needed")
            sendBroadcast(intent)
            
            // Show confirmation
            findViewById<TextView>(R.id.tv_status_message).apply {
                text = "✅ Parent notification sent. Please wait for response."
                visibility = android.view.View.VISIBLE
            }
            
        } catch (e: Exception) {
            findViewById<TextView>(R.id.tv_status_message).apply {
                text = "❌ Failed to contact parent. Please try again."
                visibility = android.view.View.VISIBLE
            }
        }
    }
    
    private fun acknowledgeWarning() {
        // Log acknowledgment
        val prefs = getSharedPreferences("security_warnings", MODE_PRIVATE)
        prefs.edit()
            .putLong("last_warning_acknowledged", System.currentTimeMillis())
            .putString("last_warning_type", intent.getStringExtra("severity") ?: "UNKNOWN")
            .apply()
        
        // Send acknowledgment broadcast
        val intent = Intent("com.shieldtechhub.shieldkids.WARNING_ACKNOWLEDGED")
        intent.putExtra("timestamp", System.currentTimeMillis())
        sendBroadcast(intent)
        
        // Allow dismissal after acknowledgment
        finish()
    }
    
    private fun handleEmergencyRequest() {
        // For true emergencies, provide alternative contact method
        try {
            val emergencyIntent = Intent(Intent.ACTION_DIAL)
            emergencyIntent.data = android.net.Uri.parse("tel:911") // Or parent's emergency number
            startActivity(emergencyIntent)
        } catch (e: Exception) {
            // If dialer fails, show emergency contact info
            findViewById<TextView>(R.id.tv_emergency_info).apply {
                text = "Emergency Contact: Call 911 or contact school/trusted adult"
                visibility = android.view.View.VISIBLE
            }
        }
    }
    
    override fun onBackPressed() {
        // Prevent easy dismissal - require acknowledgment
        findViewById<TextView>(R.id.tv_status_message).apply {
            text = "⚠️ Please acknowledge this warning before continuing"
            visibility = android.view.View.VISIBLE
        }
        
        // Don't call super.onBackPressed() to prevent dismissal
    }
    
    override fun onPause() {
        super.onPause()
        // Restart activity if it gets paused (prevents hiding it)
        if (!isFinishing) {
            val restartIntent = Intent(this, TamperingWarningActivity::class.java)
            restartIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            restartIntent.putExtras(intent.extras ?: Bundle())
            startActivity(restartIntent)
        }
    }
}
package com.shieldtechhub.shieldkids.features.authentication

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.shieldtechhub.shieldkids.R

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvEmail: TextView
    private lateinit var btnResendEmail: Button
    private lateinit var tvBackToLogin: TextView
    
    private val handler = Handler(Looper.getMainLooper())
    private var isChecking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        auth = FirebaseAuth.getInstance()
        
        // Initialize views
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvEmail = findViewById(R.id.tvEmail)
        btnResendEmail = findViewById(R.id.btnResendEmail)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        // Set up click listeners
        btnResendEmail.setOnClickListener { resendVerificationEmail() }
        tvBackToLogin.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, ParentLoginActivity::class.java))
            finish()
        }

        // Display user's email
        val user = auth.currentUser
        if (user != null) {
            tvEmail.text = user.email
            // Send verification email if not already verified
            if (!user.isEmailVerified) {
                sendVerificationEmail()
            }
        } else {
            // No user logged in, go back to login
            startActivity(Intent(this, ParentLoginActivity::class.java))
            finish()
            return
        }

        // Start checking verification status
        startVerificationCheck()
    }

    private fun sendVerificationEmail() {
        val user = auth.currentUser
        if (user != null && !user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnSuccessListener {
                    Toast.makeText(this, "Verification email sent!", Toast.LENGTH_SHORT).show()
                    updateStatus("Verification email sent. Please check your inbox.")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to send verification email: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    updateStatus("Failed to send verification email. Please try again.")
                }
        }
    }

    private fun resendVerificationEmail() {
        btnResendEmail.isEnabled = false
        btnResendEmail.text = "Sending..."
        
        sendVerificationEmail()
        
        // Re-enable button after 60 seconds
        handler.postDelayed({
            btnResendEmail.isEnabled = true
            btnResendEmail.text = "Resend Verification Email"
        }, 60000)
    }

    private fun startVerificationCheck() {
        isChecking = true
        checkVerificationStatus()
    }

    private fun checkVerificationStatus() {
        if (!isChecking) return

        val user = auth.currentUser
        if (user == null) {
            // User logged out, go back to login
            startActivity(Intent(this, ParentLoginActivity::class.java))
            finish()
            return
        }

        // Reload user to get latest verification status
        user.reload()
            .addOnSuccessListener {
                if (user.isEmailVerified) {
                    // Email verified, go to dashboard
                    handler.postDelayed({
                        finish()
                    }, 1500)
                } else {
                    // Not verified yet, check again in 3 seconds
                    updateStatus("Checking verification status...")
                    handler.postDelayed({
                        checkVerificationStatus()
                    }, 3000)
                }
            }
            .addOnFailureListener { e ->
                // Failed to reload, try again in 5 seconds
                updateStatus("Checking verification status...")
                handler.postDelayed({
                    checkVerificationStatus()
                }, 5000)
            }
    }

    private fun updateStatus(message: String) {
        tvStatus.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        isChecking = false
        handler.removeCallbacksAndMessages(null)
    }
}

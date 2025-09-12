package com.shieldtechhub.shieldkids.features.authentication

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.shieldtechhub.shieldkids.R
import java.util.regex.Pattern

class PasswordResetActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var btnSendResetLink: Button
    private lateinit var emailContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_reset)

        auth = FirebaseAuth.getInstance()

        // Initialize views
        etEmail = findViewById(R.id.etEmail)
        btnSendResetLink = findViewById(R.id.btnSendResetLink)
        emailContainer = findViewById(R.id.emailContainer)

        // Set up click listeners
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.tvBackToLogin).setOnClickListener { finish() }
        btnSendResetLink.setOnClickListener { sendPasswordResetEmail() }

        // Set up email field focus listener
        setupEmailFieldFocusListener()

        // Set up email validation
        setupEmailValidation()

        // Pre-fill email if passed from login screen
        val emailFromIntent = intent.getStringExtra("email")
        if (!emailFromIntent.isNullOrEmpty()) {
            etEmail.setText(emailFromIntent)
        }
    }

    private fun setupEmailFieldFocusListener() {
        etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                emailContainer.setBackgroundResource(R.drawable.input_field_background_focused)
                etEmail.setHintTextColor(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                emailContainer.setBackgroundResource(R.drawable.input_field_background)
                etEmail.setHintTextColor(ContextCompat.getColor(this, R.color.gray_500))
            }
        }
    }

    private fun setupEmailValidation() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmail()
            }
        })
    }

    private fun validateEmail(): Boolean {
        val email = etEmail.text.toString().trim()
        val emailPattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        return if (email.isEmpty()) {
            etEmail.error = "Email is required"
            false
        } else if (!emailPattern.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address"
            false
        } else {
            etEmail.error = null
            true
        }
    }

    private fun sendPasswordResetEmail() {
        val email = etEmail.text.toString().trim()

        if (!validateEmail()) {
            return
        }

        // Disable button and show loading state
        btnSendResetLink.isEnabled = false
        btnSendResetLink.text = "Sending..."

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                // Show success dialog with instructions
                showSuccessDialog(email)
            }
            .addOnFailureListener { e ->
                // Re-enable button
                btnSendResetLink.isEnabled = true
                btnSendResetLink.text = "Send Reset Link"
                
                // Show specific error messages
                val errorMessage = when {
                    e.message?.contains("no user record") == true -> 
                        "No account found with this email address. Please check your email or create a new account."
                    e.message?.contains("network") == true -> 
                        "Network error. Please check your internet connection and try again."
                    else -> "Failed to send reset email: ${e.localizedMessage}"
                }
                
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    private fun showSuccessDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("Reset Email Sent!")
            .setMessage("We've sent a password reset link to:\n\n$email\n\n" +
                    "Please check your email and follow the instructions to reset your password.\n\n" +
                    "If you don't see the email, check your spam folder.")
            .setPositiveButton("OK") { _, _ ->
                // Go back to login screen
                finish()
            }
            .setCancelable(false)
            .show()
    }
}

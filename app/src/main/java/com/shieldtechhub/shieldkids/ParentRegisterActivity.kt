package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ParentRegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_register)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupInputFieldFocusListeners()
        setupPasswordVisibilityToggle()
        setupClickListeners()
    }

    private fun setupInputFieldFocusListeners() {
        val usernameContainer = findViewById<View>(R.id.usernameContainer)
        val emailContainer = findViewById<View>(R.id.emailContainer)
        val passwordContainer = findViewById<View>(R.id.passwordContainer)
        
        val usernameEditText = findViewById<EditText>(R.id.etUsername)
        val emailEditText = findViewById<EditText>(R.id.etEmail)
        val passwordEditText = findViewById<EditText>(R.id.etPassword)
        
        val userIcon = findViewById<ImageView>(R.id.ivUserIcon)
        val usernameIcon = findViewById<ImageView>(R.id.ivUsernameIcon)
        val lockIcon = findViewById<ImageView>(R.id.ivLockIcon)

        // Username focus listener
        usernameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                usernameContainer.setBackgroundResource(R.drawable.input_field_background_focused)
                usernameEditText.setHintTextColor(ContextCompat.getColor(this, R.color.teal_500))
                userIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                usernameContainer.setBackgroundResource(R.drawable.input_field_background)
                usernameEditText.setHintTextColor(ContextCompat.getColor(this, R.color.gray_500))
                userIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
            }
        }

        // Email focus listener
        emailEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                emailContainer.setBackgroundResource(R.drawable.input_field_background_focused)
                emailEditText.setHintTextColor(ContextCompat.getColor(this, R.color.teal_500))
                usernameIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                emailContainer.setBackgroundResource(R.drawable.input_field_background)
                emailEditText.setHintTextColor(ContextCompat.getColor(this, R.color.gray_500))
                usernameIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
            }
        }

        // Password focus listener
        passwordEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                passwordContainer.setBackgroundResource(R.drawable.input_field_background_focused)
                passwordEditText.setHintTextColor(ContextCompat.getColor(this, R.color.teal_500))
                lockIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                passwordContainer.setBackgroundResource(R.drawable.input_field_background)
                passwordEditText.setHintTextColor(ContextCompat.getColor(this, R.color.gray_500))
                lockIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
            }
        }
    }

    private fun setupPasswordVisibilityToggle() {
        val passwordEditText = findViewById<EditText>(R.id.etPassword)
        val lockIcon = findViewById<ImageView>(R.id.ivLockIcon)

        lockIcon.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            
            if (isPasswordVisible) {
                passwordEditText.inputType = android.text.InputType.TYPE_CLASS_TEXT
                lockIcon.setImageResource(R.drawable.ic_visibility)
            } else {
                passwordEditText.inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                lockIcon.setImageResource(R.drawable.lock)
            }
            
            // Move cursor to end of text
            passwordEditText.setSelection(passwordEditText.text.length)
        }
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btnRegister).setOnClickListener { register() }
        findViewById<View>(R.id.tvLogin).setOnClickListener {
            startActivity(Intent(this, ParentLoginActivity::class.java))
            finish()
        }
        findViewById<View>(R.id.btnGoogleSignUp).setOnClickListener {
            Toast.makeText(this, "Google sign-up coming soon", Toast.LENGTH_SHORT).show()
        }
        // Terms & Conditions click listener
        findViewById<View>(R.id.tvTerms).setOnClickListener {
            Toast.makeText(this, "Terms & Conditions", Toast.LENGTH_SHORT).show()
        }
        // Privacy click listener
        findViewById<View>(R.id.tvPrivacy).setOnClickListener {
            Toast.makeText(this, "Privacy Policy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun register() {
        val username = findViewById<EditText>(R.id.etUsername).text.toString().trim()
        val email = findViewById<EditText>(R.id.etEmail).text.toString().trim()
        val pwd = findViewById<EditText>(R.id.etPassword).text.toString().trim()

        if (username.isEmpty() || email.isEmpty() || pwd.length < 6) {
            Toast.makeText(this, "Fill all fields & use ≥6 chars for password", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, pwd)
            .addOnSuccessListener { res ->
                val uid = res.user!!.uid
                
                // Create users document
                val userData = hashMapOf(
                    "name" to username,
                    "email" to email,
                    "role" to "Parent"
                )
                
                // Create parents document
                val parentData = hashMapOf(
                    "name" to username, // Reference to users doc ID
                    "children" to listOf<String>() // Empty array initially
                )
                
                // First, create the users document
                firestore.collection("users").document(uid)
                    .set(userData)
                    .addOnSuccessListener {
                        // Then create the parents document
                        firestore.collection("parents").add(parentData)
                            .addOnSuccessListener { documentReference ->
                                // Send verification email
                                res.user?.sendEmailVerification()
                                    ?.addOnSuccessListener {
                                        Toast.makeText(this, "Registration successful! Please verify your email.", Toast.LENGTH_LONG).show()
                                        startActivity(Intent(this, VerifyEmailActivity::class.java))
                                        finish()
                                    }
                                    ?.addOnFailureListener { e ->
                                        Toast.makeText(this, "Registration successful but failed to send verification email: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        startActivity(Intent(this, VerifyEmailActivity::class.java))
                                        finish()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to create parent profile: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to create user account: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
    }
}
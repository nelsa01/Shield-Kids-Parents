package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.shieldtechhub.shieldkids.databinding.ActivityParentLoginBinding
import com.shieldtechhub.shieldkids.ParentDashboardActivity

class ParentLoginActivity : AppCompatActivity() {

	private lateinit var binding: ActivityParentLoginBinding
	private val auth = FirebaseAuth.getInstance()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityParentLoginBinding.inflate(layoutInflater)
		setContentView(binding.root)

		// Set up click listeners
		binding.btnLogin.setOnClickListener { login() }
		binding.btnGoogleLogin.setOnClickListener { googleLogin() }
		binding.tvForgotPassword.setOnClickListener { forgotPassword() }
		binding.tvRegister.setOnClickListener {
			startActivity(Intent(this, ParentRegisterActivity::class.java))
			finish()
		}

		// Set up lock icon click listener for password visibility
		binding.ivLockIcon.setOnClickListener { togglePasswordVisibility() }

		// Set up focus listeners for input fields
		setupInputFieldFocusListeners()
	}

	private fun setupInputFieldFocusListeners() {
		// Email field focus listener
		binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
			val emailContainer = binding.emailContainer
			if (hasFocus) {
				emailContainer.setBackgroundResource(R.drawable.input_field_background_focused)
				binding.etEmail.setHintTextColor(ContextCompat.getColor(this, R.color.teal_500))
				binding.ivUsernameIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
			} else {
				emailContainer.setBackgroundResource(R.drawable.input_field_background)
				binding.etEmail.setHintTextColor(ContextCompat.getColor(this, R.color.gray_500))
				binding.ivUsernameIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
			}
		}

		// Password field focus listener
		binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
			val passwordContainer = binding.passwordContainer
			if (hasFocus) {
				passwordContainer.setBackgroundResource(R.drawable.input_field_background_focused)
				binding.etPassword.setHintTextColor(ContextCompat.getColor(this, R.color.teal_500))
				binding.ivLockIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
			} else {
				passwordContainer.setBackgroundResource(R.drawable.input_field_background)
				binding.etPassword.setHintTextColor(ContextCompat.getColor(this, R.color.gray_500))
				binding.ivLockIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
			}
		}
	}

	private fun togglePasswordVisibility() {
		val passwordField = binding.etPassword
		val lockIcon = binding.ivLockIcon
		
		if (passwordField.inputType == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) {
			// Show password
			passwordField.inputType = android.text.InputType.TYPE_CLASS_TEXT
			lockIcon.setImageResource(R.drawable.ic_visibility)
		} else {
			// Hide password
			passwordField.inputType = android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
			lockIcon.setImageResource(R.drawable.lock)
		}
		
		// Move cursor to end
		passwordField.setSelection(passwordField.text.length)
	}

	private fun login() {
		val email = binding.etEmail.text.toString().trim()
		val pwd   = binding.etPassword.text.toString().trim()

		if (email.isEmpty() || pwd.length < 6) {
			Toast.makeText(this, "Enter valid email & ≥6-char password", Toast.LENGTH_SHORT).show()
			return
		}

		auth.signInWithEmailAndPassword(email, pwd)
			.addOnSuccessListener { result ->
				val user = result.user
				if (user != null) {
					if (user.isEmailVerified) {
						Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
						startActivity(Intent(this, ParentDashboardActivity::class.java))
						finish()
					} else {
						Toast.makeText(this, "Please verify your email address first.", Toast.LENGTH_LONG).show()
						startActivity(Intent(this, VerifyEmailActivity::class.java))
						finish()
					}
				}
			}
			.addOnFailureListener { e ->
				Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
			}
	}

	private fun googleLogin() {
		// TODO: Implement Google Sign-In
		Toast.makeText(this, "Google Sign-In coming soon!", Toast.LENGTH_SHORT).show()
	}

	private fun forgotPassword() {
		val email = binding.etEmail.text.toString().trim()
		val intent = Intent(this, PasswordResetActivity::class.java)
		if (email.isNotEmpty()) {
			intent.putExtra("email", email)
		}
		startActivity(intent)
	}
}
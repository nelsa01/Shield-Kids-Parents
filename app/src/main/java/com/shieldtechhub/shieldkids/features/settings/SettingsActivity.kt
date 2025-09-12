package com.shieldtechhub.shieldkids.features.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.common.utils.PasscodeManager
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.features.authentication.PasswordResetActivity
import com.shieldtechhub.shieldkids.features.device_setup.ui.PermissionRequestActivity
import com.shieldtechhub.shieldkids.features.authentication.ParentLoginActivity
import com.shieldtechhub.shieldkids.features.authentication.RoleSelectionActivity
import com.shieldtechhub.shieldkids.features.core.DatabaseHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var tvUserInitials: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var tvUserName: TextView
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize views
        tvUserInitials = findViewById(R.id.tvUserInitials)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvUserName = findViewById(R.id.tvUserName)
        btnLogout = findViewById(R.id.btnLogout)

        // Set up click listeners
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        btnLogout.setOnClickListener { showLogoutDialog() }

        // Set up settings menu click listeners
        setupSettingsMenuListeners()

        // Load user information
        loadUserInfo()
    }

    private fun setupSettingsMenuListeners() {
        // Profile Management section
        findViewById<View>(R.id.btnEditProfile).setOnClickListener {
            Toast.makeText(this, "Edit profile coming soon!", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to edit profile activity
        }

        findViewById<View>(R.id.btnChangePassword).setOnClickListener {
            startActivity(Intent(this, PasswordResetActivity::class.java))
        }

        findViewById<View>(R.id.btnNotificationSettings).setOnClickListener {
            Toast.makeText(this, "Notification settings coming soon!", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to notification settings activity
        }

        // App Security section
        findViewById<View>(R.id.btnPermissions).setOnClickListener {
            val intent = Intent(this, PermissionRequestActivity::class.java).apply {
                putExtra("from_settings", true)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btnSecurity).setOnClickListener { showPasscodeDialog() }

        findViewById<View>(R.id.btnBackup).setOnClickListener {
            Toast.makeText(this, "Backup & sync coming soon!", Toast.LENGTH_SHORT).show()
            // TODO: Navigate to backup settings activity
        }

        // Support & Information section
        findViewById<View>(R.id.btnHelp).setOnClickListener {
            showHelpDialog()
        }

        findViewById<View>(R.id.btnAbout).setOnClickListener {
            showAboutDialog()
        }

        findViewById<View>(R.id.btnPrivacyPolicy).setOnClickListener {
            showPrivacyPolicyDialog()
        }
    }

    private fun loadUserInfo() {
        val user = auth.currentUser
        if (user != null) {
            // Set email
            tvUserEmail.text = user.email

            // Get user data from Firestore using DatabaseHelper
            DatabaseHelper.getCurrentUserData(
                onSuccess = { document ->
                    val name = document.getString("name") ?: ""
                    tvUserName.text = name

                    // Generate initials from name
                    val initials = generateInitials(name)
                    tvUserInitials.text = initials
                },
                onFailure = { exception ->
                    // Fallback to email if Firestore fails
                    val email = user.email ?: ""
                    tvUserName.text = email
                    val initials = generateInitials(email)
                    tvUserInitials.text = initials
                }
            )
        } else {
            // No user logged in, go back to login
            startActivity(Intent(this, ParentLoginActivity::class.java))
            finish()
        }
    }

    private fun generateInitials(name: String): String {
        return if (name.isNotEmpty()) {
            val words = name.trim().split("\\s+".toRegex())
            when {
                words.size >= 2 -> "${words[0][0]}${words[1][0]}".uppercase()
                words.size == 1 -> words[0][0].uppercase()
                else -> "U"
            }
        } else {
            "U"
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                logout()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun logout() {
        auth.signOut()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, RoleSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Help & Support")
            .setMessage("Need help with ShieldKids?\n\n" +
                    "• Getting Started Guide: Learn how to set up parental controls\n" +
                    "• FAQ: Find answers to common questions\n" +
                    "• Contact Support: Get help from our team\n\n" +
                    "Email: support@shieldtechhub.com\n" +
                    "Website: www.shieldtechhub.com/help")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About ShieldKids")
            .setMessage("ShieldKids is a comprehensive parental control app designed to keep children safe online.\n\n" +
                    "Version: 1.0.0\n" +
                    "Developed by ShieldTechHub\n\n" +
                    "Our mission is to provide parents with the tools they need to protect their children in the digital world.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPrivacyPolicyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacy & Policy")
            .setMessage("Privacy Policy for ShieldKids\n\n" +
                    "1. Information We Collect\n" +
                    "We collect information you provide directly to us, such as when you create an account.\n\n" +
                    "2. How We Use Your Information\n" +
                    "We use the information we collect to provide, maintain, and improve our services.\n\n" +
                    "3. Information Sharing\n" +
                    "We do not sell, trade, or otherwise transfer your personal information to third parties.\n\n" +
                    "4. Data Security\n" +
                    "We implement appropriate security measures to protect your personal information.\n\n" +
                    "5. Children's Privacy\n" +
                    "We are committed to protecting the privacy of children and comply with COPPA.\n\n" +
                    "For more information, please contact us at privacy@shieldtechhub.com")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPasscodeDialog() {
        val isSet = PasscodeManager.isPasscodeSet(this)
        if (!isSet) {
            // Set new passcode
            val input = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "Enter 4-digit passcode"
                filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            }
            val input2 = android.widget.EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "Confirm passcode"
                filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            }
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 16, 32, 0)
                addView(input)
                addView(input2)
            }
            AlertDialog.Builder(this)
                .setTitle("Set App Passcode")
                .setView(container)
                .setPositiveButton("Save") { _, _ ->
                    val c1 = input.text.toString()
                    val c2 = input2.text.toString()
                    if (c1.length == 4 && c1 == c2) {
                        PasscodeManager.setPasscode(this, c1)
                        Toast.makeText(this, "Passcode set", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Passcodes do not match", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Change or remove
            val options = arrayOf("Change Passcode", "Remove Passcode")
            AlertDialog.Builder(this)
                .setTitle("App Passcode")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showChangePasscodeDialog()
                        1 -> {
                            PasscodeManager.clearPasscode(this)
                            Toast.makeText(this, "Passcode removed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }
    }

    private fun showChangePasscodeDialog() {
        val current = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Current passcode"
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        val new1 = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "New passcode"
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        val new2 = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Confirm new passcode"
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(current)
            addView(new1)
            addView(new2)
        }
        AlertDialog.Builder(this)
            .setTitle("Change Passcode")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val c = current.text.toString()
                val n1 = new1.text.toString()
                val n2 = new2.text.toString()
                if (!PasscodeManager.verifyPasscode(this, c)) {
                    Toast.makeText(this, "Current passcode incorrect", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (n1.length == 4 && n1 == n2) {
                    PasscodeManager.setPasscode(this, n1)
                    Toast.makeText(this, "Passcode changed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "New passcodes do not match", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

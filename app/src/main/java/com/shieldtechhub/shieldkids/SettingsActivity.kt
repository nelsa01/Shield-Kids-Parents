package com.shieldtechhub.shieldkids

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
        // General section
        findViewById<View>(R.id.btnApps).setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        findViewById<View>(R.id.btnInternet).setOnClickListener {
            Toast.makeText(this, "Internet settings coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnDeviceUsage).setOnClickListener {
            Toast.makeText(this, "Device usage settings coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnLocation).setOnClickListener {
            Toast.makeText(this, "Location settings coming soon", Toast.LENGTH_SHORT).show()
        }

        // Account section
        findViewById<View>(R.id.btnPermissions).setOnClickListener {
            val intent = Intent(this, PermissionRequestActivity::class.java).apply {
                putExtra("from_settings", true)
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btnNotificationSettings).setOnClickListener {
            Toast.makeText(this, "Notification settings coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnInstallation).setOnClickListener {
            Toast.makeText(this, "Installation settings coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnPassCode).setOnClickListener {
            Toast.makeText(this, "PassCode settings coming soon", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnChangePassword).setOnClickListener {
            startActivity(Intent(this, PasswordResetActivity::class.java))
        }

        // Other section
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
}

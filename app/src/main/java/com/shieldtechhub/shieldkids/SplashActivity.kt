package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.shieldtechhub.shieldkids.common.utils.PermissionManager

class SplashActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide the action bar for full-screen splash
        supportActionBar?.hide()

        permissionManager = PermissionManager(this)

        // Check authentication and permissions after splash delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkAuthenticationAndNavigate()
        }, 3000) // Reduced to 3 seconds for better UX
    }

    private fun checkAuthenticationAndNavigate() {
        val currentUser = auth.currentUser
        
        if (currentUser != null && currentUser.isEmailVerified) {
            // User is authenticated and email is verified
            checkPermissionsAndNavigateToMain()
        } else {
            // No authenticated user, go to role selection
            navigateToRoleSelection()
        }
    }

    private fun checkPermissionsAndNavigateToMain() {
        val hasCriticalPermissions = permissionManager.checkCriticalPermissionsOnAppStart()
        
        if (!hasCriticalPermissions) {
            // Navigate to permission setup first
            val intent = Intent(this, PermissionRequestActivity::class.java).apply {
                putExtra("from_launch", true)
                putExtra("show_welcome", true)
            }
            startActivity(intent)
            finish()
        } else {
            // All permissions granted, go directly to main dashboard
            navigateToMainDashboard()
        }
    }

    private fun navigateToMainDashboard() {
        val intent = Intent(this, ParentDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToRoleSelection() {
        val intent = Intent(this, RoleSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
} 
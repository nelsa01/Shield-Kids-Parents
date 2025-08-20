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
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.common.utils.PasscodeManager

class SplashActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private lateinit var permissionManager: PermissionManager
    private lateinit var deviceStateManager: DeviceStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide the action bar for full-screen splash
        supportActionBar?.hide()

        permissionManager = PermissionManager(this)
        deviceStateManager = DeviceStateManager(this)

        // Check device state and navigate appropriately after splash delay
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                checkDeviceStateAndNavigate()
            } catch (e: Exception) {
                android.util.Log.e("Splash", "Navigation error", e)
                navigateToRoleSelection()
            }
        }, 3000) // Reduced to 3 seconds for better UX
    }

    private fun checkDeviceStateAndNavigate() {
        when {
            deviceStateManager.isChildDevice() -> {
                // This device is linked as a child device - go directly to child mode
                navigateToChildMode()
            }
            deviceStateManager.isParentDevice() -> {
                // This device is set up as parent device - check authentication
                checkParentAuthenticationAndNavigate()
            }
            else -> {
                // Unlinked device - go to role selection
                navigateToRoleSelection()
            }
        }
    }

    private fun checkParentAuthenticationAndNavigate() {
        val currentUser = auth.currentUser
        
        if (currentUser != null && (currentUser.isEmailVerified || true)) {
            // Parent is authenticated and email is verified
            checkPermissionsAndNavigateToMain()
        } else {
            // Parent device but not authenticated - go to parent login
            navigateToParentLogin()
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
            // All permissions granted, enforce passcode if set
            if (PasscodeManager.isPasscodeSet(this)) {
                val intent = Intent(this, PasscodeActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                navigateToMainDashboard()
            }
        }
    }

    private fun navigateToMainDashboard() {
        val intent = Intent(this, ParentDashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToChildMode() {
        val intent = Intent(this, ChildModeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToParentLogin() {
        val intent = Intent(this, ParentLoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Passcode flow navigates forward; no activity result handling

    private fun navigateToRoleSelection() {
        val intent = Intent(this, RoleSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
} 
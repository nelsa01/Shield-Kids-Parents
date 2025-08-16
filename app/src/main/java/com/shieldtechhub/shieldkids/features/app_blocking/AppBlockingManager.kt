package com.shieldtechhub.shieldkids.features.app_blocking

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.features.app_management.service.AppInventoryManager

class AppBlockingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppBlockingManager"
        
        @Volatile
        private var INSTANCE: AppBlockingManager? = null
        
        fun getInstance(context: Context): AppBlockingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppBlockingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val appInventoryManager = AppInventoryManager(context)
    
    private var overlayView: View? = null
    private var isOverlayShowing = false
    
    fun showBlockingOverlay(packageName: String, reason: String) {
        try {
            Log.d(TAG, "Showing blocking overlay for: $packageName")
            
            // Remove existing overlay if present
            hideBlockingOverlay()
            
            // Create overlay view
            overlayView = createBlockingOverlay(packageName, reason)
            
            // Configure window parameters
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                       WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                       WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                       
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
            }
            
            // Add overlay to window
            windowManager.addView(overlayView, layoutParams)
            isOverlayShowing = true
            
            // Auto-hide after a delay
            overlayView?.postDelayed({
                hideBlockingOverlay()
            }, 5000) // Hide after 5 seconds
            
            Log.d(TAG, "Blocking overlay displayed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show blocking overlay", e)
            isOverlayShowing = false
        }
    }
    
    private fun createBlockingOverlay(packageName: String, reason: String): View {
        val inflater = LayoutInflater.from(context)
        val overlayView = inflater.inflate(R.layout.overlay_app_blocked, null)
        
        // Get app info
        val appInfo = appInventoryManager.getAppInfo(packageName)
        val appName = appInfo?.name ?: packageName
        
        // Configure overlay content
        overlayView.findViewById<TextView>(R.id.tvBlockedAppName)?.text = appName
        overlayView.findViewById<TextView>(R.id.tvBlockReason)?.text = reason
        
        // Set app icon if available
        appInfo?.icon?.let { icon ->
            overlayView.findViewById<android.widget.ImageView>(R.id.ivAppIcon)?.setImageDrawable(icon)
        }
        
        // Configure buttons
        overlayView.findViewById<Button>(R.id.btnRequestAccess)?.setOnClickListener {
            handleAccessRequest(packageName, appName)
        }
        
        overlayView.findViewById<Button>(R.id.btnGoHome)?.setOnClickListener {
            hideBlockingOverlay()
            goToHomeScreen()
        }
        
        overlayView.findViewById<Button>(R.id.btnClose)?.setOnClickListener {
            hideBlockingOverlay()
        }
        
        return overlayView
    }
    
    fun hideBlockingOverlay() {
        try {
            overlayView?.let { view ->
                windowManager.removeView(view)
                overlayView = null
                isOverlayShowing = false
                Log.d(TAG, "Blocking overlay hidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide blocking overlay", e)
            overlayView = null
            isOverlayShowing = false
        }
    }
    
    private fun handleAccessRequest(packageName: String, appName: String) {
        Log.d(TAG, "Access request for: $packageName")
        
        try {
            // Create access request notification/intent
            val requestIntent = Intent("com.shieldkids.ACCESS_REQUEST").apply {
                putExtra("package_name", packageName)
                putExtra("app_name", appName)
                putExtra("timestamp", System.currentTimeMillis())
                putExtra("device_id", Build.ID)
            }
            
            context.sendBroadcast(requestIntent)
            
            // Hide overlay
            hideBlockingOverlay()
            
            // Show confirmation
            showAccessRequestConfirmation(appName)
            
            Log.d(TAG, "Access request sent for: $appName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle access request", e)
        }
    }
    
    private fun showAccessRequestConfirmation(appName: String) {
        try {
            // Create simple confirmation overlay
            val confirmationView = createConfirmationOverlay(
                "Request Sent",
                "Your request to access $appName has been sent to your parent."
            )
            
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER
            }
            
            windowManager.addView(confirmationView, layoutParams)
            
            // Auto-hide confirmation after 3 seconds
            confirmationView.postDelayed({
                try {
                    windowManager.removeView(confirmationView)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove confirmation view", e)
                }
            }, 3000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show access request confirmation", e)
        }
    }
    
    private fun createConfirmationOverlay(title: String, message: String): View {
        val inflater = LayoutInflater.from(context)
        
        // Create a simple layout programmatically since we don't have the layout file
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
            
            // Add border/shadow effect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 8f
            }
        }
        
        val titleView = TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(android.graphics.Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        
        val messageView = TextView(context).apply {
            text = message
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            gravity = Gravity.CENTER
        }
        
        container.addView(titleView)
        container.addView(messageView)
        
        return container
    }
    
    private fun goToHomeScreen() {
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            
            Log.d(TAG, "Navigated to home screen")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to home screen", e)
        }
    }
    
    // Public API
    fun isOverlayVisible(): Boolean = isOverlayShowing
    
    fun forceHideOverlay() {
        Log.d(TAG, "Force hiding overlay")
        hideBlockingOverlay()
    }
    
    fun showTemporaryMessage(title: String, message: String, durationMs: Long = 3000) {
        try {
            val messageView = createConfirmationOverlay(title, message)
            
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER
            }
            
            windowManager.addView(messageView, layoutParams)
            
            messageView.postDelayed({
                try {
                    windowManager.removeView(messageView)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to remove temporary message", e)
                }
            }, durationMs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show temporary message", e)
        }
    }
    
    fun showTimeWarning(packageName: String, remainingMinutes: Int) {
        val appInfo = appInventoryManager.getAppInfo(packageName)
        val appName = appInfo?.name ?: packageName
        
        showTemporaryMessage(
            "Time Warning",
            "You have $remainingMinutes minutes left for $appName",
            5000
        )
    }
    
    fun showBedtimeWarning() {
        showTemporaryMessage(
            "Bedtime",
            "It's bedtime! Please finish what you're doing.",
            10000
        )
    }
    
    // Check if we have permission to show overlays
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    fun getOverlayPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent() // No permission needed on older versions
        }
    }
}
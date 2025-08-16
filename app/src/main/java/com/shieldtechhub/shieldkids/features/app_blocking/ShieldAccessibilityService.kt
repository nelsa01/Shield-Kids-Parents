package com.shieldtechhub.shieldkids.features.app_blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.policy.model.ViolationType
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ShieldAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "ShieldAccessibility"
        
        fun isServiceEnabled(context: android.content.Context): Boolean {
            val accessibilityManager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) 
                as android.view.accessibility.AccessibilityManager
            
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            
            val serviceName = ComponentName(context, ShieldAccessibilityService::class.java)
            return enabledServices.any { it.resolveInfo.serviceInfo.packageName == serviceName.packageName }
        }
        
        fun getServiceIntent(): Intent {
            return Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    }
    
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var screenTimeCollector: ScreenTimeCollector
    private lateinit var appBlockingManager: AppBlockingManager
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var currentForegroundApp: String? = null
    private var appLaunchTime: Long = 0
    private var blockingInProgress = false
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Shield Accessibility Service connected")
        
        // Initialize components
        policyManager = PolicyEnforcementManager.getInstance(this)
        screenTimeCollector = ScreenTimeCollector.getInstance(this)
        appBlockingManager = AppBlockingManager.getInstance(this)
        
        // Configure accessibility service
        configureService()
        
        Log.i(TAG, "Shield Accessibility Service initialized successfully")
    }
    
    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            // Listen for app launch events
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            // Listen to all packages
            packageNames = null
            
            // Get window content for blocking
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                   AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                   AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            
            // Update frequency
            notificationTimeout = 100
            
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        }
        
        serviceInfo = info
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { handleAccessibilityEvent(it) }
    }
    
    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Optional: Handle content changes for more granular blocking
                handleWindowContentChanged(event)
            }
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Skip system UI and our own app
        if (isSystemPackage(packageName) || packageName == this.packageName) {
            return
        }
        
        Log.d(TAG, "App launched: $packageName")
        
        // Update current foreground app
        val previousApp = currentForegroundApp
        currentForegroundApp = packageName
        appLaunchTime = System.currentTimeMillis()
        
        // Record app launch for screen time tracking
        recordAppLaunch(packageName, previousApp)
        
        // Check if app should be blocked
        checkAndBlockApp(packageName)
    }
    
    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // Only handle if this is the current foreground app and it's blocked
        if (packageName == currentForegroundApp && 
            policyManager.isAppBlocked(packageName) && 
            !blockingInProgress) {
            
            Log.d(TAG, "Content changed in blocked app, re-checking block status")
            checkAndBlockApp(packageName)
        }
    }
    
    private fun checkAndBlockApp(packageName: String) {
        serviceScope.launch {
            try {
                // Check if app is blocked by policy
                if (policyManager.isAppBlocked(packageName)) {
                    val reason = policyManager.getAppBlockReason(packageName) ?: "App blocked by parent"
                    Log.w(TAG, "Blocking app: $packageName - $reason")
                    
                    blockApp(packageName, reason)
                    
                    // Report violation
                    policyManager.reportViolation(packageName, ViolationType.APP_BLOCKED_ATTEMPTED, reason)
                    return@launch
                }
                
                // Check time-based restrictions
                if (policyManager.hasTimeLimit(packageName)) {
                    val currentUsage = screenTimeCollector.getCurrentAppUsage(packageName)
                    val timeLimit = policyManager.getTimeLimit(packageName)
                    
                    if (currentUsage >= timeLimit) {
                        Log.w(TAG, "Time limit exceeded for app: $packageName")
                        
                        blockApp(packageName, "Daily time limit exceeded")
                        policyManager.reportViolation(packageName, ViolationType.TIME_LIMIT_EXCEEDED, 
                            "Used ${formatDuration(currentUsage)} of ${formatDuration(timeLimit)}")
                        return@launch
                    }
                    
                    // Check if within allowed time window
                    if (!policyManager.isWithinAllowedTime(packageName)) {
                        Log.w(TAG, "App used outside allowed time window: $packageName")
                        
                        blockApp(packageName, "App not allowed at this time")
                        policyManager.reportViolation(packageName, ViolationType.SCHEDULE_VIOLATION, 
                            "App used outside allowed schedule")
                        return@launch
                    }
                }
                
                // If we get here, app is allowed
                Log.d(TAG, "App allowed: $packageName")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking app block status for $packageName", e)
            }
        }
    }
    
    private fun blockApp(packageName: String, reason: String) {
        try {
            blockingInProgress = true
            
            Log.w(TAG, "Blocking app: $packageName - $reason")
            
            // Show blocking overlay
            appBlockingManager.showBlockingOverlay(packageName, reason)
            
            // Go back to home screen
            performGlobalAction(GLOBAL_ACTION_HOME)
            
            // Optional: Send notification to parent
            sendBlockNotificationToParent(packageName, reason)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block app: $packageName", e)
        } finally {
            blockingInProgress = false
        }
    }
    
    private fun recordAppLaunch(packageName: String, previousApp: String?) {
        try {
            Log.d(TAG, "Recording app launch: $packageName")
            
            // Record session end for previous app
            previousApp?.let { prevApp ->
                if (!isSystemPackage(prevApp)) {
                    val sessionDuration = appLaunchTime - 
                        getStoredLaunchTime(prevApp)
                    
                    if (sessionDuration > 1000) { // Only record sessions longer than 1 second
                        recordAppSession(prevApp, sessionDuration)
                    }
                }
            }
            
            // Store launch time for current app
            storeLaunchTime(packageName, appLaunchTime)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record app launch", e)
        }
    }
    
    private fun isSystemPackage(packageName: String): Boolean {
        return packageName.startsWith("com.android") ||
               packageName.startsWith("android") ||
               packageName == "com.google.android.inputmethod" ||
               packageName == "com.android.systemui" ||
               packageName == "com.android.launcher"
    }
    
    private fun getStoredLaunchTime(packageName: String): Long {
        val prefs = getSharedPreferences("app_sessions", MODE_PRIVATE)
        return prefs.getLong("launch_time_$packageName", appLaunchTime)
    }
    
    private fun storeLaunchTime(packageName: String, launchTime: Long) {
        val prefs = getSharedPreferences("app_sessions", MODE_PRIVATE)
        prefs.edit()
            .putLong("launch_time_$packageName", launchTime)
            .apply()
    }
    
    private fun recordAppSession(packageName: String, durationMs: Long) {
        serviceScope.launch {
            try {
                Log.d(TAG, "Recording session: $packageName - ${formatDuration(durationMs)}")
                
                // This would integrate with screen time tracking
                // For now, just log the session
                val sessionData = mapOf(
                    "packageName" to packageName,
                    "startTime" to (appLaunchTime - durationMs),
                    "endTime" to appLaunchTime,
                    "durationMs" to durationMs,
                    "timestamp" to System.currentTimeMillis()
                )
                
                // Store session locally
                storeSessionData(sessionData)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record app session", e)
            }
        }
    }
    
    private fun storeSessionData(sessionData: Map<String, Any>) {
        val prefs = getSharedPreferences("app_sessions", MODE_PRIVATE)
        val sessionId = "session_${System.currentTimeMillis()}"
        
        val jsonString = org.json.JSONObject(sessionData).toString()
        prefs.edit()
            .putString(sessionId, jsonString)
            .apply()
    }
    
    private fun sendBlockNotificationToParent(packageName: String, reason: String) {
        // Send local broadcast for immediate UI updates
        val intent = Intent("com.shieldkids.APP_BLOCKED").apply {
            putExtra("package_name", packageName)
            putExtra("reason", reason)
            putExtra("timestamp", System.currentTimeMillis())
        }
        sendBroadcast(intent)
        
        // TODO: Send push notification to parent device
        Log.d(TAG, "Block notification sent: $packageName")
    }
    
    private fun formatDuration(durationMs: Long): String {
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "Shield Accessibility Service interrupted")
        
        // Try to restart service components
        try {
            configureService()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconfigure service after interrupt", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "Shield Accessibility Service destroyed")
        
        // Clean up resources
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up service", e)
        }
    }
    
    // Public API for checking service status
    fun getCurrentForegroundApp(): String? = currentForegroundApp
    
    fun isBlockingActive(): Boolean = blockingInProgress
    
    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "isConnected" to (serviceInfo != null),
            "currentApp" to (currentForegroundApp ?: "none"),
            "blockingActive" to blockingInProgress,
            "lastEventTime" to appLaunchTime
        )
    }
}
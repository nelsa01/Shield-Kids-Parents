package com.shieldtechhub.shieldkids.features.policy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.common.base.SystemEventReceiver
import com.shieldtechhub.shieldkids.common.base.ServiceManager
import com.shieldtechhub.shieldkids.common.base.ShieldMonitoringService
import com.shieldtechhub.shieldkids.features.policy.model.ViolationType
import kotlinx.coroutines.*

class InstallationBlockingService : Service() {
    
    companion object {
        private const val TAG = "InstallationBlocking"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "installation_monitoring"
        
        fun startService(context: Context) {
            val intent = Intent(context, InstallationBlockingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, InstallationBlockingService::class.java)
            context.stopService(intent)
        }
    }
    
    private val policyManager by lazy { PolicyEnforcementManager.getInstance(this) }
    private var systemEventReceiver: SystemEventReceiver? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Enhanced installation monitoring
    private val packageMonitor = PackageInstallationMonitor()
    private var isMonitoring = false
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Installation blocking service created")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        startInstallationMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Installation blocking service started")
        
        if (!isMonitoring) {
            startInstallationMonitoring()
        }
        
        return START_STICKY // Restart service if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Installation blocking service destroyed")
        
        stopInstallationMonitoring()
        serviceScope.cancel()
    }
    
    private fun startInstallationMonitoring() {
        Log.d(TAG, "Starting installation monitoring")
        
        // Register system event receiver for package events
        systemEventReceiver = SystemEventReceiver()
        val intentFilter = SystemEventReceiver.createIntentFilter()
        
        registerReceiver(systemEventReceiver, intentFilter)
        
        // Start package monitoring
        packageMonitor.startMonitoring()
        isMonitoring = true
        
        // Start continuous monitoring job
        serviceScope.launch {
            startContinuousMonitoring()
        }
        
        Log.d(TAG, "Installation monitoring active")
    }
    
    private fun stopInstallationMonitoring() {
        Log.d(TAG, "Stopping installation monitoring")
        
        systemEventReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered", e)
            }
        }
        systemEventReceiver = null
        
        packageMonitor.stopMonitoring()
        isMonitoring = false
        
        Log.d(TAG, "Installation monitoring stopped")
    }
    
    private suspend fun startContinuousMonitoring() {
        while (isMonitoring) {
            try {
                // Check for newly installed apps every 5 seconds
                val newlyInstalledApps = packageMonitor.checkForNewInstallations()
                
                newlyInstalledApps.forEach { packageName ->
                    handleNewAppInstallation(packageName)
                }
                
                // Check policy integrity
                validatePolicyIntegrity()
                
                // Clean up old blocked app data
                cleanupOldBlockedApps()
                
                delay(5000) // Check every 5 seconds
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in continuous monitoring", e)
                delay(10000) // Wait longer on error
            }
        }
    }
    
    private fun handleNewAppInstallation(packageName: String) {
        Log.d(TAG, "Handling new app installation: $packageName")
        
        try {
            // Skip system apps
            if (isSystemApp(packageName)) {
                Log.d(TAG, "Skipping system app: $packageName")
                return
            }
            
            // Check if app should be blocked
            if (!policyManager.canInstallApp(packageName)) {
                Log.w(TAG, "Blocking installation of: $packageName")
                blockAppInstallation(packageName)
            } else {
                Log.d(TAG, "Installation allowed: $packageName")
                // Check if app needs to be monitored for usage
                monitorNewApp(packageName)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling app installation: $packageName", e)
        }
    }
    
    private fun blockAppInstallation(packageName: String) {
        serviceScope.launch {
            try {
                // Report violation first
                policyManager.reportViolation(
                    packageName = packageName,
                    violationType = ViolationType.INSTALLATION_BLOCKED,
                    details = "App installation blocked by parental controls"
                )
                
                // Attempt various blocking strategies
                val blockingResults = mutableListOf<Boolean>()
                
                // Strategy 1: Request uninstall
                blockingResults.add(requestAppUninstall(packageName))
                
                // Strategy 2: Disable app if uninstall fails
                if (!blockingResults.any { it }) {
                    blockingResults.add(disableApp(packageName))
                }
                
                // Strategy 3: Hide app from launcher
                if (!blockingResults.any { it }) {
                    blockingResults.add(hideAppFromLauncher(packageName))
                }
                
                // Strategy 4: Mark for blocking in accessibility service
                markAppForBlocking(packageName)
                
                val success = blockingResults.any { it }
                Log.i(TAG, "App blocking result for $packageName: $success")
                
                // Show notification to parent
                showBlockedInstallationNotification(packageName)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to block app installation: $packageName", e)
            }
        }
    }
    
    private fun requestAppUninstall(packageName: String): Boolean {
        return try {
            Log.d(TAG, "Requesting uninstall for: $packageName")
            
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = android.net.Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            startActivity(intent)
            
            // Mark for verification later
            val prefs = getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("uninstall_requested_$packageName", true)
                .putLong("uninstall_timestamp_$packageName", System.currentTimeMillis())
                .apply()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request uninstall for: $packageName", e)
            false
        }
    }
    
    private fun disableApp(packageName: String): Boolean {
        return try {
            // This requires device admin or system app privileges
            val pm = packageManager
            pm.setApplicationEnabledSetting(
                packageName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0
            )
            
            Log.i(TAG, "Disabled app: $packageName")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot disable app (insufficient permissions): $packageName", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable app: $packageName", e)
            false
        }
    }
    
    private fun hideAppFromLauncher(packageName: String): Boolean {
        return try {
            // This is a fallback - mark app as hidden in our preferences
            val prefs = getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("hidden_$packageName", true)
                .putLong("hidden_timestamp_$packageName", System.currentTimeMillis())
                .apply()
            
            Log.i(TAG, "Marked app as hidden: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide app: $packageName", e)
            false
        }
    }
    
    private fun markAppForBlocking(packageName: String) {
        // Mark app to be blocked by accessibility service
        val prefs = getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("blocked_$packageName", true)
            .putString("block_reason_$packageName", "Installation blocked by parental controls")
            .putLong("blocked_timestamp_$packageName", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Marked app for blocking: $packageName")
    }
    
    private fun monitorNewApp(packageName: String) {
        // Add new app to monitoring list for future policy application
        val prefs = getSharedPreferences("monitored_apps", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("monitored_$packageName", System.currentTimeMillis())
            .apply()
        
        Log.d(TAG, "Added app to monitoring: $packageName")
    }
    
    private fun validatePolicyIntegrity() {
        if (!policyManager.validatePolicyIntegrity()) {
            Log.w(TAG, "Policy integrity compromised")
            
            // Take corrective action
            policyManager.reportViolation(
                packageName = "system",
                violationType = ViolationType.POLICY_TAMPERING,
                details = "Policy integrity check failed during installation monitoring"
            )
        }
    }
    
    private fun cleanupOldBlockedApps() {
        val prefs = getSharedPreferences("blocked_apps", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val currentTime = System.currentTimeMillis()
        val oldThreshold = 7 * 24 * 60 * 60 * 1000L // 7 days
        
        prefs.all.keys.filter { it.startsWith("uninstall_timestamp_") }.forEach { key ->
            val timestamp = prefs.getLong(key, 0)
            if (currentTime - timestamp > oldThreshold) {
                val packageName = key.removePrefix("uninstall_timestamp_")
                
                // Check if app still exists
                if (!isAppInstalled(packageName)) {
                    // App was successfully removed, clean up
                    editor.remove("uninstall_requested_$packageName")
                    editor.remove(key)
                    editor.remove("blocked_$packageName")
                    editor.remove("block_reason_$packageName")
                    editor.remove("blocked_timestamp_$packageName")
                }
            }
        }
        
        editor.apply()
    }
    
    private fun showBlockedInstallationNotification(packageName: String) {
        val appName = getAppName(packageName) ?: packageName
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Installation Blocked")
            .setContentText("Blocked installation of $appName")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(packageName.hashCode(), notification)
    }
    
    // Utility methods
    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private fun getAppName(packageName: String): String? {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Installation Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app installations for parental controls"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shield Kids Protection")
            .setContentText("Monitoring app installations")
            .setSmallIcon(R.drawable.ic_shield)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .setOngoing(true)
            .build()
    }
    
    // Inner class for package monitoring
    private inner class PackageInstallationMonitor {
        private var lastKnownApps = mutableSetOf<String>()
        private var isInitialized = false
        
        fun startMonitoring() {
            Log.d(TAG, "Starting package installation monitor")
            
            // Initialize with current apps
            lastKnownApps.clear()
            lastKnownApps.addAll(getCurrentInstalledApps())
            isInitialized = true
            
            Log.d(TAG, "Initialized with ${lastKnownApps.size} apps")
        }
        
        fun stopMonitoring() {
            lastKnownApps.clear()
            isInitialized = false
        }
        
        fun checkForNewInstallations(): List<String> {
            if (!isInitialized) return emptyList()
            
            val currentApps = getCurrentInstalledApps()
            val newApps = currentApps.filter { it !in lastKnownApps }
            
            // Update known apps
            lastKnownApps.clear()
            lastKnownApps.addAll(currentApps)
            
            return newApps
        }
        
        private fun getCurrentInstalledApps(): List<String> {
            return try {
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    .map { it.packageName }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get installed apps", e)
                emptyList()
            }
        }
    }
}
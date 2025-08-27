package com.shieldtechhub.shieldkids.common.base

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.features.policy.PolicyEnforcementManager
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeCollector
import com.shieldtechhub.shieldkids.features.screen_time.service.ScreenTimeService
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.common.sync.UnifiedChildSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShieldMonitoringService : Service() {
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "shield_monitoring"
        const val ACTION_START_MONITORING = "start_monitoring"
        const val ACTION_STOP_MONITORING = "stop_monitoring"
        
        fun startService(context: Context) {
            val intent = Intent(context, ShieldMonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, ShieldMonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.stopService(intent)
        }
    }
    
    private var isMonitoring = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var policyManager: PolicyEnforcementManager
    private lateinit var screenTimeCollector: ScreenTimeCollector
    private lateinit var screenTimeService: ScreenTimeService
    private lateinit var deviceStateManager: DeviceStateManager
    private lateinit var unifiedSyncService: UnifiedChildSyncService
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                startMonitoring()
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
            }
        }
        
        return START_STICKY // Restart service if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // This is not a bound service
    }
    
    private fun startMonitoring() {
        if (isMonitoring) return
        
        isMonitoring = true
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Initialize monitoring components
        initializeMonitoringComponents()
    }
    
    private fun stopMonitoring() {
        isMonitoring = false
        
        // Stop unified sync service if running
        if (::unifiedSyncService.isInitialized) {
            unifiedSyncService.stopUnifiedSync()
        }
        
        stopForeground(true)
        stopSelf()
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, com.shieldtechhub.shieldkids.ParentDashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Shield Kids Active")
            .setContentText("Monitoring device for parental controls")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Shield Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring for parental controls"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun initializeMonitoringComponents() {
        try {
            // Initialize managers
            policyManager = PolicyEnforcementManager.getInstance(this)
            screenTimeCollector = ScreenTimeCollector.getInstance(this)
            screenTimeService = ScreenTimeService.getInstance(this)
            deviceStateManager = DeviceStateManager(this)
            unifiedSyncService = UnifiedChildSyncService.getInstance(this)
            
            // Start unified sync service on child devices (every 5 minutes)
            if (deviceStateManager.isChildDevice()) {
                unifiedSyncService.startUnifiedSync()
                android.util.Log.d("ShieldMonitoring", "Started unified child sync service (every 5 minutes)")
            }
            
            // Start periodic data collection
            serviceScope.launch {
                startPeriodicDataCollection()
            }
            
            android.util.Log.d("ShieldMonitoring", "Monitoring components initialized successfully")
            
        } catch (e: Exception) {
            android.util.Log.e("ShieldMonitoring", "Failed to initialize monitoring components", e)
        }
    }
    
    private suspend fun startPeriodicDataCollection() {
        try {
            // Collect initial screen time data
            screenTimeCollector.collectDailyUsageData()
            
            // Sync data to backend
            screenTimeCollector.syncUsageDataToBackend()
            
            // Validate policy integrity
            policyManager.validatePolicyIntegrity()
            
        } catch (e: Exception) {
            android.util.Log.e("ShieldMonitoring", "Error in periodic data collection", e)
        }
    }
}
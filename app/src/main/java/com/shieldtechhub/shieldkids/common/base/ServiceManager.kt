package com.shieldtechhub.shieldkids.common.base

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build

class ServiceManager(private val context: Context) {
    
    fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        @Suppress("DEPRECATION")
        return activityManager.getRunningServices(Integer.MAX_VALUE).any { service ->
            serviceClass.name == service.service.className
        }
    }
    
    fun startMonitoringService(): Boolean {
        return try {
            if (!isServiceRunning(ShieldMonitoringService::class.java)) {
                ShieldMonitoringService.startService(context)
                true
            } else {
                true // Already running
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun stopMonitoringService(): Boolean {
        return try {
            if (isServiceRunning(ShieldMonitoringService::class.java)) {
                ShieldMonitoringService.stopService(context)
                true
            } else {
                true // Already stopped
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun getServiceStatus(): ServiceStatus {
        val isMonitoringRunning = isServiceRunning(ShieldMonitoringService::class.java)
        
        return ServiceStatus(
            isMonitoringActive = isMonitoringRunning,
            canStartServices = canStartBackgroundServices()
        )
    }
    
    private fun canStartBackgroundServices(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Check if app is whitelisted from battery optimization
            // This would need to be implemented based on requirements
            true
        } else {
            true
        }
    }
}

data class ServiceStatus(
    val isMonitoringActive: Boolean,
    val canStartServices: Boolean
)
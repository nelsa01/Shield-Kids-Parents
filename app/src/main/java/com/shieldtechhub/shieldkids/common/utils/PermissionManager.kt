package com.shieldtechhub.shieldkids.common.utils

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class PermissionStatus {
    GRANTED,
    DENIED,
    NOT_REQUESTED,
    PERMANENTLY_DENIED
}

data class PermissionResult(
    val permission: String,
    val status: PermissionStatus,
    val shouldShowRationale: Boolean = false
)

class PermissionManager(private val context: Context) {
    
    companion object {
        const val REQUEST_CODE_PERMISSIONS = 1001
        
        // Essential permissions for Shield Kids
        val ESSENTIAL_PERMISSIONS = arrayOf(
            Manifest.permission.PACKAGE_USAGE_STATS,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.QUERY_ALL_PACKAGES
        )
        
        // Optional permissions
        val OPTIONAL_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    private val sharedPrefs = context.getSharedPreferences("shield_permissions", Context.MODE_PRIVATE)
    
    fun checkPermissionStatus(permission: String): PermissionStatus {
        val isGranted = when (permission) {
            Manifest.permission.PACKAGE_USAGE_STATS -> {
                hasUsageStatsPermission()
            }
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                hasOverlayPermission()
            }
            else -> {
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        
        return when {
            isGranted -> {
                PermissionStatus.GRANTED
            }
            hasBeenRequested(permission) && !shouldShowRequestRationale(permission) -> {
                PermissionStatus.PERMANENTLY_DENIED
            }
            hasBeenRequested(permission) -> {
                PermissionStatus.DENIED
            }
            else -> {
                PermissionStatus.NOT_REQUESTED
            }
        }
    }
    
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int = REQUEST_CODE_PERMISSIONS) {
        // Mark permissions as requested
        permissions.forEach { permission ->
            markPermissionAsRequested(permission)
        }
        
        // Filter to only request permissions that aren't already granted
        val permissionsToRequest = permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest, requestCode)
        }
    }
    
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): List<PermissionResult> {
        if (requestCode != REQUEST_CODE_PERMISSIONS) {
            return emptyList()
        }
        
        return permissions.mapIndexed { index, permission ->
            val isGranted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
            val status = if (isGranted) {
                PermissionStatus.GRANTED
            } else {
                if (shouldShowRequestRationale(permission)) {
                    PermissionStatus.DENIED
                } else {
                    PermissionStatus.PERMANENTLY_DENIED
                }
            }
            
            PermissionResult(
                permission = permission,
                status = status,
                shouldShowRationale = shouldShowRequestRationale(permission)
            )
        }
    }
    
    fun areEssentialPermissionsGranted(): Boolean {
        return ESSENTIAL_PERMISSIONS.all { permission ->
            checkPermissionStatus(permission) == PermissionStatus.GRANTED
        }
    }
    
    fun getMissingEssentialPermissions(): List<String> {
        return ESSENTIAL_PERMISSIONS.filter { permission ->
            checkPermissionStatus(permission) != PermissionStatus.GRANTED
        }
    }
    
    fun getPermissionStatusSummary(): Map<String, PermissionStatus> {
        val allPermissions = ESSENTIAL_PERMISSIONS + OPTIONAL_PERMISSIONS
        return allPermissions.associateWith { permission ->
            checkPermissionStatus(permission)
        }
    }
    
    private fun hasBeenRequested(permission: String): Boolean {
        return sharedPrefs.getBoolean("requested_$permission", false)
    }
    
    private fun markPermissionAsRequested(permission: String) {
        sharedPrefs.edit()
            .putBoolean("requested_$permission", true)
            .apply()
    }
    
    private fun shouldShowRequestRationale(permission: String): Boolean {
        return if (context is Activity) {
            ActivityCompat.shouldShowRequestPermissionRationale(context, permission)
        } else {
            false
        }
    }
    
    fun isSpecialPermissionRequired(permission: String): Boolean {
        return when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.PACKAGE_USAGE_STATS -> true
            else -> false
        }
    }
    
    fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.PACKAGE_USAGE_STATS -> "App usage statistics to monitor screen time"
            Manifest.permission.SYSTEM_ALERT_WINDOW -> "Display over other apps for emergency controls"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Precise location for geofencing features"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate location for geofencing features"
            Manifest.permission.QUERY_ALL_PACKAGES -> "Access to installed apps for parental controls"
            Manifest.permission.CAMERA -> "Camera access for profile photos"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Access to photos for profile images"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Save photos and reports"
            else -> "Required for app functionality"
        }
    }
    
    // Callback interface for permission results
    interface PermissionCallback {
        fun onPermissionGranted(permission: String)
        fun onPermissionDenied(permission: String, isPermanent: Boolean)
        fun onAllPermissionsGranted()
        fun onSomePermissionsDenied(deniedPermissions: List<String>)
    }
    
    private var permissionCallback: PermissionCallback? = null
    
    fun setPermissionCallback(callback: PermissionCallback?) {
        this.permissionCallback = callback
    }
    
    fun requestPermissionsWithCallback(
        activity: Activity, 
        permissions: Array<String>, 
        callback: PermissionCallback,
        requestCode: Int = REQUEST_CODE_PERMISSIONS
    ) {
        setPermissionCallback(callback)
        requestPermissions(activity, permissions, requestCode)
    }
    
    fun processPermissionResults(results: List<PermissionResult>) {
        val callback = permissionCallback ?: return
        
        val grantedPermissions = results.filter { it.status == PermissionStatus.GRANTED }
        val deniedPermissions = results.filter { 
            it.status == PermissionStatus.DENIED || it.status == PermissionStatus.PERMANENTLY_DENIED 
        }
        
        // Individual permission callbacks
        grantedPermissions.forEach { result ->
            callback.onPermissionGranted(result.permission)
        }
        
        deniedPermissions.forEach { result ->
            val isPermanent = result.status == PermissionStatus.PERMANENTLY_DENIED
            callback.onPermissionDenied(result.permission, isPermanent)
        }
        
        // Overall result callbacks
        if (deniedPermissions.isEmpty()) {
            callback.onAllPermissionsGranted()
        } else {
            callback.onSomePermissionsDenied(deniedPermissions.map { it.permission })
        }
    }
    
    fun checkCriticalPermissionsOnAppStart(): Boolean {
        val criticalPermissions = arrayOf(
            Manifest.permission.PACKAGE_USAGE_STATS,
            Manifest.permission.SYSTEM_ALERT_WINDOW
        )
        
        return criticalPermissions.all { permission ->
            checkPermissionStatus(permission) == PermissionStatus.GRANTED
        }
    }
    
    fun getPermissionPriority(permission: String): Int {
        return when (permission) {
            Manifest.permission.PACKAGE_USAGE_STATS -> 1 // Highest priority
            Manifest.permission.SYSTEM_ALERT_WINDOW -> 2
            Manifest.permission.QUERY_ALL_PACKAGES -> 3
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION -> 4
            else -> 5 // Lowest priority
        }
    }
    
    private fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Permission not required on older versions
        }
    }
}
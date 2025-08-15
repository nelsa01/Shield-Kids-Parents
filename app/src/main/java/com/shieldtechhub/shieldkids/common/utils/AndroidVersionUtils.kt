package com.shieldtechhub.shieldkids.common.utils

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object AndroidVersionUtils {
    
    // Minimum supported version (Android 7.0 / API 24)
    const val MIN_SUPPORTED_SDK = Build.VERSION_CODES.N
    
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun isOreoOrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    fun isPieOrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun isAndroid10OrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    fun isAndroid11OrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun isAndroid12OrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun isAndroid13OrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun isAndroid14OrHigher(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    
    fun isSupportedVersion(): Boolean = Build.VERSION.SDK_INT >= MIN_SUPPORTED_SDK
    
    fun getVersionName(): String = when {
        isAndroid14OrHigher() -> "Android 14+"
        isAndroid13OrHigher() -> "Android 13"
        isAndroid12OrHigher() -> "Android 12"
        isAndroid11OrHigher() -> "Android 11"
        isAndroid10OrHigher() -> "Android 10"
        isPieOrHigher() -> "Android 9"
        isOreoOrHigher() -> "Android 8"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> "Android 7"
        else -> "Unsupported (${Build.VERSION.SDK_INT})"
    }
    
    /**
     * Device Admin features availability by Android version
     */
    object DeviceAdminFeatures {
        fun supportsModernDeviceAdmin(): Boolean = isOreoOrHigher()
        fun supportsAppRestrictions(): Boolean = isOreoOrHigher()
        fun supportsPackageHiding(): Boolean = isPieOrHigher()
        fun requiresSpecialPermissions(): Boolean = isAndroid10OrHigher()
    }
    
    /**
     * Permission system changes by Android version
     */
    object PermissionFeatures {
        fun requiresRuntimePermissions(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        fun requiresScopedStorage(): Boolean = isAndroid10OrHigher()
        fun requiresPackageVisibility(): Boolean = isAndroid11OrHigher()
        fun hasNotificationPermission(): Boolean = isAndroid13OrHigher()
    }
    
    /**
     * Background execution limitations by Android version
     */
    object BackgroundFeatures {
        fun hasBackgroundLimitations(): Boolean = isOreoOrHigher()
        fun requiresForegroundService(): Boolean = isOreoOrHigher()
        fun hasStrictBackgroundLimits(): Boolean = isPieOrHigher()
        fun requiresBatteryOptimizationWhitelist(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
    
    /**
     * App usage stats requirements by Android version
     */
    object UsageStatsFeatures {
        fun supportsUsageStats(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        fun requiresUsageStatsPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
        fun hasEnhancedUsageStats(): Boolean = isPieOrHigher()
    }
    
    /**
     * Get compatibility warnings for current Android version
     */
    fun getCompatibilityWarnings(): List<String> {
        val warnings = mutableListOf<String>()
        
        if (!isSupportedVersion()) {
            warnings.add("This Android version (${Build.VERSION.SDK_INT}) is not supported. Minimum required: API $MIN_SUPPORTED_SDK")
        }
        
        if (isAndroid10OrHigher()) {
            warnings.add("Android 10+ requires additional permissions for app visibility")
        }
        
        if (isAndroid11OrHigher()) {
            warnings.add("Android 11+ has stricter background execution limits")
        }
        
        if (isAndroid12OrHigher()) {
            warnings.add("Android 12+ requires careful notification permission handling")
        }
        
        if (isAndroid13OrHigher()) {
            warnings.add("Android 13+ requires explicit notification permission")
        }
        
        return warnings
    }
    
    /**
     * Get feature availability summary
     */
    fun getFeatureAvailability(): Map<String, Boolean> {
        return mapOf(
            "Device Admin" to DeviceAdminFeatures.supportsModernDeviceAdmin(),
            "App Restrictions" to DeviceAdminFeatures.supportsAppRestrictions(),
            "Usage Stats" to UsageStatsFeatures.supportsUsageStats(),
            "Background Services" to BackgroundFeatures.requiresForegroundService(),
            "Runtime Permissions" to PermissionFeatures.requiresRuntimePermissions(),
            "Scoped Storage" to PermissionFeatures.requiresScopedStorage(),
            "Package Visibility" to PermissionFeatures.requiresPackageVisibility()
        )
    }
}
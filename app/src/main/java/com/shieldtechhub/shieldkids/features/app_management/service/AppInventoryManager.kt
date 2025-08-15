package com.shieldtechhub.shieldkids.features.app_management.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class AppInventoryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AppInventoryManager"
    }
    
    private val packageManager = context.packageManager
    
    suspend fun getAllInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting app inventory scan...")
        
        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ requires specific flags
                packageManager.getInstalledPackages(
                    PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_META_DATA.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            }
            
            val apps = packages.mapNotNull { packageInfo ->
                try {
                    createAppInfo(packageInfo)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process package: ${packageInfo.packageName}", e)
                    null
                }
            }
            
            Log.d(TAG, "App inventory scan complete. Found ${apps.size} apps")
            apps
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps", e)
            emptyList()
        }
    }
    
    suspend fun getUserInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        getAllInstalledApps().filter { !it.isSystemApp }
    }
    
    suspend fun getSystemApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        getAllInstalledApps().filter { it.isSystemApp }
    }
    
    suspend fun getAppsByCategory(category: AppCategory): List<AppInfo> = withContext(Dispatchers.IO) {
        getAllInstalledApps().filter { it.category == category }
    }
    
    suspend fun searchApps(query: String): List<AppInfo> = withContext(Dispatchers.IO) {
        getAllInstalledApps().filter { app ->
            app.name.contains(query, ignoreCase = true) ||
            app.packageName.contains(query, ignoreCase = true)
        }
    }
    
    fun getAppInfo(packageName: String): AppInfo? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            }
            createAppInfo(packageInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get app info for: $packageName", e)
            null
        }
    }
    
    private fun createAppInfo(packageInfo: PackageInfo): AppInfo? {
        val applicationInfo = packageInfo.applicationInfo ?: return null
        
        return AppInfo(
            packageName = packageInfo.packageName,
            name = getAppName(applicationInfo),
            icon = getAppIcon(applicationInfo),
            version = packageInfo.versionName ?: "Unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            isSystemApp = isSystemApp(applicationInfo),
            category = determineAppCategory(applicationInfo),
            installTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime,
            permissions = getAppPermissions(packageInfo),
            isEnabled = applicationInfo.enabled,
            targetSdkVersion = applicationInfo.targetSdkVersion,
            dataDir = applicationInfo.dataDir ?: ""
        )
    }
    
    private fun getAppName(applicationInfo: ApplicationInfo): String {
        return try {
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            applicationInfo.packageName
        }
    }
    
    private fun getAppIcon(applicationInfo: ApplicationInfo): Drawable? {
        return try {
            packageManager.getApplicationIcon(applicationInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load icon for: ${applicationInfo.packageName}", e)
            null
        }
    }
    
    private fun isSystemApp(applicationInfo: ApplicationInfo): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }
    
    private fun determineAppCategory(applicationInfo: ApplicationInfo): AppCategory {
        val packageName = applicationInfo.packageName.lowercase(Locale.getDefault())
        
        return when {
            // Social Media Apps
            packageName.contains("facebook") ||
            packageName.contains("instagram") ||
            packageName.contains("snapchat") ||
            packageName.contains("tiktok") ||
            packageName.contains("twitter") ||
            packageName.contains("discord") ||
            packageName.contains("telegram") ||
            packageName.contains("whatsapp") -> AppCategory.SOCIAL
            
            // Gaming Apps
            packageName.contains("game") ||
            packageName.contains("minecraft") ||
            packageName.contains("roblox") ||
            packageName.contains("pokemon") ||
            packageName.contains("clash") ||
            packageName.contains("candy") -> AppCategory.GAMES
            
            // Educational Apps
            packageName.contains("duolingo") ||
            packageName.contains("khan") ||
            packageName.contains("education") ||
            packageName.contains("learn") ||
            packageName.contains("school") ||
            packageName.contains("study") -> AppCategory.EDUCATIONAL
            
            // Entertainment Apps
            packageName.contains("youtube") ||
            packageName.contains("netflix") ||
            packageName.contains("disney") ||
            packageName.contains("spotify") ||
            packageName.contains("music") ||
            packageName.contains("video") -> AppCategory.ENTERTAINMENT
            
            // Productivity Apps
            packageName.contains("office") ||
            packageName.contains("docs") ||
            packageName.contains("sheets") ||
            packageName.contains("calendar") ||
            packageName.contains("notes") -> AppCategory.PRODUCTIVITY
            
            // Communication Apps
            packageName.contains("mail") ||
            packageName.contains("message") ||
            packageName.contains("chat") ||
            packageName.contains("zoom") ||
            packageName.contains("skype") -> AppCategory.COMMUNICATION
            
            // System Apps
            isSystemApp(applicationInfo) -> AppCategory.SYSTEM
            
            // Default to Other
            else -> AppCategory.OTHER
        }
    }
    
    private fun getAppPermissions(packageInfo: PackageInfo): List<String> {
        return packageInfo.requestedPermissions?.toList() ?: emptyList()
    }
    
    suspend fun refreshAppInventory(): AppInventoryResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Refreshing app inventory...")
        
        val startTime = System.currentTimeMillis()
        val apps = getAllInstalledApps()
        val endTime = System.currentTimeMillis()
        
        val userApps = apps.filter { !it.isSystemApp }
        val systemApps = apps.filter { it.isSystemApp }
        
        AppInventoryResult(
            totalApps = apps.size,
            userApps = userApps.size,
            systemApps = systemApps.size,
            categories = apps.groupBy { it.category }.mapValues { it.value.size },
            scanTimeMs = endTime - startTime,
            apps = apps
        )
    }
}

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val version: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val category: AppCategory,
    val installTime: Long,
    val lastUpdateTime: Long,
    val permissions: List<String>,
    val isEnabled: Boolean,
    val targetSdkVersion: Int,
    val dataDir: String
)

enum class AppCategory {
    SOCIAL,
    GAMES,
    EDUCATIONAL,
    ENTERTAINMENT,
    PRODUCTIVITY,
    COMMUNICATION,
    SYSTEM,
    OTHER
}

data class AppInventoryResult(
    val totalApps: Int,
    val userApps: Int,
    val systemApps: Int,
    val categories: Map<AppCategory, Int>,
    val scanTimeMs: Long,
    val apps: List<AppInfo>
)
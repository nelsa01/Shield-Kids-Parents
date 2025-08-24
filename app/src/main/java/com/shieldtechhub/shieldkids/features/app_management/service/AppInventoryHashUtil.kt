package com.shieldtechhub.shieldkids.features.app_management.service

import java.security.MessageDigest

/**
 * Utility class for generating hash signatures of app inventory
 * to detect changes in installed apps
 */
object AppInventoryHashUtil {
    
    /**
     * Generate a hash signature for the complete app inventory
     * This hash changes when apps are installed/uninstalled/updated
     */
    fun generateInventoryHash(apps: List<AppInfo>): String {
        return try {
            // Sort apps by package name for consistent hash generation
            val sortedApps = apps.sortedBy { it.packageName }
            
            // Create a string representation of all relevant app data
            val inventoryString = sortedApps.joinToString(separator = "|") { app ->
                "${app.packageName}:${app.version}:${app.versionCode}:${app.isEnabled}:${app.lastUpdateTime}"
            }
            
            // Generate MD5 hash
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(inventoryString.toByteArray())
            
            // Convert to hex string
            digest.joinToString("") { "%02x".format(it) }
            
        } catch (e: Exception) {
            // Fallback to timestamp-based hash if MD5 fails
            System.currentTimeMillis().toString()
        }
    }
    
    /**
     * Generate a lightweight hash for quick change detection
     * Only includes package names and version codes
     */
    fun generateLightweightHash(apps: List<AppInfo>): String {
        return try {
            val sortedApps = apps.sortedBy { it.packageName }
            val lightString = sortedApps.joinToString(separator = "|") { app ->
                "${app.packageName}:${app.versionCode}"
            }
            
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(lightString.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
            
        } catch (e: Exception) {
            apps.size.toString() + System.currentTimeMillis().toString().takeLast(6)
        }
    }
    
    /**
     * Generate category-based hash for detecting category distribution changes
     */
    fun generateCategoryHash(apps: List<AppInfo>): String {
        return try {
            val categoryCountMap = apps.groupBy { it.category }
                .mapValues { it.value.size }
                .toSortedMap { a, b -> a.name.compareTo(b.name) }
            
            val categoryString = categoryCountMap.entries.joinToString(separator = "|") { entry ->
                "${entry.key.name}:${entry.value}"
            }
            
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(categoryString.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
            
        } catch (e: Exception) {
            apps.groupBy { it.category }.size.toString()
        }
    }
    
    /**
     * Compare two hashes and determine if they represent the same app state
     */
    fun areInventoriesIdentical(hash1: String, hash2: String): Boolean {
        return hash1.isNotEmpty() && hash2.isNotEmpty() && hash1 == hash2
    }
    
    /**
     * Generate comprehensive inventory fingerprint with multiple hash types
     */
    fun generateInventoryFingerprint(apps: List<AppInfo>): InventoryFingerprint {
        return InventoryFingerprint(
            fullHash = generateInventoryHash(apps),
            lightweightHash = generateLightweightHash(apps),
            categoryHash = generateCategoryHash(apps),
            appCount = apps.size,
            userAppCount = apps.count { !it.isSystemApp },
            systemAppCount = apps.count { it.isSystemApp },
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Comprehensive fingerprint of app inventory state
 */
data class InventoryFingerprint(
    val fullHash: String,
    val lightweightHash: String,
    val categoryHash: String,
    val appCount: Int,
    val userAppCount: Int,
    val systemAppCount: Int,
    val timestamp: Long
) {
    
    /**
     * Check if this fingerprint matches another (apps are the same)
     */
    fun matches(other: InventoryFingerprint): Boolean {
        return fullHash == other.fullHash
    }
    
    /**
     * Check if major changes occurred (new installs/uninstalls)
     */
    fun hasMajorChanges(other: InventoryFingerprint): Boolean {
        return appCount != other.appCount || 
               userAppCount != other.userAppCount ||
               lightweightHash != other.lightweightHash
    }
    
    /**
     * Check if only minor changes occurred (updates, enable/disable)
     */
    fun hasMinorChanges(other: InventoryFingerprint): Boolean {
        return !matches(other) && !hasMajorChanges(other)
    }
    
    /**
     * Check if category distribution changed
     */
    fun hasCategoryChanges(other: InventoryFingerprint): Boolean {
        return categoryHash != other.categoryHash
    }
    
    /**
     * Get change summary compared to another fingerprint
     */
    fun getChangeSummary(other: InventoryFingerprint): String {
        return when {
            matches(other) -> "No changes"
            hasMajorChanges(other) -> {
                val appDiff = appCount - other.appCount
                when {
                    appDiff > 0 -> "+$appDiff apps installed"
                    appDiff < 0 -> "${-appDiff} apps uninstalled" 
                    else -> "Apps changed"
                }
            }
            hasMinorChanges(other) -> "App updates detected"
            else -> "Unknown changes"
        }
    }
    
    /**
     * Convert to Firebase map
     */
    fun toFirebaseMap(): Map<String, Any> = mapOf(
        "fullHash" to fullHash,
        "lightweightHash" to lightweightHash,
        "categoryHash" to categoryHash,
        "appCount" to appCount,
        "userAppCount" to userAppCount,
        "systemAppCount" to systemAppCount,
        "timestamp" to timestamp
    )
    
    companion object {
        fun fromFirebaseMap(data: Map<String, Any>): InventoryFingerprint = InventoryFingerprint(
            fullHash = data["fullHash"] as? String ?: "",
            lightweightHash = data["lightweightHash"] as? String ?: "",
            categoryHash = data["categoryHash"] as? String ?: "",
            appCount = (data["appCount"] as? Number)?.toInt() ?: 0,
            userAppCount = (data["userAppCount"] as? Number)?.toInt() ?: 0,
            systemAppCount = (data["systemAppCount"] as? Number)?.toInt() ?: 0,
            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
        )
    }
}
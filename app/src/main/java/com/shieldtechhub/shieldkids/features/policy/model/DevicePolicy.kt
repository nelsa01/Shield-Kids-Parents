package com.shieldtechhub.shieldkids.features.policy.model

import org.json.JSONArray
import org.json.JSONObject

data class DevicePolicy(
    val id: String,
    val name: String,
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Device-level restrictions
    val cameraDisabled: Boolean = false,
    val installationsBlocked: Boolean = false,
    val keyguardRestrictions: Int = 0,
    val passwordPolicy: PasswordPolicy? = null,
    
    // Time-based restrictions
    val bedtimeStart: String? = null, // HH:MM format
    val bedtimeEnd: String? = null,   // HH:MM format
    val weekdayScreenTime: Long = 0,  // Minutes per day
    val weekendScreenTime: Long = 0,  // Minutes per day
    
    // Enhanced screen time controls
    val breakReminders: Boolean = false, // Enable break reminders
    val breakInterval: Long = 60, // Minutes between break reminders
    val breakDuration: Long = 15, // Minutes for each break
    val usageWarnings: Boolean = true, // Show "X minutes remaining" warnings
    val warningThresholds: List<Int> = listOf(30, 15, 5), // Warning at 30, 15, 5 minutes remaining
    val gracePeriod: Long = 5, // Extra minutes allowed when time is up
    val weeklyScreenTime: Long = 0, // Total minutes per week (0 = no weekly limit)
    
    // App-specific policies
    val appPolicies: List<AppPolicy> = emptyList(),
    
    // Categories blocked
    val blockedCategories: List<String> = emptyList(),
    
    // Emergency settings
    val emergencyMode: Boolean = false,
    val parentPin: String? = null
) {
    
    data class PasswordPolicy(
        val minLength: Int = 4,
        val requireUppercase: Boolean = false,
        val requireLowercase: Boolean = false,
        val requireNumbers: Boolean = false,
        val requireSpecialChars: Boolean = false,
        val maxAttempts: Int = 5,
        val lockoutDuration: Long = 300000 // 5 minutes in milliseconds
    )
    
    fun toJson(): String {
        val json = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("isActive", isActive)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("cameraDisabled", cameraDisabled)
            put("installationsBlocked", installationsBlocked)
            put("keyguardRestrictions", keyguardRestrictions)
            put("bedtimeStart", bedtimeStart)
            put("bedtimeEnd", bedtimeEnd)
            put("weekdayScreenTime", weekdayScreenTime)
            put("weekendScreenTime", weekendScreenTime)
            put("breakReminders", breakReminders)
            put("breakInterval", breakInterval)
            put("breakDuration", breakDuration)
            put("usageWarnings", usageWarnings)
            put("warningThresholds", JSONArray(warningThresholds))
            put("gracePeriod", gracePeriod)
            put("weeklyScreenTime", weeklyScreenTime)
            put("emergencyMode", emergencyMode)
            put("parentPin", parentPin)
            
            // Password policy
            passwordPolicy?.let { policy ->
                put("passwordPolicy", JSONObject().apply {
                    put("minLength", policy.minLength)
                    put("requireUppercase", policy.requireUppercase)
                    put("requireLowercase", policy.requireLowercase)
                    put("requireNumbers", policy.requireNumbers)
                    put("requireSpecialChars", policy.requireSpecialChars)
                    put("maxAttempts", policy.maxAttempts)
                    put("lockoutDuration", policy.lockoutDuration)
                })
            }
            
            // App policies
            put("appPolicies", JSONArray().apply {
                appPolicies.forEach { appPolicy ->
                    put(JSONObject(appPolicy.toJson()))
                }
            })
            
            // Blocked categories
            put("blockedCategories", JSONArray().apply {
                blockedCategories.forEach { category ->
                    put(category)
                }
            })
        }
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): DevicePolicy {
            val json = JSONObject(jsonString)
            
            // Parse password policy
            val passwordPolicy = if (json.has("passwordPolicy")) {
                val policyJson = json.getJSONObject("passwordPolicy")
                PasswordPolicy(
                    minLength = policyJson.optInt("minLength", 4),
                    requireUppercase = policyJson.optBoolean("requireUppercase", false),
                    requireLowercase = policyJson.optBoolean("requireLowercase", false),
                    requireNumbers = policyJson.optBoolean("requireNumbers", false),
                    requireSpecialChars = policyJson.optBoolean("requireSpecialChars", false),
                    maxAttempts = policyJson.optInt("maxAttempts", 5),
                    lockoutDuration = policyJson.optLong("lockoutDuration", 300000)
                )
            } else null
            
            // Parse app policies
            val appPolicies = mutableListOf<AppPolicy>()
            if (json.has("appPolicies")) {
                val appPoliciesArray = json.getJSONArray("appPolicies")
                for (i in 0 until appPoliciesArray.length()) {
                    val appPolicyJson = appPoliciesArray.getJSONObject(i).toString()
                    appPolicies.add(AppPolicy.fromJson(appPolicyJson))
                }
            }
            
            // Parse blocked categories
            val blockedCategories = mutableListOf<String>()
            if (json.has("blockedCategories")) {
                val categoriesArray = json.getJSONArray("blockedCategories")
                for (i in 0 until categoriesArray.length()) {
                    blockedCategories.add(categoriesArray.getString(i))
                }
            }
            
            // Parse warning thresholds
            val warningThresholds = mutableListOf<Int>()
            if (json.has("warningThresholds")) {
                val thresholdsArray = json.getJSONArray("warningThresholds")
                for (i in 0 until thresholdsArray.length()) {
                    warningThresholds.add(thresholdsArray.getInt(i))
                }
            } else {
                warningThresholds.addAll(listOf(30, 15, 5)) // Default thresholds
            }
            
            return DevicePolicy(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description", ""),
                isActive = json.optBoolean("isActive", true),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                cameraDisabled = json.optBoolean("cameraDisabled", false),
                installationsBlocked = json.optBoolean("installationsBlocked", false),
                keyguardRestrictions = json.optInt("keyguardRestrictions", 0),
                passwordPolicy = passwordPolicy,
                bedtimeStart = json.optString("bedtimeStart").takeIf { it.isNotEmpty() },
                bedtimeEnd = json.optString("bedtimeEnd").takeIf { it.isNotEmpty() },
                weekdayScreenTime = json.optLong("weekdayScreenTime", 0),
                weekendScreenTime = json.optLong("weekendScreenTime", 0),
                breakReminders = json.optBoolean("breakReminders", false),
                breakInterval = json.optLong("breakInterval", 60),
                breakDuration = json.optLong("breakDuration", 15),
                usageWarnings = json.optBoolean("usageWarnings", true),
                warningThresholds = warningThresholds,
                gracePeriod = json.optLong("gracePeriod", 5),
                weeklyScreenTime = json.optLong("weeklyScreenTime", 0),
                appPolicies = appPolicies,
                blockedCategories = blockedCategories,
                emergencyMode = json.optBoolean("emergencyMode", false),
                parentPin = json.optString("parentPin").takeIf { it.isNotEmpty() }
            )
        }
        
        fun createDefault(deviceId: String): DevicePolicy {
            return DevicePolicy(
                id = "default_$deviceId",
                name = "Default Policy",
                description = "Basic parental controls for child safety",
                cameraDisabled = false,
                installationsBlocked = true,
                bedtimeStart = "21:00",
                bedtimeEnd = "07:00",
                weekdayScreenTime = 120, // 2 hours
                weekendScreenTime = 180, // 3 hours
                blockedCategories = listOf("SOCIAL", "GAMES"),
                passwordPolicy = PasswordPolicy(
                    minLength = 6,
                    requireNumbers = true,
                    maxAttempts = 3
                )
            )
        }
        
        fun createStrictPolicy(deviceId: String): DevicePolicy {
            return DevicePolicy(
                id = "strict_$deviceId",
                name = "Strict Policy",
                description = "High-security parental controls",
                cameraDisabled = true,
                installationsBlocked = true,
                bedtimeStart = "20:00",
                bedtimeEnd = "08:00",
                weekdayScreenTime = 60,  // 1 hour
                weekendScreenTime = 90,  // 1.5 hours
                blockedCategories = listOf("SOCIAL", "GAMES", "ENTERTAINMENT"),
                passwordPolicy = PasswordPolicy(
                    minLength = 8,
                    requireUppercase = true,
                    requireNumbers = true,
                    requireSpecialChars = true,
                    maxAttempts = 3,
                    lockoutDuration = 600000 // 10 minutes
                ),
                appPolicies = listOf(
                    AppPolicy(
                        packageName = "com.android.chrome",
                        action = AppPolicy.Action.TIME_LIMIT,
                        timeLimit = AppPolicy.TimeLimit(
                            dailyLimitMinutes = 30,
                            allowedStartTime = "09:00",
                            allowedEndTime = "18:00"
                        )
                    )
                )
            )
        }
    }
    
    // Utility functions
    fun isWithinBedtime(): Boolean {
        if (bedtimeStart == null || bedtimeEnd == null) return false
        
        val currentTime = getCurrentTimeString()
        
        // Handle overnight bedtime (e.g., 21:00 to 07:00)
        return if (bedtimeStart > bedtimeEnd) {
            currentTime >= bedtimeStart || currentTime <= bedtimeEnd
        } else {
            currentTime >= bedtimeStart && currentTime <= bedtimeEnd
        }
    }
    
    fun isWeekend(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        return dayOfWeek == java.util.Calendar.SATURDAY || dayOfWeek == java.util.Calendar.SUNDAY
    }
    
    fun getDailyScreenTimeLimit(): Long {
        return if (isWeekend()) weekendScreenTime else weekdayScreenTime
    }
    
    private fun getCurrentTimeString(): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
    
    fun isCategoryBlocked(category: String): Boolean {
        return blockedCategories.contains(category.uppercase())
    }
    
    fun getAppPolicy(packageName: String): AppPolicy? {
        return appPolicies.find { it.packageName == packageName }
    }
    
    fun hasAppRestrictions(packageName: String): Boolean {
        return getAppPolicy(packageName) != null
    }
    
    fun isAppBlocked(packageName: String): Boolean {
        return getAppPolicy(packageName)?.action == AppPolicy.Action.BLOCK
    }
    
}
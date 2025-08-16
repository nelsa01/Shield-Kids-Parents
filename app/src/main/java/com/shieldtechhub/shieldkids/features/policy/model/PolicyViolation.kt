package com.shieldtechhub.shieldkids.features.policy.model

import org.json.JSONObject

data class PolicyViolation(
    val id: String,
    val packageName: String,
    val type: ViolationType,
    val timestamp: Long,
    val details: String? = null,
    val deviceId: String,
    val userId: String? = null,
    val severity: Severity = Severity.MEDIUM,
    val resolved: Boolean = false,
    val parentNotified: Boolean = false
) {
    
    fun toJson(): String {
        val json = JSONObject().apply {
            put("id", id)
            put("packageName", packageName)
            put("type", type.name)
            put("timestamp", timestamp)
            put("details", details)
            put("deviceId", deviceId)
            put("userId", userId)
            put("severity", severity.name)
            put("resolved", resolved)
            put("parentNotified", parentNotified)
        }
        
        return json.toString()
    }
    
    companion object {
        fun fromJson(jsonString: String): PolicyViolation {
            val json = JSONObject(jsonString)
            
            return PolicyViolation(
                id = json.getString("id"),
                packageName = json.getString("packageName"),
                type = ViolationType.valueOf(json.getString("type")),
                timestamp = json.getLong("timestamp"),
                details = json.optString("details").takeIf { it.isNotEmpty() },
                deviceId = json.getString("deviceId"),
                userId = json.optString("userId").takeIf { it.isNotEmpty() },
                severity = Severity.valueOf(json.optString("severity", "MEDIUM")),
                resolved = json.optBoolean("resolved", false),
                parentNotified = json.optBoolean("parentNotified", false)
            )
        }
    }
    
    enum class Severity {
        LOW,      // Minor violations that don't require immediate action
        MEDIUM,   // Standard violations that should be logged and reported
        HIGH,     // Serious violations that require immediate parent notification
        CRITICAL  // Critical violations that may trigger emergency measures
    }
    
    fun getFormattedTimestamp(): String {
        val date = java.util.Date(timestamp)
        val formatter = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return formatter.format(date)
    }
    
    fun getDisplayMessage(): String {
        return when (type) {
            ViolationType.APP_BLOCKED_ATTEMPTED -> {
                "Attempted to open blocked app: $packageName"
            }
            ViolationType.TIME_LIMIT_EXCEEDED -> {
                "Time limit exceeded for app: $packageName"
            }
            ViolationType.INSTALLATION_BLOCKED -> {
                "Blocked installation of app: $packageName"
            }
            ViolationType.POLICY_TAMPERING -> {
                "Policy tampering detected: $details"
            }
            ViolationType.BEDTIME_VIOLATION -> {
                "Device used during bedtime hours"
            }
            ViolationType.CATEGORY_BLOCKED -> {
                "Attempted to access blocked category app: $packageName"
            }
            ViolationType.SCHEDULE_VIOLATION -> {
                "App used outside allowed schedule: $packageName"
            }
            ViolationType.UNINSTALL_ATTEMPTED -> {
                "Attempted to uninstall Shield Kids app"
            }
            ViolationType.DEVICE_ADMIN_DISABLED -> {
                "Device admin privileges were disabled"
            }
            ViolationType.UNKNOWN -> {
                "Unknown policy violation: $details"
            }
        }
    }
    
    fun getActionRequired(): String {
        return when (severity) {
            Severity.LOW -> "No immediate action required"
            Severity.MEDIUM -> "Review and monitor"
            Severity.HIGH -> "Parent notification sent"
            Severity.CRITICAL -> "Emergency measures activated"
        }
    }
    
    fun getSeverityColor(): String {
        return when (severity) {
            Severity.LOW -> "#4CAF50"      // Green
            Severity.MEDIUM -> "#FF9800"   // Orange
            Severity.HIGH -> "#F44336"     // Red
            Severity.CRITICAL -> "#9C27B0" // Purple
        }
    }
    
    fun shouldTriggerEmergencyResponse(): Boolean {
        return severity == Severity.CRITICAL || 
               type == ViolationType.POLICY_TAMPERING ||
               type == ViolationType.DEVICE_ADMIN_DISABLED ||
               type == ViolationType.UNINSTALL_ATTEMPTED
    }
    
    fun shouldNotifyParent(): Boolean {
        return severity >= Severity.MEDIUM && !parentNotified
    }
    
    fun markAsResolved(): PolicyViolation {
        return copy(resolved = true)
    }
    
    fun markAsNotified(): PolicyViolation {
        return copy(parentNotified = true)
    }
}

enum class ViolationType {
    APP_BLOCKED_ATTEMPTED,    // Child tried to open a blocked app
    TIME_LIMIT_EXCEEDED,      // App time limit was exceeded
    INSTALLATION_BLOCKED,     // App installation was blocked
    POLICY_TAMPERING,         // Someone tried to modify policies
    BEDTIME_VIOLATION,        // Device used during bedtime
    CATEGORY_BLOCKED,         // Tried to access blocked category
    SCHEDULE_VIOLATION,       // App used outside allowed schedule
    UNINSTALL_ATTEMPTED,      // Tried to uninstall Shield Kids
    DEVICE_ADMIN_DISABLED,    // Device admin was disabled
    UNKNOWN                   // Unknown or unclassified violation
}
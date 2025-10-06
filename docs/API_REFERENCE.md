# Shield Kids - API Reference

## Overview
This document provides comprehensive API documentation for the Shield Kids application, covering both Android native APIs and Firebase cloud services.

## Table of Contents
- [Core Android APIs](#core-android-apis)
- [Firebase Cloud Functions](#firebase-cloud-functions)
- [Local Service APIs](#local-service-apis)
- [Data Models](#data-models)
- [Error Handling](#error-handling)

---

## Core Android APIs

### App Management API

#### AppInventoryManager
**Location**: `features/app_management/service/AppInventoryManager.kt`

##### `scanInstalledApps()`
Scans all installed applications on the device.

```kotlin
suspend fun scanInstalledApps(): List<AppInfo>
```

**Returns**: List of `AppInfo` objects containing app metadata
**Permissions Required**: `QUERY_ALL_PACKAGES` (Android 11+)
**Example**:
```kotlin
val apps = AppInventoryManager.scanInstalledApps()
apps.forEach { app ->
    println("${app.name}: ${app.packageName}")
}
```

##### `categorizeApp(packageName: String)`
Automatically categorizes an app based on its package name and metadata.

```kotlin
fun categorizeApp(packageName: String): AppCategory
```

**Parameters**:
- `packageName`: Android package identifier (e.g., "com.facebook.katana")

**Returns**: `AppCategory` enum (SOCIAL, GAMES, EDUCATIONAL, PRODUCTIVITY, etc.)

##### `getAppWithUsage(packageName: String)`
Retrieves app information combined with usage statistics.

```kotlin
suspend fun getAppWithUsage(packageName: String): AppWithUsage?
```

**Returns**: `AppWithUsage` object or null if app not found
**Permissions Required**: `PACKAGE_USAGE_STATS`

### Permission Management API

#### PermissionManager
**Location**: `common/utils/PermissionManager.kt`

##### `requestEssentialPermissions(activity: Activity)`
Requests all permissions critical for app functionality.

```kotlin
fun requestEssentialPermissions(activity: Activity): PermissionRequestResult
```

**Permissions Requested**:
- Location access
- Usage stats access
- System alert window
- Device admin privileges

##### `checkPermissionStatus(permission: String)`
Checks current status of a specific permission.

```kotlin
fun checkPermissionStatus(permission: String): PermissionStatus
```

**Returns**: `PermissionStatus.GRANTED`, `DENIED`, or `NOT_REQUESTED`

### Device Administration API

#### DeviceAdminManager  
**Location**: `common/utils/DeviceAdminManager.kt`

##### `activateDeviceAdmin(activity: Activity)`
Initiates device admin activation flow.

```kotlin
fun activateDeviceAdmin(activity: Activity): Boolean
```

**Returns**: `true` if activation started successfully
**Side Effects**: Launches system device admin setup activity

##### `lockDevice()`
Immediately locks the device screen.

```kotlin
fun lockDevice(): Boolean
```

**Returns**: `true` if lock successful
**Requires**: Active device admin privileges

##### `disableCamera(enabled: Boolean)`
Enables or disables device camera access.

```kotlin
fun disableCamera(enabled: Boolean): Boolean  
```

**Parameters**:
- `enabled`: `false` to disable camera, `true` to enable

### Policy Management API

#### PolicyEnforcementManager
**Location**: `features/policy/PolicyEnforcementManager.kt`

##### `applyPolicy(policy: DevicePolicy)`
Applies a device policy immediately.

```kotlin
suspend fun applyPolicy(policy: DevicePolicy): PolicyResult
```

**Parameters**:
- `policy`: Complete device policy configuration

**Returns**: `PolicyResult` with success/failure details

##### `checkViolation(appPackage: String)`
Checks if app usage would violate current policies.

```kotlin
fun checkViolation(appPackage: String): PolicyViolation?
```

**Returns**: `PolicyViolation` object if violation detected, null otherwise

##### `getActivePolicies()`
Retrieves all currently active policies.

```kotlin
suspend fun getActivePolicies(): List<DevicePolicy>
```

### Screen Time API

#### ScreenTimeCollector
**Location**: `features/screen_time/service/ScreenTimeCollector.kt`

##### `getDailyUsage(date: LocalDate)`
Gets app usage data for a specific date.

```kotlin
suspend fun getDailyUsage(date: LocalDate): Map<String, Long>
```

**Returns**: Map of package name to usage time in milliseconds
**Permissions Required**: `PACKAGE_USAGE_STATS`

##### `getWeeklyReport(startDate: LocalDate)`
Generates comprehensive weekly usage report.

```kotlin
suspend fun getWeeklyReport(startDate: LocalDate): WeeklyUsageReport
```

**Returns**: Structured report with daily breakdowns and trends

---

## Firebase Cloud Functions

### Base URL
`https://us-central1-<your-project-id>.cloudfunctions.net/`

### Child Management Functions

#### `beforeCreateChild`
**Trigger**: Firestore document creation in `children/{childId}`
**Purpose**: Validates child PIN uniqueness

**Automatic Execution**: Triggered by Firestore
**Validation**: 
- Ensures PIN is unique across all children
- Automatically deletes duplicate PIN attempts

#### `onChildCreated`  
**Trigger**: Firestore document creation in `children/{childId}`
**Purpose**: Post-creation child setup

**Actions Performed**:
1. Hashes child PIN using SHA-256
2. Updates document with hashed PIN
3. Retrieves parent email from users collection
4. Sends email notification with unhashed PIN

**Email Template**:
```
Subject: Shield-Kids – Child PIN
Body: Hi,

You just added "[child_name]" to Shield-Kids.
PIN (keep it safe): [original_pin]

Regards,
Shield-Kids Team
```

---

## Local Service APIs

### Background Monitoring

#### ShieldMonitoringService
**Location**: `common/base/ShieldMonitoringService.kt`

##### Service Lifecycle
```kotlin
class ShieldMonitoringService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    override fun onDestroy()
}
```

**Service Type**: Foreground service with persistent notification
**Auto-restart**: Returns `START_STICKY` for automatic restart

##### Key Methods

##### `startMonitoring()`
Begins comprehensive device monitoring.

```kotlin
fun startMonitoring(): Boolean
```

**Monitoring Includes**:
- App launch detection
- Screen time tracking  
- Policy violation checking
- System event monitoring

##### `syncWithCloud()`
Synchronizes local data with Firebase backend.

```kotlin
suspend fun syncWithCloud(): SyncResult
```

**Sync Operations**:
- Upload usage statistics
- Download policy updates
- Sync app inventory changes
- Upload violation reports

### Data Synchronization

#### FirestoreSyncManager
**Location**: `common/utils/FirestoreSyncManager.kt`

##### `syncChildData(childId: String)`
Synchronizes all data for a specific child.

```kotlin
suspend fun syncChildData(childId: String): SyncResult
```

##### `uploadUsageData(data: List<UsageRecord>)`
Uploads usage statistics to Firestore.

```kotlin
suspend fun uploadUsageData(data: List<UsageRecord>): Boolean
```

##### `downloadPolicies(childId: String)`
Downloads latest policies from cloud.

```kotlin
suspend fun downloadPolicies(childId: String): List<DevicePolicy>
```

---

## Data Models

### Core Models

#### AppInfo
```kotlin
data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val category: AppCategory,
    val installTime: Long,
    val lastUpdateTime: Long,
    val permissions: List<String>
)
```

#### DevicePolicy
```kotlin
data class DevicePolicy(
    val id: String,
    val childId: String,
    val policyType: PolicyType,
    val restrictions: Map<String, Any>,
    val schedule: PolicySchedule?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
```

#### ScreenTimeData
```kotlin
data class ScreenTimeData(
    val date: LocalDate,
    val appUsages: Map<String, Long>, // package -> milliseconds
    val totalScreenTime: Long,
    val categories: Map<AppCategory, Long>,
    val sessionsCount: Int
)
```

#### PolicyViolation
```kotlin
data class PolicyViolation(
    val id: String,
    val childId: String,
    val policyId: String,
    val appPackage: String,
    val violationType: ViolationType,
    val timestamp: Long,
    val resolved: Boolean
)
```

### Enums

#### AppCategory
```kotlin
enum class AppCategory {
    SOCIAL, GAMES, EDUCATIONAL, PRODUCTIVITY, 
    ENTERTAINMENT, SHOPPING, NEWS, HEALTH, 
    TRAVEL, FOOD, FINANCE, SYSTEM, OTHER
}
```

#### PolicyType
```kotlin
enum class PolicyType {
    APP_BLOCKING,      // Block specific apps
    CATEGORY_TIME_LIMIT,  // Time limits per category
    DEVICE_TIME_LIMIT,    // Total device time limit
    BEDTIME_RESTRICTION,  // Time-based restrictions
    APP_INSTALLATION_BLOCK // Prevent new app installs
}
```

#### ViolationType
```kotlin
enum class ViolationType {
    TIME_LIMIT_EXCEEDED,
    BLOCKED_APP_ATTEMPT,
    INAPPROPRIATE_INSTALL_ATTEMPT,
    BEDTIME_VIOLATION
}
```

---

## Error Handling

### Standard Error Types

#### APIException
```kotlin
class APIException(
    message: String,
    val errorCode: ErrorCode,
    cause: Throwable? = null
) : Exception(message, cause)
```

#### ErrorCode Enum
```kotlin
enum class ErrorCode {
    PERMISSION_DENIED,
    DEVICE_ADMIN_REQUIRED,
    NETWORK_ERROR,
    SYNC_FAILED,
    POLICY_CONFLICT,
    INVALID_CHILD_PIN,
    FIRESTORE_ERROR
}
```

### Error Response Format

#### API Responses
```kotlin
data class APIResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: APIError?
)

data class APIError(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null
)
```

### Common Error Scenarios

#### Permission Errors
```kotlin
// Handle permission denied
try {
    val usage = ScreenTimeCollector.getDailyUsage(today)
} catch (e: SecurityException) {
    // Redirect to permission request
    PermissionManager.requestUsageStatsPermission(activity)
}
```

#### Device Admin Errors
```kotlin
// Handle device admin requirement
if (!DeviceAdminManager.isAdminActive()) {
    DeviceAdminManager.activateDeviceAdmin(activity)
    return // Wait for activation
}
```

#### Firebase Errors
```kotlin
// Handle Firestore errors
try {
    FirestoreSyncManager.syncChildData(childId)
} catch (e: FirebaseException) {
    when (e) {
        is FirebaseAuthException -> handleAuthError(e)
        is FirebaseFirestoreException -> handleFirestoreError(e)
        else -> handleGenericError(e)
    }
}
```

---

## Usage Examples

### Complete App Scan Example
```kotlin
class AppScanExample {
    suspend fun performFullAppScan(childId: String) {
        try {
            // 1. Scan installed apps
            val apps = AppInventoryManager.scanInstalledApps()
            
            // 2. Categorize and get usage data
            val appsWithUsage = apps.map { app ->
                AppInventoryManager.getAppWithUsage(app.packageName)
            }.filterNotNull()
            
            // 3. Sync with cloud
            val syncResult = FirestoreSyncManager.uploadAppInventory(
                childId, 
                appsWithUsage
            )
            
            if (syncResult.success) {
                println("Successfully synced ${apps.size} apps")
            }
            
        } catch (e: SecurityException) {
            PermissionManager.requestEssentialPermissions(activity)
        } catch (e: APIException) {
            handleAPIError(e)
        }
    }
}
```

### Policy Enforcement Example
```kotlin
class PolicyExample {
    suspend fun enforceAppPolicy(appPackage: String) {
        try {
            // 1. Check for violations
            val violation = PolicyEnforcementManager.checkViolation(appPackage)
            
            if (violation != null) {
                // 2. Block app if violation found
                when (violation.violationType) {
                    ViolationType.TIME_LIMIT_EXCEEDED -> {
                        AppBlockingManager.blockApp(appPackage)
                        showTimeViolationDialog(violation)
                    }
                    ViolationType.BLOCKED_APP_ATTEMPT -> {
                        AppBlockingManager.blockApp(appPackage)
                        showBlockedAppDialog(violation)
                    }
                }
                
                // 3. Log violation
                FirestoreSyncManager.uploadViolation(violation)
            }
            
        } catch (e: Exception) {
            Log.e("PolicyEnforcement", "Error enforcing policy", e)
        }
    }
}
```

---

**Last Updated**: September 2025  
**API Version**: 1.0.0  
**Compatibility**: Android API 24+ (Android 7.0+)
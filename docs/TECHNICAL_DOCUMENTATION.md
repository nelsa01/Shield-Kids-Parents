# Shield Kids - Technical Documentation

## Project Overview

Shield Kids is a comprehensive Android parental control application that enables parents to monitor and manage their children's device usage. The system consists of an Android mobile application with Firebase backend services for real-time synchronization and cloud functions for business logic.

## Architecture Overview

### High-Level Architecture
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Parent App    │    │   Child Device  │    │   Firebase      │
│   Dashboard     │◄──►│   Shield Kids   │◄──►│   Backend       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                      │
                                               ┌─────────────┐
                                               │   Cloud     │
                                               │  Functions  │
                                               └─────────────┘
```

### Core Components

#### 1. Android Application (`app/`)
- **Target SDK**: 36 (Android 14+)
- **Min SDK**: 24 (Android 7.0+)
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose + Traditional Views
- **Architecture Pattern**: MVVM with Repository Pattern

#### 2. Firebase Backend (`functions/`)
- **Runtime**: Node.js 22
- **Services**: Firestore, Authentication, Cloud Messaging, Cloud Functions
- **Functions**: Child management, PIN hashing, email notifications

## Technology Stack

### Android Dependencies
```kotlin
// Core Android
- androidx.core:core-ktx:1.16.0
- androidx.appcompat:appcompat:1.7.1
- androidx.lifecycle:lifecycle-*:2.7.0-2.9.2

// UI Framework
- androidx.compose.bom:2024.09.00
- androidx.activity.compose:1.10.1
- material3 + material:1.12.0

// Firebase
- firebase-auth:24.0.0
- firebase-firestore:26.0.0
- firebase-storage:21.0.0
- firebase-messaging:25.0.0

// Background Processing
- androidx.work:work-runtime-ktx:2.9.0
- kotlinx-coroutines:1.7.3
```

### Build System
- **Gradle**: 8.12.2
- **Kotlin**: 2.0.21
- **Build Configuration**: `build.gradle.kts` with version catalogs

## Project Structure

```
shield_kids_parents/
├── app/                              # Main Android application
│   ├── src/main/java/com/shieldtechhub/shieldkids/
│   │   ├── adapters/                 # RecyclerView adapters
│   │   ├── common/                   # Shared components
│   │   │   ├── base/                 # Core services & receivers
│   │   │   │   ├── ShieldMonitoringService.kt    # Background monitoring
│   │   │   │   ├── ShieldDeviceAdminReceiver.kt  # Device admin
│   │   │   │   ├── SystemEventReceiver.kt        # System events
│   │   │   │   └── BootReceiver.kt               # Auto-start service
│   │   │   ├── sync/                 # Data synchronization
│   │   │   └── utils/                # Utility classes
│   │   │       ├── PermissionManager.kt          # Permission handling
│   │   │       ├── DeviceAdminManager.kt         # Device admin management
│   │   │       ├── FirestoreSyncManager.kt       # Cloud sync
│   │   │       └── AndroidVersionUtils.kt        # Compatibility layer
│   │   ├── features/                 # Feature modules
│   │   │   ├── app_management/       # App inventory & control
│   │   │   ├── app_blocking/         # Application blocking
│   │   │   ├── screen_time/          # Usage tracking
│   │   │   └── policy/               # Policy management
│   │   ├── ui/theme/                 # Material Design theme
│   │   └── debug/                    # Debug utilities
│   ├── src/main/res/                 # Resources
│   └── build.gradle.kts              # App build configuration
├── functions/                        # Firebase Cloud Functions
│   ├── index.js                      # Cloud function definitions
│   └── package.json                  # Node.js dependencies
├── gradle/                           # Gradle wrapper & dependencies
├── build.gradle.kts                  # Root build configuration
├── firebase.json                     # Firebase project configuration
└── README_TESTING.md                 # Testing guide
```

## Core Features & Modules

### 1. Authentication & User Management
- **Parent Registration**: Email/password authentication
- **Child Profiles**: PIN-based child identification
- **Role-based Access**: Parent vs Child modes

**Key Files**:
- `ParentLoginActivity.kt`, `ParentRegisterActivity.kt`
- `RoleSelectionActivity.kt`, `ChildConnectActivity.kt`

### 2. Device Administration
- **Device Admin Privileges**: Camera control, device lock, password policies
- **System Integration**: Deep Android system permissions
- **Compatibility Layer**: Android version-specific implementations

**Key Files**:
- `ShieldDeviceAdminReceiver.kt:15-50` - Device admin callbacks
- `DeviceAdminManager.kt:25-100` - Admin privilege management
- `DeviceAdminSetupActivity.kt:30-150` - Setup flow UI

### 3. App Management & Inventory
- **App Discovery**: Scans installed applications
- **Categorization**: Automatically categorizes apps (Social, Games, Educational, etc.)
- **Policy Enforcement**: Blocks/allows apps based on parent policies

**Key Files**:
- `AppInventoryManager.kt:20-200` - Core app discovery logic
- `AppManagementActivity.kt:40-300` - App management UI
- `CategoryPoliciesActivity.kt:25-250` - Category-based policies

### 4. Screen Time Monitoring
- **Usage Tracking**: Monitors app usage statistics
- **Time Limits**: Enforces daily/weekly limits
- **Reports**: Detailed usage analytics for parents

**Key Files**:
- `ScreenTimeCollector.kt:30-180` - Usage data collection
- `ScreenTimeDashboardActivity.kt:45-400` - Parent dashboard
- `ScreenTimeCollectionWorker.kt:20-120` - Background collection

### 5. Policy Management
- **Device Policies**: Device-wide restrictions
- **App Policies**: Per-app rules and time limits  
- **Category Policies**: Rules applied to app categories
- **Real-time Enforcement**: Live policy application

**Key Files**:
- `PolicyEnforcementManager.kt:35-250` - Core policy engine
- `PolicyProcessor.kt:15-180` - Policy evaluation logic
- `PolicySyncManager.kt:25-150` - Cloud synchronization

### 6. Background Monitoring
- **System Service**: Persistent background monitoring
- **Event Detection**: App launches, installations, system events
- **Data Sync**: Real-time cloud synchronization

**Key Files**:
- `ShieldMonitoringService.kt:40-300` - Main monitoring service
- `SystemEventReceiver.kt:20-100` - System event handling
- `UnifiedChildSyncService.kt:30-200` - Data synchronization

## Data Models & Storage

### Firebase Firestore Collections
```javascript
// Document structure
users/{parentUid}                     // Parent user data
children/{childId}                    // Child profiles
  └── devices/{deviceId}              // Child devices
      ├── apps/{appId}                // Installed apps
      ├── policies/{policyId}         // Applied policies  
      ├── screen_time/{date}          // Daily usage data
      └── violations/{violationId}    // Policy violations
```

### Local Storage (SQLite)
- **DatabaseHelper.kt**: Local caching and offline support
- **Sync Mechanism**: Bi-directional Firebase synchronization
- **Conflict Resolution**: Last-write-wins with timestamps

## Security & Permissions

### Critical Permissions
```xml
<!-- Device Admin -->
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />

<!-- App Usage Stats -->
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />

<!-- System Overlay -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Accessibility (for app blocking) -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />

<!-- Location (for geofencing) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### Security Features
- **PIN Hashing**: SHA-256 with secure random generation
- **Device Admin**: Privileged system access for enforcement
- **Secure Communication**: Firebase Auth + Firestore security rules
- **Local Protection**: Passcode-protected parent settings

## Development Workflow

### Build & Test
```bash
# Build debug version
./gradlew assembleDebug

# Run tests
./gradlew test

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# Deploy Firebase functions
cd functions && npm run deploy
```

### Testing Framework
- **System Test Interface**: Built-in testing UI (`SystemTestActivity.kt`)
- **Debug Activities**: Specialized debug screens for each module
- **Validation Script**: `validate_implementation.py` for automated checks

### Code Conventions
- **Language**: Kotlin with coroutines for async operations
- **Architecture**: MVVM pattern with Repository layer
- **Naming**: PascalCase for classes, camelCase for methods/variables
- **Documentation**: KDoc comments for public APIs

## Firebase Cloud Functions

### Function Implementations
```javascript
// Child creation validation
exports.beforeCreateChild = functions.firestore
    .document("children/{childId}")
    .onCreate(async (snap, context) => {
        // PIN uniqueness validation
        // Automatic deletion on conflicts
    });

// Child registration workflow  
exports.onChildCreated = functions.firestore
    .document("children/{childId}")
    .onCreate(async (snap, ctx) => {
        // PIN hashing with SHA-256
        // Parent email notification
        // Initial policy setup
    });
```

## API Documentation

### Firebase Authentication
- **Sign Up**: Email/password registration
- **Sign In**: Persistent authentication with refresh tokens
- **Password Reset**: Email-based password recovery

### Firestore Data Access
- **Real-time Listeners**: Live data synchronization
- **Batch Operations**: Efficient bulk updates
- **Security Rules**: Role-based data access control

### Local Services API
```kotlin
// App Management
AppInventoryManager.scanInstalledApps(): List<AppInfo>
AppInventoryManager.categorizeApp(packageName: String): AppCategory

// Policy Enforcement  
PolicyEnforcementManager.applyPolicy(policy: DevicePolicy)
PolicyEnforcementManager.checkViolation(app: String): PolicyViolation?

// Screen Time
ScreenTimeCollector.getDailyUsage(date: Date): Map<String, Long>
ScreenTimeCollector.getWeeklyReport(): WeeklyUsageReport
```

## Setup Instructions

### Prerequisites
- **Android Studio**: Arctic Fox or newer
- **Java**: JDK 11+
- **Node.js**: 22+ for Firebase functions
- **Firebase CLI**: `npm install -g firebase-tools`

### Development Setup
1. **Clone Repository**:
   ```bash
   git clone <repository-url>
   cd shield_kids_parents
   ```

2. **Firebase Configuration**:
   ```bash
   firebase login
   firebase use --add  # Select your project
   ```

3. **Android Setup**:
   - Open in Android Studio
   - Sync Gradle dependencies
   - Connect physical device or setup emulator

4. **Firebase Functions Setup**:
   ```bash
   cd functions
   npm install
   npm run serve  # Local development
   ```

### Production Deployment
1. **Build Release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

2. **Deploy Cloud Functions**:
   ```bash
   cd functions && npm run deploy
   ```

3. **Security Configuration**:
   - Setup Firestore security rules
   - Configure Firebase Authentication providers
   - Enable required Firebase services

## Troubleshooting

### Common Issues
- **Permission Denials**: Some permissions require manual Settings app navigation
- **Device Admin Setup**: May show security warnings (expected behavior)
- **Package Visibility**: Android 11+ restrictions may limit app scanning
- **Battery Optimization**: May interfere with background services

### Debug Tools
- **System Test Activity**: Comprehensive status checking
- **Debug Helpers**: Module-specific debug utilities
- **Firebase Console**: Cloud data inspection
- **ADB Logging**: Real-time application logs

## Contributing Guidelines

### Code Standards
- Follow existing Kotlin coding conventions
- Add KDoc documentation for public APIs
- Include unit tests for business logic
- Update this documentation for architectural changes

### Pull Request Process
1. Create feature branch from `main`
2. Implement changes with tests
3. Update relevant documentation
4. Submit PR with detailed description

---

**Last Updated**: September 2025  
**Project Status**: Active Development  
**Current Version**: 1.0.0-debug
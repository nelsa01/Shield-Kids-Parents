# Shield Kids - Developer Setup Guide

## Quick Start

### Prerequisites Checklist
- [ ] **Android Studio**: Hedgehog 2023.1.1 or newer
- [ ] **Java Development Kit**: JDK 11 or higher
- [ ] **Node.js**: Version 22+ (for Firebase Functions)
- [ ] **Firebase CLI**: `npm install -g firebase-tools`
- [ ] **Git**: For version control
- [ ] **Android Device**: Physical device or emulator (API 24+)

## Initial Setup

### 1. Repository Setup
```bash
# Clone the repository
git clone <your-repository-url>
cd shield_kids_parents

# Verify directory structure
ls -la
# Should show: app/, functions/, gradle/, build.gradle.kts, etc.
```

### 2. Firebase Project Configuration

#### 2.1 Firebase CLI Setup
```bash
# Install Firebase CLI globally
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize Firebase in project (if not already done)
firebase init
```

#### 2.2 Connect Firebase Project
```bash
# Add your Firebase project
firebase use --add

# Select your project and give it an alias (e.g., "default")
# This creates/updates .firebaserc file
```

#### 2.3 Firebase Configuration Files
Ensure you have these files (obtain from Firebase Console):
- `app/google-services.json` - Android app configuration
- `.firebaserc` - Project alias configuration
- `firebase.json` - Firebase services configuration

### 3. Android Development Setup

#### 3.1 Open in Android Studio
```bash
# Open Android Studio and select "Open an Existing Project"
# Navigate to shield_kids_parents/ directory
```

#### 3.2 Gradle Sync
1. Android Studio will prompt for Gradle sync
2. Click "Sync Now" to download dependencies
3. Wait for sync to complete (may take several minutes initially)

#### 3.3 SDK Requirements
Verify in Android Studio > Tools > SDK Manager:
- [x] **Android SDK Platform 36** (targetSdk)
- [x] **Android SDK Platform 24** (minSdk) 
- [x] **Android SDK Build-Tools 36.0.0+**
- [x] **Android SDK Platform-Tools**

### 4. Firebase Functions Setup

```bash
# Navigate to functions directory
cd functions/

# Install Node.js dependencies
npm install

# Verify installation
npm list
# Should show firebase-admin, firebase-functions, etc.
```

## Development Environment

### Build Configuration

#### Debug Build
```bash
# From project root
./gradlew assembleDebug

# Output location:
# app/build/outputs/apk/debug/app-debug.apk
```

#### Release Build (Production)
```bash
./gradlew assembleRelease

# Output location:  
# app/build/outputs/apk/release/app-release.apk
```

### Local Firebase Development

#### Functions Emulator
```bash
cd functions/
npm run serve

# Firebase Functions will be available at:
# http://localhost:5001/<your-project-id>/us-central1/functionName
```

#### Firestore Emulator (Optional)
```bash
# From project root
firebase emulators:start --only firestore

# Access emulator UI at: http://localhost:4000
```

## Device Setup & Testing

### Physical Device Setup
1. **Enable Developer Options**:
   - Go to Settings > About phone
   - Tap "Build number" 7 times
   
2. **Enable USB Debugging**:
   - Go to Settings > Developer options
   - Enable "USB debugging"
   
3. **Install ADB Drivers** (Windows):
   - Download from Android SDK platform-tools
   - Or use Android Studio's SDK Manager

### Device Testing
```bash
# Verify device connection
adb devices

# Install debug APK
adb install app/build/outputs/apk/debug/app-debug.apk

# View real-time logs
adb logcat | grep "ShieldKids"
```

### Test App Functionality

#### System Test Interface
1. Open Shield Kids app
2. Register/Login as parent
3. Navigate to Parent Dashboard  
4. **Long-press the Settings button** → System Test opens
5. Test each component:
   - [ ] Permissions
   - [ ] Device Admin
   - [ ] App Scan
   - [ ] Background Services

## Project Structure for Developers

### Key Directories to Know
```
app/src/main/java/com/shieldtechhub/shieldkids/
├── common/
│   ├── base/          # Core services (monitoring, device admin)
│   ├── utils/         # Utility classes (permissions, sync, etc.)
│   └── sync/          # Cloud synchronization
├── features/          # Feature modules
│   ├── app_management/    # App inventory & control
│   ├── app_blocking/      # Application blocking
│   ├── screen_time/       # Usage tracking & reports
│   └── policy/            # Policy management & enforcement
├── adapters/          # RecyclerView adapters
├── ui/theme/          # Material Design theming
└── debug/             # Debug utilities & activities
```

### Important Configuration Files
- `build.gradle.kts` (app) - App dependencies & configuration
- `build.gradle.kts` (root) - Project-wide settings
- `gradle/libs.versions.toml` - Dependency version catalog
- `firebase.json` - Firebase services configuration
- `functions/package.json` - Node.js dependencies

## Development Workflow

### Feature Development Process
1. **Create Feature Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make Changes**: 
   - Follow existing code conventions (Kotlin)
   - Add appropriate documentation
   - Include unit tests where applicable

3. **Test Changes**:
   ```bash
   # Build and test
   ./gradlew assembleDebug
   ./gradlew test
   
   # Install and test on device
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Firebase Functions Testing**:
   ```bash
   cd functions/
   npm run serve  # Test locally
   npm run deploy # Deploy to Firebase (when ready)
   ```

### Code Standards
- **Language**: Kotlin for Android, JavaScript for Functions
- **Architecture**: MVVM with Repository pattern
- **Async**: Use Coroutines for background operations
- **Documentation**: KDoc for public APIs
- **Naming**: PascalCase for classes, camelCase for methods

### Git Workflow
```bash
# Before starting work
git pull origin main

# Regular commits
git add .
git commit -m "feat: description of changes"

# Push feature branch
git push origin feature/your-feature-name

# Create Pull Request via GitHub/GitLab
```

## Troubleshooting

### Common Build Issues
1. **Gradle Sync Failed**:
   ```bash
   # Clean and rebuild
   ./gradlew clean
   ./gradlew build
   ```

2. **Dependencies Not Found**:
   - Check `gradle/libs.versions.toml`
   - Verify internet connection
   - Try: File > Invalidate Caches and Restart

3. **Firebase Configuration Issues**:
   - Verify `google-services.json` is in `app/` directory
   - Check `.firebaserc` has correct project ID
   - Run `firebase projects:list` to verify access

### Runtime Issues
1. **Permissions Not Granted**:
   - Some permissions require manual Settings navigation
   - Use System Test Activity to debug permission states

2. **Device Admin Not Working**:
   - Security warnings are expected
   - May require manual activation in device settings

3. **Background Service Killed**:
   - Disable battery optimization for the app
   - Check device manufacturer's power management settings

### Development Tools
- **Android Studio Debugger**: Set breakpoints in Kotlin code
- **Firebase Console**: Monitor cloud data and functions
- **ADB Logcat**: Real-time application logs
- **System Test Activity**: Built-in debugging interface

## Environment Variables & Secrets

### Firebase Configuration
- Store sensitive Firebase config in environment variables
- Never commit `google-services.json` with real credentials to version control
- Use different Firebase projects for development/staging/production

### Local Development
Create a `local.properties` file in project root:
```properties
# Firebase project IDs
firebase.dev.project.id=your-dev-project
firebase.prod.project.id=your-prod-project

# Android SDK location (auto-generated)
sdk.dir=/path/to/Android/Sdk
```

## Additional Resources

### Documentation
- [Android Developer Guide](https://developer.android.com/)
- [Firebase Documentation](https://firebase.google.com/docs)
- [Kotlin Language Guide](https://kotlinlang.org/docs/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)

### Testing Documentation
- See `README_TESTING.md` for detailed testing procedures
- Use built-in System Test Activity for comprehensive testing

---

**Next Steps**: After setup completion, see `TECHNICAL_DOCUMENTATION.md` for detailed architecture and development guidelines.
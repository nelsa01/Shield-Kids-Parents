package com.shieldtechhub.shieldkids

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class ShieldKidsApplication : Application() {
    
    companion object {
        private const val TAG = "ShieldKidsApp"
        
        @Volatile
        private var INSTANCE: ShieldKidsApplication? = null
        
        fun getInstance(): ShieldKidsApplication? = INSTANCE
        
        var isFirebaseInitialized = false
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        
        Log.d(TAG, "Application starting - initializing Firebase...")
        initializeFirebase()
    }
    
    private fun initializeFirebase() {
        try {
            // Initialize Firebase if not already initialized
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase initialized from google-services.json")
            } else {
                Log.d(TAG, "Firebase already initialized")
            }
            
            // Configure Firestore settings for better offline support
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)  // Enable offline persistence
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
            firestore.firestoreSettings = settings
            
            // Ensure auth instance is ready
            val auth = FirebaseAuth.getInstance()
            
            // Mark as initialized
            isFirebaseInitialized = true
            Log.i(TAG, "✅ Firebase services fully initialized and configured")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize Firebase", e)
            isFirebaseInitialized = false
            
            // Continue app execution but with degraded functionality
            // Services should check isFirebaseInitialized before using Firebase
        }
    }
    
    /**
     * Check if Firebase is ready for use
     */
    fun isFirebaseReady(): Boolean {
        return isFirebaseInitialized && FirebaseApp.getApps(this).isNotEmpty()
    }
    
    /**
     * Get Firebase initialization status for debugging
     */
    fun getFirebaseStatus(): Map<String, Any> {
        return mapOf(
            "isInitialized" to isFirebaseInitialized,
            "appsCount" to FirebaseApp.getApps(this).size,
            "authReady" to try { FirebaseAuth.getInstance(); true } catch (e: Exception) { false },
            "firestoreReady" to try { FirebaseFirestore.getInstance(); true } catch (e: Exception) { false }
        )
    }
}
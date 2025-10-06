package com.shieldtechhub.shieldkids.features.core

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d("FCM_TOKEN", "Refreshed token: $token")
        // Save token to Firestore when we have user context
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM_MESSAGE", "From: ${message.from}")
        // Handle received messages here (not needed for sending)
    }
}
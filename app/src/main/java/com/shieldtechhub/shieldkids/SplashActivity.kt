package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide the action bar for full-screen splash
        supportActionBar?.hide()

        // Delay for 5 seconds then navigate to role selection
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, RoleSelectionActivity::class.java)
            startActivity(intent)
            finish() // Close splash activity so user can't go back
        }, 5000) // 5000 milliseconds = 5 seconds
    }
} 
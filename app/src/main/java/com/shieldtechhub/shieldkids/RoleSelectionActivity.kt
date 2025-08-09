package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class RoleSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        // Set up click listeners for role selection buttons
        findViewById<View>(R.id.btnParent).setOnClickListener {
            // Navigate to parent login
            startActivity(Intent(this, ParentLoginActivity::class.java))
        }

        findViewById<View>(R.id.btnChild).setOnClickListener {
            // Navigate to child connect device screen
            startActivity(Intent(this, ChildConnectActivity::class.java))
        }
    }
}
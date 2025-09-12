package com.shieldtechhub.shieldkids.features.authentication

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.shieldtechhub.shieldkids.R
import com.shieldtechhub.shieldkids.features.child_management.ui.ChildDeviceLinkingActivity
import com.shieldtechhub.shieldkids.features.authentication.ParentLoginActivity

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
            // Navigate to child device linking screen
            startActivity(Intent(this, ChildDeviceLinkingActivity::class.java))
        }
    }
}
package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class RoleSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_role_selection)

        // Find the Get Started button container and make it clickable
        val getStartedButton = findViewById<View>(R.id.getStartedButton)
        getStartedButton?.setOnClickListener {
            showRoleSelectionDialog()
        }
    }

    private fun showRoleSelectionDialog() {
        // For now, navigate directly to parent login
        // You can implement a dialog here later if needed
        startActivity(Intent(this, ParentLoginActivity::class.java))
    }
}
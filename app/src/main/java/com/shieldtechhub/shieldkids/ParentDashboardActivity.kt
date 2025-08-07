package com.shieldtechhub.shieldkids

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.shieldtechhub.shieldkids.databinding.ActivityParentDashboardBinding

class ParentDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityParentDashboardBinding
    private val auth = FirebaseAuth.getInstance()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Add Child Button
        binding.btnAddChild.setOnClickListener {
            startActivity(Intent(this, AddChildActivity::class.java))
        }

        // Requests Card
        binding.cardRequests.setOnClickListener {
            Toast.makeText(this, "Requests feature coming soon", Toast.LENGTH_SHORT).show()
        }

        // Details Link
        binding.tvDetails.setOnClickListener {
            Toast.makeText(this, "App usage details coming soon", Toast.LENGTH_SHORT).show()
        }

        // Bottom Navigation
        binding.navHome.setOnClickListener {
            // Already on home, no action needed
        }

        binding.navLocation.setOnClickListener {
            Toast.makeText(this, "Location feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.navLightning.setOnClickListener {
            Toast.makeText(this, "Lightning feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.navSettings.setOnClickListener {
            Toast.makeText(this, "Settings feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
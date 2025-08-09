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
        binding.tvWelcome.text = "Welcome, ${user.email}"

        binding.btnAddChild.setOnClickListener {
            startActivity(Intent(this, AddChildActivity::class.java))
        }

        binding.btnViewChildren.setOnClickListener {
            // Placeholder for future implementation
            Toast.makeText(this, "View Children feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
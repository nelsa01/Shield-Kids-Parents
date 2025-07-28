package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.shieldtechhub.shieldkids.databinding.ActivityParentLoginBinding
import com.shieldtechhub.shieldkids.ParentDashboardActivity

class ParentLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { login() }
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, ParentRegisterActivity::class.java))
            finish()
        }
    }

    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val pwd   = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || pwd.length < 6) {
            Toast.makeText(this, "Enter valid email & ≥6-char password", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, pwd)
            .addOnSuccessListener {
                Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, ParentDashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
    }
}
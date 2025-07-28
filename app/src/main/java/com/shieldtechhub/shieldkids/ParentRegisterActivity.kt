package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityParentRegisterBinding

class ParentRegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParentRegisterBinding
    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRegister.setOnClickListener { register() }
        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, ParentLoginActivity::class.java))
            finish()
        }
    }

    private fun register() {
        val name  = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val pwd   = binding.etPassword.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || pwd.length < 6) {
            Toast.makeText(this, "Fill all fields & use ≥6 chars for password", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, pwd)
            .addOnSuccessListener { res ->
                val uid = res.user!!.uid
                val parent = hashMapOf(
                    "name"  to name,
                    "email" to email,
                    "role"  to "parent",
                )
                db.collection("users").document(uid)
                    .set(parent)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Parent registered", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, ParentDashboardActivity::class.java))
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
    }
}
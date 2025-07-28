package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityChildLoginBinding
import com.shieldtechhub.shieldkids.SecurityUtils

class ChildLoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildLoginBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { loginChild() }
    }

    private fun loginChild() {
        val name = binding.etName.text.toString().trim()
        val refNumber = binding.etRefNumber.text.toString().trim()

        if (name.isEmpty() || refNumber.length != 4) {
            Toast.makeText(this, "Enter valid name and 4-digit reference number", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("children")
            .whereEqualTo("name", name)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "Child not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                for (document in documents) {
                    val storedHash = document.getString("refNumberHash") ?: ""
                    if (SecurityUtils.verifyRefNumber(refNumber, storedHash)) {
                        val intent = Intent(this, ChildDashboardActivity::class.java)
                        intent.putExtra("CHILD_ID", document.id)
                        startActivity(intent)
                        finish()
                        return@addOnSuccessListener
                    }
                }
                Toast.makeText(this, "Invalid reference number", Toast.LENGTH_SHORT).show()
            }
    }
}
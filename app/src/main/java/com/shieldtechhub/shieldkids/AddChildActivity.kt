package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityAddChildBinding
import com.shieldtechhub.shieldkids.SecurityUtils

class AddChildActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddChildBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSave.setOnClickListener { saveChild() }
    }

    private fun saveChild() {
        val name = binding.etName.text.toString().trim()
        val year = binding.etYear.text.toString().toIntOrNull()

        if (name.isEmpty() || year == null) {
            Toast.makeText(this, "Name and valid year required", Toast.LENGTH_SHORT).show()
            return
        }

        val parentUid = auth.currentUser?.uid ?: return
        val parentEmail = auth.currentUser?.email ?: ""

        // Generate and store hashed reference number
        val refNumber = SecurityUtils.generateRefNumber()
        val refNumberHash = SecurityUtils.hashRefNumber(refNumber)

        db.collection("children")
            .whereEqualTo("refNumberHash", refNumberHash)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    Toast.makeText(this, "Reference number conflict, please try again", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val child = hashMapOf(
                    "name" to name,
                    "yearOfBirth" to year,
                    "parentUid" to parentUid,
                    "refNumberHash" to refNumberHash,
                    "devices" to listOf<String>()
                )

                db.collection("children")
                    .add(child)
                    .addOnSuccessListener { docRef ->
                        db.collection("users").document(parentUid)
                            .update("children", FieldValue.arrayUnion(docRef.id))
                            .addOnSuccessListener {
                                // Show notification and send email
                                Toast.makeText(
                                    this,
                                    "Child added! Reference number: $refNumber",
                                    Toast.LENGTH_LONG
                                ).show()

                                finish()
                            }
                    }
            }
    }
}
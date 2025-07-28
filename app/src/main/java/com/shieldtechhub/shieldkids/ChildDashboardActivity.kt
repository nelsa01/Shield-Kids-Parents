package com.shieldtechhub.shieldkids

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class ChildDashboardActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_dashboard)

        val childId = intent.getStringExtra("CHILD_ID") ?: return
        val tvDevices: TextView = findViewById(R.id.tvDevices)

        db.collection("children").document(childId)
            .get()
            .addOnSuccessListener { document ->
                val devices = document.get("devices") as? List<String> ?: emptyList()
                tvDevices.text = if (devices.isNotEmpty()) {
                    "Your devices:\n${devices.joinToString("\n")}"
                } else {
                    "No devices added yet"
                }
            }
    }
}
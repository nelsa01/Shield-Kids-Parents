package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore

class ChildConnectActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var etConnectionCode: EditText
    private lateinit var btnConnect: Button
    private lateinit var codeContainer: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_connect)

        firestore = FirebaseFirestore.getInstance()

        // Initialize views
        etConnectionCode = findViewById(R.id.etConnectionCode)
        btnConnect = findViewById(R.id.btnConnect)
        codeContainer = findViewById(R.id.codeContainer)

        // Set up click listeners
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        btnConnect.setOnClickListener { connectDevice() }

        // Set up code field focus listener
        setupCodeFieldFocusListener()

        // Set up code validation
        setupCodeValidation()
    }

    private fun setupCodeFieldFocusListener() {
        etConnectionCode.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                codeContainer.setBackgroundResource(R.drawable.input_field_background_focused)
                etConnectionCode.setHintTextColor(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                codeContainer.setBackgroundResource(R.drawable.input_field_background)
                etConnectionCode.setHintTextColor(ContextCompat.getColor(this, R.color.gray_500))
            }
        }
    }

    private fun setupCodeValidation() {
        etConnectionCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateConnectionCode()
            }
        })
    }

    private fun validateConnectionCode(): Boolean {
        val code = etConnectionCode.text.toString().trim()
        
        return if (code.isEmpty()) {
            etConnectionCode.error = "Connection code is required"
            false
        } else if (code.length < 6) {
            etConnectionCode.error = "Connection code must be at least 6 characters"
            false
        } else {
            etConnectionCode.error = null
            true
        }
    }

    private fun connectDevice() {
        val connectionCode = etConnectionCode.text.toString().trim().uppercase()

        if (!validateConnectionCode()) {
            return
        }

        // Disable button and show loading state
        btnConnect.isEnabled = false
        btnConnect.text = "Connecting..."

        // For now, we'll just show a success message and navigate to child dashboard
        // In the future, this should validate the connection code against the database
        Toast.makeText(this, "Device connected successfully!", Toast.LENGTH_LONG).show()
        
//        // Navigate to child dashboard
//        val intent = Intent(this, ChildDashboardActivity::class.java)
//        intent.putExtra("CONNECTION_CODE", connectionCode)
//        startActivity(intent)
//        finish()
    }
}

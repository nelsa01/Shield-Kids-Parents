package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Random

class AddDeviceActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private var childId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)

        childId = intent.getStringExtra("childId") ?: ""
        if (childId.isEmpty()) { 
            finish(); 
            return 
        }

        val etName: EditText = findViewById(R.id.etDeviceName)
        val typeInput: AutoCompleteTextView = findViewById(R.id.etDeviceType)
        val btnSave: Button = findViewById(R.id.btnSaveDevice)

        // Set device type options
        val deviceTypes = listOf("Phone", "Tablet", "Laptop", "Desktop", "Gaming Console")
        typeInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceTypes))

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val type = typeInput.text.toString().trim()
            
            if (name.isEmpty() || type.isEmpty()) {
                Toast.makeText(this, "Please enter device name and select type", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Generate a 6-digit linking code
            val linkingCode = generateLinkingCode()
            
            // Create device with proper structure
            val device = hashMapOf(
                "childId" to childId,
                "name" to name,
                "linkingCode" to linkingCode,
                "type" to type,
                "isConnected" to false, // New field for connection status
                "settings" to hashMapOf(
                    "screenTime" to 0,
                    "geofence" to hashMapOf(
                        "enabled" to false,
                        "locations" to listOf<Double>()
                    ),
                    "contentFilter" to hashMapOf(
                        "enabled" to false,
                        "categories" to listOf<String>()
                    ),
                    "appRestrictions" to hashMapOf(
                        "blockedApps" to listOf<String>()
                    )
                )
            )

            // Save device to Firestore
            db.collection("devices").add(device)
                .addOnSuccessListener { documentReference ->
                    // Update the child's devices field
                    val childRef = db.collection("children").document(childId)
                    childRef.get().addOnSuccessListener { childDoc ->
                        if (childDoc.exists()) {
                            val currentDevices = childDoc.get("devices") as? HashMap<String, String> ?: HashMap()
                            currentDevices[documentReference.id] = name
                            
                            childRef.update("devices", currentDevices)
                                .addOnSuccessListener {
                                    // Navigate to waiting screen with device info
                                    val intent = Intent(this, WaitingForSetupActivity::class.java)
                                    intent.putExtra("deviceId", documentReference.id)
                                    intent.putExtra("deviceName", name)
                                    intent.putExtra("linkingCode", linkingCode)
                                    intent.putExtra("childId", childId)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to update child: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                }
                .addOnFailureListener { e -> 
                    Toast.makeText(this, "Failed to add device: ${e.localizedMessage}", Toast.LENGTH_SHORT).show() 
                }
        }
    }

    private fun generateLinkingCode(): Int {
        val random = Random()
        return 100000 + random.nextInt(900000) // Generates 6-digit code (100000-999999)
    }
}


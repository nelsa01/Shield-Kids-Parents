package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import com.shieldtechhub.shieldkids.databinding.ActivityChildDeviceLinkingBinding

class ChildDeviceLinkingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildDeviceLinkingBinding
    private lateinit var deviceStateManager: DeviceStateManager
    private val db = FirebaseFirestore.getInstance()
    private var isLinking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildDeviceLinkingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceStateManager = DeviceStateManager(this)
        
        setupUI()
        setupInputFieldListeners()
        setupClickListeners()
    }

    private fun setupUI() {
        supportActionBar?.hide()
        
        binding.tvTitle.text = "Link This Device"
        binding.tvSubtitle.text = "Enter the reference number provided by your parent to link this device."
        binding.btnLink.text = "Link Device"
    }

    private fun setupInputFieldListeners() {
        binding.etReferenceNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.referenceFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_focused)
                binding.ivReferenceIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                binding.referenceFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background)
                binding.ivReferenceIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
                validateReferenceField()
            }
        }

        binding.etReferenceNumber.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!binding.etReferenceNumber.hasFocus()) {
                    validateReferenceField()
                }
            }
        })
    }

    private fun setupClickListeners() {
        binding.btnLink.setOnClickListener {
            if (!isLinking) {
                linkDevice()
            }
        }

        binding.btnBackToSelection.setOnClickListener {
            if (!isLinking) {
                finish()
            }
        }
    }

    private fun validateReferenceField(): Boolean {
        val reference = binding.etReferenceNumber.text.toString().trim()
        
        return when {
            reference.isEmpty() -> {
                setFieldError()
                false
            }
            reference.length != 6 -> { // 6-character reference numbers
                setFieldError()
                false
            }
            !reference.matches(Regex("^[A-Z0-9]+$")) -> { // Alphanumeric uppercase only
                setFieldError()
                false
            }
            else -> {
                setFieldNormal()
                true
            }
        }
    }

    private fun setFieldError() {
        binding.referenceFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_error)
        binding.ivReferenceIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
    }

    private fun setFieldNormal() {
        binding.referenceFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background)
        binding.ivReferenceIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
    }

    private fun linkDevice() {
        val referenceNumber = binding.etReferenceNumber.text.toString().trim().uppercase()
        
        if (!validateReferenceField()) {
            showError("Please enter a valid 6-character reference number")
            binding.etReferenceNumber.requestFocus()
            return
        }

        isLinking = true
        showLoading(true)

        // Hash the reference number for security
        val referenceHash = SecurityUtils.hashRefNumber(referenceNumber)

        // Query Firebase for child document with matching reference hash
        db.collection("children")
            .whereEqualTo("refNumberHash", referenceHash)
            .get()
            .addOnSuccessListener { querySnapshot ->
                isLinking = false
                showLoading(false)
                
                if (querySnapshot.isEmpty) {
                    showError("Invalid reference number. Please check with your parent.")
                    return@addOnSuccessListener
                }

                val childDoc = querySnapshot.documents[0]
                val childData = childDoc.data

                if (childData == null) {
                    showError("Child profile not found. Please try again.")
                    return@addOnSuccessListener
                }

                // Extract child information
                val childName = childData["name"] as? String ?: "Unknown"
                val parentData = childData["parent"] as? Map<String, Any>
                val parentId = parentData?.get("docID") as? String ?: ""
                val parentEmail = parentData?.get("name") as? String ?: ""

                if (parentId.isEmpty()) {
                    showError("Parent information not found. Please contact your parent.")
                    return@addOnSuccessListener
                }

                // Register this device with the child profile
                registerDevice(childDoc.id, childName, parentId, parentEmail, referenceHash)
            }
            .addOnFailureListener { exception ->
                isLinking = false
                showLoading(false)
                showError("Failed to verify reference number: ${exception.localizedMessage}")
            }
    }

    private fun registerDevice(childId: String, childName: String, parentId: String, parentEmail: String, referenceHash: String) {
        showLoading(true)
        
        val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        val deviceInfo = mapOf(
            "deviceId" to deviceId,
            "deviceModel" to android.os.Build.MODEL,
            "androidVersion" to android.os.Build.VERSION.RELEASE,
            "linkTimestamp" to System.currentTimeMillis(),
            "deviceName" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )

        // Add this device to the child's devices collection (field for parent compatibility)
        db.collection("children").document(childId)
            .update("devices.$deviceId", deviceInfo)
            .addOnSuccessListener {
                // Also create device document in subcollection for child data storage
                createDeviceSubcollectionDocument(childId, deviceId, deviceInfo) {
                    // Clear the reference number hash to prevent reuse
                    clearReferenceNumber(childId) {
                        // Set this device as child device locally
                        deviceStateManager.setAsChildDevice(
                            childId = childId,
                            childName = childName,
                            parentId = parentId,
                            parentEmail = parentEmail
                        )
                        
                        showLoading(false)
                        showSuccess("Device linked successfully!")
                        
                        // Navigate to child mode
                        val intent = Intent(this, ChildModeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                showError("Failed to register device: ${exception.localizedMessage}")
            }
    }

    /**
     * Create device document in subcollection for child-side data storage
     */
    private fun createDeviceSubcollectionDocument(childId: String, deviceId: String, deviceInfo: Map<String, Any>, onComplete: () -> Unit) {
        val deviceDocId = "device_$deviceId"
        
        // Create device document in subcollection with basic info
        val deviceDocData = mapOf(
            "deviceId" to deviceId,
            "deviceModel" to deviceInfo["deviceModel"],
            "androidVersion" to deviceInfo["androidVersion"],
            "deviceName" to deviceInfo["deviceName"],
            "linkTimestamp" to deviceInfo["linkTimestamp"],
            "status" to "active",
            "lastAppSyncTime" to null,
            "lastPolicyUpdateTime" to null,
            "appSyncStatus" to "PENDING",
            "createdAt" to System.currentTimeMillis()
        )
        
        db.collection("children")
            .document(childId)
            .collection("devices")
            .document(deviceDocId)
            .set(deviceDocData)
            .addOnSuccessListener {
                android.util.Log.i("ChildDeviceLinking", "Created device subcollection document: $deviceDocId")
                onComplete()
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("ChildDeviceLinking", "Failed to create device subcollection document", exception)
                // Continue anyway - the main device field was created successfully
                onComplete()
            }
    }

    private fun clearReferenceNumber(childId: String, onComplete: () -> Unit) {
        // Clear the reference number hash to make it single-use
        db.collection("children").document(childId)
            .update("refNumberHash", "")
            .addOnSuccessListener {
                onComplete()
            }
            .addOnFailureListener {
                // Continue even if clearing fails - device is already linked
                onComplete()
            }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLink.isEnabled = !show
        binding.btnBackToSelection.isEnabled = !show
        binding.etReferenceNumber.isEnabled = !show
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isLinking) {
            super.onBackPressed()
        }
    }
}
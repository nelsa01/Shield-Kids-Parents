package com.shieldtechhub.shieldkids

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityAddChildBinding
import com.shieldtechhub.shieldkids.SecurityUtils
import com.shieldtechhub.shieldkids.common.utils.DeviceStateManager
import java.util.Calendar

class AddChildActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddChildBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var deviceStateManager: DeviceStateManager
    private var selectedImageUri: Uri? = null

    // Photo picker launcher for selecting child profile image from gallery
    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // Update the avatar image view to show selected photo
            binding.ivChildAvatar.setImageURI(it)
            binding.ivChildAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            // Remove tint since we're now showing an actual photo
            binding.ivChildAvatar.clearColorFilter()

            // Update the text to indicate photo was selected
            binding.tvPhotoLabel.text = "Photo selected"
            binding.tvPhotoLabel.setTextColor(ContextCompat.getColor(this, R.color.teal_500))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceStateManager = DeviceStateManager(this)

        setupCalendarPicker()
        setupPhotoSelection()
        binding.btnSave.setOnClickListener { saveChild() }
    }

    private fun setupCalendarPicker() {
        // Make the year field non-editable and show calendar on click
        binding.etYear.isFocusable = false
        binding.etYear.isClickable = true

        // Set up click listeners for both the field and the container
        binding.etYear.setOnClickListener { showYearPicker() }
        binding.yearContainer.setOnClickListener { showYearPicker() }
    }

    private fun setupPhotoSelection() {
        // Set up photo selection click listener
        binding.btnChoosePhoto.setOnClickListener {
            // Launch image picker to select from local storage
            photoPickerLauncher.launch("image/*")
        }
    }

    private fun showYearPicker() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)

        // Set default year to 10 years ago (typical for a child)
        val defaultYear = currentYear - 10

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, _, _ ->
                binding.etYear.setText(year.toString())
                validateYearField()
            },
            defaultYear,
            0, // Month (0 = January)
            1  // Day
        )

        // Set year range (1-18 years old)
        datePickerDialog.datePicker.minDate = Calendar.getInstance().apply {
            set(currentYear - 18, 0, 1)
        }.timeInMillis

        datePickerDialog.datePicker.maxDate = Calendar.getInstance().apply {
            set(currentYear - 1, 11, 31)
        }.timeInMillis

        datePickerDialog.show()
    }

    private fun validateYearField(): Boolean {
        val yearText = binding.etYear.text.toString().trim()

        return when {
            yearText.isEmpty() -> {
                binding.etYear.error = "Please select the year of birth"
                false
            }
            yearText.toIntOrNull() == null -> {
                binding.etYear.error = "Please enter a valid year"
                false
            }
            else -> {
                val year = yearText.toInt()
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                // Check if year is within valid range (1-18 years old)
                if (year < currentYear - 18 || year > currentYear - 1) {
                    binding.etYear.error = "Child must be between 1 and 18 years old"
                    false
                } else {
                    binding.etYear.error = null
                    true
                }
            }
        }
    }

    private fun saveChild() {
        val name = binding.etName.text.toString().trim()
        val yearText = binding.etYear.text.toString().trim()

        // Validate name
        if (name.isEmpty()) {
            showError("Please enter the child's name")
            binding.etName.requestFocus()
            return
        }

        if (name.length < 2) {
            showError("Name must be at least 2 characters long")
            binding.etName.requestFocus()
            return
        }

        if (name.length > 50) {
            showError("Name must be less than 50 characters")
            binding.etName.requestFocus()
            return
        }

        // Validate year of birth
        if (yearText.isEmpty()) {
            showError("Please select the year of birth")
            showYearPicker()
            return
        }

        val year = yearText.toIntOrNull()
        if (year == null) {
            showError("Please select a valid year")
            showYearPicker()
            return
        }

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // Check if year is within valid range (1-18 years old)
        if (year < currentYear - 18 || year > currentYear - 1) {
            showError("Child must be between 1 and 18 years old")
            showYearPicker()
            return
        }

        val parentUid = auth.currentUser?.uid ?: return

        // Generate and store hashed reference number
        val refNumber = SecurityUtils.generateRefNumber()
        val refNumberHash = SecurityUtils.hashRefNumber(refNumber)

        // First, ensure parent document exists
        ensureParentDocumentExists(parentUid) { parentDocRef ->
            // Now create the child
            createChildDocument(name, year, parentUid, refNumberHash, parentDocRef, refNumber)
        }
    }

    private fun ensureParentDocumentExists(parentUid: String, onSuccess: (String) -> Unit) {
        // Check if parent document already exists
        db.collection("parents")
            .whereEqualTo("name", parentUid)
            .get()
            .addOnSuccessListener { parentSnap ->
                if (!parentSnap.isEmpty) {
                    // Parent exists, return its ID
                    onSuccess(parentSnap.documents[0].id)
                } else {
                    // Create new parent document
                    val parentData = hashMapOf(
                        "name" to parentUid,
                        "children" to HashMap<String, String>()
                    )
                    db.collection("parents").add(parentData)
                        .addOnSuccessListener { docRef ->
                            onSuccess(docRef.id)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to create parent: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to check parent: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createChildDocument(name: String, year: Int, parentUid: String, refNumberHash: String, parentDocId: String, refNumber: String) {
        // Check for reference number conflict
        db.collection("children")
            .whereEqualTo("refNumberHash", refNumberHash)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    Toast.makeText(this, "Reference number conflict, please try again", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Create child document
                val child = hashMapOf(
                    "name" to name,
                    "birthYear" to year,
                    "parent" to hashMapOf(
                        "docID" to parentUid,
                        "name" to (auth.currentUser?.email ?: "")
                    ),
                    "devices" to hashMapOf<String, String>(),
                    "refNumberHash" to refNumberHash,
                    "profileImageUri" to (selectedImageUri?.toString() ?: "") // Optional profile image
                )

                db.collection("children").add(child)
                    .addOnSuccessListener { childDocRef ->
                        // Update parent's children HashMap
                        updateParentChildren(parentDocId, childDocRef.id, name, parentUid, refNumber)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to add child: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun updateParentChildren(parentDocId: String, childDocId: String, childName: String, parentUid: String, refNumber: String) {
        // Get the parent document and update its children HashMap
        db.collection("parents").document(parentDocId)
            .get()
            .addOnSuccessListener { parentDoc ->
                if (parentDoc.exists()) {
                    val currentChildren = parentDoc.get("children") as? HashMap<String, String> ?: HashMap()
                    currentChildren[childDocId] = childName

                    // Update the parent document
                    parentDoc.reference.update("children", currentChildren)
                        .addOnSuccessListener {
                            // Success! Child profile created successfully
                            // Note: We don't mark this (parent's) device as child device
                            // The actual child device will link itself using the reference number

                            // Navigate to WaitingForSetupActivity instead of showing dialog
                            val intent = Intent(this, WaitingForSetupActivity::class.java)
                            intent.putExtra("childId", childDocId)
                            intent.putExtra("childName", childName)
                            intent.putExtra("linkingCode", refNumber)
                            intent.putExtra("isFromAddChild", true) // Flag to indicate this is from adding child
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update parent: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Parent document not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get parent: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
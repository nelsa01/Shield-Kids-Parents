package com.shieldtechhub.shieldkids

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityAddChildBinding
import com.shieldtechhub.shieldkids.SecurityUtils
import java.util.Calendar

class AddChildActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddChildBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.ivProfilePhoto.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInputFieldFocusListeners()
        setupCalendarPicker()
        setupImageSelection()
        binding.btnSave.setOnClickListener { saveChild() }
    }

    private fun setupImageSelection() {
        // Make the profile photo clickable
        binding.ivProfilePhoto.setOnClickListener {
            openImagePicker()
        }
        
        // Make the edit icon clickable
        binding.ivEditPhoto.setOnClickListener {
            openImagePicker()
        }
    }
    
    private fun openImagePicker() {
        getContent.launch("image/*")
    }

    private fun setupCalendarPicker() {
        // Make the year field non-editable and show calendar on click
        binding.etYear.isFocusable = false
        binding.etYear.isClickable = true
        
        binding.etYear.setOnClickListener {
            showYearPicker()
        }
        
        // Also make the calendar icon clickable
        binding.ivYearIcon.setOnClickListener {
            showYearPicker()
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

    private fun setupInputFieldFocusListeners() {
        // Name field focus listener
        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.nameFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_focused)
                binding.ivNameIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                binding.nameFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background)
                binding.ivNameIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
                validateNameField()
            }
        }

        // Year field focus listener (for visual feedback only)
        binding.etYear.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.yearFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_focused)
                binding.ivYearIcon.setColorFilter(ContextCompat.getColor(this, R.color.teal_500))
            } else {
                binding.yearFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background)
                binding.ivYearIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
                validateYearField()
            }
        }
        
        // Add text change listeners for real-time validation
        binding.etName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!binding.etName.hasFocus()) {
                    validateNameField()
                }
            }
        })
    }
    
    private fun validateNameField(): Boolean {
        val name = binding.etName.text.toString().trim()
        
        return when {
            name.isEmpty() -> {
                binding.nameFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_error)
                binding.ivNameIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
                false
            }
            name.length < 2 -> {
                binding.nameFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_error)
                binding.ivNameIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
                false
            }
            name.length > 50 -> {
                binding.nameFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_error)
                binding.ivNameIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
                false
            }
            else -> {
                binding.nameFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background)
                binding.ivNameIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
                true
            }
        }
    }
    
    private fun validateYearField(): Boolean {
        val yearText = binding.etYear.text.toString().trim()
        
        return when {
            yearText.isEmpty() -> {
                binding.yearFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_error)
                binding.ivYearIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
                false
            }
            yearText.toIntOrNull() == null -> {
                binding.yearFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_error)
                binding.ivYearIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
                false
            }
            else -> {
                val year = yearText.toInt()
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                
                // Check if year is within valid range (1-18 years old)
                if (year < currentYear - 18 || year > currentYear - 1) {
                    binding.yearFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background_error)
                    binding.ivYearIcon.setColorFilter(ContextCompat.getColor(this, R.color.error_red))
                    false
                } else {
                    binding.yearFieldContainer.background = ContextCompat.getDrawable(this, R.drawable.input_field_background)
                    binding.ivYearIcon.setColorFilter(ContextCompat.getColor(this, R.color.gray_400))
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
                    "devices" to listOf<String>(),
                    "profileImageUri" to (selectedImageUri?.toString() ?: "")
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

                                // Navigate to Children Dashboard
                                val intent = Intent(this, ChildrenDashboardActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                    }
            }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
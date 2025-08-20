package com.shieldtechhub.shieldkids

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.FirebaseApp
import com.shieldtechhub.shieldkids.databinding.BottomSheetAddChildBinding
import java.util.Calendar

class AddChildBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetAddChildBinding? = null
    private val binding get() = _binding!!
    
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var selectedImageUri: Uri? = null
    private val storage: FirebaseStorage by lazy {
        val app = FirebaseApp.getInstance()
        val projectId = app.options.projectId
        return@lazy if (!projectId.isNullOrEmpty()) {
            // Force canonical bucket name regardless of google-services.json storage_bucket domain
            FirebaseStorage.getInstance("gs://$projectId.appspot.com")
        } else {
            FirebaseStorage.getInstance()
        }
    }
    
    // Interface for communicating with parent activity
    interface AddChildListener {
        fun onChildAdded(childId: String, childName: String)
    }
    
    private var listener: AddChildListener? = null
    
    fun setAddChildListener(listener: AddChildListener) {
        this.listener = listener
    }
    
    // Photo picker launcher for selecting child profile image from gallery
    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            Log.d("AddChild", "Selected image URI: $it")
            // Update the avatar image view to show selected photo
            binding.ivChildAvatar.setImageURI(it)
            binding.ivChildAvatar.scaleType = ImageView.ScaleType.CENTER_CROP
            // Remove tint since we're now showing an actual photo
            binding.ivChildAvatar.clearColorFilter()
            
            // Update the text to indicate photo was selected
            binding.tvPhotoLabel.text = "Photo selected"
            binding.tvPhotoLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.teal_500))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddChildBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
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
            requireContext(),
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
                            showError("Failed to create parent: ${e.localizedMessage}")
                        }
                }
            }
            .addOnFailureListener { e ->
                showError("Failed to check parent: ${e.localizedMessage}")
            }
    }

    private fun createChildDocument(name: String, year: Int, parentUid: String, refNumberHash: String, parentDocId: String, refNumber: String) {
        // Check for reference number conflict
        db.collection("children")
            .whereEqualTo("refNumberHash", refNumberHash)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    showError("Reference number conflict, please try again")
                    return@addOnSuccessListener
                }

                // Proceed to upload image if selected, then create doc with download URL
                val proceedCreate: (String) -> Unit = { imageUrl ->
                    Log.d("AddChild", "Proceeding to create child with imageUrl=$imageUrl")
                    val child = hashMapOf(
                        "name" to name,
                        "birthYear" to year,
                        "parent" to hashMapOf(
                            "docID" to parentUid,
                            "name" to (auth.currentUser?.email ?: "")
                        ),
                        "devices" to hashMapOf<String, String>(),
                        "refNumberHash" to refNumberHash,
                        "profileImageUri" to imageUrl
                    )

                    db.collection("children").add(child)
                        .addOnSuccessListener { childDocRef ->
                            Log.d("AddChild", "Child created: ${childDocRef.id}")
                            // Update parent's children HashMap
                            updateParentChildren(parentDocId, childDocRef.id, name, parentUid, refNumber)
                        }
                        .addOnFailureListener { e ->
                            showError("Failed to add child: ${e.localizedMessage}")
                            Log.e("AddChild", "Failed to add child doc", e)
                        }
                }

                val localUri = selectedImageUri
                if (localUri == null) {
                    proceedCreate("")
                } else {
                    // Upload to Firebase Storage under children/{parentUid}/{timestamp}.jpg
                    val fileName = "${System.currentTimeMillis()}_${name.replace(" ", "_")}.jpg"
                    val ref = storage.reference.child("children/$parentUid/$fileName")
                    Log.d("AddChild", "Uploading to Storage: ${ref.path}")

                    val mimeType = try { requireContext().contentResolver.getType(localUri) } catch (_: Exception) { null } ?: "image/jpeg"
                    val metadata = StorageMetadata.Builder().setContentType(mimeType).build()

                    ref.putFile(localUri, metadata)
                        .continueWithTask { task ->
                            if (!task.isSuccessful) {
                                throw task.exception ?: Exception("Upload failed")
                            }
                            ref.downloadUrl
                        }
                        .addOnSuccessListener { uri ->
                            Log.d("AddChild", "Upload success. Download URL: $uri")
                            proceedCreate(uri.toString())
                        }
                        .addOnFailureListener { e ->
                            Log.e("AddChild", "Upload failed with putFile, retrying with putBytes", e)
                            // Fallback: read bytes via ContentResolver and upload
                            try {
                                val resolver = requireContext().contentResolver
                                val bytes = resolver.openInputStream(localUri)?.use { it.readBytes() }
                                if (bytes != null && bytes.isNotEmpty()) {
                                    ref.putBytes(bytes, metadata)
                                        .continueWithTask { t ->
                                            if (!t.isSuccessful) {
                                                throw t.exception ?: Exception("Upload (bytes) failed")
                                            }
                                            ref.downloadUrl
                                        }
                                        .addOnSuccessListener { uri ->
                                            Log.d("AddChild", "Upload (bytes) success. Download URL: $uri")
                                            proceedCreate(uri.toString())
                                        }
                                        .addOnFailureListener { e2 ->
                                            Log.e("AddChild", "Upload (bytes) failed, proceeding without image", e2)
                                            proceedCreate("")
                                        }
                                } else {
                                    Log.e("AddChild", "Could not read image bytes; proceeding without image")
                                    proceedCreate("")
                                }
                            } catch (ex: Exception) {
                                Log.e("AddChild", "Exception reading image for upload", ex)
                                proceedCreate("")
                            }
                        }
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
                            // Notify the parent activity about the new child
                            listener?.onChildAdded(childDocId, childName)
                            
                            // Close the bottom sheet
                            dismiss()
                            
                            // Navigate to WaitingForSetupActivity to show the reference code
                            val intent = Intent(requireContext(), WaitingForSetupActivity::class.java)
                            intent.putExtra("childId", childDocId)
                            intent.putExtra("childName", childName)
                            intent.putExtra("linkingCode", refNumber)
                            intent.putExtra("isFromAddChild", true) // Flag to indicate this is from adding child
                            startActivity(intent)
                        }
                        .addOnFailureListener { e ->
                            showError("Failed to update parent: ${e.localizedMessage}")
                        }
                } else {
                    showError("Parent document not found")
                }
            }
            .addOnFailureListener { e ->
                showError("Failed to get parent: ${e.localizedMessage}")
            }
    }
    
    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
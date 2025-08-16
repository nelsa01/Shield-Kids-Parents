package com.shieldtechhub.shieldkids

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.common.utils.PermissionManager
import com.shieldtechhub.shieldkids.databinding.ActivityParentDashboardBinding

class ParentDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityParentDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var permissionManager: PermissionManager

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure proper system UI visibility - prevent fullscreen overlap
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
        
        binding = ActivityParentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        permissionManager = PermissionManager(this)
        setupClickListeners()
        loadChildren()
        checkCriticalPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Refresh children list when returning from AddChildActivity
        loadChildren()
    }

    private fun loadChildren() {
        val parentUid = auth.currentUser?.uid ?: return
        
        // First try to find children in the parents collection
        db.collection("parents")
            .whereEqualTo("name", parentUid)
            .get()
            .addOnSuccessListener { parentSnap ->
                if (!parentSnap.isEmpty) {
                    val parentDoc = parentSnap.documents[0]
                    val childrenMap = parentDoc.get("children") as? HashMap<String, String> ?: HashMap()
                    
                    if (childrenMap.isNotEmpty()) {
                        // Load child details from children collection
                        val childrenList = mutableListOf<Child>()
                        var loadedCount = 0
                        
                        childrenMap.forEach { (childId, childName) ->
                            db.collection("children").document(childId)
                                .get()
                                .addOnSuccessListener { childDoc ->
                                    if (childDoc.exists()) {
                                        val birthYear = childDoc.getLong("birthYear") ?: 0
                                        val profileImageUri = childDoc.getString("profileImageUri") ?: ""
                                        
                                        childrenList.add(Child(
                                            id = childId,
                                            name = childName,
                                            yearOfBirth = birthYear,
                                            profileImageUri = profileImageUri
                                        ))
                                    } else {
                                        // Fallback to just the name if child doc doesn't exist
                                        childrenList.add(Child(
                                            id = childId,
                                            name = childName,
                                            yearOfBirth = 0,
                                            profileImageUri = ""
                                        ))
                                    }
                                    
                                    loadedCount++
                                    if (loadedCount == childrenMap.size) {
                                        displayChildrenPreview(childrenList)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    // Fallback to just the name if loading fails
                                    childrenList.add(Child(
                                        id = childId,
                                        name = childName,
                                        yearOfBirth = 0,
                                        profileImageUri = ""
                                    ))
                                    
                                    loadedCount++
                                    if (loadedCount == childrenMap.size) {
                                        displayChildrenPreview(childrenList)
                                    }
                                }
                        }
                    } else {
                        displayChildrenPreview(emptyList())
                    }
                } else {
                    // No parent document found, try direct children query as fallback
                    db.collection("children")
                        .whereEqualTo("parent.docID", parentUid)
                        .get()
                        .addOnSuccessListener { childrenSnap ->
                            val children = childrenSnap.documents.mapNotNull { doc ->
                                doc.data?.let { data ->
                                    val parentData = data["parent"] as? HashMap<String, Any>
                                    if (parentData?.get("docID") == parentUid) {
                                        Child(
                                            id = doc.id,
                                            name = data["name"] as? String ?: "",
                                            yearOfBirth = data["birthYear"] as? Long ?: 0,
                                            profileImageUri = data["profileImageUri"] as? String ?: ""
                                        )
                                    } else null
                                }
                            }
                            displayChildrenPreview(children)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error loading children: ${e.message}", Toast.LENGTH_SHORT).show()
                            displayChildrenPreview(emptyList())
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading children: ${e.message}", Toast.LENGTH_SHORT).show()
                displayChildrenPreview(emptyList())
            }
    }

    private fun displayChildrenPreview(children: List<Child>) {
        // Find or create the children preview container
        var childrenContainer = binding.root.findViewById<LinearLayout>(R.id.childrenPreviewContainer)
        
        if (childrenContainer == null) {
            // Create the container if it doesn't exist
            childrenContainer = LinearLayout(this).apply {
                id = R.id.childrenPreviewContainer
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                    bottomMargin = 16
                }
            }
            
            // Insert the container after the Add Child button
            val addChildButton = binding.btnAddChild
            val parent = addChildButton.parent as LinearLayout
            val index = parent.indexOfChild(addChildButton) + 1
            parent.addView(childrenContainer, index)
        }

        // Clear existing children
        childrenContainer.removeAllViews()

        if (children.isEmpty()) {
            // Show placeholder message
            val placeholderText = TextView(this).apply {
                text = "No children added yet. Tap 'Add Child' to get started!"
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@ParentDashboardActivity, R.color.gray_500))
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            childrenContainer.addView(placeholderText)
        } else {
            // Show children preview (first 3 children)
            val childrenToShow = children.take(3)
            childrenToShow.forEach { child ->
                val childView = createChildPreviewView(child)
                childrenContainer.addView(childView)
            }
            
            // Add "View All" button if there are more than 3 children
            if (children.size > 3) {
                val viewAllButton = TextView(this).apply {
                    text = "+${children.size - 3} more"
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@ParentDashboardActivity, R.color.teal_500))
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = 16
                        topMargin = 20
                    }
                    setOnClickListener {
                        startActivity(Intent(this@ParentDashboardActivity, ChildrenDashboardActivity::class.java))
                    }
                }
                childrenContainer.addView(viewAllButton)
            }
        }
    }

    private fun createChildPreviewView(child: Child): View {
        // Create a container for each child
        val childContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
                marginEnd = 16
            }
        }

        // Create avatar
        val avatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(60, 60)
            
            // Set image based on whether custom profile image exists
            if (child.profileImageUri.isNotEmpty()) {
                try {
                    val uri = Uri.parse(child.profileImageUri)
                    setImageURI(uri)
                } catch (e: Exception) {
                    // Fallback to default image if URI is invalid
                    setImageResource(R.drawable.kidprofile)
                }
            } else {
                setImageResource(R.drawable.kidprofile)
            }
            
            background = ContextCompat.getDrawable(this@ParentDashboardActivity, R.drawable.circle_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Child: ${child.name}"
            
            // Add click listener to view child details
            setOnClickListener {
                startActivity(Intent(this@ParentDashboardActivity, ChildrenDashboardActivity::class.java))
            }
        }

        // Create name label
        val nameLabel = TextView(this).apply {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = params
            text = child.name
            textSize = 10f
            setTextColor(ContextCompat.getColor(this@ParentDashboardActivity, R.color.black))
            gravity = android.view.Gravity.CENTER
        }

        // Add views to container
        childContainer.addView(avatar)
        childContainer.addView(nameLabel)
        
        return childContainer
    }

    private fun setupClickListeners() {
        // Add Child Button
        binding.btnAddChild.setOnClickListener {
            startActivity(Intent(this, AddChildActivity::class.java))
        }

        // Requests Card
        binding.cardRequests.setOnClickListener {
            Toast.makeText(this, "Requests feature coming soon", Toast.LENGTH_SHORT).show()
        }

        // Details Link - Open App List
        binding.tvDetails.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        // Bottom Navigation
        binding.navHome.setOnClickListener {
            // Already on home, no action needed
        }

        binding.navLocation.setOnClickListener {
            Toast.makeText(this, "Location feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.navLightning.setOnClickListener {
            Toast.makeText(this, "Lightning feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun checkCriticalPermissions() {
        if (!permissionManager.checkCriticalPermissionsOnAppStart()) {
            val missingCount = permissionManager.getMissingEssentialPermissions().size
            if (missingCount > 0) {
                showPermissionPrompt(missingCount)
            }
        }
    }
    
    private fun showPermissionPrompt(missingCount: Int) {
        // Show a subtle prompt after a delay to not overwhelm the user immediately
        binding.root.postDelayed({
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Setup Required")
                .setMessage("Shield Kids needs $missingCount essential permissions to protect your children effectively. Would you like to set them up now?")
                .setPositiveButton("Setup Permissions") { _, _ ->
                    startActivity(Intent(this, PermissionRequestActivity::class.java))
                }
                .setNegativeButton("Later", null)
                .show()
        }, 3000) // 3 second delay
    }
    
    data class Child(
        val id: String,
        val name: String,
        val yearOfBirth: Long,
        val profileImageUri: String = ""
    )
}
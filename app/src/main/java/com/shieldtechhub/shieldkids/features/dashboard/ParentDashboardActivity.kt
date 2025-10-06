package com.shieldtechhub.shieldkids.features.dashboard

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
import com.shieldtechhub.shieldkids.common.utils.FirestoreSyncManager
import com.shieldtechhub.shieldkids.common.utils.ImageLoader
import com.google.firebase.firestore.ListenerRegistration
import com.shieldtechhub.shieldkids.databinding.ActivityParentDashboardBinding
import com.shieldtechhub.shieldkids.features.child_management.ui.ChildDetailActivity
import com.shieldtechhub.shieldkids.features.child_management.ui.AddChildBottomSheet
import com.shieldtechhub.shieldkids.features.app_management.ui.AppListActivity
import com.shieldtechhub.shieldkids.features.settings.SettingsActivity
import com.shieldtechhub.shieldkids.features.device_setup.ui.PermissionRequestActivity
import com.shieldtechhub.shieldkids.R

class ParentDashboardActivity : AppCompatActivity(), AddChildBottomSheet.AddChildListener {
    private lateinit var binding: ActivityParentDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var permissionManager: PermissionManager
    private var childrenListener: ListenerRegistration? = null

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
        startChildrenListener()
        checkCriticalPermissions()
    }

    override fun onResume() {
        super.onResume()
        // no-op; real-time listener keeps UI in sync
    }

    private fun startChildrenListener() {
        val parentUid = auth.currentUser?.uid ?: return
        showLoadingPlaceholder()
        childrenListener?.remove()
        childrenListener = FirestoreSyncManager.listenParentChildren(parentUid) { children, _ ->
            // Map to local Child model and display
            val mapped = children.map {
                Child(
                    id = it.id,
                    name = it.name,
                    yearOfBirth = it.yearOfBirth,
                    profileImageUri = it.profileImageUri
                )
            }
            displayChildrenPreview(mapped)
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
                        showAllChildrenView(children)
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
            layoutParams = LinearLayout.LayoutParams(120, 120)
            
            // Set image based on whether custom profile image exists
            ImageLoader.loadInto(this@ParentDashboardActivity, this, child.profileImageUri, R.drawable.kidprofile)
            
            background = ContextCompat.getDrawable(this@ParentDashboardActivity, R.drawable.circle_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Child: ${child.name}"
            
            // Add click listener to view child details
            setOnClickListener {
                val intent = Intent(this@ParentDashboardActivity, ChildDetailActivity::class.java)
                intent.putExtra("childId", child.id)
                intent.putExtra("childName", child.name)
                intent.putExtra("profileImageUri", child.profileImageUri)
                intent.putExtra("birthYear", child.yearOfBirth)
                startActivity(intent)
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
    
    private fun showLoadingPlaceholder() {
        // Use the same container logic as displayChildrenPreview
        var childrenContainer = binding.root.findViewById<LinearLayout>(R.id.childrenPreviewContainer)
        
        if (childrenContainer == null) {
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

        // Clear existing children and show loading
        childrenContainer.removeAllViews()
        
        val loadingText = TextView(this).apply {
            text = "Loading children..."
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@ParentDashboardActivity, R.color.gray_500))
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        childrenContainer.addView(loadingText)
    }

    private fun showAllChildrenView(children: List<Child>) {
        // Create a dialog or expand the view to show all children
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("All Children (${children.size})")
            .create()
        
        val scrollView = android.widget.ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        
        children.forEach { child ->
            val childItem = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 16
                }
                setPadding(16, 16, 16, 16)
                background = ContextCompat.getDrawable(this@ParentDashboardActivity, R.drawable.card_background)
                isClickable = true
                isFocusable = true
                
                setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(this@ParentDashboardActivity, ChildDetailActivity::class.java)
                    intent.putExtra("childId", child.id)
                    intent.putExtra("childName", child.name)
                    intent.putExtra("profileImageUri", child.profileImageUri)
                    intent.putExtra("birthYear", child.yearOfBirth)
                    startActivity(intent)
                }
            }
            
            // Avatar
            val avatar = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 16
                }
                if (child.profileImageUri.isNotEmpty()) {
                    try {
                        val uri = Uri.parse(child.profileImageUri)
                        setImageURI(uri)
                    } catch (e: Exception) {
                        setImageResource(R.drawable.kidprofile)
                    }
                } else {
                    setImageResource(R.drawable.kidprofile)
                }
                background = ContextCompat.getDrawable(this@ParentDashboardActivity, R.drawable.circle_background)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            
            // Child info
            val infoContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { weight = 1f }
            }
            
            val nameText = TextView(this).apply {
                text = child.name
                textSize = 16f
                setTextColor(ContextCompat.getColor(this@ParentDashboardActivity, R.color.gray_900))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            val ageText = TextView(this).apply {
                if (child.yearOfBirth > 0) {
                    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val age = currentYear - child.yearOfBirth.toInt()
                    text = "Age: $age years old"
                } else {
                    text = "Age: Not specified"
                }
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@ParentDashboardActivity, R.color.gray_600))
            }
            
            infoContainer.addView(nameText)
            infoContainer.addView(ageText)
            
            // Arrow icon
            val arrow = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(24, 24)
                setImageResource(R.drawable.ic_arrow_right)
                setColorFilter(ContextCompat.getColor(this@ParentDashboardActivity, R.color.gray_400))
            }
            
            childItem.addView(avatar)
            childItem.addView(infoContainer)
            childItem.addView(arrow)
            container.addView(childItem)
        }
        
        scrollView.addView(container)
        dialog.setView(scrollView)
        dialog.show()
    }

    private fun setupClickListeners() {
        // Add Child Button
        binding.btnAddChild.setOnClickListener {
            showAddChildBottomSheet()
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
    
    private fun showAddChildBottomSheet() {
        val bottomSheet = AddChildBottomSheet()
        bottomSheet.setAddChildListener(this)
        bottomSheet.show(supportFragmentManager, "AddChildBottomSheet")
    }
    
    override fun onChildAdded(childId: String, childName: String) {
        // Listener will auto-update; no manual reload needed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        childrenListener?.remove()
        childrenListener = null
    }
    
    data class Child(
        val id: String,
        val name: String,
        val yearOfBirth: Long,
        val profileImageUri: String = ""
    )
}
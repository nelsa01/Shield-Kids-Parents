package com.shieldtechhub.shieldkids

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.shieldtechhub.shieldkids.databinding.ActivityChildrenDashboardBinding

class ChildrenDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildrenDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildrenDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupClickListeners()
        loadChildren()
    }

    override fun onResume() {
        super.onResume()
        // Refresh children list when returning from AddChildActivity
        loadChildren()
    }

    private fun loadChildren() {
        val parentUid = auth.currentUser?.uid ?: return
        
        db.collection("children")
            .whereEqualTo("parentUid", parentUid)
            .get()
            .addOnSuccessListener { documents ->
                displayChildren(documents.documents.mapNotNull { doc ->
                    doc.data?.let { data ->
                        Child(
                            id = doc.id,
                            name = data["name"] as? String ?: "",
                            yearOfBirth = data["yearOfBirth"] as? Long ?: 0,
                            profileImageUri = data["profileImageUri"] as? String ?: ""
                        )
                    }
                })
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading children: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayChildren(children: List<Child>) {
        // Clear existing children views (except the Add Child button)
        val childrenContainer = binding.childrenContainer
        val childCount = childrenContainer.childCount
        
        // Remove all child views (keep the Add Child button which is the first child)
        for (i in childCount - 1 downTo 1) {
            childrenContainer.removeViewAt(i)
        }

        // Add child profile pictures
        children.forEach { child ->
            val childView = createChildView(child)
            childrenContainer.addView(childView)
        }
    }

    private fun createChildView(child: Child): View {
        // Create a circular ImageView for the child
        val imageView = ImageView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(80, 80).apply {
                marginEnd = 16
            }
            
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
                setImageResource(R.drawable.kidprofile) // Use kidprofile.png as default profile
            }
            
            background = ContextCompat.getDrawable(this@ChildrenDashboardActivity, R.drawable.circle_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Child: ${child.name}"
            
            // Add click listener to view child details
            setOnClickListener {
                Toast.makeText(this@ChildrenDashboardActivity, "Viewing ${child.name}", Toast.LENGTH_SHORT).show()
            }
        }
        
        return imageView
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

        // Details Link
        binding.tvDetails.setOnClickListener {
            Toast.makeText(this, "App usage details coming soon", Toast.LENGTH_SHORT).show()
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

        binding.navSettings.setOnClickListener {
            Toast.makeText(this, "Settings feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }
    
    data class Child(
        val id: String,
        val name: String,
        val yearOfBirth: Long,
        val profileImageUri: String = ""
    )
} 
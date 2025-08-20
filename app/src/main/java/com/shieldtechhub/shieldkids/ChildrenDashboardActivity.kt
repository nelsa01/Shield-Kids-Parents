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
import com.google.firebase.firestore.ListenerRegistration
import com.shieldtechhub.shieldkids.common.utils.FirestoreSyncManager
import com.shieldtechhub.shieldkids.common.utils.ImageLoader
import com.shieldtechhub.shieldkids.databinding.ActivityChildrenDashboardBinding

class ChildrenDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildrenDashboardBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var childrenListener: ListenerRegistration? = null

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
        startChildrenListener()
    }

    override fun onResume() {
        super.onResume()
        // no-op; real-time listener keeps UI in sync
    }

    private fun startChildrenListener() {
        val parentUid = auth.currentUser?.uid ?: return
        childrenListener?.remove()
        childrenListener = FirestoreSyncManager.listenParentChildren(parentUid) { children, _ ->
            val mapped = children.map {
                Child(
                    id = it.id,
                    name = it.name,
                    yearOfBirth = it.yearOfBirth,
                    profileImageUri = it.profileImageUri
                )
            }
            displayChildren(mapped)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        childrenListener?.remove()
        childrenListener = null
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
            ImageLoader.loadInto(this@ChildrenDashboardActivity, this, child.profileImageUri, R.drawable.kidprofile)
            
            background = ContextCompat.getDrawable(this@ChildrenDashboardActivity, R.drawable.circle_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Child: ${child.name}"
            
            // Open child detail screen
            setOnClickListener {
                val intent = Intent(this@ChildrenDashboardActivity, ChildDetailActivity::class.java)
                intent.putExtra("childId", child.id)
                intent.putExtra("childName", child.name)
                intent.putExtra("profileImageUri", child.profileImageUri)
                startActivity(intent)
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
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    data class Child(
        val id: String,
        val name: String,
        val yearOfBirth: Long,
        val profileImageUri: String = ""
    )
} 
package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.shieldtechhub.shieldkids.databinding.ActivityRoleSelectionBinding

class RoleSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnParent.setOnClickListener {
            startActivity(Intent(this, ParentLoginActivity::class.java))
        }

        binding.btnChild.setOnClickListener {
            startActivity(Intent(this, ChildLoginActivity::class.java))
        }
    }
}
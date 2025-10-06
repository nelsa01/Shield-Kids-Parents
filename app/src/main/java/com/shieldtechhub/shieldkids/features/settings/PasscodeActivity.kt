package com.shieldtechhub.shieldkids.features.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.shieldtechhub.shieldkids.common.utils.PasscodeManager
import com.shieldtechhub.shieldkids.databinding.ActivityPasscodeBinding
import com.shieldtechhub.shieldkids.features.authentication.ParentLoginActivity
import com.shieldtechhub.shieldkids.features.dashboard.ParentDashboardActivity

class PasscodeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPasscodeBinding
    private val digits = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasscodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupKeypad()
        binding.tvForgot.setOnClickListener { promptRelogin() }
    }

    private fun promptRelogin() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Forgot Passcode")
            .setMessage("You need to sign in again to reset your app passcode.")
            .setPositiveButton("Re-login") { _, _ ->
                try {
                    // Clear passcode and sign out
                    PasscodeManager.clearPasscode(this)
                    FirebaseAuth.getInstance().signOut()
                } catch (_: Exception) { }
                val intent = android.content.Intent(this, ParentLoginActivity::class.java)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupKeypad() {
        val buttons = listOf(
            binding.key0, binding.key1, binding.key2, binding.key3, binding.key4,
            binding.key5, binding.key6, binding.key7, binding.key8, binding.key9
        )
        buttons.forEach { btn ->
            btn.setOnClickListener { onDigit((it as TextView).text.toString()) }
        }
        binding.keyDel.setOnClickListener { onDelete() }
    }

    private fun onDigit(d: String) {
        if (digits.length >= 4) return
        digits.append(d)
        updateDots()
        if (digits.length == 4) verify()
    }

    private fun onDelete() {
        if (digits.isNotEmpty()) {
            digits.deleteCharAt(digits.length - 1)
            updateDots()
        }
    }

    private fun updateDots() {
        val dotViews = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        dotViews.forEachIndexed { index, view ->
            view.isSelected = index < digits.length
        }
    }

    private fun verify() {
        val code = digits.toString()
        if (PasscodeManager.verifyPasscode(this, code)) {
            // Navigate to dashboard and clear back stack
            val intent = android.content.Intent(this, ParentDashboardActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            Toast.makeText(this, "Incorrect pass code", Toast.LENGTH_SHORT).show()
            digits.clear()
            updateDots()
        }
    }
}



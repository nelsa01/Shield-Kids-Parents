package com.shieldtechhub.shieldkids

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.shieldtechhub.shieldkids.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: OnboardingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        viewPager = binding.viewPager
        adapter = OnboardingAdapter()
        viewPager.adapter = adapter

        // Update indicators when page changes
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateButtons(position)
            }
        })
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener {
            goToDashboard()
        }

        binding.btnNext.setOnClickListener {
            if (viewPager.currentItem == 2) { // Last slide
                goToDashboard()
            } else {
                viewPager.currentItem += 1
            }
        }
    }

    private fun updateIndicators(position: Int) {
        // Update indicator backgrounds based on current position
        binding.indicator1.background = getDrawable(
            if (position == 0) R.drawable.indicator_selected else R.drawable.indicator_unselected
        )
        binding.indicator2.background = getDrawable(
            if (position == 1) R.drawable.indicator_selected else R.drawable.indicator_unselected
        )
        binding.indicator3.background = getDrawable(
            if (position == 2) R.drawable.indicator_selected else R.drawable.indicator_unselected
        )
    }

    private fun updateButtons(position: Int) {
        if (position == 2) { // Last slide (index 2 for 3 slides)
            binding.btnNext.text = "Finish"
        } else {
            binding.btnNext.text = "Next"
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, ParentDashboardActivity::class.java))
        finish()
    }
} 
package com.shieldtechhub.shieldkids.features.onboarding.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shieldtechhub.shieldkids.R

class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    private val slides = listOf(
        OnboardingSlide(
            R.drawable.slide1,
            "Customize Your Child's Online Experience",
            "Our Content Filtering Options Put You in Control What your Kids access."
        ),
        OnboardingSlide(
            R.drawable.slide2,
            "Safe and Secure Browsing",
            "Safe Search Filters Out Explicit Content from Search Results."
        ),
        OnboardingSlide(
            R.drawable.slide3,
            "Know Where Your Child Is",
            "Geolocation Functionality Keeps you updated with your Child Location in Real-Time."
        )
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_slide, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(slides[position])
    }

    override fun getItemCount(): Int = slides.size

    class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.ivSlideImage)
        private val titleTextView: TextView = itemView.findViewById(R.id.tvSlideTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tvSlideDescription)

        fun bind(slide: OnboardingSlide) {
            imageView.setImageResource(slide.imageResId)
            titleTextView.text = slide.title
            descriptionTextView.text = slide.description
        }
    }

    data class OnboardingSlide(
        val imageResId: Int,
        val title: String,
        val description: String
    )
} 

package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

// Home tab
class HomeFragment : TabFragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // buy ticket buttons
        findViewById<View>(R.id.greenLineButton).setOnClickListener {
            openBooking("LRT-1")
        }

        findViewById<View>(R.id.purpleLineButton).setOnClickListener {
            openBooking("LRT-2")
        }

        // avatar -> account tab
        findViewById<View>(R.id.avatarCircle).setOnClickListener {
            showTab(NavTab.ACCOUNT)
        }

        findViewById<TextView>(R.id.showAllHistory).setOnClickListener {
            startActivity(Intent(requireContext(), TripHistoryActivity::class.java))
        }
    }

    override fun onTabShown() {
        showWelcomeName()
        loadProfilePicture()
        loadTripHistory()
    }

    // "WELCOME <NAME>"
    private fun showWelcomeName() {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        val name = user?.userMetadata?.get("full_name")?.jsonPrimitive?.content?.trim()
        val label = findViewById<TextView>(R.id.welcomeLabel)
        label.text = if (name.isNullOrEmpty()) "WELCOME" else "WELCOME ${name.uppercase()}"
        // don't let the user scroll it by hand
        (label.parent as View).setOnTouchListener { _, _ -> true }
        // wait for layout, then slide if too long
        label.post { slideIfTooLong(label) }
    }

    private var welcomeSlide: android.animation.ValueAnimator? = null

    // long names slide left and back, like a train sign
    private fun slideIfTooLong(label: TextView) {
        welcomeSlide?.cancel()
        label.translationX = 0f

        val box = label.parent as View
        val overflow = label.width - box.width
        if (overflow <= 0) return // fits

        val pauseMs = 1200L
        val moveMs = (overflow * 12L).coerceIn(1200L, 6000L) // longer name = slower
        val total = (pauseMs + moveMs) * 2

        // pause, slide left, pause, slide back
        fun at(ms: Long) = ms.toFloat() / total
        val x = android.animation.PropertyValuesHolder.ofKeyframe(
            View.TRANSLATION_X,
            android.animation.Keyframe.ofFloat(0f, 0f),
            android.animation.Keyframe.ofFloat(at(pauseMs), 0f),
            android.animation.Keyframe.ofFloat(at(pauseMs + moveMs), -overflow.toFloat()),
            android.animation.Keyframe.ofFloat(at(pauseMs * 2 + moveMs), -overflow.toFloat()),
            android.animation.Keyframe.ofFloat(1f, 0f)
        )
        welcomeSlide = android.animation.ObjectAnimator.ofPropertyValuesHolder(label, x).apply {
            duration = total
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
    }

    override fun onTabHidden() {
        welcomeSlide?.cancel()
    }

    // profile pic in the top right
    private fun loadProfilePicture() {
        val avatar = findViewById<ImageView>(R.id.avatarCircle)

        // show cached one first, then refresh
        ProfileAvatar.cached?.let { avatar.showProfilePicture(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            ProfileAvatar.load()?.let { avatar.showProfilePicture(it) }
        }
    }

    private fun loadTripHistory() {
        val totalTripsValue =
            findViewById<TextView>(R.id.totalTripsValue)

        val frequentRouteValue =
            findViewById<TextView>(R.id.frequentRouteValue)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val stats = TripHistoryRepository().getMonthlyStats()

                totalTripsValue.text = stats.totalTrips.toString()
                frequentRouteValue.text = stats.frequentRoute

            } catch (e: Exception) {
                totalTripsValue.text = "--"
                frequentRouteValue.text = "Unable to load"
            }
        }
    }

    private fun openBooking(line: String) {
        val intent = Intent(requireContext(), BookingActivity::class.java)
        intent.putExtra(BookingActivity.EXTRA_LINE, line)
        startActivity(intent)
    }
}

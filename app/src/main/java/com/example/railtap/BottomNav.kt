package com.example.railtap

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat

/**
 * Connects the 4 bottom navigation buttons (Home, Tickets, Lines, Account).
 * Call it in onCreate after setContentView, and pass the screen you're on, e.g.
 *   setupBottomNav(this, NavTab.HOME)
 */
enum class NavTab { HOME, TICKETS, LINES, ACCOUNT }

fun setupBottomNav(activity: Activity, current: NavTab) {
    highlightTab(activity, current)
    backGoesHome(activity, current)

    activity.findViewById<View>(R.id.navHome).setOnClickListener {
        openTab(activity, current, NavTab.HOME, DashboardActivity::class.java)
    }
    activity.findViewById<View>(R.id.navTickets).setOnClickListener {
        openTab(activity, current, NavTab.TICKETS, TicketsActivity::class.java)
    }
    activity.findViewById<View>(R.id.navLines).setOnClickListener {
        openTab(activity, current, NavTab.LINES, LinesActivity::class.java)
    }
    activity.findViewById<View>(R.id.navAccount).setOnClickListener {
        openTab(activity, current, NavTab.ACCOUNT, AccountActivity::class.java)
    }
}

/** Colors the current tab purple (bold label, filled icon) and the others gray. */
private fun highlightTab(activity: Activity, current: NavTab) {
    val active = ContextCompat.getColor(activity, R.color.brand)
    val inactive = ContextCompat.getColor(activity, R.color.bottom_nav_inactive)

    // tab -> (icon view, label view, active icon, inactive icon)
    val tabs = listOf(
        Triple(NavTab.HOME, R.id.navHomeIcon to R.id.navHomeLabel, R.drawable.ic_home_active to R.drawable.ic_home_unactive),
        Triple(NavTab.TICKETS, R.id.navTicketsIcon to R.id.navTicketsLabel, R.drawable.ic_tickets_active to R.drawable.ic_tickets_unactive),
        Triple(NavTab.LINES, R.id.navLinesIcon to R.id.navLinesLabel, R.drawable.ic_lines to R.drawable.ic_lines_unactive),
        Triple(NavTab.ACCOUNT, R.id.navAccountIcon to R.id.navAccountLabel, R.drawable.ic_person to R.drawable.ic_person)
    )
    for ((tab, views, icons) in tabs) {
        val icon = activity.findViewById<ImageView>(views.first) ?: continue
        val label = activity.findViewById<TextView>(views.second) ?: continue
        val isActive = tab == current
        val color = if (isActive) active else inactive

        icon.setImageResource(if (isActive) icons.first else icons.second)
        icon.imageTintList = ColorStateList.valueOf(color)
        label.setTextColor(color)
        label.setTypeface(label.typeface, if (isActive) Typeface.BOLD else Typeface.NORMAL)
    }
}

/**
 * Only one tab screen is kept open at a time, so on Tickets / Account the
 * phone's back button goes to Home (Home's back button closes the app).
 */
private fun backGoesHome(activity: Activity, current: NavTab) {
    if (current == NavTab.HOME || activity !is ComponentActivity) return
    activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            openTab(activity, current, NavTab.HOME, DashboardActivity::class.java)
        }
    })
}

// Time of the last tab switch, used to ignore very fast repeated taps
private var lastTabSwitchAt = 0L
private const val TAB_SWITCH_COOLDOWN_MS = 300L

private fun openTab(activity: Activity, current: NavTab, target: NavTab, screen: Class<*>) {
    // Already on this exact screen: do nothing. (Booking highlights Home but is a
    // different screen, so tapping Home there still goes back to the Dashboard.)
    if (activity.javaClass == screen) return

    // Ignore taps while the previous switch is still animating
    val now = SystemClock.elapsedRealtime()
    if (now - lastTabSwitchAt < TAB_SWITCH_COOLDOWN_MS) return
    lastTabSwitchAt = now

    // Open a fresh copy of the tab that fades in (see ScreenFade.kt).
    // We don't bring an already-open tab back to the front (REORDER_TO_FRONT),
    // because Android ignores "no animation" in that case and always slides.
    activity.startWithFade(Intent(activity, screen))

    // Close the current screen and everything behind it, so only the new tab is open.
    activity.disableCloseAnimation()
    activity.finishAffinity()
    @Suppress("DEPRECATION")
    activity.overridePendingTransition(0, 0)
}

private fun comingSoon(activity: Activity, name: String) {
    Toast.makeText(activity, "$name is coming soon", Toast.LENGTH_SHORT).show()
}

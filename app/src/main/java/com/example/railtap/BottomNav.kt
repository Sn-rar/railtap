package com.example.railtap

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Toast

/**
 * Connects the 4 bottom navigation buttons (Home, Tickets, Lines, Account).
 * Call it in onCreate after setContentView, and pass the screen you're on, e.g.
 *   setupBottomNav(this, NavTab.HOME)
 */
enum class NavTab { HOME, TICKETS, LINES, ACCOUNT }

fun setupBottomNav(activity: Activity, current: NavTab) {
    activity.findViewById<View>(R.id.navHome).setOnClickListener {
        openTab(activity, current, NavTab.HOME, DashboardActivity::class.java)
    }
    activity.findViewById<View>(R.id.navTickets).setOnClickListener {
        openTab(activity, current, NavTab.TICKETS, TicketsActivity::class.java)
    }
    // No screens for these yet, so just show a message for now
    activity.findViewById<View>(R.id.navLines).setOnClickListener {
        if (current != NavTab.LINES) comingSoon(activity, "Lines")
    }
    activity.findViewById<View>(R.id.navAccount).setOnClickListener {
        if (current != NavTab.ACCOUNT) comingSoon(activity, "Account")
    }
}

private fun openTab(activity: Activity, current: NavTab, target: NavTab, screen: Class<*>) {
    if (current == target) return // already on this screen, do nothing

    val intent = Intent(activity, screen)
    // Reuse the screen if it's already open instead of stacking copies
    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    activity.startActivity(intent)
    // No slide animation, so it feels like switching tabs
    @Suppress("DEPRECATION")
    activity.overridePendingTransition(0, 0)
}

private fun comingSoon(activity: Activity, name: String) {
    Toast.makeText(activity, "$name is coming soon", Toast.LENGTH_SHORT).show()
}

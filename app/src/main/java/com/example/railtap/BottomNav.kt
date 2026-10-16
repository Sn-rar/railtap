package com.example.railtap

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat

// Bottom nav bar (Home, Tickets, Lines, Account)
enum class NavTab { HOME, TICKETS, LINES, ACCOUNT }

// hook up the 4 tab buttons
fun setupTabBar(activity: Activity, onSelect: (NavTab) -> Unit) {
    activity.findViewById<View>(R.id.navHome).setOnClickListener { onSelect(NavTab.HOME) }
    activity.findViewById<View>(R.id.navTickets).setOnClickListener { onSelect(NavTab.TICKETS) }
    activity.findViewById<View>(R.id.navLines).setOnClickListener { onSelect(NavTab.LINES) }
    activity.findViewById<View>(R.id.navAccount).setOnClickListener { onSelect(NavTab.ACCOUNT) }
}

// for screens outside the main one (like Booking)
fun setupBottomNav(activity: Activity, current: NavTab) {
    highlightTab(activity, current)
    setupTabBar(activity) { tab -> DashboardActivity.open(activity, tab) }
}

// active tab = purple + bold, others gray
fun highlightTab(activity: Activity, current: NavTab) {
    val active = ContextCompat.getColor(activity, R.color.brand)
    val inactive = ContextCompat.getColor(activity, R.color.bottom_nav_inactive)

    // tab, (icon, label), (active icon, inactive icon)
    val tabs = listOf(
        Triple(NavTab.HOME, R.id.navHomeIcon to R.id.navHomeLabel, R.drawable.ic_home_active to R.drawable.ic_home_unactive),
        Triple(NavTab.TICKETS, R.id.navTicketsIcon to R.id.navTicketsLabel, R.drawable.ic_tickets_active to R.drawable.ic_tickets_unactive),
        Triple(NavTab.LINES, R.id.navLinesIcon to R.id.navLinesLabel, R.drawable.ic_lines_active to R.drawable.ic_lines_unactive),
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
        label.typeface = Typeface.create(label.typeface, if (isActive) Typeface.BOLD else Typeface.NORMAL)
    }
}

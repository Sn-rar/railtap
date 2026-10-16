package com.example.railtap

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/**
 * The main screen after logging in. It holds the bottom bar and the 4 tabs
 * (HomeFragment, TicketsFragment, LinesFragment, AccountFragment).
 *
 * Switching tabs only shows/hides fragments: instant, no flicker, and each tab
 * keeps its scroll position and loaded data.
 * Layout: res/layout/activity_main.xml
 */
class DashboardActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TAB = "extra_tab"

        /** Open the main screen on a specific tab (e.g. from the Receipt or Booking). */
        fun open(from: Activity, tab: NavTab = NavTab.HOME) {
            from.startActivity(Intent(from, DashboardActivity::class.java).apply {
                putExtra(EXTRA_TAB, tab.name)
                // Reuse the existing main screen and close anything opened on top of it
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
            @Suppress("DEPRECATION")
            from.overridePendingTransition(0, 0)
        }
    }

    private var currentTab = NavTab.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarPadding() // status bar on top, bottom bar stretches behind the gesture bar

        setupTabBar(this) { tab -> showTab(tab) }

        // Phone back button: from another tab go to Home; on Home, close the app as usual
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != NavTab.HOME) {
                    showTab(NavTab.HOME)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        val startTab = savedInstanceState?.getString(EXTRA_TAB)?.let { NavTab.valueOf(it) }
            ?: tabFrom(intent)
        showTab(startTab)
    }

    // Opened again (e.g. "Show Turnstile Passes" on the receipt) -> switch to the requested tab
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        showTab(tabFrom(intent))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_TAB, currentTab.name)
    }

    private fun tabFrom(intent: Intent?): NavTab =
        intent?.getStringExtra(EXTRA_TAB)?.let { runCatching { NavTab.valueOf(it) }.getOrNull() }
            ?: NavTab.HOME

    /** Shows one tab and hides the others. Each tab is created the first time it's opened. */
    fun showTab(tab: NavTab) {
        currentTab = tab
        highlightTab(this, tab)

        val fm = supportFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)
        for (t in NavTab.values()) {
            val tag = t.name
            val existing = fm.findFragmentByTag(tag)
            if (t == tab) {
                if (existing == null) transaction.add(R.id.tabContainer, newTab(t), tag)
                else transaction.show(existing)
            } else if (existing != null) {
                transaction.hide(existing)
            }
        }
        transaction.commitNow()
    }

    private fun newTab(tab: NavTab): Fragment = when (tab) {
        NavTab.HOME -> HomeFragment()
        NavTab.TICKETS -> TicketsFragment()
        NavTab.LINES -> LinesFragment()
        NavTab.ACCOUNT -> AccountFragment()
    }
}

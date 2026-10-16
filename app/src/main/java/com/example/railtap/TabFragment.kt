package com.example.railtap

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

/**
 * Base for the 4 bottom-bar tabs (Home, Tickets, Lines, Account).
 * All tabs live inside DashboardActivity; switching tabs just shows/hides them,
 * so the bottom bar never redraws (no flicker) and each tab keeps its state.
 */
abstract class TabFragment(@LayoutRes layout: Int) : Fragment(layout) {

    /** Same as Activity.findViewById, so the old screen code works unchanged. */
    protected fun <T : View> findViewById(id: Int): T = requireView().findViewById(id)

    protected val contentResolver: ContentResolver get() = requireContext().contentResolver
    protected val packageManager: PackageManager get() = requireContext().packageManager
    protected val packageName: String get() = requireContext().packageName

    /** Called every time this tab becomes visible (like onResume for a screen). */
    open fun onTabShown() {}

    /** Called when this tab is hidden or the app goes to the background. */
    open fun onTabHidden() {}

    override fun onResume() {
        super.onResume()
        if (!isHidden) onTabShown()
    }

    override fun onPause() {
        super.onPause()
        if (!isHidden) onTabHidden()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (isResumed) { if (hidden) onTabHidden() else onTabShown() }
    }

    /** Switch to another tab (e.g. Home's avatar opens Account). */
    protected fun showTab(tab: NavTab) {
        (activity as? DashboardActivity)?.showTab(tab)
    }
}

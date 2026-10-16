package com.example.railtap

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

// base class for the 4 tabs
abstract class TabFragment(@LayoutRes layout: Int) : Fragment(layout) {

    protected fun <T : View> findViewById(id: Int): T = requireView().findViewById(id)

    protected val contentResolver: ContentResolver get() = requireContext().contentResolver
    protected val packageManager: PackageManager get() = requireContext().packageManager
    protected val packageName: String get() = requireContext().packageName

    // when the tab shows up
    open fun onTabShown() {}

    // when the tab is hidden
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

    protected fun showTab(tab: NavTab) {
        (activity as? DashboardActivity)?.showTab(tab)
    }
}

package com.example.railtap

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

// keeps content from going under the status/nav bars (edge-to-edge on Android 15+)
fun Activity.applySystemBarPadding() {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // dark status bar icons
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }

    val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
    val startTop = root.paddingTop
    val startBottom = root.paddingBottom

    // bottom nav goes behind the gesture bar so it looks the same everywhere
    val bottomNav = findViewById<View>(R.id.bottomNav)?.takeIf { it.parent === root }
    val navStartBottom = bottomNav?.paddingBottom ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(root) { view: View, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        if (bottomNav != null) {
            bottomNav.updatePadding(bottom = navStartBottom + bars.bottom)
            view.updatePadding(top = startTop + bars.top, bottom = startBottom + ime.bottom)
        } else {
            view.updatePadding(
                top = startTop + bars.top,
                bottom = startBottom + maxOf(bars.bottom, ime.bottom)
            )
        }
        insets
    }
}

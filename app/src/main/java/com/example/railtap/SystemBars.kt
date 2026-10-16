package com.example.railtap

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keeps the screen's content out from under the status bar (top) and the
 * navigation / gesture bar (bottom).
 *
 * Needed because the app targets a new Android version, which draws apps
 * "edge-to-edge" (behind the system bars) on Android 15 and newer.
 *
 * Call it in onCreate right after setContentView:
 *   applySystemBarPadding()
 */
fun Activity.applySystemBarPadding() {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Dark status bar icons (clock, battery), since the app backgrounds are light
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }

    val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
    val startTop = root.paddingTop
    val startBottom = root.paddingBottom

    // Tab screens: the white bottom bar itself stretches down behind the phone's
    // navigation/gesture area (same as the Account screen), so the bar looks
    // identical on every tab and doesn't jump when switching.
    val bottomNav = findViewById<View>(R.id.bottomNav)?.takeIf { it.parent === root }
    val navStartBottom = bottomNav?.paddingBottom ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(root) { view: View, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()) // keyboard
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

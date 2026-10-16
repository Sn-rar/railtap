package com.example.railtap

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup

/**
 * Fade transitions between screens, used instead of Android's default slide.
 *
 *   startWithFade(Intent(this, LogInActivity::class.java))
 *   finishWithoutAnimation()
 *
 * Android's own screen animation is turned off, and the new screen's content
 * fades in by itself. (Doing it this way avoids an Android bug where the
 * slide still plays in some cases.)
 */
fun Activity.startWithFade(intent: Intent, fadeMs: Long = 150L) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

    // Fade in whichever screen appears next, then stop listening
    val source = this
    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: Activity) {
            if (a === source) return
            a.application.unregisterActivityLifecycleCallbacks(this)
            val content = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return

            // Fade the page, but NOT the bottom navigation bar: it's the same on every tab,
            // so it should stay perfectly still (fading it made it flicker).
            val bottomNav = a.findViewById<android.view.View>(R.id.bottomNav)
            val group = content as? ViewGroup
            if (group == null || bottomNav == null || bottomNav.parent !== group) {
                // Screens without a bottom bar (e.g. Splash -> Login): fade everything
                content.alpha = 0f
                content.animate().alpha(1f).setDuration(fadeMs).start()
                return
            }
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child === bottomNav) continue
                child.alpha = 0f
                child.animate().alpha(1f).setDuration(fadeMs).start()
            }
        }

        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    })

    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0) // older Android: no system animation
}

/** Opens a screen instantly: no slide, no fade (used for bottom-bar tab switches). */
fun Activity.startWithoutAnimation(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
}

/** Turns off the closing animation for this screen (call before finish / finishAffinity). */
fun Activity.disableCloseAnimation() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}

/** Closes this screen with no animation. */
fun Activity.finishWithoutAnimation() {
    disableCloseAnimation()
    finish()
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
}

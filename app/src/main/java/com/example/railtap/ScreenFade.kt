package com.example.railtap

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup

// fade between screens instead of the default slide
fun Activity.startWithFade(intent: Intent, fadeMs: Long = 150L) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)

    // fade in the next screen
    val source = this
    application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: Activity) {
            if (a === source) return
            a.application.unregisterActivityLifecycleCallbacks(this)
            val content = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return

            // don't fade the bottom nav, it flickers
            val bottomNav = a.findViewById<android.view.View>(R.id.bottomNav)
            val group = content as? ViewGroup
            if (group == null || bottomNav == null || bottomNav.parent !== group) {
                // no bottom nav: fade everything
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
    overridePendingTransition(0, 0)
}

// open with no animation
fun Activity.startWithoutAnimation(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    startActivity(intent)
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
}

fun Activity.disableCloseAnimation() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}

fun Activity.finishWithoutAnimation() {
    disableCloseAnimation()
    finish()
    @Suppress("DEPRECATION")
    overridePendingTransition(0, 0)
}

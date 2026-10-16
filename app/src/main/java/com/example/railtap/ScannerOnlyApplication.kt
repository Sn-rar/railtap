package com.example.railtap

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

// keeps scanner accounts on the scanner screen only
class ScannerOnlyApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (!ScannerOnlyRole.isScanner()) return
        if (activity is SplashActivity || activity is LogInActivity || activity is GateScannerActivity) return
        activity.startActivity(Intent(activity, GateScannerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity.finish()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

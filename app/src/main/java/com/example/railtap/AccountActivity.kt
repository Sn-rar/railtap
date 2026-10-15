package com.example.railtap

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

class AccountActivity : AppCompatActivity() {

    // Photo picker for the avatar
    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                findViewById<ImageView>(R.id.ivAvatar).apply {
                    setPadding(0, 0, 0, 0)
                    imageTintList = null
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    setImageURI(uri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_account)

        applyWindowInsets()
        bindUser()
        setupMenu()
        setupBottomNav(this, NavTab.ACCOUNT)
    }

    private fun applyWindowInsets() {
        val header = findViewById<View>(R.id.header)
        val bottomNav = findViewById<View>(R.id.bottomNav)
        val headerTop = header.paddingTop
        val navBottom = bottomNav.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(header.parent as View) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePadding(top = headerTop + bars.top)
            bottomNav.updatePadding(bottom = navBottom + bars.bottom)
            insets
        }
    }

    private fun bindUser() {
        // Name saved during sign up (falls back to the email)
        val user = SupabaseClient.client.auth.currentUserOrNull()
        val fullName = user?.userMetadata?.get("full_name")?.jsonPrimitive?.content
        val displayName = fullName ?: user?.email ?: "Guest"
        findViewById<TextView>(R.id.tvUserName).text = "${displayName.uppercase()}!"

        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        findViewById<TextView>(R.id.tvBuildInfo).text =
            "RailTap Rapid Transit v${info.versionName} (Build ${info.versionCode})"

        findViewById<View>(R.id.btnChangePhoto).setOnClickListener {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private fun setupMenu() {
        findViewById<View>(R.id.menuAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<View>(R.id.menuPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        findViewById<View>(R.id.menuTerms).setOnClickListener {
            startActivity(Intent(this, TermsActivity::class.java))
        }
        findViewById<View>(R.id.menuContact).setOnClickListener {
            // startActivity(Intent(this, ContactUsActivity::class.java))
            toast("Contact Us")
        }
        findViewById<View>(R.id.menuLogout).setOnClickListener {
            confirmLogout()
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to log out?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Logout") { _, _ ->
                lifecycleScope.launch {
                    try {
                        SupabaseClient.client.auth.signOut()
                    } catch (e: Exception) {
                        // Still go to login even if the network call fails
                    }
                    // Open Login and clear all screens behind it
                    startActivity(Intent(this@AccountActivity, LogInActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
            .show()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
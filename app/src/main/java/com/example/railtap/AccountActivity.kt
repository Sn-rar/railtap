package com.example.railtap

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

class AccountActivity : AppCompatActivity() {

    private companion object {
        const val AVATAR_BUCKET = "avatars"
        const val AVATAR_FILE = "avatar.jpg"
        const val AVATAR_MAX_SIZE = 512 // px, longest side
    }

    // Photo picker for the avatar
    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadAvatar(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_account)

        applyWindowInsets()
        bindUser()
        loadAvatar()
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

    // ---------- Profile picture ----------

    /** Shows a bitmap in the avatar circle. */
    private fun showAvatar(bitmap: Bitmap) {
        findViewById<ImageView>(R.id.ivAvatar).apply {
            setPadding(0, 0, 0, 0)
            imageTintList = null
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            setImageBitmap(bitmap)
        }
    }

    /** Loads the saved avatar (if any) when the screen opens. */
    private fun loadAvatar() {
        lifecycleScope.launch {
            try {
                val path = SupabaseClient.client.postgrest
                    .rpc("get_my_avatar_path")
                    .decodeAsOrNull<String>()
                if (path.isNullOrBlank()) return@launch

                val bytes = SupabaseClient.client.storage
                    .from(AVATAR_BUCKET)
                    .downloadAuthenticated(path)

                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                if (bitmap != null) showAvatar(bitmap)
            } catch (e: Exception) {
                // Keep the default avatar if loading fails
            }
        }
    }

    /** Resizes, uploads to Storage, then saves the path through the RPC. */
    private fun uploadAvatar(uri: Uri) {
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (userId == null) {
            toast("Please log in again")
            return
        }

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeScaled(uri) }
                if (bitmap == null) {
                    toast("Couldn't read that image")
                    return@launch
                }

                // Show it right away; the upload continues in the background
                showAvatar(bitmap)

                val bytes = withContext(Dispatchers.IO) {
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.toByteArray()
                    }
                }

                val path = "$userId/$AVATAR_FILE"
                SupabaseClient.client.storage
                    .from(AVATAR_BUCKET)
                    .upload(path, bytes) { upsert = true }

                SupabaseClient.client.postgrest.rpc(
                    "set_avatar",
                    buildJsonObject { put("p_path", path) }
                )

                toast("Profile photo updated")
            } catch (e: Exception) {
                toast("Upload failed. Please try again.")
            }
        }
    }

    /** Decodes the picked image, scaled down so the longest side is AVATAR_MAX_SIZE. */
    private fun decodeScaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_MAX_SIZE) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    // ---------- Menu / logout ----------

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
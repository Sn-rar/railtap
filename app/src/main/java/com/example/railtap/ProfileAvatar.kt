package com.example.railtap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads the logged-in user's profile picture (the same one set in Account),
 * so any screen can show it. Keeps it in memory so it appears instantly next time.
 */
object ProfileAvatar {

    /** Last loaded picture (null = none yet / user has no picture). */
    var cached: Bitmap? = null

    /** Downloads the user's picture from Supabase Storage. Returns null if they don't have one. */
    suspend fun load(): Bitmap? = try {
        val path = SupabaseClient.client.postgrest
            .rpc("get_my_avatar_path")
            .decodeAsOrNull<String>()

        if (path.isNullOrBlank()) {
            null
        } else {
            val bytes = SupabaseClient.client.storage
                .from("avatars") // same bucket as AccountActivity
                .downloadAuthenticated(path)
            withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
    } catch (e: Exception) {
        null // keep the default person icon if anything fails
    }.also { if (it != null) cached = it }

    /** Forget the picture (call on logout so the next user doesn't see it). */
    fun clear() {
        cached = null
    }
}

/** Shows a profile picture in an avatar ImageView, filling the rounded shape. */
fun ImageView.showProfilePicture(bitmap: Bitmap) {
    setPadding(0, 0, 0, 0)
    imageTintList = null
    scaleType = ImageView.ScaleType.CENTER_CROP
    clipToOutline = true
    setImageBitmap(bitmap)
}

package com.example.railtap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// loads the user's profile pic, cached so it shows instantly
object ProfileAvatar {

    
    var cached: Bitmap? = null

    // null if they don't have one
    suspend fun load(): Bitmap? = try {
        val path = SupabaseClient.client.postgrest
            .rpc("get_my_avatar_path")
            .decodeAsOrNull<String>()

        if (path.isNullOrBlank()) {
            null
        } else {
            val bytes = SupabaseClient.client.storage
                .from("avatars")
                .downloadAuthenticated(path)
            withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        }
    } catch (e: Exception) {
        null
    }.also { if (it != null) cached = it }

    // call on logout
    fun clear() {
        cached = null
    }
}


fun ImageView.showProfilePicture(bitmap: Bitmap) {
    setPadding(0, 0, 0, 0)
    imageTintList = null
    scaleType = ImageView.ScaleType.CENTER_CROP
    clipToOutline = true
    setImageBitmap(bitmap)
}

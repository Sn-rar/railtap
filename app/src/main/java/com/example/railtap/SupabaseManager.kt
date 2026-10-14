package com.example.railtap

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseManager {

    private const val SUPABASE_URL = "https://updwkxmylacxlocrssqk.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_30pXYndCG5wnGoG14UQPOw_vgIoXwLO"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
    }
}
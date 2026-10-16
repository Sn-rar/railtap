package com.example.railtap

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://updwkxmylacxlocrssqk.supabase.co",
        supabaseKey = "sb_publishable_30pXYndCG5wnGoG14UQPOw_vgIoXwLO"
    ) {
        install(Auth)
        install(Postgrest)
    }
}
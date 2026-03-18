package com.githow.links

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * SupabaseClient
 *
 * Singleton Supabase client. Credentials come from BuildConfig,
 * which reads from local.properties at compile time.
 *
 * Usage anywhere in the app:
 *   SupabaseClient.client.from("raw_sms").insert(payload)
 */
object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = com.githow.links.BuildConfig.SUPABASE_URL,
        supabaseKey = com.githow.links.BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
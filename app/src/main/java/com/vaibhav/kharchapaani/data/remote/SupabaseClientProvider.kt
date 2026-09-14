package com.vaibhav.kharchapaani.data.remote

import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings
import com.vaibhav.kharchapaani.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsCodeVerifierCache
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseClientProvider {

    // A dedicated, named prefs file (rather than supabase-kt's default shared prefs) so
    // backup_rules.xml and data_extraction_rules.xml can exclude the refresh token from
    // cloud backup and device transfer.
    const val AUTH_PREFS_NAME = "supabase_auth"

    @Volatile
    private var instance: SupabaseClient? = null

    fun get(context: Context): SupabaseClient =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

    private fun create(appContext: Context): SupabaseClient {
        val authSettings = SharedPreferencesSettings(
            appContext.getSharedPreferences(AUTH_PREFS_NAME, Context.MODE_PRIVATE)
        )
        return createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            httpEngine = OkHttp.create()
            install(Auth) {
                sessionManager = SettingsSessionManager(authSettings)
                codeVerifierCache = SettingsCodeVerifierCache(authSettings)
            }
            install(Postgrest)
            // Used only as a sync doorbell (SyncDoorbell); rows still arrive through pulls.
            install(Realtime)
        }
    }
}

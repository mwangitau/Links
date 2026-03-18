package com.githow.links.config

import android.content.Context
import android.content.SharedPreferences

/**
 * StationConfig — single source of truth for station identity.
 *
 * Stored in SharedPreferences so it survives app restarts
 * and can be changed per-device without touching source code.
 *
 * One APK — many stations. Each phone gets its own config.
 *
 * Fields:
 *   station_code   — short ID used in Supabase (e.g. "MANGU", "WESTLANDS")
 *   station_name   — display name (e.g. "Shell Mangu Road")
 *   till_number    — M-PESA till number for this station
 *   paybill_number — M-PESA paybill for this station
 *   station_uuid   — UUID from Supabase stations table (auto-resolved on first sync, cached)
 */
object StationConfig {

    private const val PREFS_NAME = "links_station_config"
    private const val KEY_STATION_CODE = "station_code"
    private const val KEY_STATION_NAME = "station_name"
    private const val KEY_TILL_NUMBER = "till_number"
    private const val KEY_PAYBILL_NUMBER = "paybill_number"
    private const val KEY_STATION_UUID = "station_uuid"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Getters ──────────────────────────────────────────────────────────

    fun getStationCode(context: Context): String =
        prefs(context).getString(KEY_STATION_CODE, "") ?: ""

    fun getStationName(context: Context): String =
        prefs(context).getString(KEY_STATION_NAME, "") ?: ""

    fun getTillNumber(context: Context): String =
        prefs(context).getString(KEY_TILL_NUMBER, "") ?: ""

    fun getPaybillNumber(context: Context): String =
        prefs(context).getString(KEY_PAYBILL_NUMBER, "") ?: ""

    fun getCachedUuid(context: Context): String =
        prefs(context).getString(KEY_STATION_UUID, "") ?: ""

    // ── Setters ──────────────────────────────────────────────────────────

    fun save(
        context: Context,
        stationCode: String,
        stationName: String,
        tillNumber: String,
        paybillNumber: String
    ) {
        prefs(context).edit()
            .putString(KEY_STATION_CODE, stationCode.trim().uppercase())
            .putString(KEY_STATION_NAME, stationName.trim())
            .putString(KEY_TILL_NUMBER, tillNumber.trim())
            .putString(KEY_PAYBILL_NUMBER, paybillNumber.trim())
            .putString(KEY_STATION_UUID, "") // clear cached UUID — will re-resolve on next sync
            .apply()
    }

    fun cacheUuid(context: Context, uuid: String) {
        prefs(context).edit()
            .putString(KEY_STATION_UUID, uuid)
            .apply()
    }

    // ── Validation ───────────────────────────────────────────────────────

    fun isConfigured(context: Context): Boolean =
        getStationCode(context).isNotBlank() && getStationName(context).isNotBlank()

    fun toDisplayString(context: Context): String {
        val code = getStationCode(context)
        val name = getStationName(context)
        val till = getTillNumber(context)
        return if (isConfigured(context)) "$name ($code) — Till: $till" else "Not configured"
    }
}
package com.example.rentease

import android.content.Context
import android.content.SharedPreferences

object SessionManager {

    private const val PREF_NAME  = "RentEaseSession"
    private const val KEY_UID    = "uid"
    private const val KEY_NAME   = "name"
    private const val KEY_EMAIL  = "email"
    private const val KEY_ROLE   = "role"
    private const val KEY_PHONE  = "phone"
    private const val KEY_LOGGED = "isLoggedIn"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── Save session after login/signup ───────────────────────────────────────

    fun saveSession(
        context: Context,
        uid: String,
        name: String,
        email: String,
        role: String,
        phone: String = ""
    ) {
        prefs(context).edit().apply {
            putString(KEY_UID, uid)
            putString(KEY_NAME, name)
            putString(KEY_EMAIL, email)
            putString(KEY_ROLE, role)
            putString(KEY_PHONE, phone)
            putBoolean(KEY_LOGGED, true)
            apply()
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED, false)

    fun getUid(context: Context): String =
        prefs(context).getString(KEY_UID, "") ?: ""

    fun getName(context: Context): String =
        prefs(context).getString(KEY_NAME, "User") ?: "User"

    fun getEmail(context: Context): String =
        prefs(context).getString(KEY_EMAIL, "") ?: ""

    fun getRole(context: Context): String =
        prefs(context).getString(KEY_ROLE, "Tenant") ?: "Tenant"

    fun getPhone(context: Context): String =
        prefs(context).getString(KEY_PHONE, "") ?: ""

    fun getInitials(context: Context): String {
        val name = getName(context)
        return name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
    }

    // ── Clear session on sign out ─────────────────────────────────────────────

    fun clearSession(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

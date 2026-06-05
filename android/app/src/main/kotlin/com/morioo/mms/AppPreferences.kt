package com.morioo.mms

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {

    private const val PREFS_NAME  = "morioo_prefs"
    private const val KEY_PI_URL  = "pi_url"
    const val DEFAULT_PI_URL      = "http://rasp-boesch.local:8000"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var piUrl: String
        get() = prefs.getString(KEY_PI_URL, DEFAULT_PI_URL) ?: DEFAULT_PI_URL
        set(value) { prefs.edit().putString(KEY_PI_URL, value).apply() }
}

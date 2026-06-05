package com.morioo.mms

import android.app.Application

class MoriooApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppPreferences.init(this)
    }
}

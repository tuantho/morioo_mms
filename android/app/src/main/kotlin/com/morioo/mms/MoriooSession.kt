package com.morioo.mms

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class MoriooSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen =
        DashboardScreen(carContext)
}

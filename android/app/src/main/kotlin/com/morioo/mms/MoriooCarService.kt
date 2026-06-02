package com.morioo.mms

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class MoriooCarService : CarAppService() {

    // ALLOW_ALL : acceptable pour un usage sideloadé en dev.
    // En prod Play Store il faudrait valider le host Android Auto.
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = MoriooSession()
}

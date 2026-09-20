package de.langerhans.odintools.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.overlay.QuickAccessOverlay
import javax.inject.Inject

@AndroidEntryPoint
class GamingOverlayService : Service() {

    @Inject
    lateinit var prefs: SharedPrefsRepo

    private var overlay: QuickAccessOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlay = QuickAccessOverlay(this, prefs)
        overlay?.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlay?.hide()
        overlay = null
    }
}

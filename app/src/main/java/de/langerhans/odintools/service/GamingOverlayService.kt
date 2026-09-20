package de.langerhans.odintools.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.overlay.QuickAccessOverlay
import javax.inject.Inject

@AndroidEntryPoint
class GamingOverlayService : Service() {

    private val TAG = "OdinOverlay"

    @Inject
    lateinit var prefs: SharedPrefsRepo

    private var overlay: QuickAccessOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GamingOverlayService onCreate invoked")
        overlay = QuickAccessOverlay(this, prefs)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "GamingOverlayService onStartCommand invoked")
        overlay?.show()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "GamingOverlayService onDestroy invoked")
        overlay?.hide()
        overlay = null
    }
}

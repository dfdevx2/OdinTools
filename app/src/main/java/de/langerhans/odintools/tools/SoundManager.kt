package de.langerhans.odintools.tools

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.langerhans.odintools.R
import de.langerhans.odintools.data.SharedPrefsRepo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de áudio da UI (música de fundo + efeitos sonoros). Substitui o código que existia
 * antes das refatorações para a nova interface e que se perdeu nesse processo -- os ficheiros
 * em `res/raw` (bgm_1, bgm_2, sfx_error, sfx_nav, sfx_select) continuavam no projeto, mas nada
 * os reproduzia como música de fundo, e não existiam controlos persistentes de volume/on-off
 * (só o ecrã de boas-vindas, que só corre uma vez, tinha os sliders).
 *
 * BGM usa [MediaPlayer] (ficheiro longo, em loop). SFX usa [SoundPool] (ficheiros curtos,
 * pré-carregados, disparo instantâneo sem o custo de criar um MediaPlayer novo a cada toque).
 */
@Singleton
class SoundManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPrefsRepo,
) {
    private var bgmPlayer: MediaPlayer? = null

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val sfxSelectId = soundPool.load(context, R.raw.sfx_select, 1)
    private val sfxNavId = soundPool.load(context, R.raw.sfx_nav, 1)
    private val sfxErrorId = soundPool.load(context, R.raw.sfx_error, 1)

    // -------------------------------------------------------------------------------------
    // Música de fundo
    // -------------------------------------------------------------------------------------

    /** Chamado uma vez quando a UI principal arranca (ver MainViewModel.init). */
    fun startBackgroundMusicIfEnabled() {
        if (prefs.bgmEnabled) startBackgroundMusic() else stopBackgroundMusic()
    }

    private fun startBackgroundMusic() {
        if (bgmPlayer?.isPlaying == true) return
        runCatching {
            bgmPlayer?.release()
            bgmPlayer = MediaPlayer.create(context, R.raw.bgm_1)?.apply {
                isLooping = true
                setVolume(prefs.bgmVolume, prefs.bgmVolume)
                start()
            }
        }.onFailure { Log.w(TAG, "Falha ao iniciar a música de fundo", it) }
    }

    private fun stopBackgroundMusic() {
        runCatching {
            bgmPlayer?.let { if (it.isPlaying) it.stop() }
            bgmPlayer?.release()
        }
        bgmPlayer = null
    }

    fun setBgmEnabled(enabled: Boolean) {
        prefs.bgmEnabled = enabled
        if (enabled) startBackgroundMusic() else stopBackgroundMusic()
    }

    fun setBgmVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        prefs.bgmVolume = clamped
        bgmPlayer?.setVolume(clamped, clamped)
    }

    // -------------------------------------------------------------------------------------
    // Efeitos sonoros da UI
    // -------------------------------------------------------------------------------------

    fun setSfxEnabled(enabled: Boolean) {
        prefs.sfxEnabled = enabled
    }

    fun setSfxVolume(volume: Float) {
        prefs.sfxVolume = volume.coerceIn(0f, 1f)
    }

    fun playClick() = playSfx(sfxSelectId)
    fun playNav() = playSfx(sfxNavId)
    fun playError() = playSfx(sfxErrorId)

    private fun playSfx(soundId: Int) {
        if (!prefs.sfxEnabled) return
        val volume = prefs.sfxVolume
        runCatching { soundPool.play(soundId, volume, volume, 1, 0, 1.0f) }
            .onFailure { Log.w(TAG, "Falha ao reproduzir efeito sonoro", it) }
    }

    /** Chamado a partir de OdinToolsApplication/MainActivity.onDestroy para libertar recursos nativos. */
    fun release() {
        stopBackgroundMusic()
        runCatching { soundPool.release() }
    }

    private companion object {
        const val TAG = "SoundManager"
    }
}

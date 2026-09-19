package de.langerhans.odintools.tools

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Parcel
import java.io.DataOutputStream
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
class ShellExecutor @Inject constructor() {

    private val binder: IBinder?
    var pServerAvailable: Boolean = false
        private set

    // Toggle controlado pela Interface do Odin Hub
    var forceKernelSU: Boolean = false

    // Sessão Persistente de Root (Impede que o emulador trave abrindo shell toda hora)
    private var rootProcess: Process? = null
    private var rootOs: DataOutputStream? = null

    init {
        // Inicializa PServer (Método Pulse)
        binder = runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(serviceManager, "PServerBinder") as IBinder
            pServerAvailable = true
            binder
        }.getOrDefault(null)

        // Tenta inicializar Sessão Root em 2º plano
        initRootShell()
    }

    private fun initRootShell() {
        try {
            rootProcess = Runtime.getRuntime().exec("su")
            rootOs = DataOutputStream(rootProcess?.outputStream)
        } catch (e: Exception) {
            // Usuário não tem root, ignorar
        }
    }

    fun executeAsRoot(cmd: String): Result<String?> {
        // Se a Interface pediu KernelSU, ou se o PServer não existe no aparelho (mas tem root)
        if (forceKernelSU || binder == null) {
            if (rootOs == null) initRootShell() // Tenta abrir de novo se fechou

            return try {
                if (rootOs != null) {
                    // Modo de gravação ultra-rápido para o AutoTDP (evita overhead)
                    if (cmd.startsWith("echo") || cmd.startsWith("chmod")) {
                        rootOs?.writeBytes("$cmd\n")
                        rootOs?.flush()
                        Result.success(null)
                    } else {
                        // Modo de leitura com retorno (cat, getprop, etc)
                        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                        val result = process.inputStream.bufferedReader().readText().trim()
                        process.waitFor()
                        Result.success(if (result.isEmpty() || result == "null") null else result)
                    }
                } else {
                    Result.failure(IllegalStateException("Aparelho sem PServer e sem KernelSU!"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // --- MODO PSERVER (OdinTools Original / Pulse) ---
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        data.writeStringArray(arrayOf(cmd, "1"))
        runCatching { binder!!.transact(0, data, reply, 0) }
            .getOrElse {
                return Result.failure(it)
            }
        val result = reply.createByteArray()?.toString(Charset.defaultCharset())?.trim()?.let {
            if (it == "null") null else it
        }
        data.recycle()
        reply.recycle()
        return Result.success(result)
    }

    // ==============================================================
    // FUNÇÕES ORIGINAIS DO ODINTOOLS MANTIDAS INTACTAS
    // ==============================================================

    private fun getProperty(property: String): Result<String?> {
        return executeAsRoot("getprop $property")
    }

    fun getIntProperty(property: String, defaultValue: Int): Int {
        return getProperty(property)
            .mapCatching { it?.toInt() ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    fun getFloatProperty(property: String, defaultValue: Float): Float {
        return getProperty(property)
            .mapCatching { it?.toFloat() ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    fun getBooleanProperty(property: String, defaultValue: Boolean): Boolean {
        return getProperty(property)
            .map { if (it == null) defaultValue else it == "1" }
            .getOrDefault(defaultValue)
    }

    fun getStringProperty(property: String, defaultValue: String): String {
        return getProperty(property)
            .map { it ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    private fun getSystemSetting(setting: String): Result<String?> {
        return executeAsRoot("settings get system $setting")
    }

    fun getStringSystemSetting(setting: String, defaultValue: String): String {
        return getSystemSetting(setting)
            .map { it ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    fun setStringSystemSetting(setting: String, value: String) {
        executeAsRoot("settings put system $setting $value")
    }

    fun getIntSystemSetting(setting: String, defaultValue: Int): Int {
        return getSystemSetting(setting)
            .mapCatching { it?.toInt() ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    fun setIntSystemSetting(setting: String, value: Int) {
        executeAsRoot("settings put system $setting $value")
    }

    fun getBooleanSystemSetting(setting: String, defaultValue: Boolean): Boolean {
        return getSystemSetting(setting)
            .map { if (it == null) defaultValue else it == "1" }
            .getOrDefault(defaultValue)
    }

    fun setBooleanSystemSetting(setting: String, value: Boolean) {
        setIntSystemSetting(setting, if (value) 1 else 0)
    }

    private fun getValue(file: String): Result<String?> {
        return executeAsRoot("cat $file")
    }

    fun getStringValue(file: String, defaultValue: String): String {
        return getValue(file)
            .map { it ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    fun setStringValue(file: String, value: String) {
        executeAsRoot("echo $value > $file")
    }

    fun getIntValue(file: String, defaultValue: Int): Int {
        return getValue(file)
            .mapCatching { it?.toInt() ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    fun setIntValue(file: String, value: Int) {
        executeAsRoot("echo $value > $file")
    }

    fun getFloatValue(file: String, defaultValue: Float): Float {
        return getValue(file)
            .mapCatching { it?.toFloat() ?: defaultValue }
            .getOrDefault(defaultValue)
    }

    fun setFloatValue(file: String, value: Float) {
        executeAsRoot("echo $value > $file")
    }

    fun getBooleanValue(file: String, defaultValue: Boolean): Boolean {
        return getValue(file)
            .map { if (it == null) defaultValue else it == "1" }
            .getOrDefault(defaultValue)
    }

    fun setBooleanValue(file: String, value: Boolean) {
        setIntValue(file, if (value) 1 else 0)
    }
}

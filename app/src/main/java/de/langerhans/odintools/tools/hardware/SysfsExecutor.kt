package de.langerhans.odintools.tools.hardware

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

interface SysfsExecutor {
    fun write(path: String, value: String): Boolean
    fun read(path: String): String?
    fun isAvailable(): Boolean
}

class RootExecutor : SysfsExecutor {
    private var suProcess: Process? = null
    private var os: DataOutputStream? = null

    init {
        // Inicializa o Root Shell e mantém ABERTO para disparos ultra-rápidos
        try {
            suProcess = Runtime.getRuntime().exec("su")
            os = DataOutputStream(suProcess?.outputStream)
        } catch (e: Exception) {
            Log.e("OdinHub_Root", "Falha ao iniciar Root Shell", e)
        }
    }

    override fun isAvailable(): Boolean {
        return os != null
    }

    override fun write(path: String, value: String): Boolean {
        if (!isAvailable()) return false
        return try {
            // Dispara comandos no shell já aberto
            os?.writeBytes("chmod 644 $path\n")
            os?.writeBytes("echo $value > $path\n")
            os?.flush()
            true
        } catch (e: Exception) {
            Log.e("OdinHub_Root", "Erro ao escrever em $path: ${e.message}")
            false
        }
    }

    override fun read(path: String): String? {
        if (!isAvailable()) return null
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output?.trim()
        } catch (e: Exception) {
            null
        }
    }
}

package de.langerhans.odintools.tools.hardware

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Interface unificada para escrever nos nós do Kernel do Android.
 * Será herdada pelo RootExecutor (KernelSU/Magisk) e pelo PServerExecutor (No-Root/Pulse).
 */
interface SysfsExecutor {
    fun write(path: String, value: String): Boolean
    fun read(path: String): String?
    fun isAvailable(): Boolean
}

/**
 * Executor primário usando superusuário (KernelSU / Magisk).
 * É a forma mais rápida, segura e direta de travar o hardware no Snapdragon.
 */
class RootExecutor : SysfsExecutor {
    override fun isAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output?.contains("uid=0(root)") == true
        } catch (e: Exception) {
            false
        }
    }

    override fun write(path: String, value: String): Boolean {
        if (!isAvailable()) return false
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("chmod 644 $path\n") // Garante permissão de escrita
            os.writeBytes("echo $value > $path\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
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

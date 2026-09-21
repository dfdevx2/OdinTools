package com.dfdx047.odinhub.tools.hardware

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LosslessManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val losslessDir: File
        get() = File(context.filesDir, "lossless").apply { if (!exists()) mkdirs() }

    val dllFile: File
        get() = File(losslessDir, "Lossless.dll")

    val isDllImported: Boolean
        get() = dllFile.exists() && dllFile.length() > 0

    /**
     * Importa a DLL fornecida pelo utilizador (via seletor de ficheiros) para o diretório seguro do app.
     */
    fun importDll(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                dllFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Remove a DLL importada se o utilizador quiser limpar os dados.
     */
    fun deleteDll() {
        if (dllFile.exists()) {
            dllFile.delete()
        }
    }
}
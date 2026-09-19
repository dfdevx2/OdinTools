package de.langerhans.odintools.tools

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionChecker {

    // Verifica se a permissão de Acesso a Dados de Uso (UsageStats) foi concedida
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = ContextCompat.getSystemService(context, AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // Verifica se a permissão de Sobreposição de Tela (System Alert Window) foi concedida
    fun hasOverlayAccess(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    // Retorna verdadeiro apenas se todas as permissões essenciais estiverem ativas
    fun areAllPermissionsGranted(context: Context): Boolean {
        return hasUsageAccess(context) && hasOverlayAccess(context)
    }
}

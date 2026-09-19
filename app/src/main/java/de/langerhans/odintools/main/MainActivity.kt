package de.langerhans.odintools.main

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.app.AppOpsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.ui.screens.SettingsScreen
import de.langerhans.odintools.appsettings.AppOverrideListScreen
import de.langerhans.odintools.appsettings.AppOverridesScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Solicita permissão de Notificação no Android 13+ de forma segura
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runCatching {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                }
            }
        }

        // 2. Interface do Compose com as rotas e tipos corrigidos
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "settings") {
                composable("settings") {
                    SettingsScreen(navigateToOverrideList = { navController.navigate("override_list") })
                }
                composable("override_list") {
                    AppOverrideListScreen(
                        navigateToOverrides = { packageName: String ->
                            navController.navigate("override/$packageName")
                        }
                    )
                }
                composable("override/{packageName}") { backStackEntry ->
                    val packageName = backStackEntry.arguments?.getString("packageName")
                    AppOverridesScreen(navigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}

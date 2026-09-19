package de.langerhans.odintools.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.app.AppOpsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.data.SharedPrefsRepo
import de.langerhans.odintools.service.OdinHubService
import de.langerhans.odintools.ui.screens.SettingsScreen
import de.langerhans.odintools.appsettings.AppOverrideListScreen
import de.langerhans.odintools.appsettings.AppOverridesScreen
import de.langerhans.odintools.ui.screens.PerformanceScreen
import de.langerhans.odintools.ui.screens.PermissionOnboardingWrapper
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sharedPrefsRepo: SharedPrefsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicia o serviço em segundo plano se a permissão de uso estiver ativa
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )

        if (mode == AppOpsManager.MODE_ALLOWED) {
            val serviceIntent = Intent(this, OdinHubService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }

        setContent {
            PermissionOnboardingWrapper {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "settings") {
                    composable("settings") {
                        SettingsScreen(
                            navigateToOverrideList = { navController.navigate("override_list") },
                            navigateToPerformance = { navController.navigate("performance") }
                        )
                    }
                    composable("performance") {
                        PerformanceScreen(
                            navigateBack = { navController.popBackStack() },
                            savedCustomTdfs = sharedPrefsRepo.customTdpProfiles,
                            onSaveCustomTdp = { name, watts ->
                                val currentList = sharedPrefsRepo.customTdpProfiles.toMutableList()
                                currentList.add(de.langerhans.odintools.models.CustomTdpProfile(name = name, watts = watts))
                                sharedPrefsRepo.customTdpProfiles = currentList
                            },
                            onDeleteCustomTdp = { id ->
                                val currentList = sharedPrefsRepo.customTdpProfiles.toMutableList()
                                currentList.removeAll { it.id == id }
                                sharedPrefsRepo.customTdpProfiles = currentList
                            },
                            onSelectTdp = { watts, profileName ->
                                // Aqui faremos a chamada direta ao PerformanceManager futuramente
                            }
                        )
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
}

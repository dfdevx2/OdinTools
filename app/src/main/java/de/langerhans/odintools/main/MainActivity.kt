package de.langerhans.odintools.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.appsettings.AppOverrideListScreen
import de.langerhans.odintools.appsettings.AppOverridesScreen
import de.langerhans.odintools.ui.WelcomeScreen
import de.langerhans.odintools.ui.screens.SettingsScreen
import de.langerhans.odintools.ui.theme.OdinToolsTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OdinToolsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "welcome") {

                        composable("welcome") {
                            WelcomeScreen(
                                onStartHub = {
                                    navController.navigate("settings") {
                                        popUpTo("welcome") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("settings") {
                            SettingsScreen { navController.navigate("override/list") }
                        }

                        composable("override/list") {
                            AppOverrideListScreen { navController.navigate("override/$it") }
                        }

                        composable("override/{packageName}") {
                            AppOverridesScreen { navController.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}

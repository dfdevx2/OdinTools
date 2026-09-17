package de.langerhans.odintools.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import de.langerhans.odintools.ui.screens.SettingsScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Habilita o desenho da tela por trás da barra de status (combina com o WindowInsets que colocamos)
        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()

            // Como o SettingsScreen já possui o RetroDarkTheme embutido,
            // não precisamos mais de nenhum "ThemeWrapper" aqui fora.
            NavHost(navController = navController, startDestination = "settings") {
                composable("settings") {
                    SettingsScreen(
                        navigateToOverrideList = {
                            // Deixaremos o gancho pronto para quando refizermos a tela de overrides
                        }
                    )
                }
            }
        }
    }
}

package com.simplebackup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simplebackup.app.ui.Routes
import com.simplebackup.app.ui.home.HomeScreen
import com.simplebackup.app.ui.onboarding.OnboardingScreen
import com.simplebackup.app.ui.picker.ContactPickerScreen
import com.simplebackup.app.ui.settings.SettingsScreen
import com.simplebackup.app.ui.theme.SimpleBackupTheme
import com.simplebackup.app.ui.viewer.ViewerScreen
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SimpleBackupApplication).container
        setContent {
            SimpleBackupTheme {
                Surface {
                    var startRoute by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        val s = container.settings.flow.first()
                        startRoute = if (s.lastRunMs != null || s.selectedE164.isNotEmpty()) {
                            Routes.HOME
                        } else {
                            Routes.ONBOARDING
                        }
                    }
                    val sr = startRoute
                    if (sr != null) AppNavHost(startDestination = sr)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNavHost(startDestination: String) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onContinue = {
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onEditContacts = { nav.navigate(Routes.PICKER) },
                onView = { nav.navigate(Routes.VIEWER) },
                onSettings = { nav.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.PICKER) {
            ContactPickerScreen(onDone = { nav.popBackStack() })
        }
        composable(Routes.VIEWER) {
            ViewerScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

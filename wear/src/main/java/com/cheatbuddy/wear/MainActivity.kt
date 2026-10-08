package com.cheatbuddy.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.cheatbuddy.wear.ui.components.LocalAmbientState
import com.cheatbuddy.wear.ui.components.burnInShift
import com.cheatbuddy.wear.ui.components.rememberAmbientState
import com.cheatbuddy.wear.ui.home.HomeScreen
import com.cheatbuddy.wear.ui.settings.SettingsScreen
import com.cheatbuddy.wear.ui.sheet.SheetScreen
import com.cheatbuddy.wear.ui.theme.CheatBuddyWearTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Sheet requested by the tile; consumed once by the nav host. */
    private val pendingSheet = MutableStateFlow<String?>(null)
    private val pendingSheetFlow = pendingSheet.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingSheet.value = intent.getStringExtra(EXTRA_SHEET_ID)
        setContent {
            val ambient = rememberAmbientState(this)
            CompositionLocalProvider(LocalAmbientState provides ambient) {
                CheatBuddyWearTheme {
                    WearApp(pendingSheetFlow, onPendingConsumed = { pendingSheet.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SHEET_ID)?.let { pendingSheet.value = it }
    }

    companion object {
        const val EXTRA_SHEET_ID = "sheet_id"
    }
}

private object Routes {
    const val HOME = "home"
    const val SHEET = "sheet/{sheetId}"
    const val SETTINGS = "settings"
    fun sheet(id: String) = "sheet/$id"
}

@Composable
private fun WearApp(pendingSheet: StateFlow<String?>, onPendingConsumed: () -> Unit) {
    val navController = rememberSwipeDismissableNavController()
    val ambient = LocalAmbientState.current

    LaunchedEffect(pendingSheet) {
        pendingSheet.collect { id ->
            if (id != null) {
                navController.navigate(Routes.sheet(id)) {
                    popUpTo(Routes.HOME)
                    launchSingleTop = true
                }
                onPendingConsumed()
            }
        }
    }

    AppScaffold(timeText = { TimeText() }, modifier = Modifier.burnInShift(ambient)) {
        // SwipeDismissableNavHost animates screens (scale + fade) and supports swipe-to-dismiss / predictive back.
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSheet = { navController.navigate(Routes.sheet(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.SHEET, arguments = listOf(navArgument("sheetId") { type = NavType.StringType })) {
                SheetScreen()
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

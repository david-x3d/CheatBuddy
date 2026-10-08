package com.cheatbuddy.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cheatbuddy.mobile.ui.editor.EditorScreen
import com.cheatbuddy.mobile.ui.list.SheetListScreen
import com.cheatbuddy.mobile.ui.theme.CheatBuddyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@Serializable object SheetListRoute

@Serializable data class EditorRoute(val sheetId: String)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CheatBuddyTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = SheetListRoute,
                    enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
                    exitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() },
                    popExitTransition = { fadeOut() + scaleOut(targetScale = 0.96f) },
                ) {
                    composable<SheetListRoute> {
                        SheetListScreen(onOpenSheet = { navController.navigate(EditorRoute(it)) })
                    }
                    // EditorViewModel reads EditorRoute.sheetId from its SavedStateHandle.
                    composable<EditorRoute> {
                        EditorScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

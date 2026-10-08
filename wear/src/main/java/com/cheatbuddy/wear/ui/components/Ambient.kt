package com.cheatbuddy.wear.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver

@Immutable
data class AmbientState(
    val isAmbient: Boolean = false,
    val burnInProtection: Boolean = false,
    val lowBit: Boolean = false,
    /** Incremented on every ambient update (about once a minute); drives burn-in shifting. */
    val tick: Int = 0,
)

val LocalAmbientState = compositionLocalOf { AmbientState() }

/** Observes the activity's ambient state, so the app stays visible (dimmed) instead of leaving to the watch face. */
@Composable
fun rememberAmbientState(activity: ComponentActivity): AmbientState {
    var state by remember { mutableStateOf(AmbientState()) }
    DisposableEffect(activity) {
        val callback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                state = AmbientState(
                    isAmbient = true,
                    burnInProtection = ambientDetails.burnInProtectionRequired,
                    lowBit = ambientDetails.deviceHasLowBitAmbient,
                )
            }

            override fun onUpdateAmbient() {
                state = state.copy(tick = state.tick + 1)
            }

            override fun onExitAmbient() {
                state = AmbientState()
            }
        }
        val observer = AmbientLifecycleObserver(activity, ContextCompat.getMainExecutor(activity), callback)
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    return state
}

/** Shifts content by a few pixels each ambient minute on screens that need burn-in protection. */
fun Modifier.burnInShift(state: AmbientState): Modifier =
    if (state.isAmbient && state.burnInProtection) {
        val step = state.tick % 4
        offset(x = (if (step == 1 || step == 2) 2 else -2).dp, y = (if (step >= 2) 2 else -2).dp)
    } else {
        this
    }

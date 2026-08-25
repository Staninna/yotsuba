package dev.stan.yotsuba.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Runs [onResume] every time the host lifecycle reaches RESUMED. */
@Composable
fun OnResumeEffect(onResume: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose { }
    }
}

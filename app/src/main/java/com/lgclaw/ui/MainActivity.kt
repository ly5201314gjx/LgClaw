package com.lgclaw.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lgclaw.R
import com.lgclaw.ui.theme.LGClawTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        preferHighRefreshRate()
        setContent {
            val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(application))
            val state = vm.uiState.collectAsStateWithLifecycle().value
            CompositionLocalProvider(
                LocalUiLanguage provides if (state.settingsUseChinese) UiLanguage.Chinese else UiLanguage.English
            ) {
                LGClawTheme(darkTheme = state.settingsDarkTheme) {
                    var showStartupSplash by remember { mutableStateOf(true) }
                    Box(Modifier.fillMaxSize()) {
                        ChatScreen(vm = vm)
                        if (showStartupSplash) {
                            LGClawStartupSplash(onFinished = { showStartupSplash = false })
                        }
                    }
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        preferHighRefreshRate()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) preferHighRefreshRate()
    }
    private fun preferHighRefreshRate() {
        val targetRate = bestRefreshRate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = targetRate
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display?.supportedModes
                        ?.filter { it.refreshRate >= 90f }
                        ?.maxByOrNull { it.refreshRate }
                        ?.let { preferredDisplayModeId = it.modeId }
                }
            }
        }
    }

    private fun bestRefreshRate(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 120f
        }
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay.refreshRate.takeIf { it >= 60f } ?: 120f
    }
}

@Composable
private fun LGClawStartupSplash(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 320 else 260),
        label = "lgclawSplashAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = if (visible) 420 else 260),
        label = "lgclawSplashScale"
    )
    LaunchedEffect(Unit) {
        visible = true
        delay(660)
        visible = false
        delay(280)
        onFinished()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020706).copy(alpha = alpha.coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.lgclaw_splash_icon),
            contentDescription = null,
            modifier = Modifier
                .size(168.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
        )
    }
}



package com.lgclaw.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lgclaw.ui.theme.LGClawTheme

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
                    ChatScreen(vm = vm)
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



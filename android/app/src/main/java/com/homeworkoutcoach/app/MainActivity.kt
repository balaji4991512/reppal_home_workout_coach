package com.homeworkoutcoach.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeworkoutcoach.app.data.ThemeMode
import com.homeworkoutcoach.app.ui.AppViewModel
import com.homeworkoutcoach.app.ui.Factories
import com.homeworkoutcoach.app.ui.shell.AppShell
import com.homeworkoutcoach.app.ui.theme.AppTheme
import com.homeworkoutcoach.app.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }

    @Composable
    private fun App(vm: AppViewModel = viewModel(factory = Factories.App)) {
        val settings by vm.settings.collectAsStateWithLifecycle()
        val mode = settings?.theme ?: ThemeMode.LIGHT
        val dark = isDark(mode)
        LaunchedEffect(dark) {
            val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        }
        AppTheme(mode) {
            // Wait for settings so the first screen is right (setup or week).
            val s = settings
            if (s == null) Box(Modifier.fillMaxSize().background(AppTheme.colors.bg))
            else AppShell(stringResource(R.string.app_name), s, vm::setTheme)
        }
    }
}

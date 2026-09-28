package com.mlib.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mlib.dashboard.ui.AppDrawerScreen
import com.mlib.dashboard.ui.DashboardScreen
import com.mlib.dashboard.ui.FirstRunSetupScreen
import com.mlib.dashboard.ui.SettingsScreen
import com.mlib.dashboard.ui.ThemePreferences
import com.mlib.dashboard.ui.themeSets
import com.mlib.future.FutureTheme
import kotlinx.coroutines.launch
import android.content.Intent
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		val insetsController = WindowCompat.getInsetsController(window, window.decorView)
		insetsController.hide(WindowInsetsCompat.Type.statusBars())
		insetsController.systemBarsBehavior =
		WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

		initFiles(this)

		val serviceIntent = Intent(this, LockScreenService::class.java)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			startForegroundService(serviceIntent)
		} else {
			startService(serviceIntent)
		}

		setContent {
			val context = LocalContext.current
			val themePreferences = remember { ThemePreferences(context) }
			val scope = rememberCoroutineScope()
			val isDarkMode = isSystemInDarkTheme()
			val selectedThemeIndex by themePreferences.selectedThemeIndexFlow
			.collectAsState(initial = 0)
			val setupCompleted by themePreferences.setupCompletedFlow
			.collectAsState(initial = null)
			val config = if (isDarkMode) themeSets[selectedThemeIndex].dark
			else themeSets[selectedThemeIndex].light

			LaunchedEffect(config.colors.globalBg) {
				setSystemWallpaperToColor(context, config.colors.globalBg)
			}

			FutureTheme(config = config) {
				when (setupCompleted) {
					null -> {
						Box(Modifier.fillMaxSize().background(config.colors.globalBg))
					}
					false -> FirstRunSetupScreen(
						onComplete = {
							scope.launch { themePreferences.markSetupCompleted() }
						}
					)
					true -> {
						var showApps by remember { mutableStateOf(false) }
						var showSettings by remember { mutableStateOf(false) }

						BackHandler(enabled = true) {
							when {
								showSettings -> showSettings = false
								showApps -> showApps = false
								else -> { /* consume */ }
							}
						}

						Box(modifier = Modifier.fillMaxSize()) {
							when {
								showSettings -> SettingsScreen(
									selectedThemeIndex = selectedThemeIndex,
									onThemeSelected = { newIndex ->
										scope.launch { themePreferences.updateSelectedThemeIndex(newIndex) }
									},
									onBack = { showSettings = false }
								)
								showApps -> AppDrawerScreen(onClose = { showApps = false })
								else -> DashboardScreen(
									onOpenApps = { showApps = true },
									onOpenSettings = { showSettings = true }
								)
							}
						}
					}
				}
			}
		}
	}
}

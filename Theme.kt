package com.mlib.dashboard.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mlib.future.BackgroundAnimationType
import com.mlib.future.FutureConfig
import com.mlib.future.FutureColors
import com.mlib.future.hexColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

data class ThemeSet(val name: String, val light: FutureConfig, val dark: FutureConfig)

private val GlassLightColors = FutureColors.MasemLight.copy(
	componentBg = Color(0x80FFFFFF)   
)
private val GlassDarkColors = FutureColors.MasemDark.copy(
	componentBg = Color(0x800D0D14)   
)

val themeSets = listOf(
	ThemeSet("Masem",   FutureConfig.MasemLight,   FutureConfig.MasemDark),
	ThemeSet("Aurora",  FutureConfig.AuroraLight,  FutureConfig.AuroraDark),
	ThemeSet("Julee",   FutureConfig.JuleeLight,   FutureConfig.JuleeDark),
	ThemeSet("Autumn",  FutureConfig.AutumnLight,  FutureConfig.AutumnDark),
	ThemeSet("Sunrise", FutureConfig.SunriseLight, FutureConfig.SunriseDark),
	ThemeSet("Sunset",  FutureConfig.SunsetLight,  FutureConfig.SunsetDark),
	ThemeSet("Painting",FutureConfig.PaintingLight,FutureConfig.PaintingDark),
	ThemeSet("Charcoal",FutureConfig.CharcoalLight,FutureConfig.CharcoalDark),
	ThemeSet("Celeste", FutureConfig.CelesteLight, FutureConfig.CelesteDark),

	ThemeSet(
		"Glass",
		FutureConfig(colors = GlassLightColors),
		FutureConfig(colors = GlassDarkColors)
	),

	ThemeSet(
		"Custom",
		FutureConfig(
			colors = FutureColors(primary = hexColor("#00FF88"), globalBg = hexColor("#001122")),
			backgroundAnimation = BackgroundAnimationType.CYBER_GRID
		),
		FutureConfig(
			colors = FutureColors(primary = hexColor("#00FF88"), globalBg = hexColor("#001122")),
			backgroundAnimation = BackgroundAnimationType.CYBER_GRID
		)
	)
)

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

class ThemePreferences(private val context: Context) {
	private object PreferencesKeys {
		val SELECTED_THEME_INDEX = intPreferencesKey("selected_theme_index")
		val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
	}

	val selectedThemeIndexFlow: Flow<Int> = context.themeDataStore.data
	.map { preferences -> preferences[PreferencesKeys.SELECTED_THEME_INDEX] ?: 0 }

	val setupCompletedFlow: Flow<Boolean> = context.themeDataStore.data
	.map { preferences -> preferences[PreferencesKeys.SETUP_COMPLETED] ?: false }

	suspend fun updateSelectedThemeIndex(index: Int) {
		context.themeDataStore.edit { it[PreferencesKeys.SELECTED_THEME_INDEX] = index }
	}

	suspend fun markSetupCompleted() {
		context.themeDataStore.edit { it[PreferencesKeys.SETUP_COMPLETED] = true }
	}

}

class RadialPreferences(private val context: Context) {
	private object Keys {
		val RADIAL_APPS = stringPreferencesKey("radial_apps")
	}

	val flow: Flow<List<String>> = context.themeDataStore.data
	.map { prefs ->
		prefs[Keys.RADIAL_APPS]
		?.split("|")
		?.filter { it.isNotEmpty() }
		?: emptyList()
	}

	suspend fun save(packages: List<String>) {
		context.themeDataStore.edit { prefs ->
			prefs[Keys.RADIAL_APPS] = packages.joinToString("|")
		}
	}
}

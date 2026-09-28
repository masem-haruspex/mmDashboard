package com.mlib.dashboard

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.mlib.dashboard.ui.LockScreenScreen
import com.mlib.dashboard.ui.ThemePreferences
import com.mlib.dashboard.ui.themeSets
import com.mlib.future.FutureTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class LockScreenActivity : ComponentActivity() {

	private val tag = "LOCKACT"
	private lateinit var keyguardManager: KeyguardManager
	private var pendingIntent: PendingIntent? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(tag, "onCreate")

		keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

		setShowWhenLocked(true)
		val insetsController = WindowCompat.getInsetsController(window, window.decorView)
		insetsController.hide(
			WindowInsetsCompat.Type.statusBars() or
			WindowInsetsCompat.Type.navigationBars()
		)
		insetsController.systemBarsBehavior =
		WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		setTurnScreenOn(true)
		window.addFlags(
			WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
			WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
		)

		// Tell the system not to interpret swipes from the bottom strip as
		// back / home / recents. The platform caps the total exclusion zone
		// at 200dp per edge; 100dp from the bottom is enough here and well
		// under the cap.
		window.decorView.post {
			val h = (100 * resources.displayMetrics.density).toInt()
			val w = window.decorView.width
			val height = window.decorView.height
			if (w > 0 && height > 0) {
				window.decorView.systemGestureExclusionRects = listOf(
					android.graphics.Rect(0, height - h, w, height)
				)
			}
		}

		setContent {
			val context = LocalContext.current
			val themePreferences = remember { ThemePreferences(context) }
			val isDarkMode = isSystemInDarkTheme()
			val selectedThemeIndex by themePreferences.selectedThemeIndexFlow
			.collectAsState(initial = 0)
			val config = if (isDarkMode) themeSets[selectedThemeIndex].dark
			else themeSets[selectedThemeIndex].light

			FutureTheme(config = config) {
				LockScreenScreen(
					onDismiss = {
						Log.d(tag, "onDismiss (swipe up)")
						requestUnlock()
					},
					onNotificationClick = { sbn ->
						val ci = sbn.notification.contentIntent
						Log.d(tag, "onNotificationClick key=${sbn.key}, contentIntent=$ci")
						pendingIntent = ci
						requestUnlock()
					}
				)
			}
		}
	}

	private fun requestUnlock() {
		Log.d(tag, "requestUnlock: isKeyguardSecure=${keyguardManager.isKeyguardSecure}, pendingIntent=$pendingIntent")
		if (!keyguardManager.isKeyguardSecure) {
			finishThenOpenPending()
			return
		}
		keyguardManager.requestDismissKeyguard(
			this,
			object : KeyguardManager.KeyguardDismissCallback() {
				override fun onDismissSucceeded() {
					Log.d(tag, "onDismissSucceeded")
					finishThenOpenPending()
				}
				override fun onDismissCancelled() {
					Log.d(tag, "onDismissCancelled")
					pendingIntent = null
				}
				override fun onDismissError() {
					Log.e(tag, "onDismissError")
					pendingIntent = null
					finish()
				}
			}
		)
	}

	private fun finishThenOpenPending() {
		val pi = pendingIntent
		Log.d(tag, "finishThenOpenPending: pi=$pi")
		pendingIntent = null

		if (pi != null) {
			try {
				if (android.os.Build.VERSION.SDK_INT >= 34) {
					val options = android.app.ActivityOptions.makeBasic().apply {
						pendingIntentBackgroundActivityStartMode =
						android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
					}.toBundle()

					Log.d(tag, "sending PI with BAL_ALLOWED options")
					pi.send(this, 0, null, null, null, null, options)
				} else {
					Log.d(tag, "sending PI (pre-34, no options)")
					pi.send()
				}
			} catch (e: Throwable) {
				Log.e(tag, "PendingIntent.send() failed", e)
			}
		}
		finish()
	}
}

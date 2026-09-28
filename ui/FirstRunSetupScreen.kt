package com.mlib.dashboard.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.future.modifiers.*

@Composable
fun FirstRunSetupScreen(onComplete: () -> Unit) {
	val config = FutureTheme.config
	val context = LocalContext.current

	var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
	var hasNotificationAccess by remember {
		mutableStateOf(
			NotificationManagerCompat.getEnabledListenerPackages(context)
			.contains(context.packageName)
		)
	}

	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				hasOverlay = Settings.canDrawOverlays(context)
				hasNotificationAccess = NotificationManagerCompat
				.getEnabledListenerPackages(context)
				.contains(context.packageName)
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	Box(
		modifier = Modifier
		.fillMaxSize()
		.background(config.colors.globalBg)
	) {
		FutureScreenBackground(Modifier.fillMaxSize())

		Column(
			modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			FutureTextHeading("Welcome")
			Spacer(Modifier.height(8.dp))
			FutureTextMuted(
				"Two special permissions are needed for the lock screen to work.",
				modifier = Modifier.fillMaxWidth()
			)
			Spacer(Modifier.height(24.dp))

			PermissionRow(
				label = "Display over other apps",
				granted = hasOverlay,
				onGrant = {
					context.startActivity(
						Intent(
							Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
							Uri.parse("package:${context.packageName}")
						)
					)
				}
			)

			Spacer(Modifier.height(12.dp))

			PermissionRow(
				label = "Notification access",
				granted = hasNotificationAccess,
				onGrant = {
					context.startActivity(
						Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
					)
				}
			)

			Spacer(Modifier.height(32.dp))

			FutureButton(
				onClick = onComplete,
				text = if (hasOverlay && hasNotificationAccess) "Continue" else "Skip for now",
				type = ButtonType.Highlighted
			)
		}
	}
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onGrant: () -> Unit) {
	FutureCard(Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(Modifier.weight(1f)) {
				FutureText(label)
				FutureTextMuted(if (granted) "Granted" else "Not granted")
			}
			if (granted) {
				FutureIconCheckmark(color = FutureTheme.config.colors.success)
			} else {
				FutureButton(onClick = onGrant, text = "Grant")
			}
		}
	}
}

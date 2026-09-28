package com.mlib.dashboard.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlib.dashboard.NotificationListener
import com.mlib.dashboard.getGermanPhrase
import com.mlib.dashboard.isoWeekOfYear
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.future.modifiers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass

@Composable
fun LockScreenScreen(
	onDismiss: () -> Unit,
	onNotificationClick: (StatusBarNotification) -> Unit
) {
	val config = FutureTheme.config
	val context = LocalContext.current

	val listState = rememberLazyListState()
	var clock by remember { mutableStateOf(Calendar.getInstance()) }
	var phrase by remember { mutableStateOf("...") }

	val notifications by NotificationListener.notifications.collectAsState()
	val connected by NotificationListener.connected.collectAsState()

	LaunchedEffect(Unit) {
		var lastMinute = -1
		while (true) {
			val c = Calendar.getInstance()
			val minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
			if (minute != lastMinute) {
				lastMinute = minute
				clock = c
			}
			delay(1_000)
		}
	}

	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			phrase = getGermanPhrase(Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
		}
	}

	Box(
		modifier = Modifier
		.fillMaxSize()
		.background(config.colors.globalBg)
		.pointerInput(Unit) {
			val thresholdPx = 80.dp.toPx()
			awaitPointerEventScope {
				while (true) {
					val down = awaitFirstDown(
						requireUnconsumed = false,
						pass = PointerEventPass.Initial
					)
					val startY = down.position.y
					var minY = startY
					var maxY = startY
					var triggered = false

					while (!triggered) {
						val event = awaitPointerEvent(PointerEventPass.Initial)
						val change = event.changes
						.firstOrNull { it.id == down.id } ?: break
						if (!change.pressed) break

						minY = minOf(minY, change.position.y)
						maxY = maxOf(maxY, change.position.y)

						val upDist = startY - minY
						val downDist = maxY - startY

						if (upDist > thresholdPx && upDist >= downDist) {
							triggered = true
							onDismiss()
						} else if (downDist > thresholdPx && downDist > upDist &&
						!listState.canScrollBackward) {
							triggered = true
							expandNotificationShade(context)
						}
					}
				}
			}
		}
	) {
		FutureScreenBackground(modifier = Modifier.fillMaxSize())

		Column(
			modifier = Modifier
			.fillMaxSize()
			.padding(16.dp)
		) {
			LockScreenHeader(clock)

			Spacer(Modifier.height(24.dp))

			Box(
				modifier = Modifier
				.weight(1f)
				.fillMaxWidth()
			) {
				when {
					!connected -> Column(
						modifier = Modifier.align(Alignment.Center),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						FutureTextMuted("Notification access not granted")
						Spacer(Modifier.height(8.dp))
						FutureButton(
							onClick = {
								context.startActivity(
									android.content.Intent(
										android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
									)
								)
							},
							text = "Open Settings"
						)
					}
					notifications.isEmpty() -> FutureTextMuted(
						"",
						modifier = Modifier.align(Alignment.BottomCenter)
					)
					else -> LazyColumn(
						state = listState,
						modifier = Modifier.fillMaxSize(),
						verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
					) {
						items(
							notifications.sortedBy { it.postTime },
							key = { it.key }
						) { sbn ->

							SwipeableNotificationRow(
								sbn = sbn,
								onClick = {
									onNotificationClick(sbn)
								},
								onDismiss = {
									NotificationListener.cancel(sbn.key)
								}
							)
						}
					}
				}
			}

			Spacer(Modifier.height(16.dp))
		}
	}
}

@Composable
private fun SwipeableNotificationRow(
	sbn: StatusBarNotification,
	onClick: () -> Unit,
	onDismiss: () -> Unit
) {
	val config = FutureTheme.config
	val threshold = with(LocalDensity.current) { 120.dp.toPx() }
	var offsetX by remember(sbn.key) { mutableStateOf(0f) }

	Box(
		modifier = Modifier
		.fillMaxWidth()
		.graphicsLayer { translationX = offsetX }
		.pointerInput(sbn.key) {
			detectHorizontalDragGestures(
				onDragEnd = {
					if (abs(offsetX) >= threshold) {
						onDismiss()
					}
					offsetX = 0f
				},
				onDragCancel = {
					offsetX = 0f
				},
				onHorizontalDrag = { change, dragAmount ->
					change.consume()
					offsetX += dragAmount
				}
			)
		}
		.clickable {
			onClick()
		}
	) {
		LockScreenNotificationCard(sbn)
	}
}

@Composable
private fun LockScreenNotificationCard(sbn: StatusBarNotification) {
	val extras = sbn.notification.extras
	val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
	val text = extras.getString(Notification.EXTRA_TEXT) ?: ""

	FutureCard(
		modifier = Modifier.fillMaxWidth(),
		padding = PaddingValues(12.dp)
	) {
		if (title.isNotEmpty()) {
			FutureTextHeading(title)
			if (text.isNotEmpty()) Spacer(Modifier.height(4.dp))
		}
		if (text.isNotEmpty()) {
			FutureText(text)
		}
	}
}

@Composable
private fun LockScreenHeader(cal: Calendar) {
	val hh = cal.get(Calendar.HOUR_OF_DAY)
	val mi = cal.get(Calendar.MINUTE)
	val dd = cal.get(Calendar.DAY_OF_MONTH)
	val mm = cal.get(Calendar.MONTH) + 1
	val yyyy = cal.get(Calendar.YEAR)
	val week = isoWeekOfYear(cal)

	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically
	) {
		FutureTextTitle(
			"%02d:%02d".format(Locale.US, hh, mi),
			size = 34.sp,
			modifier = Modifier.weight(1f)
		)
		Column(
			horizontalAlignment = Alignment.End,
			modifier = Modifier.padding(end = 12.dp)
		) {
			FutureTextHeading("%02d.%02d.%04d".format(Locale.US, dd, mm, yyyy), size = 24.sp)
			FutureTextHeading("Week %d".format(week), size = 24.sp)
		}
	}
}


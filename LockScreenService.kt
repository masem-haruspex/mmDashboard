package com.mlib.dashboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class LockScreenService : Service() {

	// How long the screen must have been off before we treat the next
	// ACTION_SCREEN_ON as a genuine wake-up rather than a transient off/on
	// cycle (like the LineageOS double-press-power camera gesture).
	private val screenOffGraceMs = 500L
	private var lastScreenOffTime = 0L

	private val screenStateReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			when (intent.action) {
				Intent.ACTION_SCREEN_OFF -> {
					lastScreenOffTime = SystemClock.elapsedRealtime()
				}
				Intent.ACTION_SCREEN_ON -> {
					val now = SystemClock.elapsedRealtime()
					val offDuration = now - lastScreenOffTime

					if (offDuration < screenOffGraceMs) {
						return
					}

					val i = Intent(context, LockScreenActivity::class.java).apply {
						addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
						addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
					}
					context.startActivity(i)
				}
			}
		}
	}

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
		startForeground(NOTIFICATION_ID, buildNotification())

		val filter = IntentFilter().apply {
			addAction(Intent.ACTION_SCREEN_OFF)
			addAction(Intent.ACTION_SCREEN_ON)
		}
		registerReceiver(screenStateReceiver, filter)
	}

	override fun onDestroy() {
		unregisterReceiver(screenStateReceiver)
		super.onDestroy()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		return START_STICKY
	}

	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				"Lock Screen",
				NotificationManager.IMPORTANCE_MIN
			).apply {
				description = "Keeps the custom lock screen ready"
				setShowBadge(false)
			}
			getSystemService(NotificationManager::class.java)
			.createNotificationChannel(channel)
		}
	}

	private fun buildNotification(): Notification {
		return NotificationCompat.Builder(this, CHANNEL_ID)
		.setContentTitle("Dashboard")
		.setContentText("Lock screen ready")
		.setSmallIcon(android.R.drawable.ic_lock_lock)
		.setPriority(NotificationCompat.PRIORITY_MIN)
		.setOngoing(true)
		.build()
	}

	companion object {
		private const val CHANNEL_ID = "lock_screen_service"
		private const val NOTIFICATION_ID = 1001
	}
}
